package ai.firstlook.app.data

import ai.firstlook.app.model.BriefingSegment
import ai.firstlook.app.model.RouteGeometryStatus
import ai.firstlook.app.model.RouteMatchStatus
import ai.firstlook.app.model.RouteSnapshot
import ai.firstlook.app.model.RouteScoringWeights
import ai.firstlook.app.model.RouteSegmentCandidate
import ai.firstlook.app.model.RouteSource
import ai.firstlook.app.model.SegmentType
import ai.firstlook.app.model.SegmentSelectionSource
import kotlin.math.*

/**
 * Scores candidate junctions supplied by a route/map data source. It does not discover
 * intersections from a polyline by itself and deliberately returns no results without verified
 * route geometry. The source must remain attached to its own RouteSnapshot.
 */
class RouteAnalyzer(
    private val weights: RouteScoringWeights = RouteScoringWeights(),
    private val maxSegments: Int = 3,
    private val duplicateDistanceMeters: Double = 350.0,
    private val geometryMatcher: RouteGeometryMatcher = RouteGeometryMatcher(),
    private val candidateDetector: RouteCandidateDetector = RouteCandidateDetector(),
) {
    init {
        require(maxSegments in 1..5)
        require(duplicateDistanceMeters >= 0.0)
    }

    fun select(route: RouteSnapshot, candidates: List<RouteSegmentCandidate>): List<BriefingSegment> {
        if (route.source == RouteSource.CURATED_DEMO || route.geometryStatus != RouteGeometryStatus.VERIFIED) return emptyList()
        if (route.geometry.size < 2) return emptyList()

        val ranked = candidates
            .asSequence()
            .mapNotNull { candidate ->
                when (val match = geometryMatcher.match(route, candidate)) {
                    is RouteGeometryMatch.Verified -> candidate.copy(
                        routeOffsetMeters = match.routeOffsetMeters,
                        approachCoordinate = match.approachCoordinate,
                        routeMatchStatus = RouteMatchStatus.VERIFIED,
                    )
                    is RouteGeometryMatch.Rejected -> null
                }
            }
            // Verified geometry is the authority for candidate offsets; distanceKm may be a
            // rounded provider summary and can truncate an otherwise valid route tail.
            .filter { it.routeOffsetMeters >= 0.0 }
            .map { candidate -> candidate to score(candidate) }
            .sortedWith(compareByDescending<Pair<RouteSegmentCandidate, Double>> { it.second }
                .thenBy { it.first.routeOffsetMeters }
                .thenBy { it.first.id })
            .toList()

        val selected = mutableListOf<Pair<RouteSegmentCandidate, Double>>()
        for ((candidate, score) in ranked) {
            if (selected.none { (existing, _) -> isDuplicate(candidate, existing) }) {
                selected += candidate to score
                if (selected.size == maxSegments) break
            }
        }

        return selected.map { (candidate, score) ->
            val verifiedGuidance = candidate.verifiedGuidance?.takeIf(String::isNotBlank)
            BriefingSegment(
                id = candidate.id,
                type = candidate.type,
                name = candidate.name,
                description = candidate.description,
                guidance = verifiedGuidance ?: genericGuidance(candidate.type),
                distanceFromStartKm = candidate.routeOffsetMeters / 1000.0,
                confidenceLabel = "경로 분석 · ${route.source.name}",
                laneGuidanceVerified = candidate.laneGuidanceVerified && verifiedGuidance != null,
                coordinate = candidate.coordinate,
                approachCoordinate = candidate.approachCoordinate,
                headingDegrees = candidate.headingDegrees?.takeIf { it in 0.0..360.0 },
                selectionSource = SegmentSelectionSource.ROUTE_ANALYSIS,
                selectionScore = score,
                selectionEvidence = candidate.evidence,
                routeMatchStatus = candidate.routeMatchStatus,
                junctionRecord = candidate.junctionRecord,
                routeLanes = candidate.routeLanes,
            )
        }
    }

    /** Discovers geometry-based turns, then ranks them with any supplied map/risk features. */
    fun analyze(
        route: RouteSnapshot,
        features: List<RouteMapFeature> = emptyList(),
        additionalCandidates: List<RouteSegmentCandidate> = emptyList(),
    ): List<BriefingSegment> = select(route, candidateDetector.detect(route, features) + additionalCandidates)

    private fun score(candidate: RouteSegmentCandidate): Double =
        (candidate.complexity.clamped() * weights.complexity) +
            (candidate.maneuverDensity.clamped() * weights.maneuverDensity) +
            (candidate.highwayJunction.clamped() * weights.highwayJunction) +
            (candidate.destinationProximity.clamped() * weights.destinationProximity) +
            (candidate.historicIncidentSignal.clamped() * weights.historicIncidentSignal) +
            (candidate.usableRoadView.clamped() * weights.usableRoadView) +
            (candidate.dataConfidence.clamped() * weights.dataConfidence)

    private fun isDuplicate(candidate: RouteSegmentCandidate, existing: RouteSegmentCandidate): Boolean =
        abs(candidate.routeOffsetMeters - existing.routeOffsetMeters) <= duplicateDistanceMeters ||
            haversineMeters(candidate.coordinate.latitude, candidate.coordinate.longitude,
                existing.coordinate.latitude, existing.coordinate.longitude) <= 80.0

    private fun genericGuidance(type: SegmentType): String = when (type) {
        SegmentType.INTERSECTION -> "교차로 구조와 표지를 확인하세요."
        SegmentType.HIGHWAY_JUNCTION -> "분기점 안내 표지를 확인하세요."
        SegmentType.DESTINATION_ENTRY -> "목적지 주변 도로 안내를 확인하세요."
        SegmentType.COMPLEX_TURN -> "연속 회전 구간의 진행 방향을 미리 확인하세요."
        SegmentType.HISTORICAL_RISK -> "과거 사고 통계가 있는 구간입니다. 현재 위험도를 뜻하지는 않습니다."
    }

    private fun Double.clamped() = coerceIn(0.0, 1.0)

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

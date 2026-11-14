package ai.dejavroom.app.domain

import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.RouteGeometryStatus
import ai.dejavroom.app.model.RouteMatchStatus
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.RouteScoringWeights
import ai.dejavroom.app.model.RouteSegmentCandidate
import ai.dejavroom.app.model.RouteSource
import ai.dejavroom.app.model.SegmentType
import ai.dejavroom.app.model.SegmentSelectionSource
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Picks the few points of a route worth previewing and decides where each preview stands.
 *
 * 1. Candidates must lie on the verified route geometry.
 * 2. The surroundings of the origin are skipped: that is the one place the driver already knows.
 * 3. Everything from the first maneuver near the end onward becomes a single destination card.
 * 4. The rest compete by score. Easy points never pad the list, and near-duplicates are dropped.
 * 5. Each pick gets a viewpoint before the junction ([ApproachPolicy]) and the result is returned in
 *    the order the driver will meet the points, with [BriefingSegment.selectionRank] keeping the ranking.
 *
 * It deliberately returns nothing without verified route geometry, and the source must remain attached
 * to its own RouteSnapshot.
 */
class RouteAnalyzer(
    private val weights: RouteScoringWeights = RouteScoringWeights(),
    private val selection: SelectionRules = SelectionRules(),
    private val approachPolicy: ApproachPolicy = ApproachPolicy(),
    private val geometryMatcher: RouteGeometryMatcher = RouteGeometryMatcher(),
    private val candidateDetector: RouteCandidateDetector = RouteCandidateDetector(),
) {

    fun select(route: RouteSnapshot, candidates: List<RouteSegmentCandidate>): List<BriefingSegment> {
        if (route.source == RouteSource.CURATED_DEMO || route.geometryStatus != RouteGeometryStatus.VERIFIED) return emptyList()
        if (route.geometry.size < 2) return emptyList()
        val polyline = RoutePolyline(route.geometry)
        val routeLength = polyline.lengthMeters
        if (routeLength <= 0.0) return emptyList()

        // Verified geometry is the authority for candidate offsets; distanceKm may be a rounded
        // provider summary and can truncate an otherwise valid route tail.
        val verified = candidates.mapNotNull { candidate ->
            when (val match = geometryMatcher.match(route, candidate)) {
                is RouteGeometryMatch.Verified -> candidate.copy(
                    routeOffsetMeters = match.routeOffsetMeters,
                    routeMatchStatus = RouteMatchStatus.VERIFIED,
                )
                is RouteGeometryMatch.Rejected -> null
            }
        }.filter { it.routeOffsetMeters >= 0.0 }
        val (destinationPool, junctionPool) = verified.partition { it.isDestination }

        // The final approach starts at the destination entrance or at the first maneuver close to the end,
        // whichever comes first. It only exists when the source says where the trip ends.
        val arrivalZoneStart = routeLength - min(selection.arrivalZoneMeters, routeLength * EDGE_SHARE)
        val destinationAnchor = if (destinationPool.isEmpty()) null else
            (destinationPool + junctionPool.filter { !it.passThrough && it.routeOffsetMeters >= arrivalZoneStart })
                .minWithOrNull(compareBy<RouteSegmentCandidate> { it.routeOffsetMeters }.thenBy { it.id })
        val finalApproachStart = destinationAnchor?.routeOffsetMeters ?: Double.MAX_VALUE
        val departureGuard = min(selection.departureGuardMeters, routeLength * EDGE_SHARE)

        val ranked = junctionPool
            .filter { it.routeOffsetMeters >= departureGuard && it.routeOffsetMeters < finalApproachStart }
            .map { candidate -> candidate to candidate.scoreWith(weights) }
            .sortedWith(compareByDescending<Pair<RouteSegmentCandidate, Double>> { it.second }
                .thenBy { it.first.routeOffsetMeters }
                .thenBy { it.first.id })

        val junctionSlots = selection.maxCards - if (destinationAnchor != null) 1 else 0
        val picked = mutableListOf<Pair<RouteSegmentCandidate, Double>>()
        for ((candidate, score) in ranked) {
            if (picked.size >= junctionSlots) break
            // Sorted by score, so everything after the first easy point is easy too.
            if (picked.isNotEmpty() && score < selection.minimumScore) break
            if (picked.any { (existing, _) -> candidate.isNear(existing, selection.duplicateDistanceMeters) }) continue
            // Decisions that follow each other closely share one card. When an earlier one matters about
            // as much, the card starts there: missing the first decision makes the second one moot.
            val earlierPeer = ranked
                .filter { (other, otherScore) ->
                    other.routeOffsetMeters < candidate.routeOffsetMeters &&
                        candidate.routeOffsetMeters - other.routeOffsetMeters <= selection.duplicateDistanceMeters &&
                        otherScore >= score - selection.nearTieScore &&
                        picked.none { (existing, _) -> other.isNear(existing, selection.duplicateDistanceMeters) }
                }
                .minByOrNull { it.first.routeOffsetMeters }
            picked += earlierPeer ?: (candidate to score)
        }

        val segments = mutableListOf<Pair<Double, BriefingSegment>>()
        picked.forEachIndexed { index, (candidate, score) ->
            // The destination card always takes rank 2, right after the single most important junction.
            val rank = if (destinationAnchor != null && index >= 1) index + 2 else index + 1
            segments += candidate.routeOffsetMeters to segment(route, polyline, candidate, score, rank)
        }
        if (destinationAnchor != null) {
            val folded = junctionPool.count { !it.passThrough && it.routeOffsetMeters >= finalApproachStart && it.id != destinationAnchor.id }
            segments += destinationAnchor.routeOffsetMeters to destinationSegment(
                route, polyline, destinationAnchor, destinationPool, folded, rank = if (picked.isEmpty()) 1 else 2,
            )
        }
        return segments.sortedBy { it.first }.map { it.second }
    }

    /** Ranks map/risk features; falls back to the route's shape only when a source reports no features. */
    fun analyze(
        route: RouteSnapshot,
        features: List<RouteMapFeature> = emptyList(),
        additionalCandidates: List<RouteSegmentCandidate> = emptyList(),
    ): List<BriefingSegment> = select(route, candidateDetector.detect(route, features) + additionalCandidates)

    private fun segment(
        route: RouteSnapshot,
        polyline: RoutePolyline,
        candidate: RouteSegmentCandidate,
        score: Double,
        rank: Int,
        type: SegmentType = candidate.type,
        name: String = candidate.name,
        description: String = candidate.description,
        evidence: List<String> = candidate.evidence,
    ): BriefingSegment {
        val lead = approachPolicy.leadMeters(candidate.approach, candidate.routeOffsetMeters)
        val approach = polyline.viewpointBefore(candidate.routeOffsetMeters, lead)
        val verifiedGuidance = candidate.verifiedGuidance?.takeIf(String::isNotBlank)
        return BriefingSegment(
            id = candidate.id,
            type = type,
            name = name,
            description = description,
            guidance = verifiedGuidance ?: genericGuidance(type),
            distanceFromStartKm = candidate.routeOffsetMeters / 1000.0,
            confidenceLabel = "경로 분석 · ${route.source.name}",
            laneGuidanceVerified = candidate.laneGuidanceVerified && verifiedGuidance != null,
            coordinate = candidate.coordinate,
            approachCoordinate = approach.coordinate,
            headingDegrees = candidate.headingDegrees?.takeIf { it in 0.0..360.0 },
            selectionSource = SegmentSelectionSource.ROUTE_ANALYSIS,
            selectionScore = score,
            selectionEvidence = evidence,
            routeMatchStatus = candidate.routeMatchStatus,
            junctionRecord = candidate.junctionRecord,
            routeLanes = candidate.routeLanes,
            approach = approach,
            selectionRank = rank,
            cue = candidate.cue,
        )
    }

    /** One card for the whole final approach, anchored where it begins. */
    private fun destinationSegment(
        route: RouteSnapshot,
        polyline: RoutePolyline,
        anchor: RouteSegmentCandidate,
        destinationPool: List<RouteSegmentCandidate>,
        foldedManeuvers: Int,
        rank: Int,
    ): BriefingSegment {
        val arrival = destinationPool.filter { it.isArrivalPoint }.maxByOrNull { it.routeOffsetMeters }
        val remainingMeters = (arrival?.routeOffsetMeters ?: polyline.lengthMeters) - anchor.routeOffsetMeters
        val (reasons, provenance) = anchor.evidence.partition { !it.startsWith(PROVENANCE_PREFIX) }
        val evidence = buildList {
            // Where it starts: a junction on the way in goes by its own name, a destination feature says it itself.
            val place = if (anchor.isDestination) reasons.firstOrNull() else anchor.name
            if (place != null) add(place)
            if (!anchor.isArrivalPoint) add("도착 ${roundToTens(remainingMeters)}m 전부터 진입")
            addAll(reasons.filterNot { it == place }.take(2))
            if (foldedManeuvers > 0) add("그 뒤로 조작 ${foldedManeuvers}회 더")
            if (arrival != null && arrival.id != anchor.id) addAll(arrival.evidence.filterNot { it.startsWith(PROVENANCE_PREFIX) })
            addAll(provenance)
        }.distinct()
        val name = if (anchor.isArrivalPoint) "도착 지점" else "목적지 진입"
        return segment(
            route, polyline, anchor, anchor.scoreWith(weights), rank,
            type = SegmentType.DESTINATION_ENTRY,
            name = name,
            description = evidence.firstOrNull()?.let { "$name · $it" } ?: name,
            evidence = evidence,
        )
    }

    private fun genericGuidance(type: SegmentType): String = when (type) {
        SegmentType.INTERSECTION -> "교차로 구조와 표지를 확인하세요."
        SegmentType.HIGHWAY_JUNCTION -> "분기점 안내 표지를 확인하세요."
        SegmentType.DESTINATION_ENTRY -> "목적지 주변 도로 안내를 확인하세요."
        SegmentType.COMPLEX_TURN -> "연속 회전 구간의 진행 방향을 미리 확인하세요."
        SegmentType.HISTORICAL_RISK -> "과거 사고 통계가 있는 구간입니다. 현재 위험도를 뜻하지는 않습니다."
    }

    private fun roundToTens(meters: Double): Int = ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10)

    private companion object {
        /** Guards at either end never cover more than this share of a short route. */
        const val EDGE_SHARE = 0.15
        const val PROVENANCE_PREFIX = "도로 그래프"
    }
}

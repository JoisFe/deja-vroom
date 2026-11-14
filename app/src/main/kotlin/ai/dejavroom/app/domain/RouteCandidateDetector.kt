package ai.dejavroom.app.domain

import ai.dejavroom.app.model.ApproachConditions
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RouteGeometryStatus
import ai.dejavroom.app.model.RouteSegmentCandidate
import ai.dejavroom.app.model.RouteLane
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.RouteSource
import ai.dejavroom.app.model.SegmentType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Map or public-data observation attached to a coordinate; historical incidents are not current danger. */
data class RouteMapFeature(
    val id: String,
    val type: Type,
    val name: String,
    val description: String,
    val coordinate: GeoCoordinate,
    val source: String,
    val confidence: Double,
    val complexitySignal: Double = 0.0,
    val maneuverDensitySignal: Double = 0.0,
    val highwayJunctionSignal: Double = 0.0,
    val branchCount: Int = 0,
    val historicalIncidentSignal: Double = 0.0,
    val routeLanes: List<RouteLane> = emptyList(),
    val ambiguitySignal: Double = 0.0,
    val errorCostSignal: Double = 0.0,
    val lanePressureSignal: Double = 0.0,
    /** Korean reasons, strongest first, shown to the driver as "why this point". */
    val reasons: List<String> = emptyList(),
    /** How the point is reached; decides how early its preview starts. */
    val approach: ApproachConditions = ApproachConditions.UNKNOWN,
    /** Driven straight through: a weak candidate that never counts toward a chain of maneuvers. */
    val passThrough: Boolean = false,
) {
    enum class Type { COMPLEX_INTERSECTION, HIGHWAY_JUNCTION, DESTINATION_ACCESS, HISTORICAL_ACCIDENT_HOTSPOT }

    init {
        require(id.isNotBlank() && name.isNotBlank() && source.isNotBlank())
        require(confidence.isFinite() && confidence in 0.0..1.0)
        require(complexitySignal.isFinite() && complexitySignal in 0.0..1.0)
        require(maneuverDensitySignal.isFinite() && maneuverDensitySignal in 0.0..1.0)
        require(highwayJunctionSignal.isFinite() && highwayJunctionSignal in 0.0..1.0)
        require(branchCount >= 0)
        require(historicalIncidentSignal.isFinite() && historicalIncidentSignal in 0.0..1.0)
        require(ambiguitySignal in 0.0..1.0 && errorCostSignal in 0.0..1.0 && lanePressureSignal in 0.0..1.0)
    }
}

/**
 * Creates candidates from map/public-data features and, only when a route comes with none, from its shape.
 * Geometry alone can identify sharp or clustered turns; it cannot prove intersections, lane layout,
 * highway exits, crash risk, or current road danger, and it mistakes parking-lot wiggles and road bends
 * for decisions. A source that reports maneuvers therefore replaces shape detection instead of adding to it.
 */
class RouteCandidateDetector(
    private val turnThresholdDegrees: Double = 35.0,
    private val headingWindowMeters: Double = 35.0,
    private val clusterGapMeters: Double = 300.0,
    private val approachLeadMeters: Double = 200.0,
) {
    init {
        require(turnThresholdDegrees in 0.0..180.0)
        require(headingWindowMeters > 0.0)
        require(clusterGapMeters >= 0.0)
        require(approachLeadMeters >= 0.0)
    }

    fun detect(
        route: RouteSnapshot,
        features: List<RouteMapFeature> = emptyList(),
        includeShapeTurns: Boolean = features.isEmpty(),
    ): List<RouteSegmentCandidate> {
        if (route.source == RouteSource.CURATED_DEMO || route.geometryStatus != RouteGeometryStatus.VERIFIED || route.geometry.size < 2) {
            return emptyList()
        }
        val offsets = cumulativeOffsets(route.geometry)
        val routeLength = offsets.lastOrNull()?.takeIf { it > 0.0 } ?: return emptyList()
        val turns = route.geometry.indices
            .drop(1)
            .dropLast(1)
            .mapNotNull { index ->
                val offset = offsets[index]
                if (offset <= 0.0 || offset >= routeLength) return@mapNotNull null
                val before = coordinateAtOffset(route.geometry, offsets, (offset - headingWindowMeters).coerceAtLeast(0.0))
                val after = coordinateAtOffset(route.geometry, offsets, (offset + headingWindowMeters).coerceAtMost(routeLength))
                val incoming = bearing(before, route.geometry[index])
                val outgoing = bearing(route.geometry[index], after)
                val angle = angularDifference(incoming, outgoing)
                if (angle < turnThresholdDegrees) null else Turn(index, offset, angle)
            }

        val candidates = mutableListOf<RouteSegmentCandidate>()
        val shapeTurns = if (includeShapeTurns) turns.groupConsecutive(clusterGapMeters) else emptyList()
        shapeTurns.forEachIndexed { clusterIndex, cluster ->
            val strongest = cluster.maxBy { it.angleDegrees }
            val angleStrength = ((strongest.angleDegrees - turnThresholdDegrees) / (110.0 - turnThresholdDegrees)).coerceIn(0.0, 1.0)
            val density = ((cluster.size - 1) / 2.0).coerceIn(0.0, 1.0)
            val offset = strongest.offsetMeters
            // Nudge the matching point just past the corner so its bearing belongs to the outgoing leg.
            val matchOffset = (offset + 5.0).coerceAtMost(routeLength)
            candidates += RouteSegmentCandidate(
                id = "${route.routeId}-turn-${clusterIndex + 1}",
                type = SegmentType.COMPLEX_TURN,
                name = if (cluster.size > 1) "연속 회전 구간" else "급격한 진행 방향 전환",
                description = if (cluster.size > 1) "약 ${cluster.size}회의 방향 전환이 ${clusterGapMeters.toInt()}m 이내에 이어집니다." else "경로 형상에서 약 ${strongest.angleDegrees.toInt()}° 방향 전환이 감지됐습니다.",
                routeOffsetMeters = matchOffset,
                coordinate = coordinateAtOffset(route.geometry, offsets, matchOffset),
                approachCoordinate = coordinateAtOffset(route.geometry, offsets, (offset - approachLeadMeters).coerceAtLeast(0.0)),
                headingDegrees = bearing(
                    coordinateAtOffset(route.geometry, offsets, matchOffset),
                    coordinateAtOffset(route.geometry, offsets, (matchOffset + 20.0).coerceAtMost(routeLength)),
                ),
                // Shape alone cannot tell a turn at a junction from a bend in the road.
                complexity = angleStrength * GEOMETRY_ONLY_FACTOR,
                maneuverDensity = density,
                destinationProximity = (offset / routeLength).takeIf { it >= 0.85 }?.let { ((it - 0.85) / 0.15).coerceIn(0.0, 1.0) } ?: 0.0,
                dataConfidence = 0.55,
                evidence = listOfNotNull(
                    "방향 전환 ${strongest.angleDegrees.toInt()}°",
                    if (cluster.size > 1) "${clusterGapMeters.toInt()}m 안에 전환 ${cluster.size}회" else null,
                    "경로 형상만으로 추정",
                ),
                sourceRouteId = route.routeId,
            )
        }

        features.forEach { feature ->
            val projection = projectOntoRoute(feature.coordinate, route.geometry, offsets) ?: return@forEach
            // Direction of travel just past the point; at the very end of the route, just before it.
            val headingFrom = if (projection.offsetMeters + 20.0 <= routeLength) projection.offsetMeters else (routeLength - 20.0).coerceAtLeast(0.0)
            val routePoint = coordinateAtOffset(route.geometry, offsets, headingFrom)
            val forwardPoint = coordinateAtOffset(route.geometry, offsets, (headingFrom + 20.0).coerceAtMost(routeLength))
            val segmentType = when (feature.type) {
                RouteMapFeature.Type.COMPLEX_INTERSECTION -> SegmentType.INTERSECTION
                RouteMapFeature.Type.HIGHWAY_JUNCTION -> SegmentType.HIGHWAY_JUNCTION
                RouteMapFeature.Type.DESTINATION_ACCESS -> SegmentType.DESTINATION_ENTRY
                RouteMapFeature.Type.HISTORICAL_ACCIDENT_HOTSPOT -> SegmentType.HISTORICAL_RISK
            }
            candidates += RouteSegmentCandidate(
                id = feature.id,
                type = segmentType,
                name = feature.name,
                description = feature.description,
                routeOffsetMeters = projection.offsetMeters,
                coordinate = feature.coordinate,
                approachCoordinate = coordinateAtOffset(route.geometry, offsets, (projection.offsetMeters - approachLeadMeters).coerceAtLeast(0.0)),
                headingDegrees = bearing(routePoint, forwardPoint),
                complexity = feature.complexitySignal,
                maneuverDensity = feature.maneuverDensitySignal,
                highwayJunction = feature.highwayJunctionSignal,
                ambiguity = feature.ambiguitySignal,
                errorCost = feature.errorCostSignal,
                lanePressure = feature.lanePressureSignal,
                destinationProximity = if (feature.type == RouteMapFeature.Type.DESTINATION_ACCESS) 1.0 else destinationProximity(projection.offsetMeters, routeLength),
                historicIncidentSignal = feature.historicalIncidentSignal.coerceIn(0.0, 1.0),
                dataConfidence = feature.confidence.coerceIn(0.0, 1.0),
                routeLanes = feature.routeLanes,
                approach = feature.approach,
                passThrough = feature.passThrough,
                evidence = feature.reasons
                    .plus(if (feature.type == RouteMapFeature.Type.HISTORICAL_ACCIDENT_HOTSPOT) emptyList() else listOf("도로 그래프: ${feature.source}"))
                    .plus(
                    if (feature.type == RouteMapFeature.Type.HISTORICAL_ACCIDENT_HOTSPOT) listOf("과거 사고 이력 신호 · 현재 위험도 의미 아님") else emptyList(),
                ),
                sourceRouteId = route.routeId,
            )
        }
        return withManeuverDensity(candidates)
    }

    /** Raises density for map maneuvers that sit within [clusterGapMeters] of other map maneuvers. */
    private fun withManeuverDensity(candidates: List<RouteSegmentCandidate>): List<RouteSegmentCandidate> {
        val mapManeuvers = candidates.filter {
            it.type != SegmentType.COMPLEX_TURN && it.type != SegmentType.HISTORICAL_RISK && !it.passThrough && !it.isArrivalPoint
        }
        return candidates.map { candidate ->
            if (candidate !in mapManeuvers) return@map candidate
            val neighbors = mapManeuvers.count { it !== candidate && abs(it.routeOffsetMeters - candidate.routeOffsetMeters) <= clusterGapMeters }
            if (neighbors == 0) return@map candidate
            candidate.copy(
                maneuverDensity = maxOf(candidate.maneuverDensity, (neighbors / 2.0).coerceIn(0.0, 1.0)),
                evidence = candidate.evidence + "${clusterGapMeters.toInt()}m 안에 조작 ${neighbors + 1}회",
            )
        }
    }

    private fun destinationProximity(offset: Double, routeLength: Double): Double =
        (offset / routeLength).takeIf { it >= 0.85 }?.let { ((it - 0.85) / 0.15).coerceIn(0.0, 1.0) } ?: 0.0

    private data class Turn(val pointIndex: Int, val offsetMeters: Double, val angleDegrees: Double)
    private data class Projection(val offsetMeters: Double, val lateralDistanceMeters: Double)

    private fun List<Turn>.groupConsecutive(maxGapMeters: Double): List<List<Turn>> {
        if (isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<Turn>>()
        for (turn in this) {
            val current = groups.lastOrNull()
            if (current == null || turn.offsetMeters - current.last().offsetMeters > maxGapMeters) groups += mutableListOf(turn)
            else current += turn
        }
        return groups
    }

    private fun cumulativeOffsets(points: List<GeoCoordinate>): List<Double> {
        val result = mutableListOf(0.0)
        points.zipWithNext().forEach { (a, b) -> result += result.last() + distanceMeters(a, b) }
        return result
    }

    private fun coordinateAtOffset(points: List<GeoCoordinate>, offsets: List<Double>, requested: Double): GeoCoordinate {
        val target = requested.coerceIn(0.0, offsets.last())
        val index = (0 until offsets.lastIndex).firstOrNull { offsets[it + 1] >= target } ?: points.lastIndex - 1
        val span = offsets[index + 1] - offsets[index]
        val fraction = if (span <= 0.0) 0.0 else ((target - offsets[index]) / span).coerceIn(0.0, 1.0)
        return GeoCoordinate(
            latitude = points[index].latitude + (points[index + 1].latitude - points[index].latitude) * fraction,
            longitude = points[index].longitude + (points[index + 1].longitude - points[index].longitude) * fraction,
        )
    }

    private fun projectOntoRoute(point: GeoCoordinate, points: List<GeoCoordinate>, offsets: List<Double>): Projection? =
        points.zipWithNext().mapIndexedNotNull { index, (start, end) ->
            val latitude = Math.toRadians((start.latitude + end.latitude + point.latitude) / 3.0)
            val sx = Math.toRadians(start.longitude - point.longitude) * EARTH_RADIUS * cos(latitude)
            val sy = Math.toRadians(start.latitude - point.latitude) * EARTH_RADIUS
            val ex = Math.toRadians(end.longitude - point.longitude) * EARTH_RADIUS * cos(latitude)
            val ey = Math.toRadians(end.latitude - point.latitude) * EARTH_RADIUS
            val dx = ex - sx
            val dy = ey - sy
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared <= 0.0) return@mapIndexedNotNull null
            val fraction = (-(sx * dx + sy * dy) / lengthSquared).coerceIn(0.0, 1.0)
            Projection(offsets[index] + (offsets[index + 1] - offsets[index]) * fraction, hypot(sx + fraction * dx, sy + fraction * dy))
        }.minByOrNull { it.lateralDistanceMeters }?.takeIf { it.lateralDistanceMeters <= 80.0 }

    private fun distanceMeters(a: GeoCoordinate, b: GeoCoordinate): Double {
        val meanLatitude = Math.toRadians((a.latitude + b.latitude) / 2.0)
        return hypot(
            Math.toRadians(b.longitude - a.longitude) * EARTH_RADIUS * cos(meanLatitude),
            Math.toRadians(b.latitude - a.latitude) * EARTH_RADIUS,
        )
    }

    private fun bearing(a: GeoCoordinate, b: GeoCoordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        return (Math.toDegrees(atan2(sin(dLon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))) + 360.0) % 360.0
    }

    private fun angularDifference(a: Double, b: Double): Double {
        val difference = abs(a - b) % 360.0
        return minOf(difference, 360.0 - difference)
    }

    private companion object {
        const val EARTH_RADIUS = 6_371_000.0
        const val GEOMETRY_ONLY_FACTOR = 0.6
    }
}

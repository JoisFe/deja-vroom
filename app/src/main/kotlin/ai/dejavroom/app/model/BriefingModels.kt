package ai.dejavroom.app.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class SegmentType(val label: String) {
    INTERSECTION("복잡한 교차로"), HIGHWAY_JUNCTION("고속도로 분기점"), DESTINATION_ENTRY("목적지 진입"),
    COMPLEX_TURN("연속 회전 구간"), HISTORICAL_RISK("사고 이력 참고")
}

enum class RouteSource { PLEOS_NAVIGATION, EXTERNAL_ROUTE, CURATED_DEMO }

enum class RouteGeometryStatus { VERIFIED, UNAVAILABLE, UNKNOWN }

enum class SegmentSelectionSource { ROUTE_ANALYSIS, PUBLIC_DATA, CURATED_DEMO }

enum class RouteMatchStatus { VERIFIED, UNVERIFIED, MISMATCH, STALE, NO_COVERAGE, NOT_APPLICABLE }

enum class RoadViewContentOrigin { LIVE_PROVIDER, CURATED, DEMO_ILLUSTRATION }

data class GeoCoordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }

    /** Ground distance in meters. Flat-earth approximation: accurate to well under a meter at junction scale. */
    fun distanceMetersTo(other: GeoCoordinate): Double {
        val meanLatitude = Math.toRadians((latitude + other.latitude) / 2.0)
        return hypot(
            Math.toRadians(other.longitude - longitude) * EARTH_RADIUS_METERS * cos(meanLatitude),
            Math.toRadians(other.latitude - latitude) * EARTH_RADIUS_METERS,
        )
    }

    /** Compass bearing toward [other]: 0..360 with north = 0, east = 90. */
    fun bearingTo(other: GeoCoordinate): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        return (Math.toDegrees(atan2(sin(dLon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))) + 360.0) % 360.0
    }

    private companion object { const val EARTH_RADIUS_METERS = 6_371_000.0 }
}

data class RouteLane(val indications: List<String>, val validForRoute: Boolean)

/**
 * Where a preview "stands": on the route, [leadMeters] before the decision point, facing the way the driver
 * will be travelling. Showing a junction from here instead of from on top of it is what makes it a preview.
 */
data class ApproachViewpoint(val coordinate: GeoCoordinate, val headingDegrees: Double, val leadMeters: Int)

/** Which way the route goes at a point. [degrees] is clockwise from straight ahead. */
enum class TurnDirection(val degrees: Int) {
    STRAIGHT(0), SLIGHT_RIGHT(45), RIGHT(90), SHARP_RIGHT(135), UTURN(180), SHARP_LEFT(-135), LEFT(-90), SLIGHT_LEFT(-45);

    val isRight: Boolean get() = degrees in 1..179
    val isLeft: Boolean get() = degrees < 0

    companion object {
        /** From a routing source's turn modifier ("slight right", "uturn", ...); null when it has none. */
        fun fromModifier(modifier: String?): TurnDirection? = when (modifier?.trim()?.lowercase()) {
            "straight" -> STRAIGHT
            "slight right" -> SLIGHT_RIGHT
            "right" -> RIGHT
            "sharp right" -> SHARP_RIGHT
            "uturn" -> UTURN
            "sharp left" -> SHARP_LEFT
            "left" -> LEFT
            "slight left" -> SLIGHT_LEFT
            else -> null
        }
    }
}

/**
 * What to do at a point: the direction as an arrow, the action in a driver's words, and the sign to follow
 * when map data records one. The direction is the computed route's own maneuver, never a lane number.
 */
data class ManeuverCue(val direction: TurnDirection, val action: String, val sign: String = "") {
    init { require(action.isNotBlank()) }

    /** One line for a card: "오른쪽 진출로 · 표지 양재". */
    val summary: String get() = if (sign.isBlank()) action else "$action · 표지 $sign"
}

/** How a point is reached. Together with the approach policy this decides how early its preview starts. */
data class ApproachConditions(
    /** Typical speed on the road leading in. */
    val speedMps: Double? = null,
    /** The road leading in is highway-grade: decisions come fast and signs start early. */
    val onHighway: Boolean = false,
    /** Distance back to the previous maneuver that needed an action; the preview never starts before it. */
    val previousManeuverGapMeters: Double? = null,
    /** The point is the destination itself rather than a junction on the way. */
    val arrival: Boolean = false,
) {
    init {
        require(speedMps == null || (speedMps.isFinite() && speedMps >= 0.0))
        require(previousManeuverGapMeters == null || (previousManeuverGapMeters.isFinite() && previousManeuverGapMeters >= 0.0))
    }

    companion object { val UNKNOWN = ApproachConditions() }
}

data class BriefingSegment(
    val id: String,
    val type: SegmentType,
    val name: String,
    val description: String,
    val guidance: String,
    val distanceFromStartKm: Double,
    val confidenceLabel: String = "데모 데이터 · 실제 도로 검증 안 됨",
    val laneGuidanceVerified: Boolean = false,
    val coordinate: GeoCoordinate? = null,
    val approachCoordinate: GeoCoordinate? = null,
    val headingDegrees: Double? = null,
    val selectionSource: SegmentSelectionSource = SegmentSelectionSource.CURATED_DEMO,
    val selectionScore: Double? = null,
    val selectionEvidence: List<String> = emptyList(),
    val routeMatchStatus: RouteMatchStatus = RouteMatchStatus.NOT_APPLICABLE,
    val roadViewOrigin: RoadViewContentOrigin = RoadViewContentOrigin.DEMO_ILLUSTRATION,
    val roadViewProviderName: String = "Déjà Vroom demo artwork",
    val roadViewCapturedAt: String? = null,
    val roadViewAttribution: String? = "자체 제작 데모 일러스트 · 실제 도로 사진 아님",
    val roadViewAllowsOverlay: Boolean = false,
    val junctionRecord: JunctionRecord? = null,
    val routeLanes: List<RouteLane> = emptyList(),
    /** Viewpoint before the decision point; road views start here. Null for points without route geometry. */
    val approach: ApproachViewpoint? = null,
    /** 1 = most worth previewing. 0 = unranked. Segments themselves are listed in route order. */
    val selectionRank: Int = 0,
    /** Which way to go here and what to look for; null when the source does not say. */
    val cue: ManeuverCue? = null,
) {
    /** Where its road view starts: the approach viewpoint when there is one, else the point itself. */
    val previewCoordinate: GeoCoordinate? get() = approach?.coordinate ?: coordinate

    /** Which way its road view faces: along the route at the viewpoint. */
    val previewHeadingDegrees: Double? get() = approach?.headingDegrees ?: headingDegrees

    /** A photo overlay requires verified maneuver data, route matching, and provider permission. */
    val canShowSpecificGuidance: Boolean
        get() = laneGuidanceVerified &&
            routeMatchStatus == RouteMatchStatus.VERIFIED &&
            roadViewOrigin != RoadViewContentOrigin.DEMO_ILLUSTRATION &&
            roadViewAllowsOverlay
}

data class RouteSnapshot(
    val origin: String,
    val destination: String,
    val durationMinutes: Int,
    val distanceKm: Int,
    val segments: List<BriefingSegment>,
    val routeId: String = "curated-demo",
    val source: RouteSource = RouteSource.CURATED_DEMO,
    val geometryStatus: RouteGeometryStatus = RouteGeometryStatus.UNAVAILABLE,
    val geometry: List<GeoCoordinate> = emptyList(),
) {
    /**
     * The [count] most important segments in the order the driver meets them. Each keeps its index in
     * [segments], which the detail screen and its previous/next buttons use.
     */
    fun mostImportantSegments(count: Int): List<IndexedValue<BriefingSegment>> {
        val indexed = segments.withIndex().toList()
        // Unranked lists (curated demo routes) have no order of importance; show the first ones.
        return if (segments.none { it.selectionRank > 0 }) indexed.take(count) else indexed.filter { it.value.selectionRank in 1..count }
    }
}

/** A candidate is accepted only when it is tied to a verified, source-specific route snapshot. */
data class RouteSegmentCandidate(
    val id: String,
    val type: SegmentType,
    val name: String,
    val description: String,
    val routeOffsetMeters: Double,
    val coordinate: GeoCoordinate,
    val approachCoordinate: GeoCoordinate?,
    val headingDegrees: Double?,
    /** How hard the maneuver itself is (U-turn, sharp or left turn > right turn > straight). */
    val complexity: Double = 0.0,
    /** Other maneuvers packed within a few hundred meters. */
    val maneuverDensity: Double = 0.0,
    val highwayJunction: Double = 0.0,
    /** Another allowed branch points almost the same way as the chosen exit. */
    val ambiguity: Double = 0.0,
    /** How costly a miss is: highway exits and forks are hard to undo. */
    val errorCost: Double = 0.0,
    /** Only a small share of lanes continue on the route. */
    val lanePressure: Double = 0.0,
    val destinationProximity: Double = 0.0,
    val historicIncidentSignal: Double = 0.0,
    val usableRoadView: Double = 0.0,
    val dataConfidence: Double = 0.0,
    val verifiedGuidance: String? = null,
    val laneGuidanceVerified: Boolean = false,
    val evidence: List<String> = emptyList(),
    val sourceRouteId: String? = null,
    val routeMatchStatus: RouteMatchStatus = RouteMatchStatus.UNVERIFIED,
    val junctionRecord: JunctionRecord? = null,
    val routeLanes: List<RouteLane> = emptyList(),
    val approach: ApproachConditions = ApproachConditions.UNKNOWN,
    /** Driven straight through (no maneuver); never counted as part of a maneuver chain. */
    val passThrough: Boolean = false,
    val cue: ManeuverCue? = null,
) {
    val isDestination: Boolean get() = type == SegmentType.DESTINATION_ENTRY
    val isArrivalPoint: Boolean get() = approach.arrival

    /** Weighted sum of the 0..1 signals; docs/algorithm.md explains each one. */
    fun scoreWith(weights: RouteScoringWeights): Double =
        complexity.coerceIn(0.0, 1.0) * weights.complexity +
            ambiguity.coerceIn(0.0, 1.0) * weights.ambiguity +
            errorCost.coerceIn(0.0, 1.0) * weights.errorCost +
            lanePressure.coerceIn(0.0, 1.0) * weights.lanePressure +
            maneuverDensity.coerceIn(0.0, 1.0) * weights.maneuverDensity +
            destinationProximity.coerceIn(0.0, 1.0) * weights.destinationProximity +
            historicIncidentSignal.coerceIn(0.0, 1.0) * weights.historicIncidentSignal

    /** True when both are the same place, or close enough along the route to be one chain of decisions. */
    fun isNear(other: RouteSegmentCandidate, withinMeters: Double): Boolean =
        abs(routeOffsetMeters - other.routeOffsetMeters) <= withinMeters ||
            coordinate.distanceMetersTo(other.coordinate) <= SAME_PLACE_METERS

    private companion object { const val SAME_PLACE_METERS = 80.0 }
}

/**
 * Weights for ranking preview points. Ranking asks "what would a first-time driver most regret
 * missing?", so a miss's cost and look-alike branches weigh as much as turn difficulty. Data
 * confidence and road-view availability do not affect rank.
 */
data class RouteScoringWeights(
    val complexity: Double = 0.25,
    val ambiguity: Double = 0.20,
    val errorCost: Double = 0.25,
    val lanePressure: Double = 0.10,
    val maneuverDensity: Double = 0.05,
    val destinationProximity: Double = 0.10,
    val historicIncidentSignal: Double = 0.05,
) {
    init {
        val all = listOf(complexity, ambiguity, errorCost, lanePressure, maneuverDensity, destinationProximity, historicIncidentSignal)
        require(all.all { it.isFinite() && it >= 0.0 }) { "weights의 값은 0 이상이어야 한다" }
        require(all.sum() > 0.0) { "weights가 모두 0이면 순위를 매길 수 없다" }
    }
}

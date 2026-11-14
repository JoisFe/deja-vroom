package ai.dejavroom.app.model

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
}

data class RouteLane(val indications: List<String>, val validForRoute: Boolean)

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
) {
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
)

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
)

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
)

package ai.firstlook.app.model

/** Human-reviewed intersection guidance generated from source-tagged OSM way geometry. */
data class JunctionRecord(
    val id: String,
    val routeId: String,
    val junction: GeoCoordinate,
    val approachPoint: GeoCoordinate,
    val panDegrees: Double,
    val cautionPoint: GeoCoordinate,
    val message: String,
    val detourCost: String?,
    val keepPath: List<GeoCoordinate>,
    val trapPath: List<GeoCoordinate>,
    val verifiedLaneMessage: String? = null,
    val laneGuidanceVerified: Boolean = false,
    val source: JunctionDataSource,
    val manuallyVerified: Boolean,
    val roadViewCoverageVerified: Boolean,
) {
    init {
        require(id.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")))
        require(routeId.isNotBlank())
        require(panDegrees.isFinite() && panDegrees in 0.0..360.0)
        require(message.isNotBlank() && message.length <= 60)
        require(keepPath.size == 3 && trapPath.size == 3)
        require(keepPath.distinct().size == 3 && trapPath.distinct().size == 3)
        require(!verifiedLaneMessage.isNullOrBlank() || !laneGuidanceVerified)
        require(manuallyVerified || !roadViewCoverageVerified)
    }

    val mayUseRoadView: Boolean
        get() = manuallyVerified && roadViewCoverageVerified
}

data class JunctionDataSource(
    val provider: String,
    val attribution: String,
    val license: String,
    val retrievedAt: String?,
    val approachWayId: String,
    val keepWayId: String,
    val trapWayId: String,
) {
    init {
        require(provider.isNotBlank() && attribution.isNotBlank() && license.isNotBlank())
        require(approachWayId.isNotBlank() && keepWayId.isNotBlank() && trapWayId.isNotBlank())
    }
}

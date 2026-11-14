package ai.dejavroom.app.domain

/** How points are picked for a route. Tuned in config/briefing.yml (`selection:`). */
data class SelectionRules(
    /** Cards per route, the destination card included. */
    val maxCards: Int = 5,
    /** Below this a point is an easy one (a plain right turn, a merge); only the best pick may fall under it. */
    val minimumScore: Double = 0.2,
    /** Decisions closer than this along the route share one card. */
    val duplicateDistanceMeters: Double = 350.0,
    /** The surroundings of the origin are skipped: the driver already knows them. */
    val departureGuardMeters: Double = 300.0,
    /** Maneuvers this close to the end belong to the destination card. */
    val arrivalZoneMeters: Double = 300.0,
    /** Scores closer than this count as "about as important"; the decision that comes first then wins. */
    val nearTieScore: Double = 0.05,
) {
    init {
        require(maxCards in 1..5) { "selection.maxCards는 1~5여야 한다: $maxCards" }
        require(minimumScore in 0.0..1.0) { "selection.minimumScore는 0~1이어야 한다: $minimumScore" }
        require(nearTieScore in 0.0..1.0) { "selection.nearTieScore는 0~1이어야 한다: $nearTieScore" }
        require(duplicateDistanceMeters >= 0.0 && departureGuardMeters >= 0.0 && arrivalZoneMeters >= 0.0) {
            "selection의 거리 값은 0 이상이어야 한다"
        }
    }
}

/** How a single maneuver is read. Tuned in config/briefing.yml (`maneuver:`). */
data class ManeuverRules(
    /** Branches within this angle of the chosen exit look like the same road. */
    val lookAlikeAngleDegrees: Int = 30,
    /** Routing speeds at or above this only occur on expressways. */
    val highwaySpeedKmh: Double = 70.0,
) {
    init {
        require(lookAlikeAngleDegrees in 1..90) { "maneuver.lookAlikeAngleDegrees는 1~90이어야 한다: $lookAlikeAngleDegrees" }
        require(highwaySpeedKmh in 30.0..200.0) { "maneuver.highwaySpeedKmh는 30~200이어야 한다: $highwaySpeedKmh" }
    }

    val highwaySpeedMps: Double get() = highwaySpeedKmh / 3.6
}

package ai.dejavroom.app.domain

import ai.dejavroom.app.model.ApproachConditions
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * How far before a decision point its preview starts.
 *
 * A driver has to pick a lane before the junction, not on it, so the preview stands about [leadTime] of
 * travel earlier: the faster the road, the earlier the view. Highway-grade roads get at least
 * [highwayMinLeadMeters] because exit signs and deceleration lanes start that early. The view never starts
 * before the previous maneuver, since the junction cannot be seen from the road before that turn, and an
 * arrival point is shown from close by ([arrivalLeadMeters]) because the driver is slowing down to find it.
 *
 * Tuned in config/briefing.yml (`approach:`).
 */
data class ApproachPolicy(
    val leadTime: Duration = 12.seconds,
    val minLeadMeters: Double = 70.0,
    val maxLeadMeters: Double = 350.0,
    val highwayMinLeadMeters: Double = 300.0,
    /** Used when the routing source gives no speed for the road leading in. */
    val defaultLeadMeters: Double = 150.0,
    val arrivalLeadMeters: Double = 80.0,
    val previousManeuverMarginMeters: Double = 15.0,
    /** The preview plays the approach as a drive up to the point: one frame about every [driveStepMeters]... */
    val driveStepMeters: Double = 20.0,
    /** ...in at most this many hops, however long the approach is. */
    val driveMaxSteps: Int = 10,
    /**
     * Cars keep right, so the view stands this far to the right of the route line. A road photo is looked
     * up from there, which is what keeps it on the driver's own side instead of the oncoming lanes.
     */
    val laneOffsetMeters: Double = 3.0,
) {
    init {
        require(leadTime.isPositive()) { "approach.leadSeconds는 0보다 커야 한다" }
        require(minLeadMeters in 0.0..maxLeadMeters) { "approach.minLeadMeters는 0 이상, maxLeadMeters 이하여야 한다" }
        require(highwayMinLeadMeters in minLeadMeters..maxLeadMeters) { "approach.highwayMinLeadMeters는 min~max 사이여야 한다" }
        require(defaultLeadMeters in minLeadMeters..maxLeadMeters) { "approach.defaultLeadMeters는 min~max 사이여야 한다" }
        require(arrivalLeadMeters >= 0.0 && previousManeuverMarginMeters >= 0.0) { "approach의 거리 값은 0 이상이어야 한다" }
        require(driveStepMeters >= 5.0) { "approach.driveStepMeters는 5 이상이어야 한다" }
        require(laneOffsetMeters in 0.0..10.0) { "approach.laneOffsetMeters는 0~10이어야 한다" }
        require(driveMaxSteps in 2..30) { "approach.driveMaxSteps는 2~30이어야 한다" }
    }

    /** Meters before the point at [routeOffsetMeters] where its preview should stand. */
    fun leadMeters(conditions: ApproachConditions, routeOffsetMeters: Double): Double {
        val speed = conditions.speedMps
        var lead = when {
            conditions.arrival -> arrivalLeadMeters
            speed != null && speed > 0.0 -> (speed * leadTime.toDouble(DurationUnit.SECONDS)).coerceIn(minLeadMeters, maxLeadMeters)
            else -> defaultLeadMeters
        }
        if (conditions.onHighway && !conditions.arrival) lead = max(lead, highwayMinLeadMeters)
        // Stay on the road that actually leads into this point: just past the previous turn at the earliest.
        conditions.previousManeuverGapMeters?.let { gap ->
            lead = min(lead, max(gap - previousManeuverMarginMeters, gap / 2.0))
        }
        return min(lead, routeOffsetMeters).coerceAtLeast(0.0)
    }
}

package ai.dejavroom.app.domain

import ai.dejavroom.app.model.ApproachViewpoint
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RoutePose
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A place measured against a route: [offsetMeters] along it to the nearest point, [distanceMeters] away from it. */
data class RoutePosition(val offsetMeters: Double, val distanceMeters: Double)

/** Route geometry with distances along it. Every offset is meters from the start of the route. */
class RoutePolyline(private val points: List<GeoCoordinate>) {
    init { require(points.size >= 2) { "a route needs at least two points" } }

    private val offsets = DoubleArray(points.size).also { offsets ->
        for (index in 1 until points.size) offsets[index] = offsets[index - 1] + points[index - 1].distanceMetersTo(points[index])
    }

    val lengthMeters: Double get() = offsets.last()

    fun pointAt(offsetMeters: Double): GeoCoordinate {
        val target = offsetMeters.coerceIn(0.0, lengthMeters)
        val index = (0 until points.lastIndex).firstOrNull { offsets[it + 1] >= target } ?: (points.lastIndex - 1)
        return pointOnSegment(index, target - offsets[index])
    }

    /** The point [alongMeters] into the segment that starts at [index]. */
    private fun pointOnSegment(index: Int, alongMeters: Double): GeoCoordinate {
        val span = offsets[index + 1] - offsets[index]
        val fraction = if (span <= 0.0) 0.0 else (alongMeters / span).coerceIn(0.0, 1.0)
        return GeoCoordinate(
            latitude = points[index].latitude + (points[index + 1].latitude - points[index].latitude) * fraction,
            longitude = points[index].longitude + (points[index + 1].longitude - points[index].longitude) * fraction,
        )
    }

    /**
     * Where [coordinate] is against the route: a point on it and how far away that is. Only the route
     * from [fromOffsetMeters] onward is searched, so a car that only moves forward is never put back.
     *
     * Where the route passes the same place twice (a U-turn, a ramp looping over the road), the nearest
     * point may be the later pass. Any pass within [preferEarlierWithinMeters] of the nearest counts as
     * just as likely, and the earliest of them is returned.
     */
    fun locate(coordinate: GeoCoordinate, fromOffsetMeters: Double = 0.0, preferEarlierWithinMeters: Double = 0.0): RoutePosition {
        val from = fromOffsetMeters.coerceIn(0.0, lengthMeters)
        val candidates = (0 until points.lastIndex).filter { offsets[it + 1] >= from }.map { index ->
            val span = offsets[index + 1] - offsets[index]
            val toStart = points[index].distanceMetersTo(coordinate)
            val toEnd = points[index + 1].distanceMetersTo(coordinate)
            // Foot of the perpendicular on this segment (law of cosines), kept inside the part being searched.
            val foot = if (span <= 0.0) 0.0 else (toStart * toStart - toEnd * toEnd + span * span) / (2.0 * span)
            val along = foot.coerceIn((from - offsets[index]).coerceIn(0.0, span), span)
            RoutePosition(offsets[index] + along, pointOnSegment(index, along).distanceMetersTo(coordinate))
        }
        val nearest = candidates.minByOrNull { it.distanceMeters }
            ?: return RoutePosition(lengthMeters, points.last().distanceMetersTo(coordinate))
        return candidates.first { it.distanceMeters <= nearest.distanceMeters + preferEarlierWithinMeters.coerceAtLeast(0.0) }
    }

    /**
     * Direction of travel at [offsetMeters], measured over the next [spanMeters]. At the very end of the
     * route there is no "next", so the last [spanMeters] are used instead.
     */
    fun headingAt(offsetMeters: Double, spanMeters: Double = 30.0): Double {
        val from = offsetMeters.coerceIn(0.0, lengthMeters)
        val to = (from + spanMeters).coerceAtMost(lengthMeters)
        return if (to - from >= 1.0) pointAt(from).bearingTo(pointAt(to))
        else pointAt((from - spanMeters).coerceAtLeast(0.0)).bearingTo(pointAt(from))
    }

    /**
     * Where a driver is [leadMeters] before reaching [offsetMeters], facing along the route toward it.
     * The heading follows the next stretch of road but never looks past the point into the turn itself.
     * With [laneOffsetMeters] the driver stands in the right-hand lanes rather than on the route line.
     */
    fun viewpointBefore(offsetMeters: Double, leadMeters: Double, laneOffsetMeters: Double = 0.0): ApproachViewpoint {
        val target = offsetMeters.coerceIn(0.0, lengthMeters)
        val standAt = (target - leadMeters).coerceIn(0.0, target)
        val span = (target - standAt).coerceIn(MIN_HEADING_SPAN_METERS, MAX_HEADING_SPAN_METERS)
        return ApproachViewpoint(
            coordinate = laneAt(standAt, laneOffsetMeters),
            headingDegrees = headingAt(standAt, span),
            leadMeters = (target - standAt).roundToInt(),
        )
    }

    /**
     * The drive from [leadMeters] before [offsetMeters] up to the point itself, as poses about
     * [stepMeters] apart. A long approach is thinned to [maxSteps] hops so it still plays quickly.
     *
     * It ends standing on the point and goes no further: reaching it is what the drive is for, and the
     * ramps and underpasses beyond a junction are where a road photo is least likely to be of this road.
     * The last pose already looks the way the route leaves.
     */
    fun driveTo(offsetMeters: Double, leadMeters: Double, stepMeters: Double, maxSteps: Int, laneOffsetMeters: Double = 0.0): List<RoutePose> {
        require(stepMeters > 0.0 && maxSteps >= 1)
        val target = offsetMeters.coerceIn(0.0, lengthMeters)
        val lead = target - (target - leadMeters).coerceIn(0.0, target)
        if (lead < 1.0) return emptyList()
        val hops = hopsToCover(lead, stepMeters).coerceAtMost(maxSteps)
        return (0..hops).map { hop ->
            val at = target - lead + lead * hop / hops
            RoutePose(laneAt(at, laneOffsetMeters), headingAt(at, DRIVE_HEADING_SPAN_METERS), (target - at).roundToInt())
        }
    }

    /**
     * Where the car is at [offsetMeters]: [laneOffsetMeters] to the right of the route line, as traffic
     * keeps right. The side is taken from the stretch just driven, because at a turn the car is still on
     * the road it came along.
     */
    private fun laneAt(offsetMeters: Double, laneOffsetMeters: Double): GeoCoordinate {
        val onLine = pointAt(offsetMeters)
        if (laneOffsetMeters == 0.0) return onLine
        val behind = (offsetMeters - LANE_HEADING_SPAN_METERS).coerceAtLeast(0.0)
        val travel = if (offsetMeters - behind >= 1.0) pointAt(behind).bearingTo(onLine) else headingAt(offsetMeters, LANE_HEADING_SPAN_METERS)
        return onLine.moved(travel + RIGHT_ANGLE_DEGREES, laneOffsetMeters)
    }

    /** Hops of at most [stepMeters] that cover [meters]. Rounding noise in the distance never adds a hop. */
    private fun hopsToCover(meters: Double, stepMeters: Double): Int =
        if (meters < 1.0) 0 else ceil((meters - DISTANCE_NOISE_METERS) / stepMeters).toInt().coerceAtLeast(1)

    private companion object {
        const val DISTANCE_NOISE_METERS = 0.01
        const val DRIVE_HEADING_SPAN_METERS = 20.0
        const val LANE_HEADING_SPAN_METERS = 10.0
        const val RIGHT_ANGLE_DEGREES = 90.0
        const val MIN_HEADING_SPAN_METERS = 5.0
        const val MAX_HEADING_SPAN_METERS = 30.0
    }
}

package ai.dejavroom.app.domain

import ai.dejavroom.app.model.ApproachViewpoint
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RoutePose
import kotlin.math.ceil
import kotlin.math.roundToInt

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
        val span = offsets[index + 1] - offsets[index]
        val fraction = if (span <= 0.0) 0.0 else ((target - offsets[index]) / span).coerceIn(0.0, 1.0)
        return GeoCoordinate(
            latitude = points[index].latitude + (points[index + 1].latitude - points[index].latitude) * fraction,
            longitude = points[index].longitude + (points[index + 1].longitude - points[index].longitude) * fraction,
        )
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
     */
    fun viewpointBefore(offsetMeters: Double, leadMeters: Double): ApproachViewpoint {
        val target = offsetMeters.coerceIn(0.0, lengthMeters)
        val standAt = (target - leadMeters).coerceIn(0.0, target)
        val span = (target - standAt).coerceIn(MIN_HEADING_SPAN_METERS, MAX_HEADING_SPAN_METERS)
        return ApproachViewpoint(
            coordinate = pointAt(standAt),
            headingDegrees = headingAt(standAt, span),
            leadMeters = (target - standAt).roundToInt(),
        )
    }

    /**
     * The drive from [leadMeters] before [offsetMeters] to [pastMeters] after it, as evenly spaced poses
     * about [stepMeters] apart. A long approach is thinned to [maxSteps] hops so it still plays quickly.
     */
    fun driveThrough(offsetMeters: Double, leadMeters: Double, pastMeters: Double, stepMeters: Double, maxSteps: Int): List<RoutePose> {
        require(stepMeters > 0.0 && maxSteps >= 1)
        val target = offsetMeters.coerceIn(0.0, lengthMeters)
        val from = (target - leadMeters).coerceIn(0.0, target)
        val to = (target + pastMeters).coerceIn(target, lengthMeters)
        val distance = to - from
        if (distance < 1.0) return emptyList()
        val hops = ceil(distance / stepMeters).toInt().coerceIn(1, maxSteps)
        return (0..hops).map { hop ->
            val at = from + distance * hop / hops
            RoutePose(pointAt(at), headingAt(at, DRIVE_HEADING_SPAN_METERS), (target - at).roundToInt())
        }
    }

    private companion object {
        const val DRIVE_HEADING_SPAN_METERS = 20.0
        const val MIN_HEADING_SPAN_METERS = 5.0
        const val MAX_HEADING_SPAN_METERS = 30.0
    }
}

package ai.dejavroom.app.domain

import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration.Companion.hours

/** A point the driver was shown on the way. [offsetMeters] is where it sits on the leg it belongs to. */
data class TripCheckpoint(
    val id: String,
    val name: String,
    val coordinate: GeoCoordinate,
    val offsetMeters: Double = 0.0,
)

/**
 * The trip the driver is on: one destination and the points they were shown on the way to it. Only the
 * latest trip is kept. It exists to answer one question: which of those points are already behind the car.
 *
 * A trip is driven in legs. Each time the driver asks again from where the car is, a new leg starts
 * there: what was passed so far moves to [passedBefore] and [leg] holds the points of the new plan.
 * Nothing here is a driving record; the car's position is used to move [progressMeters] and is not kept.
 */
data class Trip(
    val destination: Place,
    /** Points shown for the leg being driven now, in driving order. */
    val leg: List<TripCheckpoint>,
    /** Geometry of that leg, thinned; progress and checkpoint offsets are measured on it. */
    val route: List<GeoCoordinate>,
    val startedAtMillis: Long,
    /** Points passed on earlier legs, in the order they were passed. */
    val passedBefore: List<TripCheckpoint> = emptyList(),
    /** How far along [route] the car has been seen. It only grows. */
    val progressMeters: Double = 0.0,
) {
    init {
        require(destination.coordinate != null) { "a trip needs a destination with a position" }
        require(route.size >= 2) { "a trip needs a route" }
        require(progressMeters.isFinite() && progressMeters >= 0.0)
    }

    private val polyline by lazy { RoutePolyline(route) }

    /** True once the car is clearly beyond [checkpoint], not merely level with it. */
    fun isPassed(checkpoint: TripCheckpoint): Boolean = checkpoint.offsetMeters + PASSED_MARGIN_METERS <= progressMeters

    /** Everything behind the car, earliest first. */
    val passed: List<TripCheckpoint> get() = passedBefore + leg.filter(::isPassed)

    val upcoming: List<TripCheckpoint> get() = leg.filterNot(::isPassed)

    val arrived: Boolean get() = progressMeters >= polyline.lengthMeters

    /** A trip ends by arriving, or by being left alone for longer than any drive takes. */
    fun isOver(nowMillis: Long): Boolean = arrived || nowMillis - startedAtMillis !in 0..MAX_AGE.inWholeMilliseconds

    fun isGoingTo(coordinate: GeoCoordinate): Boolean =
        requireNotNull(destination.coordinate).distanceMetersTo(coordinate) <= SAME_DESTINATION_METERS

    /**
     * The trip once the car has been seen at [location]. Progress only moves forward. Away from the route
     * nothing can be said about what was passed, so nothing changes.
     */
    fun advancedTo(location: GeoCoordinate): Trip {
        if (location.distanceMetersTo(requireNotNull(destination.coordinate)) <= ARRIVAL_METERS) {
            return copy(progressMeters = polyline.lengthMeters)
        }
        val position = polyline.locate(location, fromOffsetMeters = progressMeters, preferEarlierWithinMeters = SAME_ROAD_METERS)
        return if (position.distanceMeters > ON_ROUTE_METERS) this
        else copy(progressMeters = max(progressMeters, position.offsetMeters))
    }

    /**
     * A new leg planned from where the car is now. What was passed stays passed; the points still ahead
     * are replaced by the new plan's.
     */
    fun continuedWith(route: List<GeoCoordinate>, shown: List<BriefingSegment>): Trip {
        val thinned = thin(route)
        val leg = checkpointsOn(thinned, shown)
        // A point the new plan still has ahead was not passed after all.
        val behind = passed.filter { before -> leg.none { it.coordinate.distanceMetersTo(before.coordinate) <= SAME_PLACE_METERS } }
        return copy(leg = leg, route = thinned, passedBefore = behind, progressMeters = 0.0)
    }

    /** The same leg with another set of points on screen: the driver asked to see more, or fewer. */
    fun showing(shown: List<BriefingSegment>): Trip = copy(leg = checkpointsOn(route, shown))

    companion object {
        /** Beyond this the car is past a point, not just level with it or misplaced by the positioning. */
        private const val PASSED_MARGIN_METERS = 30.0

        /** A rest area or a filling station beside the road still counts as being on the route. */
        private const val ON_ROUTE_METERS = 300.0

        /** Two passes of the route this close to the car cannot be told apart; the earlier one is assumed. */
        private const val SAME_ROAD_METERS = 40.0
        private const val SAME_POINT_METERS = 15.0
        private const val ARRIVAL_METERS = 150.0
        private const val SAME_DESTINATION_METERS = 150.0
        private const val SAME_PLACE_METERS = 80.0
        private const val MAX_ROUTE_POINTS = 2_000
        private val MAX_AGE = 24.hours

        fun startedWith(destination: Place, route: List<GeoCoordinate>, shown: List<BriefingSegment>, nowMillis: Long): Trip {
            val thinned = thin(route)
            return Trip(destination = destination, leg = checkpointsOn(thinned, shown), route = thinned, startedAtMillis = nowMillis)
        }

        /** Checkpoints for [shown], each placed on [route] no earlier than the one before it. */
        private fun checkpointsOn(route: List<GeoCoordinate>, shown: List<BriefingSegment>): List<TripCheckpoint> {
            val polyline = RoutePolyline(route)
            var from = 0.0
            return shown.mapNotNull { segment ->
                val coordinate = segment.coordinate ?: return@mapNotNull null
                val offset = polyline.locate(coordinate, fromOffsetMeters = from, preferEarlierWithinMeters = SAME_POINT_METERS).offsetMeters
                from = offset
                TripCheckpoint(segment.id, segment.name, coordinate, offset)
            }
        }

        /**
         * At most [MAX_ROUTE_POINTS] points, always keeping both ends. Judging progress needs the shape of
         * the road, not every vertex, and the trip is saved on the device.
         */
        internal fun thin(route: List<GeoCoordinate>): List<GeoCoordinate> {
            if (route.size <= MAX_ROUTE_POINTS) return route
            val stride = ceil(route.size / (MAX_ROUTE_POINTS - 1).toDouble()).toInt()
            return route.filterIndexed { index, _ -> index % stride == 0 || index == route.lastIndex }
        }
    }
}

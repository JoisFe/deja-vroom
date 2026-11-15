package ai.dejavroom.app.domain

import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import ai.dejavroom.app.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTest {
    /** 2 km due north; one step is about 50 m, so index 10 is 500 m along. */
    private val north = (0..40).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }
    private val destination = Place("강남역", coordinate = north.last())
    private val hour = 3_600_000L

    private fun point(id: String, index: Int) = BriefingSegment(
        id = id, type = SegmentType.INTERSECTION, name = "$id 갈림길", description = id, guidance = id,
        distanceFromStartKm = index * 0.05, coordinate = north[index],
    )

    private fun beside(index: Int, metersEast: Double) = GeoCoordinate(north[index].latitude, 127.00 + metersEast / 88_200.0)

    private val shown = listOf(point("a", 10), point("b", 20), point("c", 30))
    private val trip = Trip.startedWith(destination, north, shown, nowMillis = 0)

    @Test
    fun `a new trip has every point ahead`() {
        assertEquals(listOf(500.0, 1_000.0, 1_500.0), trip.leg.map { Math.round(it.offsetMeters / 10.0) * 10.0 })
        assertEquals(emptyList<TripCheckpoint>(), trip.passed)
        assertEquals(listOf("a", "b", "c"), trip.upcoming.map { it.id })
        assertFalse(trip.arrived)
    }

    @Test
    fun `points behind the car are passed and the rest stay ahead`() {
        val later = trip.advancedTo(north[14])
        assertEquals(listOf("a"), later.passed.map { it.id })
        assertEquals(listOf("b", "c"), later.upcoming.map { it.id })
    }

    @Test
    fun `a point the car is level with is not passed yet`() {
        // 10 m beyond the second point: on it, not behind it.
        val level = GeoCoordinate(north[20].latitude + 0.00009, 127.00)
        assertEquals(listOf("a"), trip.advancedTo(level).passed.map { it.id })
        assertEquals(listOf("a", "b"), trip.advancedTo(north[21]).passed.map { it.id })
    }

    @Test
    fun `a rest area beside the road still counts as being on the route`() {
        assertEquals(listOf("a"), trip.advancedTo(beside(14, 250.0)).passed.map { it.id })
    }

    @Test
    fun `far from the route nothing can be said so nothing changes`() {
        assertSame(trip, trip.advancedTo(beside(25, 1_000.0)))
    }

    @Test
    fun `progress never goes back`() {
        val ahead = trip.advancedTo(north[24])
        val behindAgain = ahead.advancedTo(north[5])
        assertEquals(ahead.progressMeters, behindAgain.progressMeters, 0.01)
        assertEquals(listOf("a", "b"), behindAgain.passed.map { it.id })
    }

    @Test
    fun `reaching the destination passes everything and ends the trip`() {
        val arrived = trip.advancedTo(beside(40, 60.0))
        assertTrue(arrived.arrived)
        assertEquals(listOf("a", "b", "c"), arrived.passed.map { it.id })
        assertTrue(arrived.isOver(nowMillis = hour))
    }

    @Test
    fun `a trip is over a day after it started and not before`() {
        assertFalse(trip.isOver(nowMillis = 23 * hour))
        assertTrue(trip.isOver(nowMillis = 25 * hour))
        // A clock set back cannot make an old trip young.
        assertTrue(trip.isOver(nowMillis = -hour))
    }

    @Test
    fun `a new leg keeps what was passed and replaces what was ahead`() {
        // At a rest area 700 m in, the driver asks again from there.
        val atRestArea = trip.advancedTo(north[14])
        val rest = north.drop(14)
        val next = atRestArea.continuedWith(rest, listOf(point("x", 22), point("y", 36)))
        assertEquals(listOf("a"), next.passedBefore.map { it.id })
        assertEquals(listOf("x", "y"), next.leg.map { it.id })
        assertEquals(listOf("a"), next.passed.map { it.id })
        assertEquals(0.0, next.progressMeters, 0.0)
        assertEquals(rest, next.route)
        // On the next stop the earlier passes are still there, in order.
        assertEquals(listOf("a", "x"), next.advancedTo(north[26]).passed.map { it.id })
    }

    @Test
    fun `a point the new plan still has ahead was not passed after all`() {
        // The position jumped ahead for a moment; the new plan from the real position still shows b.
        val jumped = trip.advancedTo(north[21])
        val next = jumped.continuedWith(north.drop(18), listOf(point("b2", 20), point("c2", 30)))
        assertEquals(listOf("a"), next.passedBefore.map { it.id })
    }

    @Test
    fun `the same destination is the same place give or take a road width`() {
        assertTrue(trip.isGoingTo(beside(40, 100.0)))
        assertFalse(trip.isGoingTo(north[20]))
    }

    @Test
    fun `points without a position are not checkpoints`() {
        val unplaced = point("u", 10).copy(coordinate = null)
        assertEquals(listOf("b"), Trip.startedWith(destination, north, listOf(unplaced, point("b", 20)), 0).leg.map { it.id })
    }

    @Test
    fun `a long route is thinned but keeps both ends and its length`() {
        val long = (0..9_999).map { GeoCoordinate(34.0 + it * 0.0003, 127.0) }
        val thinned = Trip.thin(long)
        assertTrue("${thinned.size} points", thinned.size in 1_000..2_000)
        assertEquals(long.first(), thinned.first())
        assertEquals(long.last(), thinned.last())
        assertEquals(RoutePolyline(long).lengthMeters, RoutePolyline(thinned).lengthMeters, 1.0)
        assertSame(north, Trip.thin(north))
    }
}

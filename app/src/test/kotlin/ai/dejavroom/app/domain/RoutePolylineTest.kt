package ai.dejavroom.app.domain

import ai.dejavroom.app.model.ApproachPhase
import ai.dejavroom.app.model.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePolylineTest {
    /** About 1 km north, then about 1 km east. One step is about 50 m. */
    private val north = (0..20).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }
    private val east = (1..20).map { GeoCoordinate(37.50 + 20 * 0.00045, 127.00 + it * 0.000565) }
    private val route = RoutePolyline(north + east)
    private val corner = RoutePolyline(north).lengthMeters

    @Test
    fun `length and points follow the geometry`() {
        assertEquals(2_000.0, route.lengthMeters, 20.0)
        assertEquals(north[10].latitude, route.pointAt(corner / 2).latitude, 1e-5)
    }

    @Test
    fun `a viewpoint stands before the junction and faces it along the incoming road`() {
        val viewpoint = route.viewpointBefore(corner, 150.0)
        assertEquals(150, viewpoint.leadMeters)
        // Still on the northbound leg, south of the corner, looking north and not along the turn (east).
        assertTrue(viewpoint.coordinate.latitude < north.last().latitude)
        assertEquals(127.00, viewpoint.coordinate.longitude, 1e-6)
        assertEquals(0.0, viewpoint.headingDegrees, 1.0)
    }

    @Test
    fun `a viewpoint cannot stand before the start of the route`() {
        val viewpoint = route.viewpointBefore(60.0, 150.0)
        assertEquals(60, viewpoint.leadMeters)
        assertEquals(north.first(), viewpoint.coordinate)
    }

    @Test
    fun `heading at the very end looks back along the last stretch`() {
        assertEquals(90.0, route.headingAt(route.lengthMeters), 1.0)
    }

    @Test
    fun `coordinates measure distance and bearing between each other`() {
        assertEquals(50.0, north[0].distanceMetersTo(north[1]), 1.0)
        assertEquals(0.0, north[0].bearingTo(north[1]), 0.5)
        assertEquals(90.0, north.last().bearingTo(east.first()), 0.5)
    }

    @Test
    fun `the drive runs from the viewpoint up to the junction and ends looking the way the route leaves`() {
        val drive = route.driveTo(corner, leadMeters = 150.0, stepMeters = 20.0, maxSteps = 30)
        // 150 m in hops of at most 20 m: 8 hops, 9 frames, counting down to the junction and no further.
        assertEquals(9, drive.size)
        assertEquals(150, drive.first().remainingMeters)
        assertEquals(0, drive.last().remainingMeters)
        assertTrue(drive.zipWithNext().all { (a, b) -> a.remainingMeters > b.remainingMeters })
        assertEquals(route.viewpointBefore(corner, 150.0).coordinate, drive.first().coordinate)
        assertEquals(0.0, drive.first().headingDegrees, 1.0) // driving north toward the corner
        assertEquals(90.0, drive.last().headingDegrees, 1.0) // on it, looking east where the route goes
    }

    @Test
    fun `the last frame of the drive stands exactly on the junction`() {
        // A distance that does not divide into the step: the frames spread out, the junction frame stays.
        val drive = route.driveTo(corner, leadMeters = 144.0, stepMeters = 20.0, maxSteps = 30)
        val onJunction = drive.last()
        assertEquals(0.0, onJunction.coordinate.distanceMetersTo(north.last()), 0.5)
        // With the default step it is also the only frame that counts as being there.
        assertEquals(listOf(onJunction), drive.filter { it.phase == ApproachPhase.AT_POINT })
        assertEquals(ApproachPhase.APPROACHING, drive.first().phase)
    }

    @Test
    fun `a long approach is thinned so the drive still plays quickly`() {
        val drive = RoutePolyline(north).driveTo(900.0, leadMeters = 350.0, stepMeters = 20.0, maxSteps = 10)
        assertEquals(11, drive.size)
        assertEquals(350, drive.first().remainingMeters)
        assertEquals(0, drive.last().remainingMeters)
    }

    @Test
    fun `a view that already stands on the point has nowhere to drive`() {
        assertEquals(emptyList<Any>(), route.driveTo(corner, leadMeters = 0.0, stepMeters = 20.0, maxSteps = 10))
    }

    @Test
    fun `the view and every frame stand in the right-hand lanes when asked to`() {
        // Northbound, so "to the right" is east. One degree of longitude here is about 88 km.
        val eastOf = { meters: Double -> 127.00 + meters / 88_200.0 }
        val viewpoint = route.viewpointBefore(corner, 150.0, laneOffsetMeters = 3.0)
        assertEquals(eastOf(3.0), viewpoint.coordinate.longitude, 2e-6)
        assertEquals(route.viewpointBefore(corner, 150.0).coordinate.latitude, viewpoint.coordinate.latitude, 1e-7)
        assertEquals(0.0, viewpoint.headingDegrees, 1.0)

        val drive = route.driveTo(corner, leadMeters = 150.0, stepMeters = 20.0, maxSteps = 30, laneOffsetMeters = 3.0)
        drive.forEach { assertEquals(eastOf(3.0), it.coordinate.longitude, 2e-6) }
        // On the corner the car is still on the road it came along, so it has not moved onto the new one's side.
        assertEquals(3.0, drive.last().coordinate.distanceMetersTo(north.last()), 0.3)
        assertEquals(90.0, drive.last().headingDegrees, 1.0)
        // On the eastbound leg the right-hand side is south.
        val later = route.viewpointBefore(corner + 500.0, 100.0, laneOffsetMeters = 3.0)
        assertTrue(later.coordinate.latitude < north.last().latitude)
    }

    @Test
    fun `a view within a few car lengths of the point counts as standing on it`() {
        assertEquals(ApproachPhase.APPROACHING, ApproachPhase.at(16))
        listOf(15, 0, -15).forEach { assertEquals(ApproachPhase.AT_POINT, ApproachPhase.at(it)) }
        assertEquals(ApproachPhase.PASSED, ApproachPhase.at(-16))
        // Without a distance nothing has been reached yet.
        assertEquals(ApproachPhase.APPROACHING, ApproachPhase.at(null))
    }

    @Test
    fun `the drive to an arrival point stops at the end of the route`() {
        val drive = route.driveTo(route.lengthMeters, leadMeters = 80.0, stepMeters = 20.0, maxSteps = 10)
        assertEquals(listOf(80, 60, 40, 20, 0), drive.map { it.remainingMeters })
    }
}

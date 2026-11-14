package ai.dejavroom.app.domain

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
}

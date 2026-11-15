package ai.dejavroom.app.domain

import ai.dejavroom.app.model.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePositionTest {
    /** 2 km due north; one step is about 50 m. One degree of longitude here is about 88 km. */
    private val north = (0..40).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }
    private val route = RoutePolyline(north)

    private fun beside(index: Int, metersEast: Double) = GeoCoordinate(north[index].latitude, 127.00 + metersEast / 88_200.0)

    @Test
    fun `a car on the road is placed where it is`() {
        val position = route.locate(north[10])
        assertEquals(500.0, position.offsetMeters, 2.0)
        assertEquals(0.0, position.distanceMeters, 0.5)
    }

    @Test
    fun `a car beside the road is placed level with itself and that far away`() {
        // Between two vertices and 200 m off to the side: a rest area next to the carriageway.
        val restArea = GeoCoordinate((north[10].latitude + north[11].latitude) / 2, 127.00 + 200.0 / 88_200.0)
        val position = route.locate(restArea)
        assertEquals(525.0, position.offsetMeters, 3.0)
        assertEquals(200.0, position.distanceMeters, 3.0)
    }

    @Test
    fun `beyond either end the nearest point is that end`() {
        assertEquals(0.0, route.locate(GeoCoordinate(37.49, 127.00)).offsetMeters, 0.01)
        assertEquals(route.lengthMeters, route.locate(GeoCoordinate(37.53, 127.00)).offsetMeters, 0.01)
    }

    @Test
    fun `searching from an offset never places the car before it`() {
        val position = route.locate(north[4], fromOffsetMeters = 1_000.0)
        assertEquals(1_000.0, position.offsetMeters, 0.01)
        assertEquals(800.0, position.distanceMeters, 5.0)
    }

    @Test
    fun `where the route doubles back the earlier pass is preferred over a slightly nearer later one`() {
        // North for 500 m, across, then back south on the other carriageway 15 m to the east.
        val out = north.take(11)
        val back = (10 downTo 0).map { beside(it, 15.0) }
        val uTurn = RoutePolyline(out + back)
        // Just after setting off, the position reads 10 m east: nearer to the way back than to the way out.
        val car = beside(1, 10.0)
        val nearest = uTurn.locate(car)
        assertTrue("the nearest point is on the way back: ${nearest.offsetMeters}", nearest.offsetMeters > 900.0)
        val assumed = uTurn.locate(car, preferEarlierWithinMeters = 40.0)
        assertTrue("but the car is taken to be on the way out: ${assumed.offsetMeters}", assumed.offsetMeters < 60.0)
    }
}

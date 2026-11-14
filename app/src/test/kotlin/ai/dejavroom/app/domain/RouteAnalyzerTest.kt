package ai.dejavroom.app.domain

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RouteGeometryStatus
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.RouteSource
import ai.dejavroom.app.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAnalyzerTest {
    /** About 1 km north, then about 1 km east: one clear 90-degree turn near the 1 km mark. */
    private fun lShapedRoute(source: RouteSource = RouteSource.EXTERNAL_ROUTE): RouteSnapshot {
        val north = (0..20).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }
        val east = (1..20).map { GeoCoordinate(37.50 + 20 * 0.00045, 127.00 + it * 0.000565) }
        return RouteSnapshot(
            origin = "A", destination = "B", durationMinutes = 5, distanceKm = 2, segments = emptyList(),
            routeId = "test-route", source = source,
            geometryStatus = RouteGeometryStatus.VERIFIED, geometry = north + east,
        )
    }

    @Test
    fun `a sharp turn on verified geometry becomes a briefing segment near the corner`() {
        val segments = RouteAnalyzer(maxSegments = 3).analyze(lShapedRoute())
        assertTrue("expected at least one segment", segments.isNotEmpty())
        val turn = segments.first()
        assertEquals(SegmentType.COMPLEX_TURN, turn.type)
        assertEquals(1.0, turn.distanceFromStartKm, 0.1)
    }

    @Test
    fun `curated demo routes are never analyzed`() {
        assertTrue(RouteAnalyzer().analyze(lShapedRoute(RouteSource.CURATED_DEMO)).isEmpty())
    }

    @Test
    fun `a straight route has nothing to brief`() {
        val straight = lShapedRoute().let { it.copy(geometry = it.geometry.take(21)) }
        assertTrue(RouteAnalyzer().analyze(straight).isEmpty())
    }
}

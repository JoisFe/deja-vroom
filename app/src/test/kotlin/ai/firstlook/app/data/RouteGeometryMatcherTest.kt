package ai.firstlook.app.data

import ai.firstlook.app.model.GeoCoordinate
import ai.firstlook.app.model.RouteGeometryStatus
import ai.firstlook.app.model.RouteSegmentCandidate
import ai.firstlook.app.model.RouteSnapshot
import ai.firstlook.app.model.RouteSource
import ai.firstlook.app.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryMatcherTest {
    @Test
    fun `accepts a candidate on the same route with matching offset and heading`() {
        val result = matcher.match(route, candidate())

        assertTrue(result is RouteGeometryMatch.Verified)
        assertEquals(1_000.0, (result as RouteGeometryMatch.Verified).routeOffsetMeters, 2.0)
        assertEquals(37.0 + 800.0 / 111_194.9, result.approachCoordinate.latitude, 0.00002)
    }

    @Test
    fun `rejects candidates with route mismatch, wrong offset, or opposite heading`() {
        assertTrue(matcher.match(route, candidate().copy(sourceRouteId = "other")) is RouteGeometryMatch.Rejected)
        assertTrue(matcher.match(route, candidate().copy(routeOffsetMeters = 5_000.0)) is RouteGeometryMatch.Rejected)
        assertTrue(matcher.match(route, candidate().copy(headingDegrees = 180.0)) is RouteGeometryMatch.Rejected)
    }

    @Test
    fun `rejects candidates away from route or geometry that is not verified`() {
        assertTrue(matcher.match(route, candidate().copy(coordinate = GeoCoordinate(37.02, 127.01))) is RouteGeometryMatch.Rejected)
        assertTrue(matcher.match(route.copy(geometryStatus = RouteGeometryStatus.UNKNOWN), candidate()) is RouteGeometryMatch.Rejected)
    }

    @Test
    fun `rejects ambiguous projections on nearby separated route sections`() {
        val loop = route.copy(
            distanceKm = 2,
            geometry = listOf(
                GeoCoordinate(37.0, 127.0),
                GeoCoordinate(37.01, 127.0),
                GeoCoordinate(37.0, 127.00001),
            ),
        )
        val nearLoop = candidate().copy(
            coordinate = GeoCoordinate(37.005, 127.000004),
            routeOffsetMeters = 550.0,
            headingDegrees = 0.0,
        )
        assertTrue(matcher.match(loop, nearLoop) is RouteGeometryMatch.Rejected)
    }

    private val matcher = RouteGeometryMatcher()
    private val route = RouteSnapshot(
        origin = "fixture origin",
        destination = "fixture destination",
        durationMinutes = 20,
        distanceKm = 40,
        segments = emptyList(),
        routeId = "fixture-route",
        source = RouteSource.EXTERNAL_ROUTE,
        geometryStatus = RouteGeometryStatus.VERIFIED,
        geometry = listOf(GeoCoordinate(37.0, 127.0), GeoCoordinate(37.36, 127.0)),
    )

    private fun candidate() = RouteSegmentCandidate(
        id = "candidate",
        type = SegmentType.INTERSECTION,
        name = "fixture intersection",
        description = "synthetic test candidate",
        routeOffsetMeters = 1_000.0,
        coordinate = GeoCoordinate(37.0 + 1_000.0 / 111_194.9, 127.0),
        approachCoordinate = null,
        headingDegrees = 0.0,
        sourceRouteId = route.routeId,
    )
}

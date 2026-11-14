package ai.firstlook.app.data

import ai.firstlook.app.model.RouteMatchStatus
import ai.firstlook.app.model.RouteGeometryStatus
import ai.firstlook.app.model.RouteSource
import ai.firstlook.app.model.GeoCoordinate
import ai.firstlook.app.model.JunctionDataSource
import ai.firstlook.app.model.JunctionRecord
import ai.firstlook.app.model.RouteSnapshot
import ai.firstlook.app.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class DemoRouteRepositoryTest {
    @Test
    fun `junction-aware repository adds only records matched to the verified route`() = runBlocking {
        val points = listOf(GeoCoordinate(37.498, 127.0), GeoCoordinate(37.5, 127.0), GeoCoordinate(37.502, 127.0))
        val route = RouteSnapshot(
            origin = "fixture start", destination = "fixture end", durationMinutes = 1, distanceKm = 1,
            segments = emptyList(), routeId = "fixture-route", source = RouteSource.EXTERNAL_ROUTE,
            geometryStatus = RouteGeometryStatus.VERIFIED, geometry = points,
        )
        val record = JunctionRecord(
            id = "fixture-junction", routeId = "fixture-route", junction = points[1],
            approachPoint = points[0], panDegrees = 0.0, cautionPoint = points[1],
            message = "분기점 표지를 미리 확인하세요.", detourCost = null,
            keepPath = listOf(GeoCoordinate(37.5, 127.0002), GeoCoordinate(37.5, 127.0004), GeoCoordinate(37.5, 127.0006)),
            trapPath = listOf(GeoCoordinate(37.5, 126.9998), GeoCoordinate(37.5, 126.9996), GeoCoordinate(37.5, 126.9994)),
            source = JunctionDataSource("OpenStreetMap", "© OpenStreetMap contributors", "ODbL-1.0", null, "1", "2", "3"),
            manuallyVerified = true, roadViewCoverageVerified = false,
        )
        val repository = JunctionAwareRouteRepository(
            routeRepository = object : RouteRepository { override suspend fun preview(origin: String, destination: String) = route },
            recordSource = object : JunctionRecordSource {
                override suspend fun records(route: RouteSnapshot) = listOf(record, record.copy(id = "wrong-route", routeId = "old-route"))
            },
        )

        val result = repository.preview("fixture start", "fixture end")

        assertEquals(1, result.segments.size)
        assertEquals("fixture-junction", result.segments.single().id)
        assertEquals(record, result.segments.single().junctionRecord)
        assertEquals(RouteMatchStatus.VERIFIED, result.segments.single().routeMatchStatus)
    }

    @Test
    fun `analyzing repository preserves curated demo segments without invoking candidate source`() = runBlocking {
        var invoked = false
        val repository = AnalyzingRouteRepository(
            routeRepository = DemoRouteRepository(),
            featureSource = object : RouteMapFeatureSource {
                override suspend fun features(route: ai.firstlook.app.model.RouteSnapshot): List<RouteMapFeature> {
                    invoked = true
                    return emptyList()
                }
            },
        )

        val route = repository.preview("수원시 영통구", "강남역")

        assertEquals(3, route.segments.size)
        assertFalse(invoked)
    }

    @Test
    fun `analyzing repository discovers verified geometry turns even when summary distance is rounded down`() = runBlocking {
        val verifiedRoute = RouteSnapshot(
            origin = "fixture start",
            destination = "fixture end",
            durationMinutes = 1,
            distanceKm = 0,
            segments = emptyList(),
            routeId = "short-verified-route",
            source = RouteSource.EXTERNAL_ROUTE,
            geometryStatus = RouteGeometryStatus.VERIFIED,
            geometry = listOf(
                GeoCoordinate(37.0, 127.0),
                GeoCoordinate(37.002, 127.0),
                GeoCoordinate(37.002, 127.002),
            ),
        )
        val repository = AnalyzingRouteRepository(
            routeRepository = object : RouteRepository {
                override suspend fun preview(origin: String, destination: String) = verifiedRoute
            },
        )

        val analyzed = repository.preview("fixture start", "fixture end")

        assertEquals(listOf(SegmentType.COMPLEX_TURN), analyzed.segments.map { it.type })
        assertEquals(RouteMatchStatus.VERIFIED, analyzed.segments.single().routeMatchStatus)
    }

    @Test
    fun `demo route contains three explicitly unverified segments`() = runBlocking {
        val route = DemoRouteRepository().preview("수원시 영통구", "강남역")

        assertEquals("수원시 영통구", route.origin)
        assertEquals("강남역", route.destination)
        assertEquals(57, route.durationMinutes)
        assertEquals(48, route.distanceKm)
        assertEquals(3, route.segments.size)
        assertFalse(route.segments.any { it.laneGuidanceVerified })
        assertEquals(RouteSource.CURATED_DEMO, route.source)
        assertEquals(RouteGeometryStatus.UNAVAILABLE, route.geometryStatus)
        assertTrue(route.segments.all { it.coordinate == null && it.routeMatchStatus == RouteMatchStatus.NOT_APPLICABLE })
    }
}

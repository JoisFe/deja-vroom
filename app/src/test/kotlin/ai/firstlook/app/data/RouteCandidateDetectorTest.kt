package ai.firstlook.app.data

import ai.firstlook.app.model.GeoCoordinate
import ai.firstlook.app.model.RouteGeometryStatus
import ai.firstlook.app.model.RouteSource
import ai.firstlook.app.model.RouteSnapshot
import ai.firstlook.app.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCandidateDetectorTest {
    private val detector = RouteCandidateDetector()

    @Test
    fun `straight route and curated demo produce no geometry candidates`() {
        val straight = verifiedRoute(points(37.000, 127.000, 37.001, 127.000, 37.002, 127.000))

        assertTrue(detector.detect(straight).isEmpty())
        assertTrue(detector.detect(straight.copy(source = RouteSource.CURATED_DEMO)).isEmpty())
    }

    @Test
    fun `sharp turn creates candidate tied to route with approach and outgoing heading`() {
        val route = verifiedRoute(points(37.000, 127.000, 37.002, 127.000, 37.002, 127.002))

        val candidate = detector.detect(route).single()

        assertEquals(SegmentType.COMPLEX_TURN, candidate.type)
        assertEquals(route.routeId, candidate.sourceRouteId)
        assertTrue(candidate.routeOffsetMeters in 200.0..250.0)
        assertTrue(candidate.approachCoordinate != null)
        assertTrue(candidate.headingDegrees!! in 80.0..100.0)
        assertTrue(candidate.evidence.any { it.startsWith("방향 전환 ") })
    }

    @Test
    fun `closely spaced turns form one denser briefing candidate`() {
        val route = verifiedRoute(points(
            37.0000, 127.0000,
            37.0005, 127.0000,
            37.0005, 127.0005,
            37.0010, 127.0005,
            37.0010, 127.0010,
        ))

        val candidate = detector.detect(route).single()

        assertEquals("연속 회전 구간", candidate.name)
        assertEquals(1.0, candidate.maneuverDensity, 0.0)
        assertTrue(candidate.evidence.any { it.contains("전환 3회") })
    }

    @Test
    fun `map features preserve source and historical risk disclaimer`() {
        val route = verifiedRoute(points(37.000, 127.000, 37.004, 127.000))
        val feature = RouteMapFeature(
            id = "historic-1",
            type = RouteMapFeature.Type.HISTORICAL_ACCIDENT_HOTSPOT,
            name = "사고 이력 참고 지점",
            description = "과거 통계 참고",
            coordinate = GeoCoordinate(37.002, 127.00001),
            source = "공공 사고 통계 fixture",
            confidence = 0.8,
            historicalIncidentSignal = 0.6,
        )

        val candidate = detector.detect(route, listOf(feature)).single()

        assertEquals("historic-1", candidate.id)
        assertTrue(candidate.evidence.any { it.contains(feature.source) })
        assertTrue(candidate.evidence.any { it.contains("현재 위험도 의미 아님") })
    }

    private fun verifiedRoute(geometry: List<GeoCoordinate>) = RouteSnapshot(
        origin = "fixture start",
        destination = "fixture end",
        durationMinutes = 10,
        distanceKm = 2,
        segments = emptyList(),
        routeId = "verified-fixture",
        source = RouteSource.EXTERNAL_ROUTE,
        geometryStatus = RouteGeometryStatus.VERIFIED,
        geometry = geometry,
    )

    private fun points(vararg latitudeLongitude: Double): List<GeoCoordinate> =
        latitudeLongitude.toList().chunked(2).map { (latitude, longitude) -> GeoCoordinate(latitude, longitude) }
}

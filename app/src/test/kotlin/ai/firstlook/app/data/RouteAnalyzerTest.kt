package ai.firstlook.app.data

import ai.firstlook.app.model.GeoCoordinate
import ai.firstlook.app.model.RouteGeometryStatus
import ai.firstlook.app.model.RouteMatchStatus
import ai.firstlook.app.model.RouteSegmentCandidate
import ai.firstlook.app.model.RouteSource
import ai.firstlook.app.model.RouteSnapshot
import ai.firstlook.app.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAnalyzerTest {
    @Test
    fun `without verified geometry or with curated demo source returns no detected candidates`() {
        val candidates = listOf(candidate("one", 1_000.0))

        assertTrue(RouteAnalyzer().select(route.copy(geometryStatus = RouteGeometryStatus.UNAVAILABLE), candidates).isEmpty())
        assertTrue(RouteAnalyzer().select(route.copy(source = RouteSource.CURATED_DEMO), candidates).isEmpty())
    }

    @Test
    fun `selects at most three high scoring candidates and removes nearby duplicates`() {
        val candidates = listOf(
            candidate("lower-score-nearby", 1_100.0, complexity = 0.4),
            candidate("top", 1_000.0, complexity = 1.0, laneGuidanceVerified = true, verifiedGuidance = "왼쪽 두 번째 차로로 이동하세요."),
            candidate("second", 8_000.0, highwayJunction = 1.0),
            candidate("third", 16_000.0, destinationProximity = 1.0),
            candidate("fourth", 30_000.0, maneuverDensity = 1.0),
        )

        val selected = RouteAnalyzer().select(route, candidates)

        assertEquals(listOf("top", "fourth", "second"), selected.map { it.id })
        assertEquals("왼쪽 두 번째 차로로 이동하세요.", selected.first().guidance)
        assertTrue(selected.first().laneGuidanceVerified)
        assertFalse(selected.first().canShowSpecificGuidance)
        assertTrue(selected.all { it.routeMatchStatus == RouteMatchStatus.VERIFIED })
        assertEquals(3, selected.size)
    }

    @Test
    fun `unverified maneuver never becomes specific lane guidance`() {
        val result = RouteAnalyzer().select(route, listOf(candidate("one", 1_000.0, laneGuidanceVerified = true)))

        assertFalse(result.single().laneGuidanceVerified)
        assertEquals("교차로 구조와 표지를 확인하세요.", result.single().guidance)
    }

    private fun candidate(
        id: String,
        offset: Double,
        complexity: Double = 0.0,
        highwayJunction: Double = 0.0,
        destinationProximity: Double = 0.0,
        maneuverDensity: Double = 0.0,
        laneGuidanceVerified: Boolean = false,
        verifiedGuidance: String? = null,
    ) = RouteSegmentCandidate(
        id = id,
        type = SegmentType.INTERSECTION,
        name = id,
        description = "test candidate",
        routeOffsetMeters = offset,
        coordinate = GeoCoordinate(37.0 + offset / 111_194.9, 127.0),
        approachCoordinate = null,
        headingDegrees = 0.0,
        complexity = complexity,
        maneuverDensity = maneuverDensity,
        highwayJunction = highwayJunction,
        destinationProximity = destinationProximity,
        dataConfidence = 1.0,
        verifiedGuidance = verifiedGuidance,
        laneGuidanceVerified = laneGuidanceVerified,
        evidence = listOf("test fixture"),
        sourceRouteId = "verified-route-fixture",
    )

    private val route = RouteSnapshot(
        origin = "origin",
        destination = "destination",
        durationMinutes = 20,
        distanceKm = 40,
        segments = emptyList(),
        routeId = "verified-route-fixture",
        source = RouteSource.EXTERNAL_ROUTE,
        geometryStatus = RouteGeometryStatus.VERIFIED,
        geometry = listOf(GeoCoordinate(37.0, 127.0), GeoCoordinate(37.36, 127.0)),
    )
}

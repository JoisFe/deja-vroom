package ai.dejavroom.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingSegmentTest {
    private val playable = BriefingSegment(
        id = "p", type = SegmentType.INTERSECTION, name = "p", description = "p", guidance = "p", distanceFromStartKm = 1.0,
        coordinate = GeoCoordinate(37.5, 127.0), headingDegrees = 90.0, routeMatchStatus = RouteMatchStatus.VERIFIED,
    )

    @Test
    fun `a verified point with a place and a heading can play its road view`() {
        assertTrue(playable.canPlayRoadView)
    }

    @Test
    fun `a point without a place or a heading cannot play its road view`() {
        assertFalse(playable.copy(coordinate = null).canPlayRoadView)
        assertFalse(playable.copy(headingDegrees = null).canPlayRoadView)
    }

    @Test
    fun `only a verified route match can play its road view`() {
        RouteMatchStatus.entries.filter { it != RouteMatchStatus.VERIFIED }.forEach { status ->
            assertFalse(status.name, playable.copy(routeMatchStatus = status).canPlayRoadView)
        }
    }
}

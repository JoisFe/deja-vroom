package ai.firstlook.app.data

import ai.firstlook.app.model.RoadViewContentOrigin
import ai.firstlook.app.model.RouteMatchStatus
import ai.firstlook.app.model.SegmentType
import ai.firstlook.app.model.BriefingSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoadViewProviderTest {
    @Test
    fun `demo provider never claims a verified road view`() = runBlocking {
        val result = DemoRoadViewProvider().findNearestViews(segment, 300)

        val view = (result as RoadViewLookupResult.Available).views.single()
        assertEquals(RoadViewContentOrigin.DEMO_ILLUSTRATION, view.contentOrigin)
        assertEquals(RouteMatchStatus.NOT_APPLICABLE, view.routeMatchStatus)
        assertFalse(view.isVerifiedForRoute)
        assertEquals(null, view.imageUrl)
    }

    @Test
    fun `live provider is not authorized by default`() = runBlocking {
        assertEquals(RoadViewLookupResult.NotAuthorized, UnconfiguredRoadViewProvider().findNearestViews(segment, 300))
    }

    @Test
    fun `specific guidance requires both verified lane data and verified photo matching`() {
        val laneVerified = segment.copy(laneGuidanceVerified = true)
        val bothVerified = laneVerified.copy(routeMatchStatus = RouteMatchStatus.VERIFIED)
        val realPhotoVerified = bothVerified.copy(roadViewOrigin = RoadViewContentOrigin.LIVE_PROVIDER)
        val overlayAuthorized = realPhotoVerified.copy(roadViewAllowsOverlay = true)

        assertFalse(laneVerified.canShowSpecificGuidance)
        assertFalse(bothVerified.canShowSpecificGuidance)
        assertFalse(realPhotoVerified.canShowSpecificGuidance)
        assertEquals(true, overlayAuthorized.canShowSpecificGuidance)
    }

    private val segment = BriefingSegment(
        id = "demo",
        type = SegmentType.INTERSECTION,
        name = "demo intersection",
        description = "demo",
        guidance = "교차로 구조와 표지를 확인하세요.",
        distanceFromStartKm = 1.0,
    )
}

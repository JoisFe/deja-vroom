package ai.firstlook.app.feature

import ai.firstlook.app.data.DemoRouteRepository
import ai.firstlook.app.data.RoadViewLookupResult
import ai.firstlook.app.data.RoadViewResult
import ai.firstlook.app.model.RoadViewContentOrigin
import ai.firstlook.app.model.RouteMatchStatus
import ai.firstlook.app.model.GeoCoordinate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstLookUiStateTest {
    @Test
    fun `selecting a segment opens its briefing`() = runBlocking {
        val route = DemoRouteRepository().preview("수원시 영통구", "강남역")
        val state = FirstLookUiState(route = route, screen = FirstLookScreen.SUMMARY)

        val selected = state.selectingBriefingSegment(1)

        assertEquals(FirstLookScreen.BRIEFING, selected.screen)
        assertEquals(1, selected.segmentIndex)
    }

    @Test
    fun `segment selection clamps to available route segments`() = runBlocking {
        val route = DemoRouteRepository().preview("수원시 영통구", "강남역")
        val state = FirstLookUiState(route = route)

        assertEquals(0, state.selectingBriefingSegment(-1).segmentIndex)
        assertEquals(route.segments.lastIndex, state.selectingBriefingSegment(route.segments.size).segmentIndex)
    }

    @Test
    fun `segment selection without a route leaves the state unchanged`() {
        val state = FirstLookUiState()

        assertEquals(state, state.selectingBriefingSegment(1))
    }

    @Test
    fun `briefing advances through all segments and completes once`() = runBlocking {
        val route = DemoRouteRepository().preview("수원시 영통구", "강남역")
        var state = FirstLookUiState(route = route, screen = FirstLookScreen.BRIEFING)

        route.segments.dropLast(1).forEachIndexed { index, _ ->
            state = state.advancingBriefing()
            assertEquals(index + 1, state.segmentIndex)
            assertEquals(FirstLookScreen.BRIEFING, state.screen)
        }
        state = state.advancingBriefing()

        assertEquals(route.segments.lastIndex, state.segmentIndex)
        assertEquals(FirstLookScreen.COMPLETE, state.screen)
    }

    @Test
    fun `briefing does not advance without a route or segments`() {
        val noRoute = FirstLookUiState()
        val emptyRoute = noRoute.copy(route = ai.firstlook.app.model.RouteSnapshot("A", "B", 0, 0, emptyList()))

        assertEquals(noRoute, noRoute.advancingBriefing())
        assertEquals(emptyRoute, emptyRoute.advancingBriefing())
    }

    @Test
    fun `driving restriction prevents segment and road view exploration`() = runBlocking {
        val route = DemoRouteRepository().preview("수원시 영통구", "강남역")
        val segmentId = route.segments.first().id
        val views = RoadViewLookupResult.Available(listOf(0, 1).map { index ->
            RoadViewResult(
                imageUrl = null,
                capturedAt = null,
                providerName = "fake",
                headingDegrees = null,
                attribution = null,
                coordinate = GeoCoordinate(37.0, 127.0),
                viewId = "view-$index",
                contentOrigin = RoadViewContentOrigin.DEMO_ILLUSTRATION,
                routeMatchStatus = RouteMatchStatus.NOT_APPLICABLE,
            )
        })
        val state = FirstLookUiState(
            route = route,
            screen = FirstLookScreen.BRIEFING,
            isDrivingRestricted = true,
            roadViewLookups = mapOf(segmentId to views),
        )

        assertEquals(state, state.selectingBriefingSegment(1))
        assertEquals(state, state.advancingBriefing())
        assertEquals(state, state.movingRoadViewIndex(segmentId, 1))
    }

    @Test
    fun `road view index clamps to the available views`() = runBlocking {
        val route = DemoRouteRepository().preview("수원시 영통구", "강남역")
        val segmentId = route.segments.first().id
        val views = RoadViewLookupResult.Available(listOf("a", "b", "c").map { id ->
            RoadViewResult(null, null, "fake", null, null, viewId = id)
        })
        val state = FirstLookUiState(route = route, roadViewLookups = mapOf(segmentId to views))

        val second = state.movingRoadViewIndex(segmentId, 1)
        val last = second.movingRoadViewIndex(segmentId, 99)
        val first = state.movingRoadViewIndex(segmentId, -99)

        assertEquals(1, second.roadViewIndices[segmentId])
        assertEquals(2, last.roadViewIndices[segmentId])
        assertEquals(0, first.roadViewIndices[segmentId])
    }

    @Test
    fun `road view development preview cannot open while driving is restricted`() {
        val available = FirstLookUiState().enteringRoadViewTest()
        val restricted = FirstLookUiState(isDrivingRestricted = true)

        assertEquals(FirstLookScreen.ROADVIEW_TEST, available.screen)
        assertEquals(restricted, restricted.enteringRoadViewTest())
    }
}

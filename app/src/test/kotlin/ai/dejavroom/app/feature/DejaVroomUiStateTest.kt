package ai.dejavroom.app.feature

import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DejaVroomUiStateTest {
    private fun segment(id: String) = BriefingSegment(
        id = id, type = SegmentType.INTERSECTION, name = id, description = id, guidance = id, distanceFromStartKm = 1.0,
    )

    private val route = RouteSnapshot(
        origin = "A", destination = "B", durationMinutes = 10, distanceKm = 5,
        segments = listOf(segment("s1"), segment("s2"), segment("s3")),
    )

    @Test
    fun `advancing past the last segment completes the briefing`() {
        val state = DejaVroomUiState(route = route, screen = DejaVroomScreen.BRIEFING, segmentIndex = 2)
        assertEquals(DejaVroomScreen.COMPLETE, state.advancingBriefing().screen)
    }

    @Test
    fun `advancing moves to the next segment`() {
        val state = DejaVroomUiState(route = route, screen = DejaVroomScreen.BRIEFING, segmentIndex = 0)
        assertEquals(1, state.advancingBriefing().segmentIndex)
    }

    @Test
    fun `selecting an out-of-range segment is clamped`() {
        val state = DejaVroomUiState(route = route)
        assertEquals(2, state.selectingBriefingSegment(9).segmentIndex)
        assertEquals(0, state.selectingBriefingSegment(-3).segmentIndex)
    }

    @Test
    fun `previous segment never goes below zero`() {
        val state = DejaVroomUiState(route = route, segmentIndex = 0)
        assertEquals(0, state.previousBriefingSegment().segmentIndex)
    }

    @Test
    fun `driving restriction blocks every briefing transition`() {
        val state = DejaVroomUiState(route = route, segmentIndex = 1, isDrivingRestricted = true)
        assertSame(state, state.advancingBriefing())
        assertSame(state, state.previousBriefingSegment())
        assertSame(state, state.selectingBriefingSegment(0))
        assertSame(state, state.enteringRoadViewTest())
    }

    private fun ranked(id: String, rank: Int) = segment(id).copy(selectionRank = rank)

    /** Five points in driving order whose importance ranks are 4, 3, 1, 5, 2. */
    private val rankedRoute = route.copy(segments = listOf(ranked("a", 4), ranked("b", 3), ranked("c", 1), ranked("d", 5), ranked("e", 2)))

    @Test
    fun `the shown points are always in driving order whatever their rank`() {
        assertEquals(listOf("b", "c", "e"), DejaVroomUiState(route = rankedRoute).shownSegments.map { it.id })
        assertEquals(listOf("a", "b", "c", "d", "e"), DejaVroomUiState(route = rankedRoute, displayCount = 5).shownSegments.map { it.id })
    }

    @Test
    fun `positions count the shown points so numbers never skip`() {
        // In the three-point view the cards are 1/3, 2/3, 3/3; stepping forward from the second reaches the third.
        val state = DejaVroomUiState(route = rankedRoute, screen = DejaVroomScreen.BRIEFING, segmentIndex = 1)
        assertEquals("c", state.shownSegments[state.segmentIndex].id)
        val next = state.advancingBriefing()
        assertEquals(2, next.segmentIndex)
        assertEquals("e", next.shownSegments[next.segmentIndex].id)
        assertEquals(DejaVroomScreen.COMPLETE, next.advancingBriefing().screen)
    }
}

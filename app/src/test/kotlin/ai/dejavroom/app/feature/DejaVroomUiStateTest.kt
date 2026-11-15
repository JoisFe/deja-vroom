package ai.dejavroom.app.feature

import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DejaVroomUiStateTest {
    private fun segment(id: String) = BriefingSegment(
        id = id,
        type = SegmentType.INTERSECTION,
        name = id,
        description = id,
        guidance = id,
        distanceFromStartKm = 1.0,
    )

    private val route = RouteSnapshot(
        origin = "A",
        destination = "B",
        durationMinutes = 10,
        distanceKm = 5,
        segments = listOf(segment("s1"), segment("s2"), segment("s3")),
    )

    @Test
    fun `driving restriction keeps the road view closed`() {
        val state = DejaVroomUiState(route = route, segmentIndex = 1, isDrivingRestricted = true)
        assertSame(state, state.enteringRoadView())
    }

    private fun ranked(id: String, rank: Int) = segment(id).copy(selectionRank = rank)

    /** Five points in driving order whose importance ranks are 4, 3, 1, 5, 2. */
    private val rankedRoute = route.copy(
        segments = listOf(ranked("a", 4), ranked("b", 3), ranked("c", 1), ranked("d", 5), ranked("e", 2)),
    )

    @Test
    fun `every picked point is shown, in driving order whatever its rank`() {
        assertEquals(listOf("a", "b", "c", "d", "e"), DejaVroomUiState(route = rankedRoute).shownSegments.map { it.id })
    }
}

package ai.dejavroom.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteSnapshotTest {
    private fun segment(id: String, rank: Int) = BriefingSegment(
        id = id, type = SegmentType.INTERSECTION, name = id, description = id, guidance = id, distanceFromStartKm = 1.0, selectionRank = rank,
    )

    private fun route(vararg ranks: Int) = RouteSnapshot(
        origin = "A", destination = "B", durationMinutes = 10, distanceKm = 5,
        segments = ranks.mapIndexed { index, rank -> segment("s$index", rank) },
    )

    @Test
    fun `the most important segments stay in driving order and keep their index`() {
        // Driving order with ranks 4, 3, 1, 5, 2: the top three are the 2nd, 3rd and 5th points on the way.
        val shown = route(4, 3, 1, 5, 2).mostImportantSegments(3)
        assertEquals(listOf(1, 2, 4), shown.map { it.index })
        assertEquals(listOf("s1", "s2", "s4"), shown.map { it.value.id })
    }

    @Test
    fun `asking for more than there are shows everything`() {
        assertEquals(listOf(0, 1), route(2, 1).mostImportantSegments(5).map { it.index })
    }

    @Test
    fun `unranked demo segments are shown from the start`() {
        assertEquals(listOf(0, 1, 2), route(0, 0, 0, 0).mostImportantSegments(3).map { it.index })
    }
}

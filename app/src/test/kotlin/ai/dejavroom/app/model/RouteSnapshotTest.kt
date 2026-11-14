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
    fun `the most important segments are listed nearest to the origin first`() {
        // Driving order with ranks 4, 3, 1, 5, 2: the top three are the 2nd, 3rd and 5th points on the way,
        // and they are shown in that driving order, not by rank.
        val shown = route(4, 3, 1, 5, 2).mostImportantSegments(3)
        assertEquals(listOf("s1", "s2", "s4"), shown.map { it.id })
        assertEquals(listOf("s0", "s1", "s2", "s3", "s4"), route(4, 3, 1, 5, 2).mostImportantSegments(5).map { it.id })
    }

    @Test
    fun `asking for more than there are shows everything`() {
        assertEquals(listOf("s0", "s1"), route(2, 1).mostImportantSegments(5).map { it.id })
    }

    @Test
    fun `unranked demo segments are shown from the start`() {
        assertEquals(listOf("s0", "s1", "s2"), route(0, 0, 0, 0).mostImportantSegments(3).map { it.id })
    }
}

package ai.dejavroom.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoadViewPageEventTest {
    @Test
    fun `drive progress carries the distance left to the point`() {
        assertEquals(RoadViewPageEvent.DriveProgress(125), RoadViewPageEvent.parse("drive-progress:125"))
        assertEquals(RoadViewPageEvent.DriveProgress(-40), RoadViewPageEvent.parse("drive-progress:-40"))
        assertEquals(RoadViewPageEvent.DriveFinished, RoadViewPageEvent.parse("drive-finished"))
    }

    @Test
    fun `anything that is not a known fixed message is ignored`() {
        assertNull(RoadViewPageEvent.parse("drive-progress:99999"))
        assertNull(RoadViewPageEvent.parse("drive-progress:abc"))
    }

    @Test
    fun `a selected point is accepted for any card a route can have`() {
        assertEquals(RoadViewPageEvent.RouteCandidateSelected(0), RoadViewPageEvent.parse("route-candidate-selected:0"))
        assertEquals(RoadViewPageEvent.RouteCandidateSelected(11), RoadViewPageEvent.parse("route-candidate-selected:11"))
        assertNull(RoadViewPageEvent.parse("route-candidate-selected:30"))
        assertNull(RoadViewPageEvent.parse("route-candidate-selected:-1"))
        assertNull(RoadViewPageEvent.parse("<script>alert(1)</script>"))
        assertNull(RoadViewPageEvent.parse(null))
    }
}

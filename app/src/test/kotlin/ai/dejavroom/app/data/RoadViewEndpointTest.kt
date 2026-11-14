package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadViewEndpointTest {
    private val pages = requireNotNull(RoadViewEndpoint.parse("https://team.github.io/deja-vroom/roadview/"))

    @Test
    fun `empty or missing setting means not configured`() {
        assertNull(RoadViewEndpoint.parse(""))
        assertNull(RoadViewEndpoint.parse("   "))
    }

    @Test
    fun `only https or local http are accepted`() {
        assertNull(RoadViewEndpoint.parse("http://team.github.io/deja-vroom/roadview/"))
        assertNull(RoadViewEndpoint.parse("ftp://team.github.io/roadview/"))
        assertNull(RoadViewEndpoint.parse("https://team.github.io/roadview/?x=1"))
        assertEquals("http://localhost:8080", RoadViewEndpoint.parse("http://localhost:8080/roadview/")?.origin)
    }

    @Test
    fun `page url and origin are derived from the base url`() {
        assertEquals("https://team.github.io/deja-vroom/roadview/", pages.pageUrl)
        assertEquals("https://team.github.io", pages.origin)
        assertEquals("https://team.github.io/deja-vroom/roadview/", RoadViewEndpoint.parse("https://team.github.io/deja-vroom/roadview")?.pageUrl)
    }

    @Test
    fun `thumbnail url carries coordinate and heading`() {
        assertEquals(
            "https://team.github.io/deja-vroom/roadview/?thumb=1&lat=37.497952&lng=127.027619&heading=90.000000",
            pages.thumbnailUrl(GeoCoordinate(37.497952, 127.027619), 90.0),
        )
        assertTrue(pages.thumbnailUrl(GeoCoordinate(37.5, 127.0), null).endsWith("&heading=0.000000"))
    }

    @Test
    fun `only the roadview page itself is allowed`() {
        assertTrue(pages.isRoadViewPage("https://team.github.io/deja-vroom/roadview/"))
        assertTrue(pages.isRoadViewPage("https://team.github.io/deja-vroom/roadview/?thumb=1&lat=1&lng=2"))
        assertFalse(pages.isRoadViewPage("https://team.github.io/other/"))
        assertFalse(pages.isRoadViewPage("https://evil.example/deja-vroom/roadview/"))
        assertFalse(pages.isRoadViewPage("http://team.github.io/deja-vroom/roadview/"))
    }
}

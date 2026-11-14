package ai.dejavroom.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Roadview page and the app must agree on the bridge name, or the page ignores the app's candidates. */
class RoadViewBridgeContractTest {
    private val page: String = listOf("../web/roadview/index.html", "web/roadview/index.html")
        .map(::File).first { it.exists() }.readText()

    @Test
    fun `roadview page uses the app bridge name`() {
        assertTrue("index.html must use window.$ROADVIEW_BRIDGE_NAME", page.contains("window.$ROADVIEW_BRIDGE_NAME"))
    }

    @Test
    fun `roadview page has no stale bridge name`() {
        assertFalse(page.contains("FirstLookBridge"))
    }
}

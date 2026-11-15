package ai.dejavroom.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Roadview page and the app must agree on the bridge name, or the page ignores the app's candidates. */
class RoadViewBridgeContractTest {
    private fun webFile(name: String): String = listOf("../web/roadview/$name", "web/roadview/$name")
        .map(::File).first { it.exists() }.readText()

    private val page = webFile("index.html")
    private val script = webFile("roadview.js")

    @Test
    fun `roadview script uses the app bridge name`() {
        assertTrue("roadview.js must use window.$ROADVIEW_BRIDGE_NAME", script.contains("window.$ROADVIEW_BRIDGE_NAME"))
        assertFalse(script.contains("FirstLookBridge"))
    }

    @Test
    fun `the place search page speaks over the same bridge and keeps its key as a placeholder too`() {
        val placesPage = webFile("places.html")
        val placesScript = webFile("places.js")
        assertTrue(placesPage.contains("<script src=\"places.js\"></script>"))
        assertTrue(placesPage.contains("content=\"__KAKAO_JS_KEY__\""))
        assertTrue(placesScript.contains("window.$ROADVIEW_BRIDGE_NAME"))
        // The messages the app's parser knows.
        listOf("\"ready\"", "\"fail:config\"", "\"fail:sdk-load\"", "type: \"places\"", "type: \"places-error\"").forEach {
            assertTrue("places.js must send $it", placesScript.contains(it))
        }
    }

    @Test
    fun `page loads the script and keeps the key as a deploy-time placeholder`() {
        assertTrue(page.contains("<script src=\"roadview.js\"></script>"))
        assertTrue(page.contains("content=\"__KAKAO_JS_KEY__\""))
    }
}

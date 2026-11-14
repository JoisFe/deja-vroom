package ai.firstlook.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoadViewPageEventTest {
    @Test
    fun `accepts only known page status events`() {
        assertEquals(RoadViewPageEvent.Ready, RoadViewPageEvent.parse("ready"))
        assertEquals(RoadViewPageEvent.ConfigReceived, RoadViewPageEvent.parse("config-received"))
        assertEquals(RoadViewPageEvent.SdkLoaded, RoadViewPageEvent.parse("sdk-loaded"))
        assertEquals(RoadViewPageEvent.CspBlocked, RoadViewPageEvent.parse("csp-blocked"))
        assertEquals(RoadViewPageEvent.SdkReady, RoadViewPageEvent.parse("sdk-ready"))
        assertEquals(RoadViewPageEvent.Shown, RoadViewPageEvent.parse("shown"))
        assertEquals(RoadViewPageEvent.NativeArrowCandidatesHidden(2), RoadViewPageEvent.parse("native-arrow-candidates-hidden:2"))
        assertEquals(RoadViewPageEvent.NativeArrowLayerUnverified, RoadViewPageEvent.parse("native-arrow-layer-unverified"))
        assertEquals(RoadViewPageEvent.RouteOverlayReady(3), RoadViewPageEvent.parse("route-overlay-ready:3"))
        assertEquals(RoadViewPageEvent.JunctionOverlayReady(7), RoadViewPageEvent.parse("junction-overlay-ready:7"))
        assertEquals(RoadViewPageEvent.OverlaySuppressed("route-mismatch"), RoadViewPageEvent.parse("overlay-suppressed:route-mismatch"))
        assertEquals(
            RoadViewPageEvent.Failed(RoadViewPageEvent.FailureReason.NO_PANO),
            RoadViewPageEvent.parse("fail:no-pano"),
        )
    }

    @Test
    fun `rejects malformed or unrecognized messages`() {
        assertNull(RoadViewPageEvent.parse(null))
        assertNull(RoadViewPageEvent.parse("{\"type\":\"ready\"}"))
        assertNull(RoadViewPageEvent.parse("fail:https://attacker.invalid"))
        assertNull(RoadViewPageEvent.parse(" ready"))
        assertNull(RoadViewPageEvent.parse("native-arrow-candidates-hidden:999"))
        assertNull(RoadViewPageEvent.parse("junction-overlay-ready:8"))
        assertNull(RoadViewPageEvent.parse("overlay-suppressed:<script>"))
    }
}

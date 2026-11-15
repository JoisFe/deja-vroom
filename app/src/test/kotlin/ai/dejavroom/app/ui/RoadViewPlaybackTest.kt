package ai.dejavroom.app.ui

import ai.dejavroom.app.data.RoadViewPageEvent
import ai.dejavroom.app.ui.RoadViewPlayback.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadViewPlaybackTest {
    private fun after(vararg events: RoadViewPageEvent) = events.fold(RoadViewPlayback()) { playback, event -> playback.after(event) }

    private val loading = arrayOf(RoadViewPageEvent.Ready, RoadViewPageEvent.ConfigReceived, RoadViewPageEvent.SdkLoaded, RoadViewPageEvent.SdkReady)

    @Test
    fun `a point starts with nothing known`() {
        val fresh = RoadViewPlayback()
        assertEquals(Stage.PREPARING, fresh.stage)
        assertNull(fresh.remainingMeters)
        assertNull(fresh.failure)
        assertFalse(fresh.isShown || fresh.isConnected)
    }

    @Test
    fun `the page works its way up to showing the picture`() {
        assertEquals(Stage.CONNECTED, after(RoadViewPageEvent.Ready).stage)
        assertEquals(Stage.SDK_READY, after(*loading).stage)
        assertTrue(after(*loading, RoadViewPageEvent.Shown).isShown)
        assertTrue(after(*loading, RoadViewPageEvent.RouteCandidateSelected(2)).isShown)
    }

    @Test
    fun `a late or repeated message never takes the screen back`() {
        val shown = after(*loading, RoadViewPageEvent.Shown)
        assertSame(shown, shown.after(RoadViewPageEvent.SdkLoaded))
        assertSame(shown, shown.after(RoadViewPageEvent.Shown))
        // Messages about arrows and overlays mean nothing to the screen.
        assertSame(shown, shown.after(RoadViewPageEvent.NativeArrowScaled(3)))
        assertSame(shown, shown.after(RoadViewPageEvent.DriveFinished))
    }

    @Test
    fun `the distance follows what the page reports, on the way and past the point`() {
        val shown = after(*loading, RoadViewPageEvent.Shown)
        assertEquals(125, shown.after(RoadViewPageEvent.DriveProgress(125)).remainingMeters)
        assertEquals(0, shown.after(RoadViewPageEvent.DriveProgress(125)).after(RoadViewPageEvent.DriveProgress(0)).remainingMeters)
        assertEquals(-40, shown.after(RoadViewPageEvent.DriveProgress(-40)).remainingMeters)
    }

    @Test
    fun `a failure is final and names what went wrong`() {
        val noPhotos = after(*loading, RoadViewPageEvent.CandidateNoCoverage)
        assertEquals(RoadViewFailure.NO_PHOTOS_NEARBY, noPhotos.failure)
        assertSame(noPhotos, noPhotos.after(RoadViewPageEvent.Shown))
        assertSame(noPhotos, noPhotos.failed(RoadViewFailure.RENDERER_GONE))
        assertEquals(RoadViewFailure.KEY_MISSING, after(RoadViewPageEvent.Failed(RoadViewPageEvent.FailureReason.CONFIG)).failure)
        assertEquals(RoadViewFailure.SDK_NOT_LOADED, after(RoadViewPageEvent.Ready, RoadViewPageEvent.Failed(RoadViewPageEvent.FailureReason.SDK_LOAD)).failure)
        assertEquals(RoadViewFailure.PAGE_UNREACHABLE, RoadViewPlayback().failed(RoadViewFailure.PAGE_UNREACHABLE).failure)
    }

    @Test
    fun `a page that never speaks is told apart from one that stalls on the way`() {
        assertEquals(RoadViewFailure.PAGE_SILENT, RoadViewPlayback().afterWaitingForPage().failure)
        // Once it has spoken, that deadline no longer applies.
        val connected = after(RoadViewPageEvent.Ready)
        assertSame(connected, connected.afterWaitingForPage())

        assertEquals(RoadViewFailure.TIMED_OUT, connected.afterWaitingForPicture().failure)
        assertEquals(RoadViewFailure.SDK_STALLED, after(RoadViewPageEvent.Ready, RoadViewPageEvent.SdkLoaded).afterWaitingForPicture().failure)
        val shown = after(*loading, RoadViewPageEvent.Shown)
        assertSame(shown, shown.afterWaitingForPicture())
    }

    @Test
    fun `every failure has something to say and none of it is a stack of jargon`() {
        RoadViewFailure.entries.forEach { failure ->
            val text = roadViewFailureText(failure)
            assertTrue(text, text.endsWith(".") && text.length in 10..80)
        }
    }
}

package ai.firstlook.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BriefingSpeechControllerTest {
    @Test
    fun `announces only when enabled, unrestricted, and briefing is visible`() = runBlocking {
        val output = RecordingBriefingSpeechOutput()
        val controller = BriefingSpeechController(output)

        controller.announce("first segment", enabled = true, restricted = false, briefingVisible = true)
        controller.announce("disabled", enabled = false, restricted = false, briefingVisible = true)
        controller.announce("restricted", enabled = true, restricted = true, briefingVisible = true)
        controller.announce("hidden", enabled = true, restricted = false, briefingVisible = false)

        assertEquals(listOf("first segment"), output.spoken)
    }

    @Test
    fun `stop forwards cancellation to the configured output`() {
        val output = RecordingBriefingSpeechOutput()

        BriefingSpeechController(output).stop()

        assertEquals(1, output.stopCount)
    }
}

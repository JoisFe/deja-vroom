package ai.dejavroom.app.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DejaVroomTokensTest {
    @Test
    fun `every icon parses and has something to draw`() {
        // The path data is hand-written, so a typo would otherwise only show up on the screen that uses it.
        DejaVroomIcons.all.forEach { icon ->
            assertTrue("${icon.name} is empty", icon.root.size > 0)
        }
        assertEquals(DejaVroomIcons.all.size, DejaVroomIcons.all.map { it.name }.toSet().size)
    }

    @Test
    fun `the reference windows are drawn at full size`() {
        assertEquals(1f, DejaVroomDimens.scaleFor(1680.dp, 1190.dp, compact = false))
        assertEquals(1f, DejaVroomDimens.scaleFor(840.dp, 1190.dp, compact = true))
        assertEquals(1f, DejaVroomDimens.scaleFor(2560.dp, 1440.dp, compact = false))
    }

    @Test
    fun `a smaller window shrinks every size by the tighter side`() {
        assertEquals(0.75f, DejaVroomDimens.scaleFor(1260.dp, 1190.dp, compact = false), 0.001f)
        assertEquals(0.8f, DejaVroomDimens.scaleFor(1680.dp, 952.dp, compact = false), 0.001f)
        assertEquals(75.dp, DejaVroomDimens(0.75f).buttonLarge)
    }

    @Test
    fun `the smallest label never drops under the guide's 12sp`() {
        val smallest = DejaVroomDimens.scaleFor(320.dp, 480.dp, compact = true)
        assertTrue(DejaVroomType(smallest).labelSmall.fontSize.value >= 12f)
    }
}

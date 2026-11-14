package ai.firstlook.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class FirstLookThemeTest {
    @Test
    fun `palette follows requested system brightness`() {
        val light = firstLookColors(false)
        val dark = firstLookColors(true)
        assertTrue(light.background.luminance() > 0.8f)
        assertTrue(dark.background.luminance() < 0.05f)
        assertNotEquals(light.surface, dark.surface)
        assertEquals(light, firstLookColors(false))
        assertEquals(dark, firstLookColors(true))
    }

    @Test
    fun `text and status colors meet contrast requirements on intended surfaces`() {
        for (dark in listOf(false, true)) {
            val colors = firstLookColors(dark)
            val surfaces = listOf(colors.background, colors.frame, colors.surface, colors.surfaceVariant)
            for (surface in surfaces) {
                assertContrast(colors.onSurface, surface, "onSurface dark=$dark")
                assertContrast(colors.onSurfaceVariant, surface, "onSurfaceVariant dark=$dark")
                assertContrast(colors.primary, surface, "primary dark=$dark")
                assertContrast(colors.warning, surface, "warning dark=$dark")
                assertContrast(colors.error, surface, "error dark=$dark")
            }
            assertContrast(colors.onPrimary, colors.primary, "onPrimary dark=$dark")
            assertContrast(colors.onSurface, colors.selectedSurface, "selected dark=$dark")
            assertContrast(colors.warning, colors.warningContainer, "warning container dark=$dark")
            assertContrast(colors.error, colors.errorContainer, "error container dark=$dark")
            assertContrast(colors.success, colors.successContainer, "success container dark=$dark")
            for (surface in listOf(colors.surface, colors.selectedSurface)) {
                assertContrast(colors.violet, composite(colors.violet, surface), "violet badge dark=$dark")
                assertContrast(colors.teal, composite(colors.teal, surface), "teal badge dark=$dark")
            }
        }
    }

    @Test
    fun `material scheme uses the app semantic palette`() {
        for (dark in listOf(false, true)) {
            val colors = firstLookColors(dark)
            val scheme = colors.materialColorScheme(dark)
            assertEquals(colors.background, scheme.background)
            assertEquals(colors.surface, scheme.surface)
            assertEquals(colors.onSurfaceVariant, scheme.onSurfaceVariant)
            assertEquals(colors.primary, scheme.primary)
            assertEquals(colors.onPrimary, scheme.onPrimary)
            assertEquals(colors.error, scheme.error)
        }
    }

    private fun assertContrast(foreground: Color, background: Color, role: String) {
        val ratio = (max(foreground.luminance(), background.luminance()) + 0.05f) /
            (min(foreground.luminance(), background.luminance()) + 0.05f)
        assertTrue("$role contrast was $ratio; expected at least 4.5", ratio >= 4.5f)
    }

    private fun composite(foreground: Color, background: Color): Color {
        val alpha = .10f
        return Color(
            red = foreground.red * alpha + background.red * (1 - alpha),
            green = foreground.green * alpha + background.green * (1 - alpha),
            blue = foreground.blue * alpha + background.blue * (1 - alpha),
        )
    }
}

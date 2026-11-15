package ai.dejavroom.app.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The Pleos Connect UI design guide asks for a contrast ratio of at least 4.5:1 between text or icons and
 * what is behind them. Every pair the screens use is listed here, for the day and the night palette.
 */
class DejaVroomColorsTest {
    @Test
    fun `text and icons keep 4_5 to 1 against their backgrounds by day and by night`() {
        listOf(false, true).forEach { dark ->
            val c = dejaVroomColors(dark)
            val pairs = listOf(
                "onSurface on background" to (c.onSurface to c.background),
                "onSurface on backgroundBottom" to (c.onSurface to c.backgroundBottom),
                "onSurface on frame" to (c.onSurface to c.frame),
                "onSurface on surface" to (c.onSurface to c.surface),
                "onSurface on surfaceVariant" to (c.onSurface to c.surfaceVariant),
                "onSurface on selectedSurface" to (c.onSurface to c.selectedSurface),
                "onSurfaceVariant on background" to (c.onSurfaceVariant to c.background),
                "onSurfaceVariant on backgroundBottom" to (c.onSurfaceVariant to c.backgroundBottom),
                "onSurfaceVariant on frame" to (c.onSurfaceVariant to c.frame),
                "onSurfaceVariant on surface" to (c.onSurfaceVariant to c.surface),
                "onSurfaceVariant on surfaceVariant" to (c.onSurfaceVariant to c.surfaceVariant),
                "accent on background" to (c.accent to c.background),
                "accent on backgroundBottom" to (c.accent to c.backgroundBottom),
                "accent on surface" to (c.accent to c.surface),
                "accent on surfaceVariant" to (c.accent to c.surfaceVariant),
                "accent on selectedSurface" to (c.accent to c.selectedSurface),
                "onPrimary on primary" to (c.onPrimary to c.primary),
                "onPrimary on primaryStart" to (c.onPrimary to c.primaryStart),
                "onPrimary on primaryEnd" to (c.onPrimary to c.primaryEnd),
                "onMarker on marker" to (c.onMarker to c.marker),
                "success on successContainer" to (c.success to c.successContainer),
                "success on surface" to (c.success to c.surface),
                "warning on warningContainer" to (c.warning to c.warningContainer),
                "error on errorContainer" to (c.error to c.errorContainer),
                "error on surface" to (c.error to c.surface),
            )
            pairs.forEach { (name, pair) ->
                val ratio = contrast(pair.first, pair.second)
                assertTrue("${if (dark) "night" else "day"}: $name is ${"%.2f".format(ratio)}:1", ratio >= MIN_CONTRAST)
            }
        }
    }

    @Test
    fun `labels over a photo keep 4_5 to 1 against their chip or scrim`() {
        // The scrim is translucent; the worst case behind it is a white sky.
        val scrimOnWhite = blend(PhotoOverlay.scrim, Color.White)
        val chipOnBlack = blend(PhotoOverlay.chip, Color.Black)
        assertTrue(contrast(PhotoOverlay.onScrim, scrimOnWhite) >= MIN_CONTRAST)
        assertTrue(contrast(PhotoOverlay.onScrimVariant, scrimOnWhite) >= MIN_CONTRAST)
        assertTrue(contrast(PhotoOverlay.onChip, chipOnBlack) >= MIN_CONTRAST)
        assertTrue(contrast(PhotoOverlay.onChipAccent, chipOnBlack) >= MIN_CONTRAST)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (lighter, darker) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** WCAG 2 relative luminance of an opaque sRGB color. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double = if (value <= 0.03928f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun blend(top: Color, bottom: Color): Color = Color(
        red = top.red * top.alpha + bottom.red * (1 - top.alpha),
        green = top.green * top.alpha + bottom.green * (1 - top.alpha),
        blue = top.blue * top.alpha + bottom.blue * (1 - top.alpha),
    )

    private companion object {
        const val MIN_CONTRAST = 4.5
    }
}

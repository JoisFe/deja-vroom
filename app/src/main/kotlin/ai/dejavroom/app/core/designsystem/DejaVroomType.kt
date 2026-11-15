package ai.dejavroom.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * The type scale of the Pleos Connect UI design guide (Headline 48-60, Title 32-40, Body 26-30,
 * Label 20-24), which is sized for a car display read at arm's length. Screens pick a role; they never
 * write a size of their own. The typeface is the system one (Asta Sans on Pleos Connect).
 */
@Immutable
internal class DejaVroomType(scale: Float = 1f) {
    /** Largest screen titles. */
    val headline = style(56, 72, FontWeight.ExtraBold, scale)

    /** Largest title of a narrow (portrait) window. */
    val headlineSmall = style(48, 64, FontWeight.Bold, scale)
    val titleLarge = style(40, 52, FontWeight.Bold, scale)
    val titleMedium = style(36, 44, FontWeight.Bold, scale)

    /** Label of a large button. */
    val titleSmall = style(32, 40, FontWeight.Bold, scale)

    /** What the driver types and the names of places. */
    val bodyLarge = style(30, 38, FontWeight.SemiBold, scale)
    val bodyMedium = style(28, 36, FontWeight.Normal, scale)
    val bodySmall = style(26, 34, FontWeight.Normal, scale)

    /** Chips, field names and text buttons. */
    val labelMedium = style(24, 28, FontWeight.Medium, scale)

    /** Credits and other lines the driver does not need while deciding. */
    val labelSmall = style(20, 24, FontWeight.Normal, scale)

    private fun style(size: Int, lineHeight: Int, weight: FontWeight, scale: Float) =
        TextStyle(fontSize = (size * scale).sp, lineHeight = (lineHeight * scale).sp, fontWeight = weight, lineBreak = WholeWords)

    private companion object {
        // Korean wraps between any two syllables by default, which leaves a lone "다" on the next line.
        // Phrase breaking keeps words whole where the platform supports it (Android 13 and later).
        val WholeWords = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Default, LineBreak.WordBreak.Phrase)
    }
}

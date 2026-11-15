package ai.dejavroom.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sizes drawn for the Pleos Connect app window: about 1680 x 1190 dp in the wide (2:1) layout and about
 * 840 x 1190 dp in the narrow (1:2) one, at 160 dpi. [scale] shrinks all of them together for a smaller
 * window, so a layout that fits the reference window fits there too.
 */
@Immutable
internal class DejaVroomDimens(val scale: Float = 1f) {
    /** A size drawn for the reference window, scaled to the current one. Use it for one-off sizes. */
    fun dp(value: Int): Dp = (value * scale).dp

    val railWidth = dp(120)
    val railWidthCompact = dp(96)
    val headerHeight = dp(96)

    /** Space between the window edge and the content, on the sides. */
    val screenPadding = dp(48)
    val screenPaddingCompact = dp(24)

    // Button heights follow the three sizes drawn in the Pleos Connect button guide.
    val buttonLarge = dp(100)
    val buttonMedium = dp(80)
    val buttonSmall = dp(64)
    val fieldHeight = dp(88)

    /** Smallest area a finger has to hit (Android for Cars: 76 x 76 dp). */
    val touchTarget = dp(76)

    val radiusSmall = dp(16)
    val radiusMedium = dp(24)
    val radiusLarge = dp(32)

    val iconSmall = dp(28)
    val icon = dp(36)
    val iconLarge = dp(44)

    val spaceXs = dp(8)
    val spaceS = dp(12)
    val spaceM = dp(16)
    val spaceL = dp(24)
    val spaceXl = dp(32)
    val spaceXxl = dp(48)

    companion object {
        private const val REFERENCE_WIDTH = 1680f
        private const val REFERENCE_WIDTH_COMPACT = 840f
        private const val REFERENCE_HEIGHT = 1190f

        // Below this the smallest label would drop under the guide's 12sp floor.
        private const val MIN_SCALE = 0.6f

        /** How much smaller than the reference window this one is, never larger than 1. */
        fun scaleFor(width: Dp, height: Dp, compact: Boolean): Float {
            val referenceWidth = if (compact) REFERENCE_WIDTH_COMPACT else REFERENCE_WIDTH
            return minOf(width.value / referenceWidth, height.value / REFERENCE_HEIGHT).coerceIn(MIN_SCALE, 1f)
        }
    }
}

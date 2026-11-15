package ai.dejavroom.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * App semantic colors. These are app palettes, not official Pleos brand token values; the blue follows the
 * one drawn in the Pleos Connect UI design guide. Every text and icon pair keeps the guide's 4.5:1 contrast
 * (checked in DejaVroomColorsTest).
 */
@Immutable
internal data class DejaVroomColors(
    /** Top of the window; the system bars take this color too. */
    val background: Color,
    val backgroundBottom: Color,
    val frame: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    /** Fills only (buttons, the selected rail tile). Blue text and icons use [accent]. */
    val primary: Color,
    val primaryStart: Color,
    val primaryEnd: Color,
    val onPrimary: Color,
    /** Blue for text and icons on surfaces, dark enough to read on the tinted ones as well. */
    val accent: Color,
    /** Boundary of a control the driver has to find, such as a text field. */
    val outline: Color,
    /** Faint edge of a card. */
    val hairline: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val selectedSurface: Color,
    /** The one warm color: a place to look at. Numbers on the route and the moment the car reaches one. */
    val marker: Color,
    val onMarker: Color,
    val violet: Color,
    val teal: Color,
    val shadow: Color,
) {
    val backgroundBrush: Brush get() = Brush.verticalGradient(listOf(background, backgroundBottom))
    val primaryBrush: Brush get() = Brush.horizontalGradient(listOf(primaryStart, primaryEnd))

    fun materialColorScheme(darkTheme: Boolean): ColorScheme {
        val base = if (darkTheme) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = selectedSurface, onPrimaryContainer = onSurface,
            secondary = teal, onSecondary = onPrimary,
            secondaryContainer = surfaceVariant, onSecondaryContainer = onSurface,
            tertiary = violet, onTertiary = onPrimary,
            tertiaryContainer = surfaceVariant, onTertiaryContainer = onSurface,
            background = background, onBackground = onSurface,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary, outline = outline, outlineVariant = hairline,
            error = error, onError = onPrimary,
            errorContainer = errorContainer, onErrorContainer = error,
        )
    }
}

private val LightColors = DejaVroomColors(
    background = Color(0xFFFAFCFF), backgroundBottom = Color(0xFFE7EFFC), frame = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEEF3FB),
    onSurface = Color(0xFF131417), onSurfaceVariant = Color(0xFF44464E),
    primary = Color(0xFF0064FF), primaryStart = Color(0xFF0F6BFF), primaryEnd = Color(0xFF0050E0),
    onPrimary = Color.White, accent = Color(0xFF0052D6),
    outline = Color(0xFFAAB4C5), hairline = Color(0xFFE1E7F1),
    success = Color(0xFF176342), successContainer = Color(0xFFDDF3EA),
    warning = Color(0xFF795000), warningContainer = Color(0xFFFFF1CF),
    error = Color(0xFFB42318), errorContainer = Color(0xFFFFE4DF),
    selectedSurface = Color(0xFFE3EEFF),
    marker = Color(0xFFFF8A1F), onMarker = Color(0xFF1F1300),
    violet = Color(0xFF6941AD), teal = Color(0xFF096B70),
    shadow = Color(0xFF1A3E80),
)

private val DarkColors = DejaVroomColors(
    background = Color(0xFF0D1422), backgroundBottom = Color(0xFF101B30), frame = Color(0xFF111A2B),
    surface = Color(0xFF18233A), surfaceVariant = Color(0xFF1F2C46),
    onSurface = Color(0xFFF1F4FA), onSurfaceVariant = Color(0xFFB8C4D8),
    primary = Color(0xFF8AB8FF), primaryStart = Color(0xFFA6C9FF), primaryEnd = Color(0xFF7FB1FF),
    onPrimary = Color(0xFF071F4A), accent = Color(0xFFA9CBFF),
    outline = Color(0xFF5C6B84), hairline = Color(0xFF27344E),
    success = Color(0xFF8DDFB6), successContainer = Color(0xFF173E30),
    warning = Color(0xFFFFD782), warningContainer = Color(0xFF483711),
    error = Color(0xFFFFB4A9), errorContainer = Color(0xFF512824),
    selectedSurface = Color(0xFF1C3564),
    marker = Color(0xFFFF9A3D), onMarker = Color(0xFF241300),
    violet = Color(0xFFD3B8FF), teal = Color(0xFF8BDBDF),
    shadow = Color(0xFF000000),
)

/**
 * Colors for labels drawn over a road photo. The photo looks the same by day and by night, so these do not
 * follow the theme.
 */
internal object PhotoOverlay {
    val scrim = Color(0xEB0B1730)
    val onScrim = Color.White
    val onScrimVariant = Color(0xFFE6ECF8)
    val chip = Color(0xF2FFFFFF)
    val onChip = Color(0xFF131417)
    val onChipAccent = Color(0xFF0052D6)

    /** A filled blue spot on the scrim, such as the circle behind a turn arrow; [onScrim] goes on it. */
    val badge = Color(0xFF0064FF)

    /** Behind a photo that has not arrived yet. */
    val placeholder = Color(0xFF17202D)
}

/** Pure palette selection, also usable by platform window styling and unit tests. */
internal fun dejaVroomColors(darkTheme: Boolean): DejaVroomColors = if (darkTheme) DarkColors else LightColors

private val LocalDejaVroomColors = staticCompositionLocalOf { LightColors }
private val LocalDejaVroomType = staticCompositionLocalOf { DejaVroomType() }
private val LocalDejaVroomDimens = staticCompositionLocalOf { DejaVroomDimens() }

internal object DejaVroomTheme {
    val colors: DejaVroomColors
        @Composable @ReadOnlyComposable get() = LocalDejaVroomColors.current
    val type: DejaVroomType
        @Composable @ReadOnlyComposable get() = LocalDejaVroomType.current
    val dimens: DejaVroomDimens
        @Composable @ReadOnlyComposable get() = LocalDejaVroomDimens.current
}

/** Follows Android's current system night mode unless a preview supplies an explicit mode. */
@Composable
internal fun DejaVroomTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = dejaVroomColors(darkTheme)
    CompositionLocalProvider(LocalDejaVroomColors provides colors) {
        MaterialTheme(colorScheme = colors.materialColorScheme(darkTheme)) {
            DejaVroomScale(scale = 1f, content = content)
        }
    }
}

/**
 * Sizes every type and dimension token for the window the app is shown in. The tokens are drawn for the
 * Pleos Connect window (see [DejaVroomDimens]); a smaller window gets the same layout, smaller.
 */
@Composable
internal fun DejaVroomScale(scale: Float, content: @Composable () -> Unit) {
    val type = remember(scale) { DejaVroomType(scale) }
    val dimens = remember(scale) { DejaVroomDimens(scale) }
    CompositionLocalProvider(
        LocalDejaVroomType provides type,
        LocalDejaVroomDimens provides dimens,
        LocalTextStyle provides type.bodyMedium,
        content = content,
    )
}

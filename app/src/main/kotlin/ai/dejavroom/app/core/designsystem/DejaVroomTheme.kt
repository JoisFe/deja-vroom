package ai.dejavroom.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** App semantic colors; these are app palettes, not official Pleos brand token values. */
@Immutable
internal data class DejaVroomColors(
    val background: Color,
    val frame: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val outline: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val selectedSurface: Color,
    val violet: Color,
    val teal: Color,
) {
    // Compatibility names let existing UI migrate without changing its information hierarchy.
    val panel: Color get() = surface
    val panelRaised: Color get() = surfaceVariant
    val white: Color get() = onSurface
    val muted: Color get() = onSurfaceVariant
    val dim: Color get() = onSurfaceVariant
    val blue: Color get() = primary
    val blueLight: Color get() = primary
    val green: Color get() = success
    val stroke: Color get() = outline

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
            surfaceTint = primary, outline = outline, outlineVariant = outline,
            error = error, onError = onPrimary,
            errorContainer = errorContainer, onErrorContainer = error,
        )
    }
}

private val LightColors = DejaVroomColors(
    background = Color(0xFFF4F6FA), frame = Color(0xFFE9EEF5),
    surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEDF1F7),
    onSurface = Color(0xFF172033), onSurfaceVariant = Color(0xFF4B586D),
    primary = Color(0xFF175CD3), onPrimary = Color.White, outline = Color(0xFF79869A),
    success = Color(0xFF176342), successContainer = Color(0xFFDDF3EA),
    warning = Color(0xFF795000), warningContainer = Color(0xFFFFF1CF),
    error = Color(0xFFB42318), errorContainer = Color(0xFFFFE4DF),
    selectedSurface = Color(0xFFE4EDFF), violet = Color(0xFF6941AD), teal = Color(0xFF096B70),
)

private val DarkColors = DejaVroomColors(
    background = Color(0xFF10151E), frame = Color(0xFF151C28),
    surface = Color(0xFF1B2432), surfaceVariant = Color(0xFF273346),
    onSurface = Color(0xFFF3F6FC), onSurfaceVariant = Color(0xFFC1CCDD),
    primary = Color(0xFF9AC1FF), onPrimary = Color(0xFF10264C), outline = Color(0xFF8595AD),
    success = Color(0xFF8DDFB6), successContainer = Color(0xFF173E30),
    warning = Color(0xFFFFD782), warningContainer = Color(0xFF483711),
    error = Color(0xFFFFB4A9), errorContainer = Color(0xFF512824),
    selectedSurface = Color(0xFF253D60), violet = Color(0xFFD3B8FF), teal = Color(0xFF8BDBDF),
)

/** Pure palette selection, also usable by platform window styling and unit tests. */
internal fun dejaVroomColors(darkTheme: Boolean): DejaVroomColors = if (darkTheme) DarkColors else LightColors

private val LocalFirstLookColors = staticCompositionLocalOf { LightColors }

internal object DejaVroomTheme {
    val colors: DejaVroomColors
        @Composable get() = LocalFirstLookColors.current
}

/** Follows Android's current system night mode unless a preview supplies an explicit mode. */
@Composable
internal fun DejaVroomTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = dejaVroomColors(darkTheme)
    CompositionLocalProvider(LocalFirstLookColors provides colors) {
        MaterialTheme(colorScheme = colors.materialColorScheme(darkTheme), content = content)
    }
}

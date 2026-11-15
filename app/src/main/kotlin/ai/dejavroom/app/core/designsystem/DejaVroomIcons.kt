package ai.dejavroom.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons drawn on a 24 x 24 grid with one stroke width, so they sit together at any size. They are
 * written here as path data instead of pulled from an icon library, which would add a dependency for
 * twenty shapes. The color in the paths is a placeholder: the icon is tinted where it is drawn.
 */
internal object DejaVroomIcons {
    val Home by lazy {
        icon("Home", strokes = listOf("M4 10.8 L12 4 L20 10.8 V19 A1 1 0 0 1 19 20 H14.5 V14.5 H9.5 V20 H5 A1 1 0 0 1 4 19 Z"))
    }
    val Navigation by lazy { icon("Navigation", strokes = listOf("M5 19.5 L12 4 L19 19.5 L12 15.9 Z")) }
    val Music by lazy {
        icon("Music", strokes = listOf("M9 17.5 V6 L19 4 V15.5", circle(6.5f, 17.5f, 2.5f), circle(16.5f, 15.5f, 2.5f)))
    }
    val Phone by lazy {
        icon(
            "Phone",
            strokes = listOf(
                "M6.5 4 H9.5 L11.1 8.2 L9.1 9.6 A12 12 0 0 0 14.4 14.9 L15.8 12.9 L20 14.5 V17.5 " +
                    "A2.5 2.5 0 0 1 17.5 20 A13.5 13.5 0 0 1 4 6.5 A2.5 2.5 0 0 1 6.5 4 Z",
            ),
        )
    }
    val Car by lazy {
        icon(
            "Car",
            strokes = listOf(
                "M5.5 11 L7 6.8 A2 2 0 0 1 8.9 5.5 H15.1 A2 2 0 0 1 17 6.8 L18.5 11",
                roundedRect(3.5f, 11f, 17f, 6f, 2f),
                "M6.5 17 V18.8", "M17.5 17 V18.8", "M7.2 14 H7.3", "M16.7 14 H16.8",
            ),
        )
    }
    val Apps by lazy {
        icon(
            "Apps",
            strokes = listOf(
                roundedRect(4.5f, 4.5f, 6f, 6f, 1.5f), roundedRect(13.5f, 4.5f, 6f, 6f, 1.5f),
                roundedRect(4.5f, 13.5f, 6f, 6f, 1.5f), roundedRect(13.5f, 13.5f, 6f, 6f, 1.5f),
            ),
        )
    }
    val Settings by lazy {
        icon(
            "Settings",
            strokes = listOf(
                circle(12f, 12f, 2.8f), circle(12f, 12f, 6.3f),
                "M12 3.3 V5.7", "M12 18.3 V20.7", "M3.3 12 H5.7", "M18.3 12 H20.7",
                "M5.85 5.85 L7.55 7.55", "M16.45 16.45 L18.15 18.15", "M5.85 18.15 L7.55 16.45", "M16.45 7.55 L18.15 5.85",
            ),
        )
    }
    val Search by lazy { icon("Search", strokes = listOf(circle(11f, 11f, 6.5f), "M20 20 L15.8 15.8")) }
    val MyLocation by lazy {
        icon(
            "MyLocation",
            strokes = listOf(circle(12f, 12f, 6.5f), "M12 2.8 V5.5", "M12 18.5 V21.2", "M2.8 12 H5.5", "M18.5 12 H21.2"),
            fills = listOf(circle(12f, 12f, 2f)),
        )
    }
    val Swap by lazy {
        icon("Swap", strokes = listOf("M8 19 V5", "M4.8 8.2 L8 5 L11.2 8.2", "M16 5 V19", "M12.8 15.8 L16 19 L19.2 15.8"))
    }
    val Close by lazy { icon("Close", strokes = listOf("M7 7 L17 17", "M17 7 L7 17")) }
    val Check by lazy { icon("Check", strokes = listOf("M5 12.5 L9.5 17 L19 7.5")) }
    val History by lazy { icon("History", strokes = listOf(circle(12f, 12f, 8f), "M12 7.5 V12 L15 14")) }
    val ChevronRight by lazy { icon("ChevronRight", strokes = listOf("M9.5 5.5 L16 12 L9.5 18.5")) }
    val ChevronLeft by lazy { icon("ChevronLeft", strokes = listOf("M14.5 5.5 L8 12 L14.5 18.5")) }
    val ArrowForward by lazy { icon("ArrowForward", strokes = listOf("M5 12 H19", "M13 6 L19 12 L13 18")) }
    val Warning by lazy { icon("Warning", strokes = listOf("M12 4 L21 19.5 H3 Z", "M12 10 V14.5", "M12 17 H12.01")) }
    val Place by lazy {
        icon(
            "Place",
            strokes = listOf(
                "M12 21 C12 21 5.5 15.3 5.5 10 A6.5 6.5 0 0 1 18.5 10 C18.5 15.3 12 21 12 21 Z",
                circle(12f, 10f, 2.3f),
            ),
        )
    }
    val Sparkle by lazy {
        icon(
            "Sparkle",
            fills = listOf(
                "M11 4.5 L13 10 L18.5 12 L13 14 L11 19.5 L9 14 L3.5 12 L9 10 Z",
                "M18.5 3 L19.3 5.2 L21.5 6 L19.3 6.8 L18.5 9 L17.7 6.8 L15.5 6 L17.7 5.2 Z",
            ),
        )
    }
    val VolumeOn by lazy {
        icon("VolumeOn", strokes = listOf("M4 9.5 H7.5 L12 6 V18 L7.5 14.5 H4 Z", "M15.5 9 A4.2 4.2 0 0 1 15.5 15", "M18 6.5 A7.8 7.8 0 0 1 18 17.5"))
    }
    val VolumeOff by lazy {
        icon("VolumeOff", strokes = listOf("M4 9.5 H7.5 L12 6 V18 L7.5 14.5 H4 Z", "M15.5 9.5 L20.5 14.5", "M20.5 9.5 L15.5 14.5"))
    }

    /** Every icon, for the test that draws each of them once. */
    val all: List<ImageVector>
        get() = listOf(
            Home, Navigation, Music, Phone, Car, Apps, Settings, Search, MyLocation, Swap, Close, Check, History,
            ChevronRight, ChevronLeft, ArrowForward, Warning, Place, Sparkle, VolumeOn, VolumeOff,
        )

    private const val GRID = 24f
    private const val STROKE_WIDTH = 1.9f
    private val ink = SolidColor(Color.Black)

    private fun icon(name: String, strokes: List<String> = emptyList(), fills: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = GRID.dp, defaultHeight = GRID.dp, viewportWidth = GRID, viewportHeight = GRID).apply {
            strokes.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    stroke = ink,
                    strokeLineWidth = STROKE_WIDTH,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            fills.forEach { data -> addPath(pathData = addPathNodes(data), fill = ink) }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy Z"

    private fun roundedRect(x: Float, y: Float, width: Float, height: Float, r: Float): String =
        "M${x + r} $y H${x + width - r} A$r $r 0 0 1 ${x + width} ${y + r} V${y + height - r} " +
            "A$r $r 0 0 1 ${x + width - r} ${y + height} H${x + r} A$r $r 0 0 1 $x ${y + height - r} " +
            "V${y + r} A$r $r 0 0 1 ${x + r} $y Z"
}

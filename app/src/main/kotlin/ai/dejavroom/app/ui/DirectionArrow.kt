package ai.dejavroom.app.ui

import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.model.TurnDirection
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.sin

/**
 * A turn arrow drawn as a path, so it looks the same whatever fonts the head unit has: a stem coming up
 * from the bottom that bends toward [direction] and ends in an arrowhead. A U-turn loops back on the left.
 */
@Composable
internal fun DirectionArrow(direction: TurnDirection, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier.semantics { contentDescription = directionDescription(direction) }) {
        val shape = arrowShape(direction)
        val stroke = Stroke(width = size.minDimension * STROKE_SHARE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun scaled(point: Offset) = Offset(point.x * size.width, point.y * size.height)

        val stem = Path().apply {
            val start = scaled(shape.stem.first())
            moveTo(start.x, start.y)
            if (shape.curved) {
                // U-turn: up, over the top, and down again.
                val (up, controlOut, controlBack, down, tip) = shape.stem.drop(1).map(::scaled)
                lineTo(up.x, up.y)
                cubicTo(controlOut.x, controlOut.y, controlBack.x, controlBack.y, down.x, down.y)
                lineTo(tip.x, tip.y)
            } else {
                shape.stem.drop(1).map(::scaled).forEach { lineTo(it.x, it.y) }
            }
        }
        drawPath(stem, color, style = stroke)

        val head = Path().apply {
            val (left, tip, right) = shape.head.map(::scaled)
            moveTo(left.x, left.y)
            lineTo(tip.x, tip.y)
            lineTo(right.x, right.y)
        }
        drawPath(head, color, style = stroke)
    }
}

/** The arrow in a tinted circle, sized by [modifier]. Points without a known direction get a plain dot. */
@Composable
internal fun DirectionBadge(direction: TurnDirection?, modifier: Modifier = Modifier) {
    Box(modifier.background(DejaVroomTheme.colors.blue.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
        if (direction != null) {
            DirectionArrow(direction, Modifier.fillMaxSize(ARROW_SHARE_IN_BADGE), DejaVroomTheme.colors.blueLight)
        } else {
            Box(Modifier.fillMaxSize(DOT_SHARE_IN_BADGE).background(DejaVroomTheme.colors.blueLight, CircleShape))
        }
    }
}

/** Arrow geometry in a unit square (x right, y down). [stem] ends at the tip; [head] is wing, tip, wing. */
internal data class ArrowShape(val stem: List<Offset>, val head: List<Offset>, val curved: Boolean = false)

internal fun arrowShape(direction: TurnDirection): ArrowShape {
    if (direction == TurnDirection.UTURN) {
        val tip = Offset(0.30f, 0.74f)
        return ArrowShape(
            stem = listOf(Offset(0.70f, 0.90f), Offset(0.70f, 0.40f), Offset(0.70f, 0.08f), Offset(0.30f, 0.08f), Offset(0.30f, 0.40f), tip),
            head = arrowHead(tip, headingDegrees = 180.0),
            curved = true,
        )
    }
    val start = Offset(0.5f, 0.92f)
    val heading = direction.degrees.toDouble()
    if (direction == TurnDirection.STRAIGHT) {
        val tip = Offset(0.5f, 0.10f)
        return ArrowShape(stem = listOf(start, tip), head = arrowHead(tip, heading))
    }
    val bend = Offset(0.5f, BEND_Y)
    val tip = Offset(
        x = bend.x + (ARM_LENGTH * sin(Math.toRadians(heading))).toFloat(),
        y = bend.y - (ARM_LENGTH * cos(Math.toRadians(heading))).toFloat(),
    )
    return ArrowShape(stem = listOf(start, bend, tip), head = arrowHead(tip, heading))
}

/** Two wings swept back from [tip]; [headingDegrees] is clockwise from straight up. */
private fun arrowHead(tip: Offset, headingDegrees: Double): List<Offset> {
    fun wing(sweepDegrees: Double): Offset {
        val back = Math.toRadians(headingDegrees + 180.0 + sweepDegrees)
        return Offset(tip.x + (HEAD_LENGTH * sin(back)).toFloat(), tip.y - (HEAD_LENGTH * cos(back)).toFloat())
    }
    return listOf(wing(-HEAD_SWEEP_DEGREES), tip, wing(HEAD_SWEEP_DEGREES))
}

internal fun directionDescription(direction: TurnDirection): String = when (direction) {
    TurnDirection.STRAIGHT -> "직진"
    TurnDirection.SLIGHT_RIGHT -> "오른쪽 방향"
    TurnDirection.RIGHT -> "오른쪽"
    TurnDirection.SHARP_RIGHT -> "오른쪽 급회전"
    TurnDirection.UTURN -> "유턴"
    TurnDirection.SHARP_LEFT -> "왼쪽 급회전"
    TurnDirection.LEFT -> "왼쪽"
    TurnDirection.SLIGHT_LEFT -> "왼쪽 방향"
}

private const val ARROW_SHARE_IN_BADGE = 0.6f
private const val DOT_SHARE_IN_BADGE = 0.22f
private const val STROKE_SHARE = 0.12f
private const val BEND_Y = 0.52f
private const val ARM_LENGTH = 0.36
private const val HEAD_LENGTH = 0.22
private const val HEAD_SWEEP_DEGREES = 32.0

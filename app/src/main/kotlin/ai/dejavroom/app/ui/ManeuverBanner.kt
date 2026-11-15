package ai.dejavroom.app.ui

import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.core.designsystem.PhotoOverlay
import ai.dejavroom.app.model.ApproachPhase
import ai.dejavroom.app.model.ManeuverCue
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight

private const val ARRIVAL_FADE_MS = 280
private const val ARRIVAL_GROWTH = 0.18f

/** Reaching the point lights up the whole road view, not just the banner. */
@Composable
internal fun ArrivalFrame(atPoint: Boolean, modifier: Modifier = Modifier) {
    val dimens = DejaVroomTheme.dimens
    val arrivalColor = DejaVroomTheme.colors.marker
    val glow by animateFloatAsState(if (atPoint) 1f else 0f, tween(ARRIVAL_FADE_MS), label = "arrival-frame")
    // The corners are those of the photo box the frame is drawn on.
    if (glow > 0f) Box(modifier.border(dimens.dp(8), arrivalColor.copy(alpha = glow), RoundedCornerShape(dimens.radiusLarge)))
}

/**
 * What to do at the point ahead, readable at a glance over the road view. On the way it counts down
 * quietly. On the point it turns orange and grows once, settling with a single soft overshoot, and then
 * stays that way: the moment is marked, and nothing is left moving over the road.
 */
@Composable
internal fun ManeuverBanner(cue: ManeuverCue, remainingMeters: Int?, modifier: Modifier = Modifier) {
    val type = DejaVroomTheme.type
    val dimens = DejaVroomTheme.dimens
    // On the way the banner wears the fixed colors of a label over a photo; on the point, the marker orange.
    val arrivalColor = DejaVroomTheme.colors.marker
    // Dark on the orange: white on it does not reach the 4.5:1 contrast.
    val onArrivalColor = DejaVroomTheme.colors.onMarker
    val atPoint = ApproachPhase.at(remainingMeters) == ApproachPhase.AT_POINT
    // 0 on the way and once past, 1 on the point.
    val emphasis by animateFloatAsState(
        targetValue = if (atPoint) 1f else 0f,
        animationSpec = if (atPoint) spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow) else tween(ARRIVAL_FADE_MS),
        label = "arrival-banner",
    )
    // The spring overshoots for the size; colors stay between their two ends.
    val tint = emphasis.coerceIn(0f, 1f)
    Row(
        modifier
            .graphicsLayer {
                // Grows downward from its top edge so it never leaves the road view.
                transformOrigin = TransformOrigin(0.5f, 0f)
                val scale = 1f + ARRIVAL_GROWTH * emphasis
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(lerp(PhotoOverlay.scrim, arrivalColor, tint))
            .padding(start = dimens.spaceS, end = dimens.spaceXl, top = dimens.dp(10), bottom = dimens.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(dimens.dp(68)).background(lerp(PhotoOverlay.badge, onArrivalColor, tint), CircleShape), contentAlignment = Alignment.Center) {
            DirectionArrow(cue.direction, Modifier.size(dimens.dp(40)), lerp(PhotoOverlay.onScrim, arrivalColor, tint))
        }
        Spacer(Modifier.width(dimens.spaceM))
        Column {
            Text(bannerTitle(cue, remainingMeters), color = lerp(PhotoOverlay.onScrim, onArrivalColor, tint), style = type.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            if (cue.sign.isNotBlank()) Text("표지 ${cue.sign}", color = lerp(PhotoOverlay.onScrimVariant, onArrivalColor, tint), style = type.labelMedium, maxLines = 1)
        }
    }
}

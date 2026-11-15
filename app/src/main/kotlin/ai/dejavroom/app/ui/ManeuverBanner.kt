package ai.dejavroom.app.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ArrivalColor = Color(0xFFFF8A1F)
private val BannerCalmColor = Color(0xEB0B1730)
private const val ARRIVAL_FADE_MS = 280
private const val ARRIVAL_GROWTH = 0.18f

/** Reaching the point lights up the whole road view, not just the banner. */
@Composable
internal fun ArrivalFrame(atPoint: Boolean, modifier: Modifier = Modifier) {
    val glow by animateFloatAsState(if (atPoint) 1f else 0f, tween(ARRIVAL_FADE_MS), label = "arrival-frame")
    if (glow > 0f) Box(modifier.border(6.dp, ArrivalColor.copy(alpha = glow), RoundedCornerShape(16.dp)))
}

/**
 * What to do at the point ahead, readable at a glance over the road view. On the way it counts down
 * quietly. On the point it turns orange and grows once, settling with a single soft overshoot, and then
 * stays that way: the moment is marked, and nothing is left moving over the road.
 */
@Composable
internal fun ManeuverBanner(cue: ManeuverCue, remainingMeters: Int?, modifier: Modifier = Modifier) {
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
            .clip(RoundedCornerShape(22.dp))
            .background(lerp(BannerCalmColor, ArrivalColor, tint))
            .padding(start = 10.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(54.dp).background(lerp(Color(0xFF1F6BFF), Color.White, tint), CircleShape), contentAlignment = Alignment.Center) {
            DirectionArrow(cue.direction, Modifier.size(34.dp), lerp(Color.White, ArrivalColor, tint))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(bannerTitle(cue, remainingMeters), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (cue.sign.isNotBlank()) Text("표지 ${cue.sign}", color = lerp(Color(0xFFCFE0FF), Color.White, tint), fontSize = 15.sp, maxLines = 1)
        }
    }
}

package ai.dejavroom.app.ui

import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.feature.DejaVroomUiState
import ai.dejavroom.app.feature.DejaVroomViewModel
import ai.dejavroom.app.model.BriefingSegment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun NavigationScreen(state: DejaVroomUiState, vm: DejaVroomViewModel, compact: Boolean) {
    val route = state.route
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    if (compact) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            Text(NAVIGATION_HEADLINE, color = colors.onSurface, style = type.headlineSmall)
            Spacer(Modifier.height(dimens.spaceL))
            if (route != null) {
                Text(
                    "${route.origin}  →  ${route.destination}  ·  약 ${route.durationMinutes}분 · ${route.distanceKm}km",
                    color = colors.onSurface,
                    style = type.bodyLarge,
                )
            }
            Spacer(Modifier.height(dimens.spaceL))
            SecondaryButton("목록으로 돌아가기", Modifier.fillMaxWidth(), size = ButtonSize.Medium, onClick = vm::backToSummary)
            Spacer(Modifier.height(dimens.spaceXl))
            RouteRecap(state.shownSegments, route?.origin, route?.destination, Modifier.fillMaxWidth())
            // Room for the card's shadow, which the scrolling column would otherwise cut off at its lower edge.
            Spacer(Modifier.height(dimens.spaceL))
        }
        return
    }
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(dimens.dp(56)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(NAVIGATION_HEADLINE, color = colors.onSurface, style = type.headline)
            Spacer(Modifier.height(dimens.spaceL))
            if (route != null) {
                Text(
                    "${route.origin}  →  ${route.destination}  ·  약 ${route.durationMinutes}분 · ${route.distanceKm}km",
                    color = colors.onSurface,
                    style = type.bodyLarge,
                )
            }
            Spacer(Modifier.height(dimens.spaceXl))
            SecondaryButton(
                "목록으로 돌아가기",
                Modifier.width(dimens.dp(360)),
                size = ButtonSize.Medium,
                onClick = vm::backToSummary,
            )
        }
        // A route can have more points than fit the window at this row height; the card then scrolls instead of
        // being cut off. The padding leaves room for its shadow inside the scrolling column.
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            RouteRecap(
                state.shownSegments,
                route?.origin,
                route?.destination,
                Modifier.fillMaxWidth().padding(vertical = dimens.spaceL),
            )
        }
    }
}

/**
 * The previewed points in driving order, as a timeline from [origin] to [destination]: the number, the place,
 * what to look for, and the turn arrow. An end without a name gets no row.
 */
@Composable
internal fun RouteRecap(
    segments: List<BriefingSegment>,
    origin: String?,
    destination: String?,
    modifier: Modifier = Modifier,
) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    Column(modifier.cardSurface().padding(vertical = dimens.dp(36), horizontal = dimens.dp(40))) {
        Text("가는 길에 미리 본 곳", color = colors.onSurface, style = type.titleMedium)
        Spacer(Modifier.height(dimens.spaceM))
        if (segments.isEmpty()) Text("미리 본 지점이 없습니다.", color = colors.onSurfaceVariant, style = type.bodySmall)
        if (origin != null) {
            RecapEndpoint("출발 · $origin", colors.accent, first = true, last = segments.isEmpty() && destination == null)
        }
        segments.forEachIndexed { index, segment ->
            Row(
                Modifier
                    .heightIn(min = dimens.dp(112))
                    .recapConnector(
                        first = origin == null && index == 0,
                        last =
                        destination == null && index == segments.lastIndex,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberMarker(index + 1, size = dimens.dp(RECAP_MARKER_SIZE))
                Spacer(Modifier.width(dimens.dp(RECAP_MARKER_GAP)))
                Column(Modifier.weight(1f)) {
                    Text(
                        segment.name,
                        color = colors.onSurface,
                        style = type.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        recapLine(segment),
                        color = colors.onSurfaceVariant,
                        style = type.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(dimens.spaceM))
                DirectionBadge(segment.cue?.direction, Modifier.size(dimens.dp(64)))
            }
        }
        if (destination != null) {
            RecapEndpoint("도착 · $destination", colors.violet, first = origin == null && segments.isEmpty(), last = true)
        }
    }
}

/** Where the route starts or ends: a ring in the marker column, so the connector runs into its center. */
@Composable
private fun RecapEndpoint(text: String, ringColor: Color, first: Boolean, last: Boolean) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    Row(
        Modifier.heightIn(min = dimens.dp(80)).recapConnector(first, last),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(dimens.dp(RECAP_MARKER_SIZE)), contentAlignment = Alignment.Center) {
            // Filled, so the connector behind it does not show through the ring.
            Box(
                Modifier.size(
                    dimens.dp(28),
                ).background(colors.surface, CircleShape).border(dimens.dp(5), ringColor, CircleShape),
            )
        }
        Spacer(Modifier.width(dimens.dp(RECAP_MARKER_GAP)))
        Text(
            text,
            Modifier.weight(1f),
            color = colors.onSurfaceVariant,
            style = DejaVroomTheme.type.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * This row's piece of the line that joins the timeline's markers. It runs down the middle of the marker
 * column and stops at the marker in the [first] and the [last] row. The rows touch, so the pieces read as
 * one line whatever height each row ends up with.
 */
@Composable
private fun Modifier.recapConnector(first: Boolean, last: Boolean): Modifier {
    val dimens = DejaVroomTheme.dimens
    val color = DejaVroomTheme.colors.outline
    val x = dimens.dp(RECAP_MARKER_SIZE) / 2
    val width = dimens.dp(3)
    return this.drawBehind {
        val middle = size.height / 2
        val top = if (first) middle else 0f
        val bottom = if (last) middle else size.height
        if (top < bottom) drawLine(color, Offset(x.toPx(), top), Offset(x.toPx(), bottom), strokeWidth = width.toPx())
    }
}

// The marker column of the recap timeline and the gap after it, in reference-window dp. The connector is
// drawn down the middle of that column, so the markers, the rings and the line share these.
private const val RECAP_MARKER_SIZE = 64
private const val RECAP_MARKER_GAP = 22

package ai.dejavroom.app.ui

import ai.dejavroom.app.core.designsystem.DejaVroomIcons
import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.core.designsystem.PhotoOverlay
import ai.dejavroom.app.data.RoadViewLookupResult
import ai.dejavroom.app.feature.DejaVroomUiState
import ai.dejavroom.app.feature.DejaVroomViewModel
import ai.dejavroom.app.model.BriefingSegment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.ceil

@Composable
internal fun SummaryScreen(state: DejaVroomUiState, vm: DejaVroomViewModel, compact: Boolean) {
    val route = state.route ?: return
    val segments = state.shownSegments
    // What the trip says is already behind the car; the first point after those is the next one to meet.
    val passedIds = state.passedSegmentIds
    val nextIndex = state.nextSegmentIndex.takeIf { state.trip?.passed?.isNotEmpty() == true && segments.getOrNull(it)?.id !in passedIds }
    val startLabel = if (nextIndex != null && nextIndex > 0) "다음 지점부터 보기" else "처음부터 보기"
    val openFromStart = { vm.openSegmentRoadView(state.nextSegmentIndex) }
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    // The strip of passed places shows only on a trip in progress; spacing by arrangement leaves no gap for it otherwise.
    if (compact) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
            Text("미리 볼 곳 ${segments.size}곳 · 가는 순서", color = colors.onSurface, style = type.titleSmall)
            PassedStrip(state, vm::replanFromCurrentLocation)
            // Lazy, so only the cards on screen hold a road view of their own.
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
                itemsIndexed(segments, key = { _, segment -> segment.id }) { index, segment ->
                    SegmentCard(index + 1, segments.size, segment, state.roadViewLookups[segment.id], compact = true, modifier = Modifier.fillMaxWidth().height(dimens.dp(520)), passed = segment.id in passedIds, next = index == nextIndex, onClick = { vm.openSegmentRoadView(index) })
                }
            }
            Row(Modifier.fillMaxWidth().height(dimens.buttonMedium), horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
                SecondaryButton(startLabel, Modifier.weight(1f), size = ButtonSize.Medium, onClick = openFromStart)
                PrimaryButton("주행 시작", Modifier.weight(1f), trailingIcon = DejaVroomIcons.ChevronRight, size = ButtonSize.Medium, onClick = vm::startNavigation)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(dimens.spaceL)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(DejaVroomIcons.Sparkle, tint = colors.accent, size = dimens.dp(56))
            Spacer(Modifier.width(dimens.spaceM))
            Column(Modifier.weight(1f)) {
                Text("헷갈리기 쉬운 곳을\n가는 순서대로 미리 보세요.", color = colors.onSurface, style = type.titleLarge)
                Text("${segments.size}곳을 골랐습니다. 사진은 분기점에 닿기 전 지점에서 찍힌 모습입니다.", color = colors.onSurfaceVariant, style = type.bodySmall)
            }
        }
        PassedStrip(state, vm::replanFromCurrentLocation)
        val cards = rememberLazyListState()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Cards that fit share the row. More than that scroll sideways, with the next one peeking in
            // so it is plain there are more; only the cards on screen hold a road view of their own.
            val cardGap = dimens.spaceL
            val minCardWidth = dimens.dp(420)
            val fitting = ((maxWidth + cardGap) / (minCardWidth + cardGap)).toInt().coerceAtLeast(1)
            val across = if (segments.size <= fitting) segments.size.coerceAtLeast(1).toFloat() else fitting - CARD_PEEK
            val cardWidth = (maxWidth - cardGap * (ceil(across).toInt() - 1)) / across
            LazyRow(Modifier.fillMaxSize(), state = cards, horizontalArrangement = Arrangement.spacedBy(cardGap)) {
                itemsIndexed(segments, key = { _, segment -> segment.id }) { index, segment ->
                    SegmentCard(index + 1, segments.size, segment, state.roadViewLookups[segment.id], compact = false, modifier = Modifier.width(cardWidth).fillParentMaxHeight(), passed = segment.id in passedIds, next = index == nextIndex, onClick = { vm.openSegmentRoadView(index) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(dimens.buttonLarge), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(startLabel, Modifier.width(dimens.dp(360)), onClick = openFromStart)
            Box(Modifier.weight(1f).padding(horizontal = dimens.spaceM), contentAlignment = Alignment.Center) {
                if (cards.canScrollForward || cards.canScrollBackward) {
                    Text("옆으로 넘기면 ${segments.size}곳을 모두 볼 수 있습니다", color = colors.onSurfaceVariant, style = type.labelMedium, textAlign = TextAlign.Center)
                }
            }
            PrimaryButton("주행 시작", Modifier.width(dimens.dp(440)), trailingIcon = DejaVroomIcons.ChevronRight, onClick = vm::startNavigation)
        }
    }
}

/**
 * What is already behind the car on this trip, in one line. Once the car has moved along the cards on
 * screen, the rest of the way can be planned again from where it is now.
 */
@Composable
private fun PassedStrip(state: DejaVroomUiState, onReplan: () -> Unit, modifier: Modifier = Modifier) {
    val passed = state.trip?.passed.orEmpty()
    if (passed.isEmpty()) return
    val working = state.isLocating || state.isSearchingRoute
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTarget)
            .cardSurface(RoundedCornerShape(dimens.radiusMedium), elevated = false)
            .padding(start = dimens.spaceL, end = dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(DejaVroomIcons.Check, tint = colors.success)
        Spacer(Modifier.width(dimens.spaceS))
        Text(passedLine(passed), Modifier.weight(1f), color = colors.onSurfaceVariant, style = DejaVroomTheme.type.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (state.passedSegmentIds.isNotEmpty()) {
            TextAction(if (working) "다시 찾는 중…" else "현재 위치에서 다시 찾기", enabled = !working, onClick = onReplan)
        }
    }
}

private const val CARD_PEEK = 0.4f

/**
 * [passed] dims a point the car has already gone by (it can still be opened); [next] marks the first
 * one still ahead, so a driver coming back to the app sees where they are in the list.
 */
@Composable
internal fun SegmentCard(
    number: Int,
    total: Int,
    segment: BriefingSegment,
    lookup: RoadViewLookupResult?,
    compact: Boolean,
    modifier: Modifier = Modifier,
    passed: Boolean = false,
    next: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    val overlayInset = dimens.dp(20)
    Column(modifier.alpha(if (passed) .5f else 1f).cardSurface().clickable(onClick = onClick)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            RoadViewThumbnail(lookup, compact, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().clickable(onClick = onClick))
            // Both top corners share one row, so on a narrow card the chip on the right shortens instead of
            // running under the number.
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().padding(overlayInset),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberMarker(number, ring = true, passed = passed)
                if (next) Chip("다음", container = colors.primary, content = colors.onPrimary)
                Spacer(Modifier.weight(1f))
                if (passed) {
                    Chip("지나옴", container = PhotoOverlay.chip, content = PhotoOverlay.onChip, icon = DejaVroomIcons.Check)
                } else {
                    Chip(segment.type.label, container = PhotoOverlay.chip, content = PhotoOverlay.onChip)
                }
            }
            // The photo is taken this far before the point, so the driver sees it coming. The distance says
            // where the photo stands, which is why it sits on the photo. It is lifted off the bottom edge
            // because the photo provider writes its own credit there, and that must stay readable.
            leadLabel(segment.approach?.leadMeters)?.let { lead ->
                Chip("$lead 전", Modifier.align(Alignment.BottomStart).padding(start = overlayInset, bottom = dimens.dp(64)), container = PhotoOverlay.chip, content = PhotoOverlay.onChipAccent)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = dimens.dp(24), vertical = dimens.dp(22)), verticalAlignment = Alignment.CenterVertically) {
            DirectionBadge(segment.cue?.direction, Modifier.size(dimens.touchTarget))
            Spacer(Modifier.width(dimens.spaceM))
            Column(Modifier.weight(1f)) {
                Text(segment.name, color = colors.onSurface, style = type.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cardSubtitle(segment), color = colors.onSurfaceVariant, style = type.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(dimens.spaceS))
            Text("$number / $total", color = colors.onSurfaceVariant, style = type.labelMedium, maxLines = 1)
        }
    }
}

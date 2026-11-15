package ai.dejavroom.app.ui

import ai.dejavroom.app.R
import ai.dejavroom.app.core.designsystem.DejaVroomIcons
import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.core.designsystem.PhotoOverlay
import ai.dejavroom.app.data.RoadViewContent
import ai.dejavroom.app.data.RoadViewLookupResult
import ai.dejavroom.app.feature.DejaVroomUiState
import ai.dejavroom.app.feature.DejaVroomViewModel
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.RoadViewContentOrigin
import ai.dejavroom.app.model.SegmentType
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun BriefingScreen(state: DejaVroomUiState, vm: DejaVroomViewModel, compact: Boolean) {
    val route = state.route ?: return
    val segments = state.shownSegments
    val segment = segments.getOrNull(state.segmentIndex) ?: return
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    if (compact) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(dimens.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                Chip("${state.segmentIndex + 1} / ${segments.size}", container = colors.primary, content = colors.onPrimary)
                Spacer(Modifier.width(dimens.spaceM))
                Column(Modifier.weight(1f)) {
                    Text(segment.type.label, color = colors.onSurface, style = type.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(segment.name, color = colors.onSurfaceVariant, style = type.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(dimens.spaceXs))
            BriefingHero(
                segment = segment,
                index = state.segmentIndex,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                compact = true,
                roadViewLookup = state.roadViewLookups[segment.id],
                viewIndex = state.roadViewIndices[segment.id] ?: 0,
                onPreviousView = vm::previousRoadView,
                onNextView = vm::nextRoadView,
                onRetry = vm::retryRoadView,
            )
            Spacer(Modifier.height(dimens.spaceS))
            BriefingCompactSegments(segments, state.segmentIndex, vm::selectSegment)
            Spacer(Modifier.height(dimens.spaceS))
            BriefingCompactControls(state, segments.size, vm)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(dimens.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            Chip("${state.segmentIndex + 1} / ${segments.size}", container = colors.primary, content = colors.onPrimary)
            Spacer(Modifier.width(dimens.spaceL))
            Column(Modifier.weight(1f)) {
                Text(segment.type.label, color = colors.onSurface, style = type.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${segment.name} · 약 ${segmentPreviewSeconds(segment)}초 확인", color = colors.onSurfaceVariant, style = type.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(dimens.spaceS))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(dimens.spaceL)) {
            BriefingHero(
                segment = segment,
                index = state.segmentIndex,
                modifier = Modifier.weight(2.7f).fillMaxHeight(),
                compact = false,
                roadViewLookup = state.roadViewLookups[segment.id],
                viewIndex = state.roadViewIndices[segment.id] ?: 0,
                onPreviousView = vm::previousRoadView,
                onNextView = vm::nextRoadView,
                onRetry = vm::retryRoadView,
            )
            BriefingSegmentList(
                segments = segments,
                activeIndex = state.segmentIndex,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onSelect = vm::selectSegment,
            )
        }
        Spacer(Modifier.height(dimens.spaceM))
        // Not clipped to its shape: the glow of the primary button reaches past the bar's edge.
        val barShape = RoundedCornerShape(dimens.radiusMedium)
        Row(
            Modifier.fillMaxWidth().height(dimens.buttonLarge).background(colors.surface, barShape).border(1.dp, colors.hairline, barShape).padding(horizontal = dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction(voiceLabel(state), color = if (state.voiceEnabled) colors.accent else colors.onSurfaceVariant, onClick = vm::toggleVoice)
            Spacer(Modifier.weight(1f))
            repeat(segments.size) { index ->
                Box(Modifier.padding(horizontal = dimens.spaceXs).size(if (index == state.segmentIndex) dimens.dp(20) else dimens.dp(14)).background(if (index == state.segmentIndex) colors.primary else colors.outline, CircleShape))
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("이전 구간", Modifier.width(dimens.dp(260)), enabled = state.segmentIndex > 0, leadingIcon = DejaVroomIcons.ChevronLeft, size = ButtonSize.Medium, onClick = vm::previousSegment)
            Spacer(Modifier.width(dimens.spaceM))
            PrimaryButton(
                if (state.segmentIndex == segments.lastIndex) "브리핑 완료" else "다음 구간",
                Modifier.width(dimens.dp(320)),
                trailingIcon = DejaVroomIcons.ChevronRight,
                size = ButtonSize.Medium,
                onClick = vm::nextSegment,
            )
        }
    }
}

@Composable
internal fun BriefingHero(
    segment: BriefingSegment,
    index: Int,
    modifier: Modifier,
    compact: Boolean,
    roadViewLookup: RoadViewLookupResult?,
    viewIndex: Int,
    onPreviousView: () -> Unit,
    onNextView: () -> Unit,
    onRetry: () -> Unit,
) {
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    Box(modifier.clip(RoundedCornerShape(if (compact) dimens.radiusMedium else dimens.radiusLarge))) {
        val views = (roadViewLookup as? RoadViewLookupResult.Available)?.views.orEmpty()
        val currentView = views.getOrNull(viewIndex)
        when (val content = currentView?.content) {
            RoadViewContent.Illustration -> RoadScene(Modifier.fillMaxSize(), variant = index, large = true)
            is RoadViewContent.StillImage -> RoadViewPhoto(currentView, content, Modifier.fillMaxSize())
            is RoadViewContent.InteractivePage -> RoadViewThumbnail(roadViewLookup, compact = compact, modifier = Modifier.fillMaxSize())
            null -> RoadViewUnavailableCanvas(roadViewStatusMessage(roadViewLookup), Modifier.fillMaxSize())
        }
        if (views.size > 1) {
            Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = dimens.spaceS), horizontalArrangement = Arrangement.SpaceBetween) {
                ViewArrow(DejaVroomIcons.ChevronLeft, contentDescription = "이전 시점", enabled = viewIndex > 0, onClick = onPreviousView)
                ViewArrow(DejaVroomIcons.ChevronRight, contentDescription = "다음 시점", enabled = viewIndex < views.lastIndex, onClick = onNextView)
            }
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(if (compact) dimens.spaceS else dimens.spaceL), verticalAlignment = Alignment.Bottom) {
            // Fixed photo-overlay scrim and pale foreground keep guidance legible in either app theme.
            Column(
                Modifier.weight(1f).padding(end = if (compact) dimens.spaceXs else dimens.spaceM)
                    .clip(RoundedCornerShape(if (compact) dimens.radiusSmall else dimens.radiusMedium))
                    .background(PhotoOverlay.scrim)
                    .padding(horizontal = if (compact) dimens.spaceM else dimens.spaceL, vertical = if (compact) dimens.spaceS else dimens.spaceM),
            ) {
                Text(
                    if (segment.canShowSpecificGuidance) segment.guidance else "잠시 후 ${segment.type.label} 구간이 나타납니다.",
                    color = PhotoOverlay.onScrim, style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(dimens.dp(4)))
                Text(
                    if (segment.canShowSpecificGuidance) segment.confidenceLabel
                    else "${contentOriginLabel(currentView?.contentOrigin ?: segment.roadViewOrigin)} · ${routeMatchLabel(currentView?.routeMatchStatus ?: segment.routeMatchStatus)} · 차로 안내 미검증",
                    color = PhotoOverlay.onScrimVariant, style = type.labelMedium,
                    maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis,
                )
                if (currentView != null || segment.roadViewOrigin == RoadViewContentOrigin.LIVE_PROVIDER) {
                    Text(
                        listOfNotNull(
                            if (currentView != null) currentView.attribution ?: "출처 정보 없음" else segment.roadViewAttribution,
                            currentView?.capturedAt ?: segment.roadViewCapturedAt ?: "촬영일 정보 없음",
                            currentView?.let { routeMatchLabel(it.routeMatchStatus) },
                        ).joinToString(" · "),
                        color = PhotoOverlay.onScrimVariant, style = type.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (views.size > 1) Text("시점 ${viewIndex + 1} / ${views.size}", color = PhotoOverlay.onScrimVariant, style = type.labelMedium)
                if (roadViewLookup is RoadViewLookupResult.Unavailable || roadViewLookup is RoadViewLookupResult.NotConfigured) {
                    TextAction("다시 확인", color = PhotoOverlay.onScrim, onClick = onRetry)
                }
            }
            RouteMiniMap(index, Modifier.width(if (compact) dimens.dp(180) else dimens.dp(240)).height(if (compact) dimens.dp(120) else dimens.dp(160)))
        }
    }
}

/**
 * A previous/next arrow over the photo, on its own scrim so it reads on any picture. [IconAction] cannot be
 * disabled, and at the first and the last view one arrow has to stay in its place without responding.
 */
@Composable
private fun ViewArrow(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(DejaVroomTheme.dimens.touchTarget)
            .clip(CircleShape)
            .background(PhotoOverlay.scrim)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, tint = if (enabled) PhotoOverlay.onScrim else PhotoOverlay.onScrim.copy(alpha = DISABLED_ARROW_ALPHA), contentDescription = contentDescription)
    }
}

@Composable
internal fun BriefingCompactControls(state: DejaVroomUiState, segmentCount: Int, vm: DejaVroomViewModel) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Narrower than this, the two buttons no longer fit beside the voice toggle and the dots.
        if (maxWidth < dimens.dp(688)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                Row(Modifier.fillMaxWidth().height(dimens.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                    TextAction(voiceLabel(state), color = if (state.voiceEnabled) colors.accent else colors.onSurfaceVariant, onClick = vm::toggleVoice)
                    Spacer(Modifier.weight(1f))
                    repeat(segmentCount) { index ->
                        Box(Modifier.padding(horizontal = dimens.dp(5)).size(if (index == state.segmentIndex) dimens.dp(14) else dimens.dp(10)).background(if (index == state.segmentIndex) colors.primary else colors.outline, CircleShape))
                    }
                }
                Row(Modifier.fillMaxWidth().height(dimens.buttonMedium), horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
                    SecondaryButton("이전", Modifier.weight(1f), enabled = state.segmentIndex > 0, leadingIcon = DejaVroomIcons.ChevronLeft, size = ButtonSize.Medium, onClick = vm::previousSegment)
                    PrimaryButton(
                        if (state.segmentIndex == segmentCount - 1) "브리핑 완료" else "다음 구간",
                        Modifier.weight(1f),
                        trailingIcon = DejaVroomIcons.ChevronRight,
                        size = ButtonSize.Medium,
                        onClick = vm::nextSegment,
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(dimens.buttonMedium), verticalAlignment = Alignment.CenterVertically) {
                TextAction(voiceLabel(state), color = if (state.voiceEnabled) colors.accent else colors.onSurfaceVariant, onClick = vm::toggleVoice)
                Spacer(Modifier.weight(1f))
                repeat(segmentCount) { index ->
                    Box(Modifier.padding(horizontal = dimens.dp(5)).size(if (index == state.segmentIndex) dimens.dp(16) else dimens.dp(12)).background(if (index == state.segmentIndex) colors.primary else colors.outline, CircleShape))
                }
                Spacer(Modifier.weight(1f))
                SecondaryButton("이전", Modifier.width(dimens.dp(180)), enabled = state.segmentIndex > 0, leadingIcon = DejaVroomIcons.ChevronLeft, size = ButtonSize.Medium, onClick = vm::previousSegment)
                Spacer(Modifier.width(dimens.spaceS))
                PrimaryButton(
                    if (state.segmentIndex == segmentCount - 1) "완료" else "다음",
                    Modifier.width(dimens.dp(200)),
                    trailingIcon = DejaVroomIcons.ChevronRight,
                    size = ButtonSize.Medium,
                    onClick = vm::nextSegment,
                )
            }
        }
    }
}

@Composable
internal fun BriefingCompactSegments(segments: List<BriefingSegment>, activeIndex: Int, onSelect: (Int) -> Unit) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    val shape = RoundedCornerShape(dimens.radiusSmall)
    Row(Modifier.fillMaxWidth().height(dimens.dp(104)), horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)) {
        segments.forEachIndexed { index, segment ->
            val selected = index == activeIndex
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(shape)
                    .background(if (selected) colors.selectedSurface else colors.surface)
                    .border(if (selected) dimens.dp(3) else 1.dp, if (selected) colors.primary else colors.hairline, shape)
                    .clickable { onSelect(index) }.padding(horizontal = dimens.spaceS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", color = if (selected) colors.accent else colors.onSurfaceVariant, style = type.labelMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(dimens.spaceXs))
                Column(Modifier.weight(1f)) {
                    Text(segment.type.label, color = colors.onSurface, style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("약 ${segmentPreviewSeconds(segment)}초", color = colors.onSurfaceVariant, style = type.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun BriefingSegmentList(
    segments: List<BriefingSegment>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    val shape = RoundedCornerShape(dimens.radiusMedium)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimens.spaceM)) {
        Text("이번 경로의 주요 구간 ${segments.size}곳", color = colors.onSurface, style = type.titleSmall)
        segments.forEachIndexed { index, segment ->
            val selected = index == activeIndex
            Row(
                Modifier.weight(1f).fillMaxWidth().clip(shape)
                    .background(if (selected) colors.selectedSurface else colors.surface)
                    .border(if (selected) dimens.dp(3) else 1.dp, if (selected) colors.primary else colors.hairline, shape)
                    .clickable { onSelect(index) }
                    .padding(horizontal = dimens.spaceM, vertical = dimens.spaceS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberMarker(index + 1, size = dimens.dp(56))
                Spacer(Modifier.width(dimens.spaceM))
                Image(painterResource(when (index) { 1 -> R.drawable.demo_highway; 2 -> R.drawable.demo_destination; else -> R.drawable.demo_intersection }), contentDescription = "AI 생성 데모 이미지", contentScale = ContentScale.Crop, modifier = Modifier.size(dimens.dp(100)).clip(RoundedCornerShape(dimens.radiusSmall)))
                Spacer(Modifier.width(dimens.spaceM))
                Column(Modifier.weight(1f)) {
                    Text(segment.type.label, color = colors.onSurface, style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(segment.description, color = colors.onSurfaceVariant, style = type.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("약 ${segmentPreviewSeconds(segment)}초", color = colors.accent, style = type.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

internal fun segmentPreviewSeconds(segment: BriefingSegment): Int = when (segment.type) {
    SegmentType.INTERSECTION -> 8
    SegmentType.HIGHWAY_JUNCTION -> 10
    SegmentType.DESTINATION_ENTRY -> 7
    SegmentType.COMPLEX_TURN -> 8
    SegmentType.HISTORICAL_RISK -> 8
}

@Composable
internal fun RoadViewUnavailableCanvas(message: String, modifier: Modifier = Modifier) {
    Box(modifier.background(DejaVroomTheme.colors.surfaceVariant), contentAlignment = Alignment.Center) {
        Text(
            message,
            Modifier.padding(DejaVroomTheme.dimens.spaceXl),
            color = DejaVroomTheme.colors.onSurfaceVariant,
            style = DejaVroomTheme.type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
    }
}

private const val DISABLED_ARROW_ALPHA = 0.4f

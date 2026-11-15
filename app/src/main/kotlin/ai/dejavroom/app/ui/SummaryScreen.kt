package ai.dejavroom.app.ui

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.dejavroom.app.BuildConfig
import ai.dejavroom.app.R
import ai.dejavroom.app.core.designsystem.DejaVroomTheme
import ai.dejavroom.app.core.designsystem.dejaVroomColors
import ai.dejavroom.app.data.RoadViewLookupResult
import ai.dejavroom.app.feature.DejaVroomScreen
import ai.dejavroom.app.feature.DejaVroomUiState
import ai.dejavroom.app.feature.DejaVroomViewModel
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.RoadViewContentOrigin
import ai.dejavroom.app.model.RouteMatchStatus
import ai.dejavroom.app.model.RouteSource
import ai.dejavroom.app.model.SegmentSelectionSource
import ai.dejavroom.app.model.SegmentType
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun SummaryScreen(state: DejaVroomUiState, vm: DejaVroomViewModel, compact: Boolean) {
    val route = state.route ?: return
    val segments = state.shownSegments
    // What the trip says is already behind the car; the first point after those is the next one to meet.
    val passedIds = state.passedSegmentIds
    val nextIndex = state.nextSegmentIndex.takeIf { state.trip?.passed?.isNotEmpty() == true && segments.getOrNull(it)?.id !in passedIds }
    val startLabel = if (nextIndex != null && nextIndex > 0) "다음 지점부터 보기" else "처음부터 보기"
    val openFromStart = { vm.openSegmentRoadView(state.nextSegmentIndex) }
    if (compact) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("미리 볼 곳 ${segments.size}곳 · 가는 순서", Modifier.weight(1f), color = DejaVroomTheme.colors.white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                CandidateCountSelector(state.displayCount, vm::showSegmentCount)
            }
            PassedStrip(state, vm::replanFromCurrentLocation, Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.forEachIndexed { index, segment ->
                    SegmentCard(index + 1, segments.size, segment, state.roadViewLookups[segment.id], compact = true, modifier = Modifier.fillMaxWidth().height(260.dp), passed = segment.id in passedIds, next = index == nextIndex, onClick = { vm.openSegmentRoadView(index) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(startLabel, Modifier.weight(1f), onClick = openFromStart)
                PrimaryButton("주행 시작", "›", Modifier.weight(1f), vm::startNavigation)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("✦", color = DejaVroomTheme.colors.blue, fontSize = 30.sp)
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text("헷갈리기 쉬운 곳을\n가는 순서대로 미리 보세요.", color = DejaVroomTheme.colors.white, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("${segments.size}곳을 골랐습니다. 사진은 분기점에 닿기 전 지점에서 찍힌 모습입니다.", color = DejaVroomTheme.colors.muted, fontSize = 14.sp)
            }
            CandidateCountSelector(state.displayCount, vm::showSegmentCount)
        }
        PassedStrip(state, vm::replanFromCurrentLocation, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(if (state.trip?.passed.isNullOrEmpty()) 21.dp else 9.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            segments.forEachIndexed { index, segment ->
                SegmentCard(index + 1, segments.size, segment, state.roadViewLookups[segment.id], compact = false, modifier = Modifier.weight(1f), passed = segment.id in passedIds, next = index == nextIndex, onClick = { vm.openSegmentRoadView(index) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(startLabel, Modifier.width(240.dp), onClick = openFromStart)
            Spacer(Modifier.weight(1f))
            Text("●   ●   ●", color = DejaVroomTheme.colors.blue, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            PrimaryButton("주행 시작", "›", Modifier.width(250.dp), vm::startNavigation)
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
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(DejaVroomTheme.colors.panel).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓", color = DejaVroomTheme.colors.green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(9.dp))
        Text(passedLine(passed), Modifier.weight(1f), color = DejaVroomTheme.colors.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (state.passedSegmentIds.isNotEmpty()) {
            TextButton(onClick = onReplan, enabled = !working, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    if (working) "다시 찾는 중…" else "현재 위치에서 다시 찾기",
                    color = if (working) DejaVroomTheme.colors.muted else DejaVroomTheme.colors.blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun CandidateCountSelector(selectedCount: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DejaVroomUiState.DISPLAY_COUNT_CHOICES.forEach { count ->
            TextButton(onClick = { onSelect(count) }) {
                Text("${count}개", color = if (selectedCount == count) DejaVroomTheme.colors.blueLight else DejaVroomTheme.colors.muted, fontWeight = if (selectedCount == count) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

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
    val tint = when (number) { 2 -> DejaVroomTheme.colors.violet; 3 -> DejaVroomTheme.colors.teal; else -> DejaVroomTheme.colors.blue }
    val chipTint = if (passed) DejaVroomTheme.colors.muted else tint
    Column(modifier.alpha(if (passed) .5f else 1f).clip(RoundedCornerShape(22.dp)).background(DejaVroomTheme.colors.panel).border(1.dp, DejaVroomTheme.colors.stroke.copy(alpha = .8f), RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$number / $total", color = DejaVroomTheme.colors.white, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (next) {
                Spacer(Modifier.width(8.dp))
                Text("다음", Modifier.background(DejaVroomTheme.colors.blue, CircleShape).padding(horizontal = 9.dp, vertical = 4.dp), color = DejaVroomTheme.colors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Text(if (passed) "✓ 지나옴" else segment.type.label, Modifier.background(chipTint.copy(alpha = .10f), CircleShape).padding(horizontal = 11.dp, vertical = 7.dp), color = chipTint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(if (compact) 140.dp else 300.dp)) {
            RoadViewThumbnail(lookup, compact, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().clickable(onClick = onClick))
        }
        Row(Modifier.fillMaxWidth().height(68.dp).background(DejaVroomTheme.colors.panelRaised).padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            DirectionBadge(segment.cue?.direction, Modifier.size(38.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(segment.name, color = DejaVroomTheme.colors.white, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cardSubtitle(segment), color = DejaVroomTheme.colors.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // The photo is taken this far before the point, so the driver sees it coming.
            leadLabel(segment.approach?.leadMeters)?.let { lead ->
                Spacer(Modifier.width(8.dp))
                Text("$lead 전", Modifier.background(DejaVroomTheme.colors.blue.copy(alpha = .12f), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp), color = DejaVroomTheme.colors.blueLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

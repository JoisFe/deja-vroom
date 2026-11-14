package ai.dejavroom.app.ui

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
internal fun BriefingScreen(state: DejaVroomUiState, vm: DejaVroomViewModel, compact: Boolean) {
    val route = state.route ?: return
    if (route.segments.isEmpty()) return
    val segment = route.segments[state.segmentIndex]
    if (compact) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 3.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.segmentIndex + 1} / ${route.segments.size}", Modifier.background(DejaVroomTheme.colors.blue, CircleShape).padding(horizontal = 13.dp, vertical = 5.dp), color = DejaVroomTheme.colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(segment.type.label, color = DejaVroomTheme.colors.white, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(segment.name, color = DejaVroomTheme.colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(5.dp))
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
            Spacer(Modifier.height(7.dp))
            BriefingCompactSegments(route.segments.take(3), state.segmentIndex, vm::selectSegment)
            Spacer(Modifier.height(7.dp))
            BriefingCompactControls(state, route.segments.size, vm)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${state.segmentIndex + 1} / ${route.segments.size}", Modifier.background(DejaVroomTheme.colors.blue, CircleShape).padding(horizontal = 17.dp, vertical = 7.dp), color = DejaVroomTheme.colors.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(segment.type.label, color = DejaVroomTheme.colors.white, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${segment.name} · 약 ${segmentPreviewSeconds(segment)}초 확인", color = DejaVroomTheme.colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                segments = route.segments.take(3),
                activeIndex = state.segmentIndex,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onSelect = vm::selectSegment,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(16.dp)).background(DejaVroomTheme.colors.panel).border(1.dp, DejaVroomTheme.colors.stroke, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::toggleVoice) {
                Text(voiceLabel(state), color = if (state.voiceEnabled) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            repeat(route.segments.size) { index ->
                Box(Modifier.padding(horizontal = 5.dp).size(if (index == state.segmentIndex) 12.dp else 9.dp).background(if (index == state.segmentIndex) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.stroke, CircleShape))
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("‹  이전 구간", Modifier.width(150.dp), enabled = state.segmentIndex > 0, onClick = vm::previousSegment)
            Spacer(Modifier.width(10.dp))
            PrimaryButton(if (state.segmentIndex == route.segments.lastIndex) "브리핑 완료" else "다음 구간", "›", Modifier.width(200.dp), vm::nextSegment)
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
    Box(modifier.clip(RoundedCornerShape(if (compact) 16.dp else 20.dp))) {
        val views = (roadViewLookup as? RoadViewLookupResult.Available)?.views.orEmpty()
        val currentView = views.getOrNull(viewIndex)
        when {
            currentView?.contentOrigin == RoadViewContentOrigin.DEMO_ILLUSTRATION -> RoadScene(Modifier.fillMaxSize(), variant = index, large = true)
            currentView != null -> RoadViewUnavailableCanvas("사진 데이터 수신 · 화면 표시 모듈 미연결", Modifier.fillMaxSize())
            else -> RoadViewUnavailableCanvas(roadViewStatusMessage(roadViewLookup), Modifier.fillMaxSize())
        }
        if (views.size > 1) {
            Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onPreviousView, enabled = viewIndex > 0) { Text("‹", color = DejaVroomTheme.colors.white, fontSize = 30.sp) }
                TextButton(onClick = onNextView, enabled = viewIndex < views.lastIndex) { Text("›", color = DejaVroomTheme.colors.white, fontSize = 30.sp) }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(if (compact) 8.dp else 14.dp), verticalAlignment = Alignment.Bottom) {
            // Fixed photo-overlay scrim and pale foreground keep guidance legible in either app theme.
            Column(Modifier.weight(1f).padding(end = if (compact) 6.dp else 12.dp).clip(RoundedCornerShape(if (compact) 10.dp else 14.dp)).background(Color(0xE6111B33)).padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 8.dp else 12.dp)) {
                Text(if (segment.canShowSpecificGuidance) segment.guidance else "잠시 후 ${segment.type.label} 구간이 나타납니다.", color = Color.White, fontSize = if (compact) 14.sp else 17.sp, lineHeight = if (compact) 18.sp else 23.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (segment.canShowSpecificGuidance) segment.confidenceLabel
                    else "${contentOriginLabel(currentView?.contentOrigin ?: segment.roadViewOrigin)} · ${routeMatchLabel(currentView?.routeMatchStatus ?: segment.routeMatchStatus)} · 차로 안내 미검증",
                    color = Color(0xFFE6ECF8), fontSize = 12.sp,
                    maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis,
                )
                if (currentView != null || segment.roadViewOrigin == RoadViewContentOrigin.LIVE_PROVIDER) {
                    Text(
                        listOfNotNull(
                            if (currentView != null) currentView.attribution ?: "출처 정보 없음" else segment.roadViewAttribution,
                            currentView?.capturedAt ?: segment.roadViewCapturedAt ?: "촬영일 정보 없음",
                            currentView?.let { routeMatchLabel(it.routeMatchStatus) },
                        ).joinToString(" · "),
                        color = Color(0xFFE6ECF8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (views.size > 1) Text("시점 ${viewIndex + 1} / ${views.size}", color = Color(0xFFE6ECF8), fontSize = 10.sp)
                if (roadViewLookup is RoadViewLookupResult.Unavailable || roadViewLookup == RoadViewLookupResult.NotAuthorized) {
                    TextButton(onClick = onRetry) { Text("다시 확인", color = Color.White, fontSize = 11.sp) }
                }
            }
            RouteMiniMap(index, Modifier.width(if (compact) 100.dp else 148.dp).height(if (compact) 66.dp else 100.dp))
        }
    }
}

@Composable
internal fun BriefingCompactControls(state: DejaVroomUiState, segmentCount: Int, vm: DejaVroomViewModel) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 430.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::toggleVoice) { Text(voiceLabel(state), color = if (state.voiceEnabled) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.weight(1f))
                    repeat(segmentCount) { index ->
                        Box(Modifier.padding(horizontal = 3.dp).size(if (index == state.segmentIndex) 9.dp else 6.dp).background(if (index == state.segmentIndex) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.stroke, CircleShape))
                    }
                }
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryButton("‹ 이전", Modifier.weight(1f), enabled = state.segmentIndex > 0, onClick = vm::previousSegment)
                    PrimaryButton(if (state.segmentIndex == segmentCount - 1) "브리핑 완료" else "다음 구간", "›", Modifier.weight(1f), vm::nextSegment)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::toggleVoice) { Text(voiceLabel(state), color = if (state.voiceEnabled) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                repeat(segmentCount) { index ->
                    Box(Modifier.padding(horizontal = 3.dp).size(if (index == state.segmentIndex) 10.dp else 7.dp).background(if (index == state.segmentIndex) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.stroke, CircleShape))
                }
                Spacer(Modifier.weight(1f))
                SecondaryButton("‹ 이전", Modifier.width(88.dp), enabled = state.segmentIndex > 0, onClick = vm::previousSegment)
                Spacer(Modifier.width(6.dp))
                PrimaryButton(if (state.segmentIndex == segmentCount - 1) "완료" else "다음", "›", Modifier.width(118.dp), vm::nextSegment)
            }
        }
    }
}

@Composable
internal fun BriefingCompactSegments(segments: List<BriefingSegment>, activeIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEachIndexed { index, segment ->
            val selected = index == activeIndex
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .background(if (selected) DejaVroomTheme.colors.selectedSurface else DejaVroomTheme.colors.panel)
                    .border(if (selected) 1.5.dp else 1.dp, if (selected) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.stroke, RoundedCornerShape(10.dp))
                    .clickable { onSelect(index) }.padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", color = if (selected) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(segment.type.label, color = DejaVroomTheme.colors.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("약 ${segmentPreviewSeconds(segment)}초", color = DejaVroomTheme.colors.muted, fontSize = 12.sp)
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("이번 경로의 주요 구간 ${segments.size}곳", color = DejaVroomTheme.colors.white, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        segments.forEachIndexed { index, segment ->
            val selected = index == activeIndex
            Row(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(15.dp))
                    .background(if (selected) DejaVroomTheme.colors.selectedSurface else DejaVroomTheme.colors.panel)
                    .border(if (selected) 2.dp else 1.dp, if (selected) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.stroke, RoundedCornerShape(15.dp))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", Modifier.size(34.dp).background(if (selected) DejaVroomTheme.colors.blue else DejaVroomTheme.colors.panelRaised, CircleShape).padding(top = 5.dp), color = if (selected) DejaVroomTheme.colors.onPrimary else DejaVroomTheme.colors.white, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.width(9.dp))
                Image(painterResource(when (index) { 1 -> R.drawable.demo_highway; 2 -> R.drawable.demo_destination; else -> R.drawable.demo_intersection }), contentDescription = "AI 생성 데모 이미지", contentScale = ContentScale.Crop, modifier = Modifier.size(62.dp).clip(RoundedCornerShape(9.dp)))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(segment.type.label, color = DejaVroomTheme.colors.white, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(segment.description, color = DejaVroomTheme.colors.muted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("약 ${segmentPreviewSeconds(segment)}초", color = DejaVroomTheme.colors.blueLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
    Box(modifier.background(DejaVroomTheme.colors.panelRaised), contentAlignment = Alignment.Center) {
        Text(message, Modifier.padding(24.dp), color = DejaVroomTheme.colors.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

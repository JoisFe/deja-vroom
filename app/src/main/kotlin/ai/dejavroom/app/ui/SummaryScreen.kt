package ai.dejavroom.app.ui

import ai.dejavroom.app.data.RoadViewEndpoint
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
internal fun SummaryScreen(state: DejaVroomUiState, vm: DejaVroomViewModel, compact: Boolean) {
    val route = state.route ?: return
    val segments = route.segments
    val displayCount = remember(route.routeId) { mutableIntStateOf(3) }
    val visibleSegments = segments.take(displayCount.intValue)
    if (compact) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("복잡 구간 후보 ${segments.size}곳 · 점수순", Modifier.weight(1f), color = DejaVroomTheme.colors.white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                CandidateCountSelector(displayCount.intValue) { displayCount.intValue = it }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleSegments.forEachIndexed { index, segment ->
                    SegmentCard(index + 1, segments.size, segment, compact = true, modifier = Modifier.fillMaxWidth().height(260.dp), onClick = { vm.openSegmentRoadView(index) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("첫 후보 거리뷰", Modifier.weight(1f), onClick = { vm.openSegmentRoadView(0) })
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
                Text("복잡한 구간을 먼저 훑고,\n원하는 곳을 열어보세요.", color = DejaVroomTheme.colors.white, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("경로 분석 점수순으로 최대 ${segments.size}곳을 찾았습니다. 구간을 선택하면 해당 Roadview가 열립니다.", color = DejaVroomTheme.colors.muted, fontSize = 14.sp)
            }
            CandidateCountSelector(displayCount.intValue) { displayCount.intValue = it }
        }
        Spacer(Modifier.height(21.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            visibleSegments.forEachIndexed { index, segment ->
                SegmentCard(index + 1, segments.size, segment, compact = false, modifier = Modifier.weight(1f), onClick = { vm.openSegmentRoadView(index) })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("첫 후보 Roadview 열기", Modifier.width(240.dp), onClick = { vm.openSegmentRoadView(0) })
            Spacer(Modifier.weight(1f))
            Text("●   ●   ●", color = DejaVroomTheme.colors.blue, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            PrimaryButton("주행 시작", "›", Modifier.width(250.dp), vm::startNavigation)
        }
    }
}

@Composable
internal fun CandidateCountSelector(selectedCount: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(3, 5).forEach { count ->
            TextButton(onClick = { onSelect(count) }) {
                Text("${count}개", color = if (selectedCount == count) DejaVroomTheme.colors.blueLight else DejaVroomTheme.colors.muted, fontWeight = if (selectedCount == count) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun SegmentCard(number: Int, total: Int, segment: BriefingSegment, compact: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = when (number) { 2 -> DejaVroomTheme.colors.violet; 3 -> DejaVroomTheme.colors.teal; else -> DejaVroomTheme.colors.blue }
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(DejaVroomTheme.colors.panel).border(1.dp, DejaVroomTheme.colors.stroke.copy(alpha = .8f), RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$number / $total", color = DejaVroomTheme.colors.white, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(segment.type.label, Modifier.background(tint.copy(alpha = .10f), CircleShape).padding(horizontal = 11.dp, vertical = 7.dp), color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(if (compact) 140.dp else 300.dp)) {
            RoadviewThumbnail(segment, compact, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().clickable(onClick = onClick))
        }
        Row(Modifier.fillMaxWidth().height(68.dp).background(DejaVroomTheme.colors.panelRaised).padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when (number) { 1 -> "◉"; 2 -> "╱"; else -> "↱" }, color = DejaVroomTheme.colors.blueLight, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(segment.name, color = DejaVroomTheme.colors.white, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Show why this point was picked, not just a number.
                val reasons = selectionReasons(segment)
                Text(if (reasons.isEmpty()) "${selectionSourceLabel(segment.selectionSource)} · 거리뷰 보기 ›" else "$reasons · 거리뷰 ›", color = DejaVroomTheme.colors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun RoadviewThumbnail(segment: BriefingSegment, compact: Boolean, modifier: Modifier = Modifier) {
    val endpoint = remember { RoadViewEndpoint.parse(BuildConfig.ROADVIEW_BASE_URL) }
    val coordinate = segment.coordinate
    val unavailable = when {
        endpoint == null -> RoadViewEndpoint.SETUP_MESSAGE
        coordinate == null -> "실제 도로 좌표 없음"
        else -> null
    }
    if (unavailable != null || endpoint == null || coordinate == null) {
        ThumbnailMessage(unavailable.orEmpty(), modifier)
        return
    }
    val url = endpoint.thumbnailUrl(coordinate, segment.headingDegrees)
    var loadFailed by remember(url) { mutableStateOf(false) }
    if (loadFailed) {
        ThumbnailMessage("로드뷰 페이지에 연결할 수 없습니다", modifier)
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                tag = url
                setBackgroundColor(android.graphics.Color.rgb(29, 41, 57))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        !endpoint.isRoadViewPage(request.url.toString())

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) loadFailed = true
                    }
                }
                loadUrl(url)
            }
        },
        update = { view -> if (view.tag != url) { view.tag = url; view.loadUrl(url) } },
        onRelease = { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        },
    )
}

@Composable
private fun ThumbnailMessage(message: String, modifier: Modifier) {
    Box(modifier.background(DejaVroomTheme.colors.panelRaised).padding(12.dp), contentAlignment = Alignment.Center) {
        Text(message, color = DejaVroomTheme.colors.muted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

/** Top two human-readable reasons; source and provenance tags are left out. */
internal fun selectionReasons(segment: BriefingSegment): String = segment.selectionEvidence
    .filterNot { it.startsWith("도로 그래프") || it.startsWith("경로와 거리") || it == "경로 형상만으로 추정" }
    .take(2)
    .joinToString(" · ")

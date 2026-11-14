package ai.firstlook.app

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import ai.firstlook.app.core.designsystem.FirstLookTheme
import ai.firstlook.app.core.designsystem.firstLookColors
import ai.firstlook.app.data.RoadViewLookupResult
import ai.firstlook.app.feature.FirstLookScreen
import ai.firstlook.app.feature.FirstLookUiState
import ai.firstlook.app.feature.FirstLookViewModel
import ai.firstlook.app.model.BriefingSegment
import ai.firstlook.app.model.RoadViewContentOrigin
import ai.firstlook.app.model.RouteMatchStatus
import ai.firstlook.app.model.RouteSource
import ai.firstlook.app.model.SegmentSelectionSource
import ai.firstlook.app.model.SegmentType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val palette = firstLookColors(darkTheme)
            SideEffect {
                window.statusBarColor = palette.background.toArgb()
                window.navigationBarColor = palette.background.toArgb()
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            FirstLookApp(darkTheme = darkTheme)
        }
    }
}

@Composable
private fun FirstLookApp(darkTheme: Boolean, vm: FirstLookViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    FirstLookTheme(darkTheme) {
        Surface(Modifier.fillMaxSize(), color = FirstLookTheme.colors.background) {
            if (state.isDrivingRestricted) {
                DrivingRestrictionScreen(vm)
                return@Surface
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp).clip(RoundedCornerShape(30.dp)).background(FirstLookTheme.colors.frame).border(1.dp, FirstLookTheme.colors.stroke.copy(alpha = .62f), RoundedCornerShape(30.dp))) {
                val compact = maxWidth < 1024.dp || maxHeight > maxWidth
                Row(Modifier.fillMaxSize()) {
                NavigationRail(state.screen, compact)
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = if (compact) 12.dp else 22.dp, end = if (compact) 12.dp else 24.dp, top = if (compact) 8.dp else 16.dp, bottom = if (compact) 8.dp else 16.dp)) {
                    FirstLookHeader(state, compact, vm)
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                    when (state.screen) {
                        FirstLookScreen.DESTINATION -> DestinationScreen(state, vm, compact)
                        FirstLookScreen.SUMMARY -> SummaryScreen(state, vm, compact)
                        FirstLookScreen.BRIEFING -> BriefingScreen(state, vm, compact)
                        FirstLookScreen.COMPLETE -> CompleteScreen(state, vm, compact)
                        FirstLookScreen.NAVIGATION -> NavigationScreen(state, vm, compact)
                        FirstLookScreen.ROADVIEW_TEST -> RoadViewTestScreen(
                            route = state.route,
                            candidates = state.route?.segments.orEmpty(),
                            selectedIndex = state.segmentIndex,
                            onBack = vm::closeRoadViewTest,
                            onPrevious = { vm.openSegmentRoadView(state.segmentIndex - 1) },
                            onNext = { vm.openSegmentRoadView(state.segmentIndex + 1) },
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun NavigationRail(screen: FirstLookScreen, compact: Boolean) {
    val activeLabel = when (screen) {
        FirstLookScreen.DESTINATION -> "검색"
        FirstLookScreen.SUMMARY, FirstLookScreen.BRIEFING, FirstLookScreen.COMPLETE, FirstLookScreen.NAVIGATION, FirstLookScreen.ROADVIEW_TEST -> "내비게이션"
    }
    Column(
        Modifier.width(if (compact) 64.dp else 88.dp).fillMaxHeight().background(FirstLookTheme.colors.panel).padding(top = 23.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pleos", color = FirstLookTheme.colors.white, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(32.dp))
        listOf("⌂" to "지도", "➤" to "내비게이션", "♫" to "미디어", "☎︎" to "전화", "▣" to "차량").forEach { (icon, label) ->
            val selected = label == activeLabel
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).then(if (selected) Modifier.background(FirstLookTheme.colors.blue.copy(alpha = .12f)) else Modifier).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(icon, color = if (selected) FirstLookTheme.colors.blueLight else FirstLookTheme.colors.muted, fontSize = 24.sp)
                if (!compact && (selected || label == "지도")) Text(label, color = if (selected) FirstLookTheme.colors.white else FirstLookTheme.colors.muted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("▦", color = FirstLookTheme.colors.muted, fontSize = 23.sp, modifier = Modifier.padding(10.dp))
        Text("⚙", color = FirstLookTheme.colors.muted, fontSize = 22.sp, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun FirstLookHeader(state: FirstLookUiState, compact: Boolean, vm: FirstLookViewModel) {
    val route = state.route
    if (compact) {
        Column(Modifier.fillMaxWidth().height(if (route == null && BuildConfig.DEBUG) 70.dp else if (route == null) 50.dp else 78.dp).padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pleos", color = FirstLookTheme.colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(11.dp))
                Box(Modifier.width(1.dp).height(24.dp).background(FirstLookTheme.colors.stroke))
                Spacer(Modifier.width(11.dp))
                Text("Déjà Vroom", color = FirstLookTheme.colors.white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("브리핑", color = FirstLookTheme.colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (route != null) {
                Spacer(Modifier.height(5.dp))
                Text("${route.origin}  ›  ${route.destination}     약 ${route.durationMinutes}분 · ${route.distanceKm}km · ${routeSourceLabel(route.source)}", color = FirstLookTheme.colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (BuildConfig.DEBUG) {
                TextButton(onClick = vm::openRoadViewTest, modifier = Modifier.align(Alignment.End)) {
                    Text("Kakao Roadview 개발 테스트", color = FirstLookTheme.colors.blue, fontSize = 11.sp)
                }
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Pleos", color = FirstLookTheme.colors.white, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(18.dp))
        Box(Modifier.width(1.dp).height(30.dp).background(FirstLookTheme.colors.stroke))
        Spacer(Modifier.width(18.dp))
        Text("Déjà Vroom", color = FirstLookTheme.colors.white, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text("초행길 사전 브리핑", color = FirstLookTheme.colors.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))

        if (route != null) {
            Text("⌖", color = FirstLookTheme.colors.blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            Text(route.origin, Modifier.widthIn(max = 110.dp), color = FirstLookTheme.colors.white, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("  ›  ", color = FirstLookTheme.colors.muted, fontSize = 16.sp)
            Text(route.destination, Modifier.widthIn(max = 120.dp), color = FirstLookTheme.colors.white, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.width(1.dp).height(30.dp).background(FirstLookTheme.colors.stroke))
            Spacer(Modifier.width(16.dp))
            Text("⌑", color = FirstLookTheme.colors.blue, fontSize = 18.sp)
            Spacer(Modifier.width(7.dp))
            Text("약 ${route.durationMinutes}분 · ${route.distanceKm}km · ${routeSourceLabel(route.source)}", color = FirstLookTheme.colors.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("출발 전 주행 미리보기", color = FirstLookTheme.colors.muted, fontSize = 14.sp)
                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = vm::openRoadViewTest) {
                        Text("Kakao Roadview 개발 테스트", color = FirstLookTheme.colors.blue, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationScreen(state: FirstLookUiState, vm: FirstLookViewModel, compact: Boolean) {
    if (compact) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            DestinationForm(state, vm, Modifier.fillMaxWidth(), dense = true)
            Spacer(Modifier.height(16.dp))
            RouteAnalysisPreview(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            RouteDataAttribution()
        }
    } else {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            DestinationForm(state, vm, Modifier.weight(1.05f).fillMaxHeight(), dense = false)
            Column(Modifier.weight(.95f), verticalArrangement = Arrangement.Center) {
                RouteAnalysisPreview(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                RouteDataAttribution()
            }
        }
    }
}

@Composable
private fun DestinationForm(state: FirstLookUiState, vm: FirstLookViewModel, modifier: Modifier, dense: Boolean) {
    Column(modifier, verticalArrangement = if (dense) Arrangement.Top else Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("FIRSTLOOK  ·  ROUTE ANALYSIS")
            }
            Spacer(Modifier.height(if (dense) 7.dp else 13.dp))
            Text("처음 가는 길도,\n처음 보는 길이 아니도록.", color = FirstLookTheme.colors.white, fontSize = if (dense) 24.sp else 31.sp, lineHeight = if (dense) 30.sp else 39.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("출발 전에 헷갈리기 쉬운 구간을 먼저 살펴보세요.", color = FirstLookTheme.colors.muted, fontSize = if (dense) 13.sp else 16.sp)
            Spacer(Modifier.height(if (dense) 10.dp else 18.dp))
            RouteFieldLabel("출발지")
            RouteSearchField(
                icon = "⌾",
                value = state.originInput,
                placeholder = "출발 장소를 검색하세요",
                onValueChange = vm::updateOrigin,
                dense = dense,
            )
            Spacer(Modifier.height(10.dp))
            RouteFieldLabel("목적지")
            RouteSearchField(
                icon = "⌕",
                value = state.destinationInput,
                placeholder = "목적지를 검색하세요",
                onValueChange = vm::updateDestination,
                dense = dense,
            )
            if (state.errorMessage != null) Text(state.errorMessage, color = FirstLookTheme.colors.error, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(if (dense) 8.dp else 13.dp))
            Text("최근 목적지", color = FirstLookTheme.colors.muted, fontSize = 14.sp)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("강남역", "코엑스", "서울역").forEach { place ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(FirstLookTheme.colors.panel).clickable { vm.updateDestination(place) }.padding(horizontal = 10.dp, vertical = 9.dp)) {
                        Text("●  $place", color = FirstLookTheme.colors.white, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (place == "강남역") "서울 강남구" else "최근 검색", color = FirstLookTheme.colors.dim, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(if (dense) 8.dp else 12.dp))
            PrimaryButton(
                if (state.isSearchingRoute) "복잡 교차로 분석 중" else "복잡 구간 분석",
                if (state.isSearchingRoute) "…" else "›",
                Modifier.fillMaxWidth(),
                vm::searchRoute,
                enabled = !state.isSearchingRoute,
            )
    }
}

@Composable
private fun RouteSearchField(
    icon: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    dense: Boolean,
) {
    Box(
        Modifier.fillMaxWidth()
            .padding(top = 7.dp)
            .background(FirstLookTheme.colors.panel, RoundedCornerShape(15.dp))
            .border(1.dp, FirstLookTheme.colors.stroke, RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = if (dense) 12.dp else 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, color = FirstLookTheme.colors.blueLight, fontSize = 21.sp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = FirstLookTheme.colors.dim, fontSize = 17.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = FirstLookTheme.colors.white, fontSize = 17.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RouteAnalysisPreview(modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(FirstLookTheme.colors.panel).border(1.dp, FirstLookTheme.colors.stroke, RoundedCornerShape(22.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("경로 속 복잡 교차로를 찾아\n로드뷰로 바로 연결합니다", color = FirstLookTheme.colors.white, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        listOf(
            "01" to "목적지 검색 후 실제 도로 경로 계산",
            "02" to "교차로 갈래 수와 분기 형태 분석·점수화",
            "03" to "점수가 높은 구간 중 로드뷰가 있는 곳 표시",
        ).forEach { (number, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(number, color = FirstLookTheme.colors.blueLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(label, color = FirstLookTheme.colors.muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RouteDataAttribution() {
    Text("경로·도로 그래프: OpenStreetMap / OSRM 개발 서비스 · 복잡도는 차로·현재 위험도 의미가 아닙니다.", color = FirstLookTheme.colors.dim, fontSize = 11.sp, lineHeight = 14.sp)
}

@Composable
private fun RouteFieldLabel(label: String) {
    Text(label, color = FirstLookTheme.colors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun RouteField(icon: String, value: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 7.dp).background(FirstLookTheme.colors.panel, RoundedCornerShape(15.dp)).padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = if (enabled) FirstLookTheme.colors.blueLight else FirstLookTheme.colors.dim, fontSize = 19.sp)
        Spacer(Modifier.width(10.dp))
        Text(value, color = if (enabled) FirstLookTheme.colors.white else FirstLookTheme.colors.muted, fontSize = 17.sp)
    }
}

@Composable
private fun SummaryScreen(state: FirstLookUiState, vm: FirstLookViewModel, compact: Boolean) {
    val route = state.route ?: return
    val segments = route.segments
    val displayCount = remember(route.routeId) { mutableIntStateOf(3) }
    val visibleSegments = segments.take(displayCount.intValue)
    if (compact) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("복잡 구간 후보 ${segments.size}곳 · 점수순", Modifier.weight(1f), color = FirstLookTheme.colors.white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            Text("✦", color = FirstLookTheme.colors.blue, fontSize = 30.sp)
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text("복잡한 구간을 먼저 훑고,\n원하는 곳을 열어보세요.", color = FirstLookTheme.colors.white, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("경로 분석 점수순으로 최대 ${segments.size}곳을 찾았습니다. 구간을 선택하면 해당 Roadview가 열립니다.", color = FirstLookTheme.colors.muted, fontSize = 14.sp)
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
            Text("●   ●   ●", color = FirstLookTheme.colors.blue, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            PrimaryButton("주행 시작", "›", Modifier.width(250.dp), vm::startNavigation)
        }
    }
}

@Composable
private fun CandidateCountSelector(selectedCount: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(3, 5).forEach { count ->
            TextButton(onClick = { onSelect(count) }) {
                Text("${count}개", color = if (selectedCount == count) FirstLookTheme.colors.blueLight else FirstLookTheme.colors.muted, fontWeight = if (selectedCount == count) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SegmentCard(number: Int, total: Int, segment: BriefingSegment, compact: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = when (number) { 2 -> FirstLookTheme.colors.violet; 3 -> FirstLookTheme.colors.teal; else -> FirstLookTheme.colors.blue }
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(FirstLookTheme.colors.panel).border(1.dp, FirstLookTheme.colors.stroke.copy(alpha = .8f), RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$number / $total", color = FirstLookTheme.colors.white, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(segment.type.label, Modifier.background(tint.copy(alpha = .10f), CircleShape).padding(horizontal = 11.dp, vertical = 7.dp), color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(if (compact) 140.dp else 300.dp)) {
            RoadviewThumbnail(segment, compact, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().clickable(onClick = onClick))
        }
        Row(Modifier.fillMaxWidth().height(68.dp).background(FirstLookTheme.colors.panelRaised).padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when (number) { 1 -> "◉"; 2 -> "╱"; else -> "↱" }, color = FirstLookTheme.colors.blueLight, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(segment.description, color = FirstLookTheme.colors.white, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val score = segment.selectionScore?.let { " · 점수 ${(it * 100).toInt()}" }.orEmpty()
                Text("${selectionSourceLabel(segment.selectionSource)}$score · 거리뷰 보기 ›", color = FirstLookTheme.colors.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RoadviewThumbnail(segment: BriefingSegment, compact: Boolean, modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) {
        Box(modifier.background(FirstLookTheme.colors.panelRaised), contentAlignment = Alignment.Center) {
            Text("Roadview 사진 미리보기는 개발 빌드에서만 제공됩니다.", color = FirstLookTheme.colors.muted, fontSize = 11.sp)
        }
        return
    }
    val coordinate = segment.coordinate
    if (coordinate == null) {
        Box(modifier.background(FirstLookTheme.colors.panelRaised), contentAlignment = Alignment.Center) {
            Text("실제 도로 좌표 없음", color = FirstLookTheme.colors.muted, fontSize = 11.sp)
        }
        return
    }
    val layout = if (compact) "compact" else "expanded"
    val url = "http://localhost:8080/roadview/?thumb=mapillary&lat=${coordinate.latitude}&lng=${coordinate.longitude}&heading=${segment.headingDegrees ?: 0.0}&layout=$layout"
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
                webViewClient = WebViewClient()
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
private fun BriefingScreen(state: FirstLookUiState, vm: FirstLookViewModel, compact: Boolean) {
    val route = state.route ?: return
    if (route.segments.isEmpty()) return
    val segment = route.segments[state.segmentIndex]
    if (compact) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 3.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.segmentIndex + 1} / ${route.segments.size}", Modifier.background(FirstLookTheme.colors.blue, CircleShape).padding(horizontal = 13.dp, vertical = 5.dp), color = FirstLookTheme.colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(segment.type.label, color = FirstLookTheme.colors.white, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(segment.name, color = FirstLookTheme.colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text("${state.segmentIndex + 1} / ${route.segments.size}", Modifier.background(FirstLookTheme.colors.blue, CircleShape).padding(horizontal = 17.dp, vertical = 7.dp), color = FirstLookTheme.colors.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(segment.type.label, color = FirstLookTheme.colors.white, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${segment.name} · 약 ${segmentPreviewSeconds(segment)}초 확인", color = FirstLookTheme.colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Row(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(16.dp)).background(FirstLookTheme.colors.panel).border(1.dp, FirstLookTheme.colors.stroke, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::toggleVoice) {
                Text(voiceLabel(state), color = if (state.voiceEnabled) FirstLookTheme.colors.blue else FirstLookTheme.colors.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            repeat(route.segments.size) { index ->
                Box(Modifier.padding(horizontal = 5.dp).size(if (index == state.segmentIndex) 12.dp else 9.dp).background(if (index == state.segmentIndex) FirstLookTheme.colors.blue else FirstLookTheme.colors.stroke, CircleShape))
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("‹  이전 구간", Modifier.width(150.dp), enabled = state.segmentIndex > 0, onClick = vm::previousSegment)
            Spacer(Modifier.width(10.dp))
            PrimaryButton(if (state.segmentIndex == route.segments.lastIndex) "브리핑 완료" else "다음 구간", "›", Modifier.width(200.dp), vm::nextSegment)
        }
    }
}

@Composable
private fun BriefingHero(
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
                TextButton(onClick = onPreviousView, enabled = viewIndex > 0) { Text("‹", color = FirstLookTheme.colors.white, fontSize = 30.sp) }
                TextButton(onClick = onNextView, enabled = viewIndex < views.lastIndex) { Text("›", color = FirstLookTheme.colors.white, fontSize = 30.sp) }
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
private fun BriefingCompactControls(state: FirstLookUiState, segmentCount: Int, vm: FirstLookViewModel) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 430.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::toggleVoice) { Text(voiceLabel(state), color = if (state.voiceEnabled) FirstLookTheme.colors.blue else FirstLookTheme.colors.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.weight(1f))
                    repeat(segmentCount) { index ->
                        Box(Modifier.padding(horizontal = 3.dp).size(if (index == state.segmentIndex) 9.dp else 6.dp).background(if (index == state.segmentIndex) FirstLookTheme.colors.blue else FirstLookTheme.colors.stroke, CircleShape))
                    }
                }
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryButton("‹ 이전", Modifier.weight(1f), enabled = state.segmentIndex > 0, onClick = vm::previousSegment)
                    PrimaryButton(if (state.segmentIndex == segmentCount - 1) "브리핑 완료" else "다음 구간", "›", Modifier.weight(1f), vm::nextSegment)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::toggleVoice) { Text(voiceLabel(state), color = if (state.voiceEnabled) FirstLookTheme.colors.blue else FirstLookTheme.colors.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                repeat(segmentCount) { index ->
                    Box(Modifier.padding(horizontal = 3.dp).size(if (index == state.segmentIndex) 10.dp else 7.dp).background(if (index == state.segmentIndex) FirstLookTheme.colors.blue else FirstLookTheme.colors.stroke, CircleShape))
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
private fun BriefingCompactSegments(segments: List<BriefingSegment>, activeIndex: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEachIndexed { index, segment ->
            val selected = index == activeIndex
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .background(if (selected) FirstLookTheme.colors.selectedSurface else FirstLookTheme.colors.panel)
                    .border(if (selected) 1.5.dp else 1.dp, if (selected) FirstLookTheme.colors.blue else FirstLookTheme.colors.stroke, RoundedCornerShape(10.dp))
                    .clickable { onSelect(index) }.padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", color = if (selected) FirstLookTheme.colors.blue else FirstLookTheme.colors.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(segment.type.label, color = FirstLookTheme.colors.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("약 ${segmentPreviewSeconds(segment)}초", color = FirstLookTheme.colors.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BriefingSegmentList(
    segments: List<BriefingSegment>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("이번 경로의 주요 구간 ${segments.size}곳", color = FirstLookTheme.colors.white, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        segments.forEachIndexed { index, segment ->
            val selected = index == activeIndex
            Row(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(15.dp))
                    .background(if (selected) FirstLookTheme.colors.selectedSurface else FirstLookTheme.colors.panel)
                    .border(if (selected) 2.dp else 1.dp, if (selected) FirstLookTheme.colors.blue else FirstLookTheme.colors.stroke, RoundedCornerShape(15.dp))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", Modifier.size(34.dp).background(if (selected) FirstLookTheme.colors.blue else FirstLookTheme.colors.panelRaised, CircleShape).padding(top = 5.dp), color = if (selected) FirstLookTheme.colors.onPrimary else FirstLookTheme.colors.white, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.width(9.dp))
                Image(painterResource(when (index) { 1 -> R.drawable.demo_highway; 2 -> R.drawable.demo_destination; else -> R.drawable.demo_intersection }), contentDescription = "AI 생성 데모 이미지", contentScale = ContentScale.Crop, modifier = Modifier.size(62.dp).clip(RoundedCornerShape(9.dp)))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(segment.type.label, color = FirstLookTheme.colors.white, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(segment.description, color = FirstLookTheme.colors.muted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("약 ${segmentPreviewSeconds(segment)}초", color = FirstLookTheme.colors.blueLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun segmentPreviewSeconds(segment: BriefingSegment): Int = when (segment.type) {
    SegmentType.INTERSECTION -> 8
    SegmentType.HIGHWAY_JUNCTION -> 10
    SegmentType.DESTINATION_ENTRY -> 7
    SegmentType.COMPLEX_TURN -> 8
    SegmentType.HISTORICAL_RISK -> 8
}

private fun voiceLabel(state: FirstLookUiState): String = when {
    !state.voiceEnabled -> "음성 안내 OFF"
    state.voiceOutputAvailable -> "음성 안내 ON"
    else -> "음성 요청 ON · TTS 미연동"
}

private fun selectionSourceLabel(source: SegmentSelectionSource): String = when (source) {
    SegmentSelectionSource.ROUTE_ANALYSIS -> "경로 후보 분석"
    SegmentSelectionSource.PUBLIC_DATA -> "공공 데이터 후보"
    SegmentSelectionSource.CURATED_DEMO -> "사전 구성 데모"
}

private fun contentOriginLabel(origin: RoadViewContentOrigin): String = when (origin) {
    RoadViewContentOrigin.LIVE_PROVIDER -> "공급자 도로 사진"
    RoadViewContentOrigin.CURATED -> "등록 이미지"
    RoadViewContentOrigin.DEMO_ILLUSTRATION -> "AI 생성 데모 · 실제 사진 아님"
}

private fun routeMatchLabel(status: RouteMatchStatus): String = when (status) {
    RouteMatchStatus.VERIFIED -> "경로 일치 확인"
    RouteMatchStatus.UNVERIFIED -> "경로 일치 미확인"
    RouteMatchStatus.MISMATCH -> "경로 불일치"
    RouteMatchStatus.STALE -> "오래된 정보"
    RouteMatchStatus.NO_COVERAGE -> "사진 없음"
    RouteMatchStatus.NOT_APPLICABLE -> "실경로 매칭 미적용"
}

private fun roadViewStatusMessage(result: RoadViewLookupResult?): String = when (result) {
    null -> "도로 사진 정보를 확인 중입니다."
    RoadViewLookupResult.NoCoverage -> "이 구간은 사진 제공 범위 밖입니다. 실제 경로 안내를 확인하세요."
    RoadViewLookupResult.NotAuthorized -> "도로 사진 제공자가 연결되지 않았습니다. Demo 그림만 표시합니다."
    is RoadViewLookupResult.Unavailable -> when (result.reason) {
        ai.firstlook.app.data.RoadViewUnavailableReason.OFFLINE -> "네트워크 연결을 확인할 수 없습니다."
        ai.firstlook.app.data.RoadViewUnavailableReason.TIMEOUT -> "사진 조회 시간이 초과되었습니다."
        ai.firstlook.app.data.RoadViewUnavailableReason.QUOTA -> "사진 조회 한도에 도달했습니다."
        ai.firstlook.app.data.RoadViewUnavailableReason.PROVIDER_ERROR -> "사진 정보를 불러오지 못했습니다."
    }
    is RoadViewLookupResult.Available -> if (result.views.isEmpty()) "이 구간의 사진이 없습니다." else "사진 정보를 확인 중입니다."
}

@Composable
private fun RoadViewUnavailableCanvas(message: String, modifier: Modifier = Modifier) {
    Box(modifier.background(FirstLookTheme.colors.panelRaised), contentAlignment = Alignment.Center) {
        Text(message, Modifier.padding(24.dp), color = FirstLookTheme.colors.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DrivingRestrictionScreen(vm: FirstLookViewModel) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("주행 제한 상태", color = FirstLookTheme.colors.white, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("안전을 위해 목적지 입력과 사진 탐색을 제한했습니다.", color = FirstLookTheme.colors.muted, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text("이 화면은 앱 내 Mock 시연이며 실제 차량 상태가 아닙니다.", color = FirstLookTheme.colors.warning, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        SecondaryButton("Demo 제한 해제", Modifier.width(220.dp), onClick = vm::toggleDemoDrivingRestriction)
    }
}

private fun routeSourceLabel(source: RouteSource): String = when (source) {
    RouteSource.PLEOS_NAVIGATION -> "Pleos 경로"
    RouteSource.EXTERNAL_ROUTE -> "OSRM/OSM 경로 · 개발 서비스"
    RouteSource.CURATED_DEMO -> "Demo 경로"
}

@Composable
private fun CompleteScreen(state: FirstLookUiState, vm: FirstLookViewModel, compact: Boolean) {
    val route = state.route ?: return
    val layout = if (compact) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()
    Column(layout.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(74.dp).background(FirstLookTheme.colors.successContainer, CircleShape), contentAlignment = Alignment.Center) { Text("✓", color = FirstLookTheme.colors.green, fontSize = 40.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(18.dp))
        Text("주요 구간을 모두 확인했습니다.", color = FirstLookTheme.colors.white, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("이제 주행을 시작할 수 있어요.", color = FirstLookTheme.colors.muted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryMetric("확인한 구간", "${route.segments.size}곳", if (compact) Modifier.weight(1f) else Modifier.width(160.dp))
            SummaryMetric("예상 소요 시간", "약 ${route.durationMinutes}분", if (compact) Modifier.weight(1f) else Modifier.width(160.dp))
            SummaryMetric("전체 거리", "${route.distanceKm}km", if (compact) Modifier.weight(1f) else Modifier.width(160.dp))
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("주행 시작", "›", if (compact) Modifier.weight(1f) else Modifier.width(230.dp), vm::startNavigation)
            SecondaryButton("다시 보기", if (compact) Modifier.weight(1f) else Modifier.width(190.dp), onClick = vm::replay)
        }
    }
}

@Composable
private fun NavigationScreen(state: FirstLookUiState, vm: FirstLookViewModel, compact: Boolean) {
    val route = state.route
    if (compact) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.Center) {
            Eyebrow("PLEOS NAVIGATION  ·  MOCK")
            Spacer(Modifier.height(10.dp))
            Text("주행을 시작합니다", color = FirstLookTheme.colors.white, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text("Pleos 내비게이션 연동 전환을 시연하는 화면입니다.", color = FirstLookTheme.colors.muted, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            if (route != null) Text("${route.origin}  →  ${route.destination}  ·  약 ${route.durationMinutes}분 · ${route.distanceKm}km", color = FirstLookTheme.colors.white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("실제 목적지 설정·주행 전환 API는 연결되지 않았습니다.", color = FirstLookTheme.colors.warning, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("브리핑 요약으로 돌아가기", Modifier.fillMaxWidth(), onClick = vm::backToSummary)
            Spacer(Modifier.height(12.dp))
            RoadScene(Modifier.fillMaxWidth().aspectRatio(1.8f), variant = 1, large = true)
        }
        return
    }
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Eyebrow("PLEOS NAVIGATION  ·  MOCK")
            Spacer(Modifier.height(16.dp))
            Text("주행을 시작합니다", color = FirstLookTheme.colors.white, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Pleos 내비게이션 연동 전환을 시연하는 화면입니다.", color = FirstLookTheme.colors.muted, fontSize = 16.sp)
            Spacer(Modifier.height(23.dp))
            if (route != null) Text("${route.origin}  →  ${route.destination}  ·  약 ${route.durationMinutes}분 · ${route.distanceKm}km", color = FirstLookTheme.colors.white, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            Text("실제 목적지 설정·주행 전환 API는 연결되지 않았습니다.", color = FirstLookTheme.colors.warning, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            SecondaryButton("브리핑 요약으로 돌아가기", Modifier.width(250.dp), onClick = vm::backToSummary)
        }
        RoadScene(Modifier.weight(1.05f).aspectRatio(1.4f), variant = 1, large = true)
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier.width(160.dp)) {
    Column(modifier.background(FirstLookTheme.colors.panel, RoundedCornerShape(15.dp)).border(1.dp, FirstLookTheme.colors.stroke.copy(alpha = .65f), RoundedCornerShape(15.dp)).padding(15.dp)) {
        Text(label, color = FirstLookTheme.colors.muted, fontSize = 13.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = FirstLookTheme.colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RouteMiniMap(activeIndex: Int, modifier: Modifier = Modifier.fillMaxWidth().height(116.dp)) {
    val colors = FirstLookTheme.colors
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(colors.panelRaised).border(1.dp, colors.stroke.copy(alpha = .7f), RoundedCornerShape(12.dp)).padding(9.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(size.width * .08f, size.height * .78f)
                cubicTo(size.width * .30f, size.height * .72f, size.width * .20f, size.height * .16f, size.width * .48f, size.height * .30f)
                cubicTo(size.width * .73f, size.height * .43f, size.width * .66f, size.height * .82f, size.width * .91f, size.height * .20f)
            }
            drawPath(path, colors.blue.copy(alpha = .35f), style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, colors.blue, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            listOf(Offset(size.width * .24f, size.height * .69f), Offset(size.width * .48f, size.height * .30f), Offset(size.width * .72f, size.height * .47f)).forEachIndexed { index, point ->
                drawCircle(if (index == activeIndex) colors.warning else colors.white, radius = 5.dp.toPx(), center = point)
            }
        }
        Text("경로 위치 · 데모", Modifier.align(Alignment.TopStart), color = colors.muted, fontSize = 10.sp)
    }
}

@Composable
private fun RoadScene(modifier: Modifier = Modifier, variant: Int, large: Boolean) {
    Box(modifier.clip(RoundedCornerShape(if (large) 20.dp else 0.dp)).background(FirstLookTheme.colors.panelRaised)) {
        val illustration = when (variant) {
            1 -> R.drawable.demo_highway
            2 -> R.drawable.demo_destination
            else -> R.drawable.demo_intersection
        }
        Image(
            painter = painterResource(illustration),
            contentDescription = "가상의 도로 장면 데모 이미지",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Keep the provenance badge static over photos so its contrast never depends on the image.
        Text(if (large) "AI 생성 데모 · 실제 도로 사진 아님" else "AI 생성 데모", Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0xCC111B33), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, fontSize = if (large) 11.sp else 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoBanner(icon: String, title: String, body: String) {
    Row(Modifier.fillMaxWidth().background(FirstLookTheme.colors.panel, RoundedCornerShape(14.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = FirstLookTheme.colors.blueLight, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = FirstLookTheme.colors.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = FirstLookTheme.colors.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, color = FirstLookTheme.colors.blueLight, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
}

@Composable
private fun PrimaryButton(text: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(54.dp), shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = FirstLookTheme.colors.blue, contentColor = FirstLookTheme.colors.onPrimary)) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(icon, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp).background(if (enabled) FirstLookTheme.colors.panelRaised else FirstLookTheme.colors.panel, RoundedCornerShape(28.dp)).border(1.dp, FirstLookTheme.colors.stroke.copy(alpha = .8f), RoundedCornerShape(28.dp))) {
        Text(text, color = if (enabled) FirstLookTheme.colors.white else FirstLookTheme.colors.dim, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

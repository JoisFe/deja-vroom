package ai.dejavroom.app.ui

import ai.dejavroom.app.BuildConfig
import ai.dejavroom.app.data.RoadViewConfig
import ai.dejavroom.app.data.BriefingSettings
import ai.dejavroom.app.data.OsmRouteRepository
import ai.dejavroom.app.data.PleosCurrentLocationRepository
import ai.dejavroom.app.data.SearchDefaults
import ai.dejavroom.app.data.SharedPreferencesTripStore
import ai.dejavroom.app.data.RoadViewProviderFactory
import ai.dejavroom.app.R
import ai.dejavroom.app.data.SharedPreferencesRecentSearchStore
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
internal fun DejaVroomApp(darkTheme: Boolean, vm: DejaVroomViewModel = viewModel(factory = DejaVroomViewModelFactory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Every time the app comes to the front (first start, or again at a rest area) it asks where the car is.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onAppForegrounded() }
    DejaVroomTheme(darkTheme) {
        Surface(Modifier.fillMaxSize(), color = DejaVroomTheme.colors.background) {
            if (state.isDrivingRestricted) {
                DrivingRestrictionScreen(vm)
                return@Surface
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp).clip(RoundedCornerShape(30.dp)).background(DejaVroomTheme.colors.frame).border(1.dp, DejaVroomTheme.colors.stroke.copy(alpha = .62f), RoundedCornerShape(30.dp))) {
                val compact = maxWidth < 1024.dp || maxHeight > maxWidth
                Row(Modifier.fillMaxSize()) {
                NavigationRail(state.screen, compact)
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = if (compact) 12.dp else 22.dp, end = if (compact) 12.dp else 24.dp, top = if (compact) 8.dp else 16.dp, bottom = if (compact) 8.dp else 16.dp)) {
                    DejaVroomHeader(state, compact, vm)
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                    when (state.screen) {
                        DejaVroomScreen.DESTINATION -> DestinationScreen(state, remember(vm) { vm.destinationActions() }, compact)
                        DejaVroomScreen.SUMMARY -> SummaryScreen(state, vm, compact)
                        DejaVroomScreen.BRIEFING -> BriefingScreen(state, vm, compact)
                        DejaVroomScreen.COMPLETE -> CompleteScreen(state, vm, compact)
                        DejaVroomScreen.NAVIGATION -> NavigationScreen(state, vm, compact)
                        DejaVroomScreen.ROADVIEW_TEST -> RoadViewScreen(
                            lookup = state.roadViewLookups[
                                state.shownSegments.getOrNull(state.segmentIndex)?.id ?: DejaVroomViewModel.DEVELOPER_FIXTURE_KEY
                            ],
                            route = state.route,
                            candidates = state.shownSegments,
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
internal fun NavigationRail(screen: DejaVroomScreen, compact: Boolean) {
    val activeLabel = when (screen) {
        DejaVroomScreen.DESTINATION -> "검색"
        DejaVroomScreen.SUMMARY, DejaVroomScreen.BRIEFING, DejaVroomScreen.COMPLETE, DejaVroomScreen.NAVIGATION, DejaVroomScreen.ROADVIEW_TEST -> "내비게이션"
    }
    Column(
        Modifier.width(if (compact) 64.dp else 88.dp).fillMaxHeight().background(DejaVroomTheme.colors.panel).padding(top = 23.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pleos", color = DejaVroomTheme.colors.white, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(32.dp))
        listOf("⌂" to "지도", "➤" to "내비게이션", "♫" to "미디어", "☎︎" to "전화", "▣" to "차량").forEach { (icon, label) ->
            val selected = label == activeLabel
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).then(if (selected) Modifier.background(DejaVroomTheme.colors.blue.copy(alpha = .12f)) else Modifier).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(icon, color = if (selected) DejaVroomTheme.colors.blueLight else DejaVroomTheme.colors.muted, fontSize = 24.sp)
                if (!compact && (selected || label == "지도")) Text(label, color = if (selected) DejaVroomTheme.colors.white else DejaVroomTheme.colors.muted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("▦", color = DejaVroomTheme.colors.muted, fontSize = 23.sp, modifier = Modifier.padding(10.dp))
        Text("⚙", color = DejaVroomTheme.colors.muted, fontSize = 22.sp, modifier = Modifier.padding(10.dp))
    }
}

@Composable
internal fun DejaVroomHeader(state: DejaVroomUiState, compact: Boolean, vm: DejaVroomViewModel) {
    val route = state.route
    if (compact) {
        Column(Modifier.fillMaxWidth().height(if (route == null && BuildConfig.DEBUG) 70.dp else if (route == null) 50.dp else 78.dp).padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pleos", color = DejaVroomTheme.colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(11.dp))
                Box(Modifier.width(1.dp).height(24.dp).background(DejaVroomTheme.colors.stroke))
                Spacer(Modifier.width(11.dp))
                Text("Déjà Vroom", color = DejaVroomTheme.colors.white, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("브리핑", color = DejaVroomTheme.colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (route != null) {
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${route.origin}  ›  ${route.destination}     약 ${route.durationMinutes}분 · ${route.distanceKm}km · ${routeSourceLabel(route.source)}", Modifier.weight(1f), color = DejaVroomTheme.colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.screen != DejaVroomScreen.DESTINATION) EditRouteButton(vm::editRoute)
                }
            } else if (BuildConfig.DEBUG) {
                TextButton(onClick = vm::openRoadViewTest, modifier = Modifier.align(Alignment.End)) {
                    Text("Kakao Roadview 개발 테스트", color = DejaVroomTheme.colors.blue, fontSize = 11.sp)
                }
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Pleos", color = DejaVroomTheme.colors.white, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(18.dp))
        Box(Modifier.width(1.dp).height(30.dp).background(DejaVroomTheme.colors.stroke))
        Spacer(Modifier.width(18.dp))
        Text("Déjà Vroom", color = DejaVroomTheme.colors.white, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text("초행길 사전 브리핑", color = DejaVroomTheme.colors.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))

        if (route != null) {
            Text("⌖", color = DejaVroomTheme.colors.blue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            Text(route.origin, Modifier.widthIn(max = 110.dp), color = DejaVroomTheme.colors.white, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("  ›  ", color = DejaVroomTheme.colors.muted, fontSize = 16.sp)
            Text(route.destination, Modifier.widthIn(max = 120.dp), color = DejaVroomTheme.colors.white, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.width(1.dp).height(30.dp).background(DejaVroomTheme.colors.stroke))
            Spacer(Modifier.width(16.dp))
            Text("⌑", color = DejaVroomTheme.colors.blue, fontSize = 18.sp)
            Spacer(Modifier.width(7.dp))
            Text("약 ${route.durationMinutes}분 · ${route.distanceKm}km · ${routeSourceLabel(route.source)}", color = DejaVroomTheme.colors.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (state.screen != DejaVroomScreen.DESTINATION) {
                Spacer(Modifier.width(12.dp))
                EditRouteButton(vm::editRoute)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("출발 전 주행 미리보기", color = DejaVroomTheme.colors.muted, fontSize = 14.sp)
                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = vm::openRoadViewTest) {
                        Text("Kakao Roadview 개발 테스트", color = DejaVroomTheme.colors.blue, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DrivingRestrictionScreen(vm: DejaVroomViewModel) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("주행 제한 상태", color = DejaVroomTheme.colors.white, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("안전을 위해 목적지 입력과 사진 탐색을 제한했습니다.", color = DejaVroomTheme.colors.muted, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text("이 화면은 앱 내 Mock 시연이며 실제 차량 상태가 아닙니다.", color = DejaVroomTheme.colors.warning, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        SecondaryButton("Demo 제한 해제", Modifier.width(220.dp), onClick = vm::toggleDemoDrivingRestriction)
    }
}

/** Returns to the search screen with both inputs kept. */
@Composable
private fun EditRouteButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("⌕ 경로 다시 검색", color = DejaVroomTheme.colors.blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun DejaVroomViewModel.destinationActions() = DestinationActions(
    onValueChange = ::updateField,
    onFocus = ::focusField,
    onClear = ::clearField,
    onSwap = ::swapFields,
    onUseCurrentLocation = ::useCurrentLocation,
    onSelectPlace = { field, place -> selectPlace(field, place) },
    onRemoveRecent = ::removeRecent,
    onClearRecents = ::clearRecents,
    onDismissSuggestions = ::dismissSuggestions,
    onSearch = ::searchRoute,
)

/** config/roadview.yml (asset) + local.properties overrides (BuildConfig) → the road view implementation to use. */
private fun roadViewConfig(app: android.app.Application): RoadViewConfig {
    val yaml = runCatching { app.assets.open(RoadViewConfig.ASSET_NAME).bufferedReader().use { it.readText() } }.getOrNull()
    return RoadViewConfig.resolve(
        yaml = yaml,
        providerOverride = BuildConfig.ROADVIEW_PROVIDER_OVERRIDE,
        kakaoBaseUrlOverride = BuildConfig.ROADVIEW_BASE_URL_OVERRIDE,
        mapillaryClientToken = BuildConfig.MAPILLARY_CLIENT_TOKEN,
        pageRevision = BuildConfig.ROADVIEW_PAGE_REVISION,
    )
}

/** config/briefing.yml (asset) → tuning values of the preview algorithm. A broken file falls back to the defaults. */
private fun briefingSettings(app: android.app.Application): BriefingSettings = runCatching {
    BriefingSettings.fromYaml(app.assets.open(BriefingSettings.ASSET_NAME).bufferedReader().use { it.readText() })
}.getOrElse { failure ->
    android.util.Log.w("DejaVroomBriefing", "config/briefing.yml을 읽지 못해 기본값을 쓴다: ${failure.message}")
    BriefingSettings()
}

/** config/search.yml (asset) → what the search screen starts with. A broken file falls back to the defaults. */
private fun searchDefaults(app: android.app.Application): SearchDefaults = runCatching {
    SearchDefaults.fromYaml(app.assets.open(SearchDefaults.ASSET_NAME).bufferedReader().use { it.readText() })
}.getOrElse { failure ->
    android.util.Log.w("DejaVroomSearch", "config/search.yml을 읽지 못해 기본값을 쓴다: ${failure.message}")
    SearchDefaults()
}

/** Stores, the vehicle's navigation and the configs under config/ need the application, so the ViewModel is built with it. */
private val DejaVroomViewModelFactory = viewModelFactory {
    initializer {
        val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        DejaVroomViewModel(
            repository = OsmRouteRepository(settings = briefingSettings(app)),
            roadViewProvider = RoadViewProviderFactory.create(roadViewConfig(app)),
            recentSearchStore = SharedPreferencesRecentSearchStore(app),
            currentLocation = PleosCurrentLocationRepository(app),
            tripStore = SharedPreferencesTripStore(app),
            searchDefaults = searchDefaults(app),
        )
    }
}

package ai.dejavroom.app.ui

import ai.dejavroom.app.BuildConfig
import ai.dejavroom.app.data.RoadViewConfig
import ai.dejavroom.app.data.BriefingSettings
import ai.dejavroom.app.data.KakaoPlaceSearchRepository
import ai.dejavroom.app.data.OsmRouteRepository
import ai.dejavroom.app.data.PlaceSearchFactory
import ai.dejavroom.app.data.PleosCurrentLocationRepository
import ai.dejavroom.app.data.SearchSettings
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
import ai.dejavroom.app.core.designsystem.DejaVroomDimens
import ai.dejavroom.app.core.designsystem.DejaVroomIcons
import ai.dejavroom.app.core.designsystem.DejaVroomScale
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
        Surface(Modifier.fillMaxSize(), color = DejaVroomTheme.colors.background, contentColor = DejaVroomTheme.colors.onSurface) {
            BoxWithConstraints(Modifier.fillMaxSize().background(DejaVroomTheme.colors.backgroundBrush)) {
                val compact = maxWidth < 1024.dp || maxHeight > maxWidth
                // Every size below is drawn for the Pleos Connect window and shrinks together in a smaller one.
                DejaVroomScale(DejaVroomDimens.scaleFor(maxWidth, maxHeight, compact)) {
                    if (state.isDrivingRestricted) {
                        DrivingRestrictionScreen(vm)
                        return@DejaVroomScale
                    }
                    val dimens = DejaVroomTheme.dimens
                    // The car's status bar and navigation bar are system UI outside this window, so the app draws
                    // no rail of its own and its content takes the full width.
                    // The side and bottom padding of every screen lives here, so screens add none of their own.
                    Column(Modifier.fillMaxSize().padding(horizontal = if (compact) dimens.screenPaddingCompact else dimens.screenPadding)) {
                        DejaVroomHeader(state, compact, vm)
                        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = if (compact) dimens.spaceL else dimens.dp(40))) {
                            when (state.screen) {
                                DejaVroomScreen.DESTINATION -> DestinationScreen(state, remember(vm) { vm.destinationActions() }, compact)
                                DejaVroomScreen.SUMMARY -> SummaryScreen(state, vm, compact)
                                DejaVroomScreen.NAVIGATION -> NavigationScreen(state, vm, compact)
                                DejaVroomScreen.ROAD_VIEW -> RoadViewScreen(
                                    lookup = state.roadViewLookups[
                                        state.shownSegments.getOrNull(state.segmentIndex)?.id ?: DejaVroomViewModel.DEVELOPER_FIXTURE_KEY
                                    ],
                                    route = state.route,
                                    candidates = state.shownSegments,
                                    selectedIndex = state.segmentIndex,
                                    onBack = vm::closeRoadView,
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
}

@Composable
internal fun DejaVroomHeader(state: DejaVroomUiState, compact: Boolean, vm: DejaVroomViewModel) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    val route = state.route
    val wordmark = type.bodyLarge.copy(fontWeight = FontWeight.Bold)
    // A release build never shows the developer entry, whatever local.properties says: its fixture is a debug asset.
    val developerEntry = BuildConfig.DEBUG && BuildConfig.DEVELOPER_ENTRY
    // A rehearsal edit to config/*.yml that broke the file must not go unnoticed; release builds never show it.
    val configProblem = state.configProblem
    if (BuildConfig.DEBUG && configProblem != null) {
        Text(configProblem, Modifier.fillMaxWidth().padding(top = dimens.spaceXs), color = colors.warning, style = type.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (compact) {
        Column(Modifier.fillMaxWidth().padding(vertical = dimens.spaceM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Déjà Vroom", color = colors.onSurface, style = wordmark, maxLines = 1)
                Spacer(Modifier.width(dimens.spaceS))
                Text("브리핑", color = colors.onSurfaceVariant, style = type.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (route != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${route.origin}  ›  ${route.destination}     약 ${route.durationMinutes}분 · ${route.distanceKm}km", Modifier.weight(1f), color = colors.onSurfaceVariant, style = type.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.screen != DejaVroomScreen.DESTINATION) EditRouteButton(vm::editRoute)
                }
            } else if (developerEntry) {
                TextAction(DEVELOPER_ROAD_VIEW_LABEL, Modifier.align(Alignment.End), onClick = vm::openDeveloperRoadView)
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(dimens.headerHeight), verticalAlignment = Alignment.CenterVertically) {
        Text("Déjà Vroom", color = colors.onSurface, style = wordmark, maxLines = 1)
        Spacer(Modifier.width(dimens.spaceM))
        Text("초행길 사전 브리핑", Modifier.weight(1f), color = colors.onSurfaceVariant, style = type.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)

        if (route != null) {
            AppIcon(DejaVroomIcons.Place, tint = colors.accent, size = dimens.iconSmall)
            Spacer(Modifier.width(dimens.spaceXs))
            Text(route.origin, Modifier.widthIn(max = dimens.dp(220)), color = colors.onSurface, style = type.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            AppIcon(DejaVroomIcons.ChevronRight, Modifier.padding(horizontal = dimens.spaceXs), tint = colors.onSurfaceVariant, size = dimens.iconSmall)
            Text(route.destination, Modifier.widthIn(max = dimens.dp(240)), color = colors.onSurface, style = type.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            HeaderDivider()
            Text("약 ${route.durationMinutes}분 · ${route.distanceKm}km", color = colors.onSurface, style = type.labelMedium, maxLines = 1)
            if (state.screen != DejaVroomScreen.DESTINATION) {
                Spacer(Modifier.width(dimens.spaceS))
                EditRouteButton(vm::editRoute)
            }
        } else {
            Text("출발 전 주행 미리보기", color = colors.onSurfaceVariant, style = type.labelMedium, maxLines = 1)
            if (developerEntry) {
                Spacer(Modifier.width(dimens.spaceS))
                TextAction(DEVELOPER_ROAD_VIEW_LABEL, onClick = vm::openDeveloperRoadView)
            }
        }
    }
}

@Composable
private fun HeaderDivider() {
    val dimens = DejaVroomTheme.dimens
    Box(Modifier.padding(horizontal = dimens.dp(20)).width(1.dp).height(dimens.dp(34)).background(DejaVroomTheme.colors.outline))
}

@Composable
internal fun DrivingRestrictionScreen(vm: DejaVroomViewModel) {
    val colors = DejaVroomTheme.colors
    val dimens = DejaVroomTheme.dimens
    val type = DejaVroomTheme.type
    Column(Modifier.fillMaxSize().padding(dimens.screenPadding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("주행 제한 상태", color = colors.onSurface, style = type.headlineSmall)
        Spacer(Modifier.height(dimens.spaceM))
        Text("안전을 위해 목적지 입력과 사진 탐색을 제한했습니다.", color = colors.onSurfaceVariant, style = type.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(dimens.spaceL))
        NoticeBanner("이 화면은 앱 내 Mock 시연이며 실제 차량 상태가 아닙니다.")
        Spacer(Modifier.height(dimens.spaceXl))
        SecondaryButton("Demo 제한 해제", Modifier.width(dimens.dp(360)), size = ButtonSize.Medium, onClick = vm::toggleDemoDrivingRestriction)
    }
}

/** Returns to the search screen with both inputs kept. */
@Composable
private fun EditRouteButton(onClick: () -> Unit) {
    TextAction("경로 다시 검색", icon = DejaVroomIcons.Search, onClick = onClick)
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

/** Settings read from a config file, with [problem] saying why the file was ignored when it was. */
private data class ConfigRead<T>(val settings: T, val problem: String? = null)

/** config/briefing.yml (asset) → tuning values of the preview algorithm. A broken file falls back to the defaults. */
private fun briefingSettings(app: android.app.Application): ConfigRead<BriefingSettings> = runCatching {
    BriefingSettings.fromYaml(app.assets.open(BriefingSettings.ASSET_NAME).bufferedReader().use { it.readText() })
}.fold(onSuccess = { ConfigRead(it) }) { failure ->
    val problem = "config/briefing.yml을 읽지 못해 기본값으로 실행한다: ${failure.message}"
    android.util.Log.w("DejaVroomBriefing", problem)
    ConfigRead(BriefingSettings(), problem)
}

/** config/search.yml (asset) → what the search screen starts with. A broken file falls back to the defaults. */
private fun searchSettings(app: android.app.Application): ConfigRead<SearchSettings> = runCatching {
    SearchSettings.fromYaml(app.assets.open(SearchSettings.ASSET_NAME).bufferedReader().use { it.readText() })
}.fold(onSuccess = { ConfigRead(it) }) { failure ->
    val problem = "config/search.yml을 읽지 못해 기본값으로 실행한다: ${failure.message}"
    android.util.Log.w("DejaVroomSearch", problem)
    ConfigRead(SearchSettings(), problem)
}

/** Stores, the vehicle's navigation and the configs under config/ need the application, so the ViewModel is built with it. */
private val DejaVroomViewModelFactory = viewModelFactory {
    initializer {
        val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        val roadView = roadViewConfig(app)
        val search = searchSettings(app)
        val briefing = briefingSettings(app)
        // config/search.yml picks the place search; the Kakao one lives on the same site as the road view.
        val places = PlaceSearchFactory.create(search.settings.placeSearch, roadView) { site -> KakaoPlaceSearchRepository(app, site) }
        DejaVroomViewModel(
            repository = OsmRouteRepository(geocoder = places.geocoder, settings = briefing.settings),
            roadViewProvider = RoadViewProviderFactory.create(roadView),
            placeSearch = places.suggestions,
            recentSearchStore = SharedPreferencesRecentSearchStore(app),
            currentLocation = PleosCurrentLocationRepository(app),
            tripStore = SharedPreferencesTripStore(app),
            searchSettings = search.settings,
            configProblem = listOfNotNull(briefing.problem, search.problem).joinToString(" · ").ifEmpty { null },
        )
    }
}

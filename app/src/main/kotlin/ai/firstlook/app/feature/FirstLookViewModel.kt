package ai.firstlook.app.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.firstlook.app.data.OsmRouteRepository
import ai.firstlook.app.data.DemoRoadViewProvider
import ai.firstlook.app.data.DrivingRestrictionSource
import ai.firstlook.app.data.FakeDrivingRestrictionSource
import ai.firstlook.app.data.NoopBriefingSpeechOutput
import ai.firstlook.app.data.BriefingSpeechOutput
import ai.firstlook.app.data.BriefingSpeechController
import ai.firstlook.app.data.RoadViewLookupResult
import ai.firstlook.app.data.RoadViewProvider
import ai.firstlook.app.data.RouteRepository
import ai.firstlook.app.model.RouteSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FirstLookUiState(
    val originInput: String = "수원시 영통구",
    val destinationInput: String = "강남역",
    val route: RouteSnapshot? = null,
    val segmentIndex: Int = 0,
    val screen: FirstLookScreen = FirstLookScreen.DESTINATION,
    val voiceEnabled: Boolean = true,
    val voiceOutputAvailable: Boolean = false,
    val errorMessage: String? = null,
    val roadViewLookups: Map<String, RoadViewLookupResult> = emptyMap(),
    val roadViewIndices: Map<String, Int> = emptyMap(),
    val isDrivingRestricted: Boolean = false,
    val isSearchingRoute: Boolean = false,
)

internal fun FirstLookUiState.selectingBriefingSegment(index: Int): FirstLookUiState {
    if (isDrivingRestricted) return this
    val segments = route?.segments ?: return this
    if (segments.isEmpty()) return this
    return copy(
        screen = FirstLookScreen.BRIEFING,
        segmentIndex = index.coerceIn(segments.indices),
    )
}

internal fun FirstLookUiState.previousBriefingSegment(): FirstLookUiState =
    if (isDrivingRestricted) this else copy(segmentIndex = (segmentIndex - 1).coerceAtLeast(0))

internal fun FirstLookUiState.advancingBriefing(): FirstLookUiState {
    if (isDrivingRestricted) return this
    val segments = route?.segments ?: return this
    if (segments.isEmpty()) return this
    return if (segmentIndex >= segments.lastIndex) copy(screen = FirstLookScreen.COMPLETE)
    else copy(segmentIndex = segmentIndex + 1)
}

internal fun FirstLookUiState.movingRoadViewIndex(segmentId: String, delta: Int): FirstLookUiState {
    if (isDrivingRestricted || delta == 0) return this
    val views = (roadViewLookups[segmentId] as? RoadViewLookupResult.Available)?.views.orEmpty()
    if (views.isEmpty()) return this
    val index = roadViewIndices[segmentId] ?: 0
    return copy(roadViewIndices = roadViewIndices + (segmentId to (index + delta).coerceIn(views.indices)))
}

internal fun FirstLookUiState.enteringRoadViewTest(): FirstLookUiState =
    if (isDrivingRestricted) this else copy(screen = FirstLookScreen.ROADVIEW_TEST)

enum class FirstLookScreen { DESTINATION, SUMMARY, BRIEFING, COMPLETE, NAVIGATION, ROADVIEW_TEST }

class FirstLookViewModel(
    private val repository: RouteRepository = OsmRouteRepository(),
    private val roadViewProvider: RoadViewProvider = DemoRoadViewProvider(),
    private val speechOutput: BriefingSpeechOutput = NoopBriefingSpeechOutput(),
    private val restrictionSource: DrivingRestrictionSource = FakeDrivingRestrictionSource(),
) : ViewModel() {
    private val speechController = BriefingSpeechController(speechOutput)
    private val mutableState = MutableStateFlow(FirstLookUiState(voiceOutputAvailable = speechOutput.outputAvailable))
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            restrictionSource.isRestricted.collect { restricted ->
                mutableState.update { it.copy(isDrivingRestricted = restricted) }
                if (restricted) speechController.stop()
            }
        }
    }

    fun updateDestination(value: String) {
        if (state.value.isDrivingRestricted) return
        mutableState.update { it.copy(destinationInput = value.take(80), errorMessage = null) }
    }
    fun updateOrigin(value: String) {
        if (state.value.isDrivingRestricted) return
        mutableState.update { it.copy(originInput = value.take(80), errorMessage = null) }
    }
    fun searchRoute() {
        if (state.value.isDrivingRestricted || state.value.isSearchingRoute) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSearchingRoute = true, errorMessage = null) }
            try {
                val route = repository.preview(state.value.originInput, state.value.destinationInput)
                speechController.stop()
                mutableState.update {
                    it.copy(
                        route = route,
                        screen = FirstLookScreen.SUMMARY,
                        segmentIndex = 0,
                        roadViewLookups = emptyMap(),
                        roadViewIndices = emptyMap(),
                        isSearchingRoute = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(isSearchingRoute = false, errorMessage = failure.message ?: "실제 경로와 교차로를 분석하지 못했습니다.") }
            }
        }
    }
    fun startBriefing() {
        if (state.value.isDrivingRestricted) return
        transition { it.copy(screen = FirstLookScreen.BRIEFING, segmentIndex = 0) }
        announceCurrentSegment()
    }

    fun selectSegment(index: Int) {
        if (state.value.isDrivingRestricted) return
        transition { it.selectingBriefingSegment(index) }
        announceCurrentSegment()
    }

    fun openSegmentRoadView(index: Int) {
        if (state.value.isDrivingRestricted) return
        val route = state.value.route ?: return
        if (index !in route.segments.indices) return
        speechController.stop()
        transition { it.copy(screen = FirstLookScreen.ROADVIEW_TEST, segmentIndex = index) }
    }

    fun openSelectedRoadView() = openSegmentRoadView(state.value.segmentIndex)

    fun previousSegment() {
        if (state.value.isDrivingRestricted) return
        transition { it.previousBriefingSegment() }
        announceCurrentSegment()
    }

    fun nextSegment() {
        if (state.value.isDrivingRestricted) return
        transition { it.advancingBriefing() }
        announceCurrentSegment()
    }

    fun startNavigation() {
        speechController.stop()
        transition { it.copy(screen = FirstLookScreen.NAVIGATION) }
    }

    fun openRoadViewTest() {
        if (state.value.isDrivingRestricted) return
        speechController.stop()
        transition { it.enteringRoadViewTest() }
    }

    fun closeRoadViewTest() {
        transition { it.copy(screen = if (it.route == null) FirstLookScreen.DESTINATION else FirstLookScreen.SUMMARY) }
    }

    fun replay() {
        if (state.value.isDrivingRestricted) return
        transition { it.copy(screen = FirstLookScreen.BRIEFING, segmentIndex = 0) }
        announceCurrentSegment()
    }

    fun toggleVoice() {
        val enabled = !state.value.voiceEnabled
        if (!enabled) speechController.stop()
        mutableState.update { it.copy(voiceEnabled = enabled) }
        if (enabled) announceCurrentSegment()
    }

    fun backToSummary() {
        speechController.stop()
        transition { it.copy(screen = FirstLookScreen.SUMMARY) }
    }

    fun nextRoadView() = changeRoadViewIndex(delta = 1)
    fun previousRoadView() = changeRoadViewIndex(delta = -1)
    fun retryRoadView() {
        if (state.value.isDrivingRestricted) return
        val segment = currentSegment() ?: return
        viewModelScope.launch { loadRoadViews(segment.id) }
    }

    /** Explicitly labeled demo toggle; it never changes the actual vehicle restriction source. */
    fun toggleDemoDrivingRestriction() {
        (restrictionSource as? FakeDrivingRestrictionSource)?.let { fake ->
            fake.setRestricted(!state.value.isDrivingRestricted)
        }
    }

    private fun transition(transform: (FirstLookUiState) -> FirstLookUiState) {
        mutableState.update { current ->
            val next = transform(current)
            if (next.screen != current.screen || next.segmentIndex != current.segmentIndex) speechController.stop()
            next
        }
    }

    private fun announceCurrentSegment() {
        val current = state.value
        val segment = currentSegment() ?: return
        if (current.route == null) return
        viewModelScope.launch {
            speechController.announce(
                text = "${current.segmentIndex + 1}번째 구간입니다. ${segment.type.label}. ${segment.guidance}",
                enabled = current.voiceEnabled,
                restricted = current.isDrivingRestricted,
                briefingVisible = current.screen == FirstLookScreen.BRIEFING,
            )
        }
    }

    private fun currentSegment() = state.value.route?.segments?.getOrNull(state.value.segmentIndex)

    private fun changeRoadViewIndex(delta: Int) {
        if (state.value.isDrivingRestricted) return
        val segmentId = currentSegment()?.id ?: return
        mutableState.update { it.movingRoadViewIndex(segmentId, delta) }
    }

    private suspend fun loadRoadViews(segmentId: String) {
        val requestedRoute = state.value.route ?: return
        val segment = requestedRoute.segments.firstOrNull { it.id == segmentId } ?: return
        val result = try {
            roadViewProvider.findNearestViews(segment, radiusMeters = 300)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            RoadViewLookupResult.Unavailable(ai.firstlook.app.data.RoadViewUnavailableReason.PROVIDER_ERROR)
        }
        mutableState.update { current ->
            if (current.route?.routeId != requestedRoute.routeId) return@update current
            current.copy(
                roadViewLookups = current.roadViewLookups + (segmentId to result),
                roadViewIndices = current.roadViewIndices + (segmentId to 0),
            )
        }
    }

    override fun onCleared() {
        speechController.stop()
        super.onCleared()
    }
}

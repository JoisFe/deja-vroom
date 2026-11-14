package ai.dejavroom.app.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.dejavroom.app.data.OsmRouteRepository
import ai.dejavroom.app.data.DemoRoadViewProvider
import ai.dejavroom.app.data.DrivingRestrictionSource
import ai.dejavroom.app.data.FakeDrivingRestrictionSource
import ai.dejavroom.app.data.NoopBriefingSpeechOutput
import ai.dejavroom.app.data.BriefingSpeechOutput
import ai.dejavroom.app.data.BriefingSpeechController
import ai.dejavroom.app.data.RoadViewLookupResult
import ai.dejavroom.app.data.RoadViewProvider
import ai.dejavroom.app.data.RoadViewTarget
import ai.dejavroom.app.data.RoadViewUnavailableReason
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.data.RouteRepository
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.Place
import ai.dejavroom.app.model.PlaceInput
import ai.dejavroom.app.model.SearchField
import ai.dejavroom.app.data.InMemoryRecentSearchStore
import ai.dejavroom.app.data.PhotonPlaceSearchRepository
import ai.dejavroom.app.data.PlaceSearchRepository
import ai.dejavroom.app.data.RecentSearchStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DejaVroomUiState(
    val originInput: String = "수원시 영통구",
    val destinationInput: String = "강남역",
    val route: RouteSnapshot? = null,
    /** How many points the driver asked to see. */
    val displayCount: Int = DEFAULT_DISPLAY_COUNT,
    /** Position in [shownSegments], not in the full route. */
    val segmentIndex: Int = 0,
    val screen: DejaVroomScreen = DejaVroomScreen.DESTINATION,
    val voiceEnabled: Boolean = true,
    val voiceOutputAvailable: Boolean = false,
    val errorMessage: String? = null,
    val roadViewLookups: Map<String, RoadViewLookupResult> = emptyMap(),
    val roadViewIndices: Map<String, Int> = emptyMap(),
    val isDrivingRestricted: Boolean = false,
    val isSearchingRoute: Boolean = false,
    val originPlace: Place? = null,
    val destinationPlace: Place? = null,
    val activeField: SearchField? = null,
    val suggestions: SuggestionState = SuggestionState(),
    val recentSearches: List<Place> = emptyList(),
) {
    /**
     * The points on screen: the [displayCount] most important ones, nearest to the origin first. Cards,
     * their numbers and the previous/next buttons all walk this one list, so "2 / 3" means the same everywhere.
     */
    val shownSegments: List<BriefingSegment>
        get() = route?.mostImportantSegments(displayCount).orEmpty()

    companion object {
        const val DEFAULT_DISPLAY_COUNT = 3
        val DISPLAY_COUNT_CHOICES = listOf(3, 5)
    }
}

enum class SuggestionStatus { IDLE, LOADING, RESULTS, EMPTY, ERROR }

/** Suggestions for one field. Previous [items] stay visible while loading so the list does not jump. */
data class SuggestionState(
    val field: SearchField? = null,
    val query: String = "",
    val status: SuggestionStatus = SuggestionStatus.IDLE,
    val items: List<Place> = emptyList(),
)

internal fun DejaVroomUiState.inputOf(field: SearchField) =
    if (field == SearchField.ORIGIN) originInput else destinationInput

/** Back to the search screen with both inputs kept, so the user can tweak one field and search again. */
internal fun DejaVroomUiState.editingRoute(): DejaVroomUiState =
    if (isDrivingRestricted) this
    else copy(screen = DejaVroomScreen.DESTINATION, activeField = null, suggestions = SuggestionState(), errorMessage = null)

internal fun DejaVroomUiState.swappingFields(): DejaVroomUiState = copy(
    originInput = destinationInput, destinationInput = originInput,
    originPlace = destinationPlace, destinationPlace = originPlace,
    suggestions = SuggestionState(), errorMessage = null,
)

internal fun DejaVroomUiState.selectingBriefingSegment(index: Int): DejaVroomUiState {
    if (isDrivingRestricted) return this
    val segments = shownSegments
    if (segments.isEmpty()) return this
    return copy(
        screen = DejaVroomScreen.BRIEFING,
        segmentIndex = index.coerceIn(segments.indices),
    )
}

internal fun DejaVroomUiState.previousBriefingSegment(): DejaVroomUiState =
    if (isDrivingRestricted) this else copy(segmentIndex = (segmentIndex - 1).coerceAtLeast(0))

internal fun DejaVroomUiState.advancingBriefing(): DejaVroomUiState {
    if (isDrivingRestricted) return this
    val segments = shownSegments
    if (segments.isEmpty()) return this
    return if (segmentIndex >= segments.lastIndex) copy(screen = DejaVroomScreen.COMPLETE)
    else copy(segmentIndex = segmentIndex + 1)
}

internal fun DejaVroomUiState.movingRoadViewIndex(segmentId: String, delta: Int): DejaVroomUiState {
    if (isDrivingRestricted || delta == 0) return this
    val views = (roadViewLookups[segmentId] as? RoadViewLookupResult.Available)?.views.orEmpty()
    if (views.isEmpty()) return this
    val index = roadViewIndices[segmentId] ?: 0
    return copy(roadViewIndices = roadViewIndices + (segmentId to (index + delta).coerceIn(views.indices)))
}

internal fun DejaVroomUiState.enteringRoadViewTest(): DejaVroomUiState =
    if (isDrivingRestricted) this else copy(screen = DejaVroomScreen.ROADVIEW_TEST)

enum class DejaVroomScreen { DESTINATION, SUMMARY, BRIEFING, COMPLETE, NAVIGATION, ROADVIEW_TEST }

@OptIn(FlowPreview::class)
class DejaVroomViewModel(
    private val repository: RouteRepository = OsmRouteRepository(),
    private val roadViewProvider: RoadViewProvider = DemoRoadViewProvider(),
    private val speechOutput: BriefingSpeechOutput = NoopBriefingSpeechOutput(),
    private val restrictionSource: DrivingRestrictionSource = FakeDrivingRestrictionSource(),
    private val placeSearch: PlaceSearchRepository = PhotonPlaceSearchRepository(),
    private val recentSearchStore: RecentSearchStore = InMemoryRecentSearchStore(),
    private val suggestionDebounceMillis: Long = 300,
) : ViewModel() {
    private val speechController = BriefingSpeechController(speechOutput)
    private val mutableState = MutableStateFlow(
        DejaVroomUiState(voiceOutputAvailable = speechOutput.outputAvailable, recentSearches = recentSearchStore.all()),
    )
    val state = mutableState.asStateFlow()
    private val suggestionQueries = MutableStateFlow<Pair<SearchField, String>?>(null)

    init {
        viewModelScope.launch {
            suggestionQueries
                .debounce(suggestionDebounceMillis)
                .distinctUntilChanged()
                .collectLatest { request -> loadSuggestions(request) }
        }
        viewModelScope.launch {
            restrictionSource.isRestricted.collect { restricted ->
                mutableState.update { it.copy(isDrivingRestricted = restricted) }
                if (restricted) speechController.stop()
            }
        }
    }

    fun updateDestination(value: String) = updateField(SearchField.DESTINATION, value)
    fun updateOrigin(value: String) = updateField(SearchField.ORIGIN, value)

    fun updateField(field: SearchField, value: String) {
        if (state.value.isDrivingRestricted) return
        val text = value.take(80)
        mutableState.update {
            // Typing invalidates a previously picked place for that field.
            when (field) {
                SearchField.ORIGIN -> it.copy(originInput = text, originPlace = null)
                SearchField.DESTINATION -> it.copy(destinationInput = text, destinationPlace = null)
            }.copy(activeField = field, errorMessage = null)
        }
        requestSuggestions(field, text)
    }

    fun focusField(field: SearchField) {
        if (state.value.isDrivingRestricted) return
        mutableState.update { it.copy(activeField = field) }
        requestSuggestions(field, state.value.inputOf(field))
    }

    fun dismissSuggestions() {
        suggestionQueries.value = null
        mutableState.update { it.copy(activeField = null, suggestions = SuggestionState()) }
    }

    fun selectPlace(place: Place) {
        val field = state.value.activeField ?: SearchField.DESTINATION
        selectPlace(field, place)
    }

    fun selectPlace(field: SearchField, place: Place) {
        if (state.value.isDrivingRestricted) return
        suggestionQueries.value = null
        mutableState.update {
            when (field) {
                SearchField.ORIGIN -> it.copy(originInput = place.name, originPlace = place)
                SearchField.DESTINATION -> it.copy(destinationInput = place.name, destinationPlace = place)
            }.copy(activeField = null, suggestions = SuggestionState(), errorMessage = null)
        }
    }

    fun clearField(field: SearchField) = updateField(field, "")

    fun swapFields() {
        if (state.value.isDrivingRestricted) return
        suggestionQueries.value = null
        mutableState.update { it.swappingFields() }
    }

    fun removeRecent(place: Place) {
        val recents = recentSearchStore.remove(place)
        mutableState.update { it.copy(recentSearches = recents) }
    }

    fun clearRecents() {
        val recents = recentSearchStore.clear()
        mutableState.update { it.copy(recentSearches = recents) }
    }

    fun editRoute() {
        speechController.stop()
        suggestionQueries.value = null
        transition { it.editingRoute() }
    }

    private fun requestSuggestions(field: SearchField, text: String) {
        val query = text.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            suggestionQueries.value = null
            mutableState.update { it.copy(suggestions = SuggestionState(field = field, query = query)) }
            return
        }
        mutableState.update { current ->
            val keep = current.suggestions.takeIf { it.field == field }?.items.orEmpty()
            current.copy(suggestions = SuggestionState(field, query, SuggestionStatus.LOADING, keep))
        }
        suggestionQueries.value = field to query
    }

    private suspend fun loadSuggestions(request: Pair<SearchField, String>?) {
        val (field, query) = request ?: return
        val bias = if (field == SearchField.DESTINATION) state.value.originPlace?.coordinate else null
        val next = try {
            val places = placeSearch.suggest(query, bias)
            SuggestionState(field, query, if (places.isEmpty()) SuggestionStatus.EMPTY else SuggestionStatus.RESULTS, places)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SuggestionState(field, query, SuggestionStatus.ERROR)
        }
        mutableState.update { current ->
            // Drop results for a field or text the user has already moved away from.
            if (current.activeField != field || current.inputOf(field).trim() != query) current
            else current.copy(suggestions = next)
        }
    }
    fun searchRoute() {
        if (state.value.isDrivingRestricted || state.value.isSearchingRoute) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSearchingRoute = true, errorMessage = null) }
            try {
                val current = state.value
                val route = repository.preview(
                    PlaceInput(current.originInput, current.originPlace),
                    PlaceInput(current.destinationInput, current.destinationPlace),
                )
                speechController.stop()
                val searched = current.destinationPlace?.takeIf { it.name == current.destinationInput.trim() }
                    ?: Place(current.destinationInput.trim(), route.destination.takeIf { it != current.destinationInput.trim() }.orEmpty())
                val recents = recentSearchStore.add(searched)
                suggestionQueries.value = null
                mutableState.update {
                    it.copy(
                        route = route,
                        screen = DejaVroomScreen.SUMMARY,
                        segmentIndex = 0,
                        roadViewLookups = emptyMap(),
                        roadViewIndices = emptyMap(),
                        isSearchingRoute = false,
                        recentSearches = recents,
                        activeField = null,
                        suggestions = SuggestionState(),
                    )
                }
                route.segments.forEach { segment -> launch { loadRoadViews(segment.id) } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(isSearchingRoute = false, errorMessage = failure.message ?: "실제 경로와 교차로를 분석하지 못했습니다.") }
            }
        }
    }
    fun startBriefing() {
        if (state.value.isDrivingRestricted) return
        transition { it.copy(screen = DejaVroomScreen.BRIEFING, segmentIndex = 0) }
        announceCurrentSegment()
    }

    fun selectSegment(index: Int) {
        if (state.value.isDrivingRestricted) return
        transition { it.selectingBriefingSegment(index) }
        announceCurrentSegment()
    }

    fun openSegmentRoadView(index: Int) {
        if (state.value.isDrivingRestricted) return
        if (index !in state.value.shownSegments.indices) return
        speechController.stop()
        transition { it.copy(screen = DejaVroomScreen.ROADVIEW_TEST, segmentIndex = index) }
    }

    /** Shows the [count] most important points. The list changes, so the position starts over. */
    fun showSegmentCount(count: Int) {
        if (count !in DejaVroomUiState.DISPLAY_COUNT_CHOICES) return
        mutableState.update { it.copy(displayCount = count, segmentIndex = 0) }
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
        transition { it.copy(screen = DejaVroomScreen.NAVIGATION) }
    }

    fun openRoadViewTest() {
        if (state.value.isDrivingRestricted) return
        speechController.stop()
        transition { it.enteringRoadViewTest() }
        if (state.value.route == null) viewModelScope.launch { loadDeveloperFixtureView() }
    }

    fun closeRoadViewTest() {
        transition { it.copy(screen = if (it.route == null) DejaVroomScreen.DESTINATION else DejaVroomScreen.SUMMARY) }
    }

    fun replay() {
        if (state.value.isDrivingRestricted) return
        transition { it.copy(screen = DejaVroomScreen.BRIEFING, segmentIndex = 0) }
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
        transition { it.copy(screen = DejaVroomScreen.SUMMARY) }
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

    private fun transition(transform: (DejaVroomUiState) -> DejaVroomUiState) {
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
                briefingVisible = current.screen == DejaVroomScreen.BRIEFING,
            )
        }
    }

    private fun currentSegment() = state.value.shownSegments.getOrNull(state.value.segmentIndex)

    private fun changeRoadViewIndex(delta: Int) {
        if (state.value.isDrivingRestricted) return
        val segmentId = currentSegment()?.id ?: return
        mutableState.update { it.movingRoadViewIndex(segmentId, delta) }
    }

    private suspend fun loadRoadViews(segmentId: String) {
        val requestedRoute = state.value.route ?: return
        val segment = requestedRoute.segments.firstOrNull { it.id == segmentId } ?: return
        val result = segment.roadViewTarget()?.let { lookupSafely(it) } ?: RoadViewLookupResult.NoCoverage
        mutableState.update { current ->
            if (current.route?.routeId != requestedRoute.routeId) return@update current
            current.copy(
                roadViewLookups = current.roadViewLookups + (segmentId to result),
                roadViewIndices = current.roadViewIndices + (segmentId to 0),
            )
        }
    }

    /** Developer entry without a route: the reviewed Hannam-daero junction used by the Roadview fixture. */
    private suspend fun loadDeveloperFixtureView() {
        val result = lookupSafely(RoadViewTarget(DEVELOPER_FIXTURE_COORDINATE, headingDegrees = null))
        mutableState.update { it.copy(roadViewLookups = it.roadViewLookups + (DEVELOPER_FIXTURE_KEY to result)) }
    }

    private suspend fun lookupSafely(target: RoadViewTarget): RoadViewLookupResult = try {
        roadViewProvider.lookup(target)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        RoadViewLookupResult.Unavailable(RoadViewUnavailableReason.PROVIDER_ERROR)
    }

    /** Road views start at the approach viewpoint, before the junction, facing the way the driver travels. */
    private fun BriefingSegment.roadViewTarget(): RoadViewTarget? =
        previewCoordinate?.let { RoadViewTarget(it, previewHeadingDegrees) }

    companion object {
        private const val MIN_QUERY_LENGTH = 2

        /** Key in [DejaVroomUiState.roadViewLookups] for the developer Roadview entry without a route. */
        const val DEVELOPER_FIXTURE_KEY = "developer-fixture"
        private val DEVELOPER_FIXTURE_COORDINATE = GeoCoordinate(37.537183, 127.005454)
    }

    override fun onCleared() {
        speechController.stop()
        super.onCleared()
    }
}

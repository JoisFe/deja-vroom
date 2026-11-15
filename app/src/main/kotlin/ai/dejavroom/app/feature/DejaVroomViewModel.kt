package ai.dejavroom.app.feature

import ai.dejavroom.app.data.CurrentLocation
import ai.dejavroom.app.data.CurrentLocationRepository
import ai.dejavroom.app.data.DemoRoadViewProvider
import ai.dejavroom.app.data.DrivingRestrictionSource
import ai.dejavroom.app.data.FakeDrivingRestrictionSource
import ai.dejavroom.app.data.InMemoryRecentSearchStore
import ai.dejavroom.app.data.InMemoryTripStore
import ai.dejavroom.app.data.NoCurrentLocationRepository
import ai.dejavroom.app.data.OsmRouteRepository
import ai.dejavroom.app.data.PhotonPlaceSearchRepository
import ai.dejavroom.app.data.PlaceSearchRepository
import ai.dejavroom.app.data.PlaceSearchUnavailableException
import ai.dejavroom.app.data.RecentSearchStore
import ai.dejavroom.app.data.RoadViewLookupResult
import ai.dejavroom.app.data.RoadViewProvider
import ai.dejavroom.app.data.RoadViewTarget
import ai.dejavroom.app.data.RoadViewUnavailableReason
import ai.dejavroom.app.data.RoutePreviewException
import ai.dejavroom.app.data.RoutePreviewFailure
import ai.dejavroom.app.data.RouteRepository
import ai.dejavroom.app.data.SearchSettings
import ai.dejavroom.app.data.TripStore
import ai.dejavroom.app.domain.Trip
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import ai.dejavroom.app.model.PlaceInput
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.SearchField
import ai.dejavroom.app.model.ServiceArea
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DejaVroomUiState(
    val originInput: String = "",
    val destinationInput: String = "",
    val route: RouteSnapshot? = null,
    /** Position in [shownSegments]. */
    val segmentIndex: Int = 0,
    val screen: DejaVroomScreen = DejaVroomScreen.DESTINATION,
    /** Why the last route search did not end in cards; cleared by the next edit or search. */
    val searchProblem: SearchProblem? = null,
    val roadViewLookups: Map<String, RoadViewLookupResult> = emptyMap(),
    val isDrivingRestricted: Boolean = false,
    val isSearchingRoute: Boolean = false,
    val originPlace: Place? = null,
    val destinationPlace: Place? = null,
    val activeField: SearchField? = null,
    val suggestions: SuggestionState = SuggestionState(),
    val recentSearches: List<Place> = emptyList(),
    /** The trip being driven; it knows which of the points shown are already behind the car. */
    val trip: Trip? = null,
    val isLocating: Boolean = false,
    /** Why the origin could not be set to where the car is; shown until the next attempt. */
    val locationNotice: LocationNotice? = null,
    /** Whose data the place suggestions are; shown under them. */
    val placeSearchAttribution: String = "",
    /** Why a config file was ignored and the defaults run instead; for the team's own builds, never the driver. */
    val configProblem: String? = null,
) {
    /**
     * The points on screen: every point picked for the route, nearest to the origin first. Cards, their
     * numbers and the previous/next buttons all walk this one list, so "2 / 6" means the same everywhere.
     */
    val shownSegments: List<BriefingSegment>
        get() = route?.segments.orEmpty()

    /** Ids of the points on screen that are already behind the car. */
    val passedSegmentIds: Set<String>
        get() = trip?.let { trip -> trip.leg.filter(trip::isPassed).mapTo(mutableSetOf()) { it.id } }.orEmpty()

    /** Position in [shownSegments] of the first point still ahead; 0 when nothing is behind yet. */
    val nextSegmentIndex: Int
        get() = passedSegmentIds.let { passed -> shownSegments.indexOfFirst { it.id !in passed }.coerceAtLeast(0) }

    /** The trip the search screen can pick up again: under way, and going where the destination field says. */
    val resumableTrip: Trip?
        get() = trip?.takeIf { !it.arrived && it.destination.name == destinationInput.trim() }
}

enum class SuggestionStatus { IDLE, LOADING, RESULTS, EMPTY, ERROR }

/** Why the car's own position could not be used as the origin. */
enum class LocationNotice {
    NO_NAVIGATION,
    NO_ANSWER,
    NO_POSITION,
    NO_PERMISSION,
    OUTSIDE_SERVICE_AREA,
    ;

    companion object {
        fun of(reason: CurrentLocation.Reason): LocationNotice = when (reason) {
            CurrentLocation.Reason.NO_NAVIGATION -> NO_NAVIGATION
            CurrentLocation.Reason.NO_ANSWER -> NO_ANSWER
            CurrentLocation.Reason.NO_POSITION -> NO_POSITION
            CurrentLocation.Reason.NO_PERMISSION -> NO_PERMISSION
        }
    }
}

/** Why the last route search did not end in cards. */
sealed interface SearchProblem {
    data class RouteFailed(val reason: RoutePreviewFailure) : SearchProblem

    /** The place search that was asked to resolve typed text cannot answer; [hint] says what to set up. */
    data class PlaceSearchUnavailable(val hint: String?) : SearchProblem
}

/** Suggestions for one field. Previous [items] stay visible while loading so the list does not jump. */
data class SuggestionState(
    val field: SearchField? = null,
    val query: String = "",
    val status: SuggestionStatus = SuggestionStatus.IDLE,
    val items: List<Place> = emptyList(),
    /** With [SuggestionStatus.ERROR]: what the place search said is wrong, when it said anything. */
    val problem: String? = null,
)

internal fun DejaVroomUiState.inputOf(field: SearchField) =
    if (field == SearchField.ORIGIN) originInput else destinationInput

/** Back to the search screen with both inputs kept, so the user can tweak one field and search again. */
internal fun DejaVroomUiState.editingRoute(): DejaVroomUiState = if (isDrivingRestricted) {
    this
} else {
    copy(
        screen = DejaVroomScreen.DESTINATION,
        activeField = null,
        suggestions = SuggestionState(),
        searchProblem = null,
    )
}

internal fun DejaVroomUiState.swappingFields(): DejaVroomUiState = copy(
    originInput = destinationInput,
    destinationInput = originInput,
    originPlace = destinationPlace,
    destinationPlace = originPlace,
    suggestions = SuggestionState(),
    searchProblem = null,
    locationNotice = null,
)

/** The origin field showing where the car is. */
internal fun DejaVroomUiState.startingFrom(here: Place): DejaVroomUiState =
    copy(originInput = here.name, originPlace = here, locationNotice = null, searchProblem = null)

/** True while the origin field still shows the car's position as it was read. */
internal val DejaVroomUiState.startsFromCurrentLocation: Boolean
    get() = originPlace?.live == true && originPlace.name == originInput.trim()

/**
 * "현재 위치" is a label, not text to edit, so the origin field opens empty when it is touched. Left
 * empty, it goes back to the label; typing anything lets go of the car's position.
 */
internal fun DejaVroomUiState.withCarOriginRestored(): DejaVroomUiState =
    if (originInput.isBlank() && originPlace?.live == true) copy(originInput = originPlace.name) else this

internal fun DejaVroomUiState.enteringRoadView(): DejaVroomUiState =
    if (isDrivingRestricted) this else copy(screen = DejaVroomScreen.ROAD_VIEW)

enum class DejaVroomScreen { DESTINATION, SUMMARY, NAVIGATION, ROAD_VIEW }

@OptIn(FlowPreview::class)
class DejaVroomViewModel(
    private val repository: RouteRepository = OsmRouteRepository(),
    private val roadViewProvider: RoadViewProvider = DemoRoadViewProvider(),
    private val restrictionSource: DrivingRestrictionSource = FakeDrivingRestrictionSource(),
    private val placeSearch: PlaceSearchRepository = PhotonPlaceSearchRepository(),
    private val recentSearchStore: RecentSearchStore = InMemoryRecentSearchStore(),
    private val suggestionDebounceMillis: Long = 300,
    private val currentLocation: CurrentLocationRepository = NoCurrentLocationRepository,
    private val tripStore: TripStore = InMemoryTripStore(),
    private val searchSettings: SearchSettings = SearchSettings(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val configProblem: String? = null,
) : ViewModel() {
    /** A trip that ended (arrived, or left alone for a day) is forgotten instead of being offered again. */
    private val savedTrip: Trip? = tripStore.load()?.takeUnless { it.isOver(nowMillis()) }.also {
        if (it == null) tripStore.clear()
    }
    private val mutableState = MutableStateFlow(
        DejaVroomUiState(
            originInput = searchSettings.origin,
            // A trip still under way comes back with its destination already filled in.
            destinationInput = savedTrip?.destination?.name ?: searchSettings.destination,
            destinationPlace = savedTrip?.destination,
            trip = savedTrip,
            recentSearches = recentSearchStore.all(),
            placeSearchAttribution = placeSearch.attribution,
            configProblem = configProblem,
        ),
    )

    /** Once the driver has chosen an origin, the car's position no longer replaces it on its own. */
    private var originChosen = false
    private var foregroundJob: Job? = null
    val state = mutableState.asStateFlow()
    private val suggestionQueries = MutableStateFlow<Pair<SearchField, String>?>(null)

    init {
        viewModelScope.launch {
            suggestionQueries
                .debounce(suggestionDebounceMillis)
                .distinctUntilChanged()
                .collectLatest { request -> loadSuggestions(request) }
        }
        // A place search that needs a moment to come up starts now, not at the first letter typed.
        viewModelScope.launch { placeSearch.prepare() }
        viewModelScope.launch {
            restrictionSource.isRestricted.collect { restricted ->
                mutableState.update { it.copy(isDrivingRestricted = restricted) }
            }
        }
    }

    fun updateDestination(value: String) = updateField(SearchField.DESTINATION, value)
    fun updateOrigin(value: String) = updateField(SearchField.ORIGIN, value)

    fun updateField(field: SearchField, value: String) {
        if (state.value.isDrivingRestricted) return
        val text = value.take(80)
        if (field == SearchField.ORIGIN) originChosen = true
        mutableState.update {
            // Typing invalidates a previously picked place for that field.
            when (field) {
                SearchField.ORIGIN -> it.copy(originInput = text, originPlace = null, locationNotice = null)
                SearchField.DESTINATION -> it.copy(destinationInput = text, destinationPlace = null)
            }.copy(activeField = field, searchProblem = null)
        }
        requestSuggestions(field, text)
    }

    fun focusField(field: SearchField) {
        if (state.value.isDrivingRestricted) return
        mutableState.update { current ->
            if (field == SearchField.ORIGIN && current.startsFromCurrentLocation) {
                current.copy(originInput = "", activeField = field)
            } else if (field == SearchField.ORIGIN) {
                current.copy(activeField = field)
            } else {
                current.withCarOriginRestored().copy(activeField = field)
            }
        }
        requestSuggestions(field, state.value.inputOf(field))
    }

    fun dismissSuggestions() {
        suggestionQueries.value = null
        mutableState.update { it.withCarOriginRestored().copy(activeField = null, suggestions = SuggestionState()) }
    }

    fun selectPlace(place: Place) {
        val field = state.value.activeField ?: SearchField.DESTINATION
        selectPlace(field, place)
    }

    fun selectPlace(field: SearchField, place: Place) {
        if (state.value.isDrivingRestricted) return
        suggestionQueries.value = null
        if (field == SearchField.ORIGIN) originChosen = true
        mutableState.update {
            when (field) {
                SearchField.ORIGIN -> it.copy(originInput = place.name, originPlace = place, locationNotice = null)

                SearchField.DESTINATION -> it.withCarOriginRestored().copy(
                    destinationInput = place.name,
                    destinationPlace = place,
                )
            }.copy(activeField = null, suggestions = SuggestionState(), searchProblem = null)
        }
    }

    fun clearField(field: SearchField) = updateField(field, "")

    fun swapFields() {
        if (state.value.isDrivingRestricted) return
        suggestionQueries.value = null
        originChosen = true
        mutableState.update { it.withCarOriginRestored().swappingFields() }
    }

    /**
     * The app came to the front: at the start, or again at a rest area. Where the car is now becomes the
     * origin (unless the driver chose one), and the trip under way learns what is behind the car. A
     * position that cannot be read changes nothing and says nothing; only [useCurrentLocation] reports it.
     */
    fun onAppForegrounded() {
        if (foregroundJob?.isActive == true) return
        foregroundJob = viewModelScope.launch {
            val here = (locate() as? Here.At)?.place ?: return@launch
            advanceTrip(here)
            mutableState.update { current ->
                val follows = current.startsFromCurrentLocation ||
                    (searchSettings.originSource == SearchSettings.OriginSource.CURRENT && !originChosen)
                val busy =
                    current.isDrivingRestricted || current.isSearchingRoute || current.activeField == SearchField.ORIGIN
                if (follows && !busy) current.startingFrom(here) else current
            }
        }
    }

    /** The driver asked for the car's position as the origin. */
    fun useCurrentLocation() {
        if (state.value.isDrivingRestricted || state.value.isLocating) return
        originChosen = true
        suggestionQueries.value = null
        viewModelScope.launch { startFromCurrentLocation() }
    }

    /** From the cards: plan the rest of the trip again from where the car is now. */
    fun replanFromCurrentLocation() {
        val current = state.value
        if (current.isDrivingRestricted || current.isLocating || current.isSearchingRoute) return
        originChosen = true
        viewModelScope.launch {
            // Either failure is explained on the search screen: no position to plan from, or no route found.
            if (!startFromCurrentLocation()) {
                transition { it.editingRoute() }
            } else if (!searchRouteNow()) {
                transition { it.copy(screen = DejaVroomScreen.DESTINATION) }
            }
        }
    }

    /** Puts the car's position in the origin field, or says why it cannot. True when it did. */
    private suspend fun startFromCurrentLocation(): Boolean {
        mutableState.update { it.copy(isLocating = true, locationNotice = null) }
        val here = locate()
        mutableState.update { current ->
            val closed = if (current.activeField == SearchField.ORIGIN) {
                current.copy(activeField = null, suggestions = SuggestionState())
            } else {
                current
            }
            when (here) {
                is Here.At -> closed.startingFrom(here.place)
                is Here.Unknown -> closed.copy(locationNotice = here.notice)
            }.copy(isLocating = false)
        }
        if (here is Here.At) advanceTrip(here.place)
        return here is Here.At
    }

    /** The car's position as a place a route can start from, or why there is none. */
    private sealed interface Here {
        data class At(val place: Place) : Here
        data class Unknown(val notice: LocationNotice) : Here
    }

    private suspend fun locate(): Here = when (val answer = currentLocation.current()) {
        is CurrentLocation.Unavailable -> Here.Unknown(LocationNotice.of(answer.reason))

        is CurrentLocation.Found ->
            if (answer.coordinate in ServiceArea) {
                Here.At(Place.currentLocation(answer.coordinate, answer.address))
            } else {
                Here.Unknown(LocationNotice.OUTSIDE_SERVICE_AREA)
            }
    }

    /** Tells the trip under way where the car has got to. */
    private fun advanceTrip(here: Place) {
        val trip = state.value.trip ?: return
        val advanced = trip.advancedTo(requireNotNull(here.coordinate))
        if (advanced == trip) return
        tripStore.save(advanced)
        mutableState.update { if (it.trip == trip) it.copy(trip = advanced) else it }
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
        val bias = if (field == SearchField.DESTINATION) state.value.originPlace?.searchBias else null
        val next = try {
            val places = placeSearch.suggest(query, bias)
            SuggestionState(
                field,
                query,
                if (places.isEmpty()) SuggestionStatus.EMPTY else SuggestionStatus.RESULTS,
                places,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unavailable: PlaceSearchUnavailableException) {
            SuggestionState(field, query, SuggestionStatus.ERROR, problem = unavailable.message)
        } catch (_: Exception) {
            SuggestionState(field, query, SuggestionStatus.ERROR)
        }
        mutableState.update { current ->
            // Drop results for a field or text the user has already moved away from.
            if (current.activeField != field || current.inputOf(field).trim() != query) {
                current
            } else {
                current.copy(suggestions = next)
            }
        }
    }
    fun searchRoute() {
        if (state.value.isDrivingRestricted || state.value.isSearchingRoute) return
        viewModelScope.launch { searchRouteNow() }
    }

    /** True when a route was found and its cards are on screen. */
    private suspend fun searchRouteNow(): Boolean {
        mutableState.update { it.withCarOriginRestored().copy(isSearchingRoute = true, searchProblem = null) }
        return try {
            // The car may have moved since its position was read, so a route from it starts with a fresh read.
            if (state.value.startsFromCurrentLocation) {
                (locate() as? Here.At)?.place?.let { here ->
                    advanceTrip(here)
                    mutableState.update { it.startingFrom(here) }
                }
            }
            val current = state.value
            val route = repository.preview(
                PlaceInput(current.originInput, current.originPlace),
                PlaceInput(current.destinationInput, current.destinationPlace),
            )
            val searched = current.destinationPlace?.takeIf { it.name == current.destinationInput.trim() }
                ?: Place(
                    current.destinationInput.trim(),
                    route.destination.takeIf { it != current.destinationInput.trim() }.orEmpty(),
                )
            // Where the car happens to be is not a place someone searched for.
            val recents = if (searched.live) recentSearchStore.all() else recentSearchStore.add(searched)
            val trip = tripFor(route, current)
            if (trip == null) tripStore.clear() else tripStore.save(trip)
            suggestionQueries.value = null
            mutableState.update {
                it.copy(
                    route = route,
                    trip = trip,
                    screen = DejaVroomScreen.SUMMARY,
                    segmentIndex = 0,
                    roadViewLookups = emptyMap(),
                    isSearchingRoute = false,
                    recentSearches = recents,
                    activeField = null,
                    suggestions = SuggestionState(),
                )
            }
            route.segments.forEach { segment -> viewModelScope.launch { loadRoadViews(segment.id) } }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: RoutePreviewException) {
            failedWith(SearchProblem.RouteFailed(failure.reason))
        } catch (unavailable: PlaceSearchUnavailableException) {
            failedWith(SearchProblem.PlaceSearchUnavailable(unavailable.message))
        } catch (_: Exception) {
            // Whatever else went wrong, its text is not for the driver.
            failedWith(SearchProblem.RouteFailed(RoutePreviewFailure.UNEXPECTED))
        }
    }

    private fun failedWith(problem: SearchProblem): Boolean {
        mutableState.update { it.copy(isSearchingRoute = false, searchProblem = problem) }
        return false
    }

    /**
     * The trip this route belongs to. Asked from where the car is, toward the same destination, it is the
     * next leg of the trip under way and what was passed stays passed. Anything else starts a new trip.
     * A route without real geometry (the curated demo) has no trip.
     */
    private fun tripFor(route: RouteSnapshot, searched: DejaVroomUiState): Trip? {
        val end = route.geometry.lastOrNull()?.takeIf { route.geometry.size >= 2 } ?: return null
        val here = searched.originPlace?.takeIf { searched.startsFromCurrentLocation }?.coordinate
        val underWay = searched.trip?.takeIf { here != null && !it.isOver(nowMillis()) && it.isGoingTo(end) }
        return underWay?.continuedWith(route.geometry, route.segments)
            ?: Trip.startedWith(Place(route.destination, coordinate = end), route.geometry, route.segments, nowMillis())
    }

    fun openSegmentRoadView(index: Int) {
        if (state.value.isDrivingRestricted) return
        if (index !in state.value.shownSegments.indices) return
        transition { it.copy(screen = DejaVroomScreen.ROAD_VIEW, segmentIndex = index) }
    }

    fun startNavigation() {
        transition { it.copy(screen = DejaVroomScreen.NAVIGATION) }
    }

    fun openDeveloperRoadView() {
        if (state.value.isDrivingRestricted) return
        transition { it.enteringRoadView() }
        if (state.value.route == null) viewModelScope.launch { loadDeveloperFixtureView() }
    }

    fun closeRoadView() {
        transition { it.copy(screen = if (it.route == null) DejaVroomScreen.DESTINATION else DejaVroomScreen.SUMMARY) }
    }

    fun backToSummary() {
        transition { it.copy(screen = DejaVroomScreen.SUMMARY) }
    }

    /** Explicitly labeled demo toggle; it never changes the actual vehicle restriction source. */
    fun toggleDemoDrivingRestriction() {
        (restrictionSource as? FakeDrivingRestrictionSource)?.let { fake ->
            fake.setRestricted(!state.value.isDrivingRestricted)
        }
    }

    private fun transition(transform: (DejaVroomUiState) -> DejaVroomUiState) {
        mutableState.update(transform)
    }

    private suspend fun loadRoadViews(segmentId: String) {
        val requestedRoute = state.value.route ?: return
        val segment = requestedRoute.segments.firstOrNull { it.id == segmentId } ?: return
        val result = segment.roadViewTarget()?.let { lookupSafely(it) } ?: RoadViewLookupResult.NoCoverage
        mutableState.update { current ->
            if (current.route?.routeId != requestedRoute.routeId) return@update current
            current.copy(roadViewLookups = current.roadViewLookups + (segmentId to result))
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
        currentLocation.release()
        placeSearch.release()
        super.onCleared()
    }
}

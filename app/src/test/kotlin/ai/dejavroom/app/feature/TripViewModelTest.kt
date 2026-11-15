package ai.dejavroom.app.feature

import ai.dejavroom.app.data.CurrentLocation
import ai.dejavroom.app.data.FakeCurrentLocationRepository
import ai.dejavroom.app.data.FakePlaceSearchRepository
import ai.dejavroom.app.data.InMemoryRecentSearchStore
import ai.dejavroom.app.data.InMemoryTripStore
import ai.dejavroom.app.data.PlaceSearchRepository
import ai.dejavroom.app.data.RouteRepository
import ai.dejavroom.app.data.SearchDefaults
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import ai.dejavroom.app.model.PlaceInput
import ai.dejavroom.app.model.RouteGeometryStatus
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.RouteSource
import ai.dejavroom.app.model.SearchField
import ai.dejavroom.app.model.SegmentType
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The rest-area story: the car's position as the origin, and what is already behind it on the trip. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** One road, 2 km due north (a step is about 50 m), with three places on it worth a preview. */
    private val road = (0..40).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }
    private fun at(index: Int, address: String = "") = CurrentLocation.Found(road[index], address)
    private val noNavigation = CurrentLocation.Unavailable(CurrentLocation.Reason.NO_NAVIGATION)

    /** Routes follow the road from wherever the origin is to its end; "코엑스" ends earlier than "강남역". */
    private inner class RoadRepository : RouteRepository {
        val origins = mutableListOf<PlaceInput>()
        var failNext = false

        override suspend fun preview(origin: String, destination: String) = error("string overload not expected")
        override suspend fun preview(origin: PlaceInput, destination: PlaceInput): RouteSnapshot {
            origins += origin
            if (failNext) error("경로를 찾지 못했습니다.")
            val from = origin.resolvedPlace?.coordinate?.let { start -> road.indexOfFirst { it.latitude >= start.latitude } } ?: 0
            val to = if (destination.text.trim() == "코엑스") 32 else 40
            val ahead = listOf(10, 20, 30).filter { it > from + 1 && it < to }
            return RouteSnapshot(
                origin = origin.text, destination = destination.text, durationMinutes = 10, distanceKm = 2,
                segments = ahead.mapIndexed { rank, index ->
                    BriefingSegment(
                        id = "from$from-$index", type = SegmentType.INTERSECTION, name = "${index * 50}m 갈림길", description = "d", guidance = "g",
                        distanceFromStartKm = (index - from) * 0.05, coordinate = road[index], selectionRank = rank + 1,
                    )
                },
                routeId = "road-$from-$to", source = RouteSource.EXTERNAL_ROUTE,
                geometryStatus = RouteGeometryStatus.VERIFIED, geometry = road.subList(from, to + 1),
            )
        }
    }

    private val routes = RoadRepository()
    private val location = FakeCurrentLocationRepository()
    private val trips = InMemoryTripStore()
    private val recents = InMemoryRecentSearchStore()
    private var now = 1_000_000L

    private fun viewModel(defaults: SearchDefaults = SearchDefaults()) = DejaVroomViewModel(
        repository = routes, placeSearch = FakePlaceSearchRepository(), recentSearchStore = recents,
        currentLocation = location, tripStore = trips, searchDefaults = defaults, nowMillis = { now },
    )

    @Test
    fun `the search screen opens with the configured places`() = runTest(dispatcher) {
        val state = viewModel(SearchDefaults(origin = "판교역", destination = "코엑스")).state.value
        assertEquals("판교역" to "코엑스", state.originInput to state.destinationInput)
        assertNull(state.trip)
    }

    @Test
    fun `when the app comes to the front the car's position becomes the origin`() = runTest(dispatcher) {
        location.next = at(0, "경기 수원시 영통구 매탄동")
        val vm = viewModel()
        vm.onAppForegrounded()
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(Place.CURRENT_LOCATION_NAME, state.originInput)
        assertEquals(Place.currentLocation(road[0], "경기 수원시 영통구 매탄동"), state.originPlace)
        assertTrue(state.startsFromCurrentLocation)
        assertNull(state.locationNotice)
    }

    @Test
    fun `without a position the configured origin stays and nothing is said`() = runTest(dispatcher) {
        location.next = noNavigation
        val vm = viewModel()
        vm.onAppForegrounded()
        advanceUntilIdle()
        assertEquals("수원시 영통구", vm.state.value.originInput)
        assertNull(vm.state.value.locationNotice)
    }

    @Test
    fun `an origin fixed in the config or chosen by the driver is not replaced`() = runTest(dispatcher) {
        location.next = at(0)
        val fixed = viewModel(SearchDefaults(originSource = SearchDefaults.OriginSource.PLACE))
        fixed.onAppForegrounded()
        advanceUntilIdle()
        assertEquals("수원시 영통구", fixed.state.value.originInput)

        val chosen = viewModel()
        chosen.updateOrigin("서울역")
        chosen.onAppForegrounded()
        advanceUntilIdle()
        assertEquals("서울역", chosen.state.value.originInput)
    }

    @Test
    fun `the current location button says why it cannot find the car`() = runTest(dispatcher) {
        val vm = viewModel()
        location.next = noNavigation
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertEquals(LocationNotice.NO_NAVIGATION, vm.state.value.locationNotice)
        assertEquals("수원시 영통구", vm.state.value.originInput)
        assertFalse(vm.state.value.isLocating)

        // A position outside the area routes can be planned in is refused too.
        location.next = CurrentLocation.Found(GeoCoordinate(37.42, -122.08))
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertEquals(LocationNotice.OUTSIDE_SERVICE_AREA, vm.state.value.locationNotice)

        location.next = at(3)
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertNull(vm.state.value.locationNotice)
        assertTrue(vm.state.value.startsFromCurrentLocation)
        // Typing over it lets go of the car's position.
        vm.updateOrigin("서울역")
        assertFalse(vm.state.value.startsFromCurrentLocation)
    }

    @Test
    fun `the current location label opens empty for typing and comes back when nothing is typed`() = runTest(dispatcher) {
        location.next = at(0, "경기 수원시 영통구")
        val vm = viewModel()
        vm.onAppForegrounded()
        advanceUntilIdle()

        // Touching the field: there is nothing to erase before typing or speaking a place.
        vm.focusField(SearchField.ORIGIN)
        assertEquals("", vm.state.value.originInput)
        assertFalse(vm.state.value.startsFromCurrentLocation)
        // Closing it without typing, or moving on to the destination, puts the label back.
        vm.dismissSuggestions()
        assertEquals(Place.CURRENT_LOCATION_NAME, vm.state.value.originInput)
        assertTrue(vm.state.value.startsFromCurrentLocation)
        vm.focusField(SearchField.ORIGIN)
        vm.focusField(SearchField.DESTINATION)
        assertTrue(vm.state.value.startsFromCurrentLocation)

        // Searching with the field still open starts from the car as well.
        vm.focusField(SearchField.ORIGIN)
        vm.searchRoute()
        advanceUntilIdle()
        assertEquals(road[0], routes.origins.single().resolvedPlace?.coordinate)

        // Typing lets go of the car's position, and so does clearing the field by hand.
        vm.editRoute()
        vm.focusField(SearchField.ORIGIN)
        vm.updateOrigin("서울역")
        vm.dismissSuggestions()
        assertEquals("서울역", vm.state.value.originInput)
        vm.useCurrentLocation()
        advanceUntilIdle()
        vm.clearField(SearchField.ORIGIN)
        vm.dismissSuggestions()
        assertEquals("", vm.state.value.originInput)
    }

    @Test
    fun `a route from the car's position starts with a fresh read of it`() = runTest(dispatcher) {
        location.next = at(0)
        val vm = viewModel()
        vm.onAppForegrounded()
        advanceUntilIdle()
        // The car moves on before the driver asks for the route.
        location.next = at(6, "달라진 주소")
        vm.searchRoute()
        advanceUntilIdle()
        assertEquals(road[6], routes.origins.single().resolvedPlace?.coordinate)
        assertEquals("달라진 주소", vm.state.value.originPlace?.address)
        assertEquals(Place.CURRENT_LOCATION_NAME, vm.state.value.route?.origin)
    }

    @Test
    fun `place suggestions are told only roughly where the car is`() = runTest(dispatcher) {
        val biases = mutableListOf<GeoCoordinate?>()
        val search = object : PlaceSearchRepository {
            override suspend fun suggest(query: String, bias: GeoCoordinate?): List<Place> = emptyList<Place>().also { biases += bias }
        }
        location.next = CurrentLocation.Found(GeoCoordinate(37.23861, 127.10572))
        val vm = DejaVroomViewModel(repository = routes, placeSearch = search, currentLocation = location, suggestionDebounceMillis = 0)
        vm.onAppForegrounded()
        advanceUntilIdle()
        vm.updateDestination("강남역")
        advanceUntilIdle()
        // To about 10 km, never the position itself.
        assertEquals(listOf<GeoCoordinate?>(GeoCoordinate(37.2, 127.1)), biases)
        // A place the driver picked is not a secret: it is passed on as it is.
        val seoulStation = FakePlaceSearchRepository.DEMO_PLACES[1]
        assertEquals(seoulStation.coordinate, seoulStation.searchBias)
    }

    @Test
    fun `a search starts a trip that remembers the points on screen`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.searchRoute()
        advanceUntilIdle()
        val trip = vm.state.value.trip!!
        assertEquals("강남역", trip.destination.name)
        assertEquals(vm.state.value.shownSegments.map { it.id }, trip.leg.map { it.id })
        assertEquals(trip, trips.load())
        assertEquals(emptySet<String>(), vm.state.value.passedSegmentIds)
        assertEquals(0, vm.state.value.nextSegmentIndex)
    }

    @Test
    fun `coming back to the app marks what is behind the car`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.searchRoute()
        advanceUntilIdle()
        // At a rest area 700 m in, the app comes to the front again with the cards still on screen.
        location.next = at(14)
        vm.onAppForegrounded()
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(setOf("from0-10"), state.passedSegmentIds)
        assertEquals(1, state.nextSegmentIndex)
        assertEquals(DejaVroomScreen.SUMMARY, state.screen)
        assertEquals(state.trip, trips.load())
    }

    @Test
    fun `planning again from the rest area keeps what was passed and looks only ahead`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.searchRoute()
        advanceUntilIdle()
        location.next = at(14)
        vm.onAppForegrounded()
        advanceUntilIdle()

        vm.replanFromCurrentLocation()
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(DejaVroomScreen.SUMMARY, state.screen)
        assertEquals(Place.CURRENT_LOCATION_NAME, state.route?.origin)
        assertEquals(listOf("from14-20", "from14-30"), state.shownSegments.map { it.id })
        assertEquals(listOf("500m 갈림길"), state.trip!!.passed.map { it.name })
        assertEquals(emptySet<String>(), state.passedSegmentIds)
        assertEquals(state.trip, trips.load())
    }

    @Test
    fun `another destination or a typed origin starts a new trip`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.searchRoute()
        advanceUntilIdle()
        location.next = at(14)
        vm.useCurrentLocation()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.trip!!.passed.size)

        vm.updateDestination("코엑스")
        vm.searchRoute()
        advanceUntilIdle()
        assertEquals("코엑스", vm.state.value.trip!!.destination.name)
        assertEquals(0, vm.state.value.trip!!.passed.size)

        // Back to the first destination, but planned from a typed place: that is a new plan, not a continuation.
        location.next = at(24)
        vm.onAppForegrounded()
        advanceUntilIdle()
        vm.updateOrigin("수원시 영통구")
        vm.updateDestination("강남역")
        vm.searchRoute()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.trip!!.passed.size)
    }

    @Test
    fun `a trip under way comes back with its destination after the app was closed`() = runTest(dispatcher) {
        val first = viewModel()
        first.updateDestination("코엑스")
        first.searchRoute()
        advanceUntilIdle()

        // The app was closed on the way; it is opened again at a rest area an hour later.
        now += 3_600_000L
        location.next = at(14, "기흥휴게소")
        val reopened = viewModel()
        assertEquals("코엑스", reopened.state.value.destinationInput)
        assertEquals("코엑스", reopened.state.value.resumableTrip?.destination?.name)
        reopened.onAppForegrounded()
        advanceUntilIdle()
        assertEquals(1, reopened.state.value.trip!!.passed.size)
        reopened.searchRoute()
        advanceUntilIdle()
        assertEquals(listOf("500m 갈림길"), reopened.state.value.trip!!.passedBefore.map { it.name })
        assertEquals(listOf("from14-20", "from14-30"), reopened.state.value.shownSegments.map { it.id })
    }

    @Test
    fun `a trip that ended is forgotten`() = runTest(dispatcher) {
        val first = viewModel()
        first.updateDestination("코엑스")
        first.searchRoute()
        advanceUntilIdle()
        now += 25 * 3_600_000L
        val nextDay = viewModel()
        assertEquals("강남역", nextDay.state.value.destinationInput)
        assertNull(nextDay.state.value.trip)
        assertNull(trips.load())
    }

    @Test
    fun `when planning again fails the search screen says why`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.searchRoute()
        advanceUntilIdle()
        location.next = noNavigation
        vm.replanFromCurrentLocation()
        advanceUntilIdle()
        assertEquals(DejaVroomScreen.DESTINATION, vm.state.value.screen)
        assertEquals(LocationNotice.NO_NAVIGATION, vm.state.value.locationNotice)

        location.next = at(14)
        routes.failNext = true
        vm.replanFromCurrentLocation()
        advanceUntilIdle()
        assertEquals(DejaVroomScreen.DESTINATION, vm.state.value.screen)
        assertEquals("경로를 찾지 못했습니다.", vm.state.value.errorMessage)
        assertFalse(vm.state.value.isSearchingRoute)
    }

    @Test
    fun `where the car happens to be is never kept as a recent search`() = runTest(dispatcher) {
        location.next = at(0)
        val vm = viewModel()
        vm.useCurrentLocation()
        advanceUntilIdle()
        vm.updateDestination("서울역")
        vm.swapFields()
        vm.searchRoute()
        advanceUntilIdle()
        assertEquals(DejaVroomScreen.SUMMARY, vm.state.value.screen)
        assertEquals(emptyList<Place>(), recents.all())
    }

    @Test
    fun `showing more points keeps the trip in step with the screen`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.searchRoute()
        advanceUntilIdle()
        vm.showSegmentCount(5)
        assertEquals(vm.state.value.shownSegments.map { it.id }, vm.state.value.trip!!.leg.map { it.id })
        assertEquals(vm.state.value.trip, trips.load())
    }

    @Test
    fun `the car is asked once each time the app comes to the front and let go with the screen`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(0, location.askCount)
        vm.onAppForegrounded()
        vm.onAppForegrounded() // a second call while the first is still asking is not a second question
        advanceUntilIdle()
        assertEquals(1, location.askCount)
        assertFalse(location.released)
        ViewModelStore().apply { put("vm", vm) }.clear()
        assertTrue(location.released)
    }
}

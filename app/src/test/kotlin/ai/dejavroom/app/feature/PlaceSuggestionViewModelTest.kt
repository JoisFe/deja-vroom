package ai.dejavroom.app.feature

import ai.dejavroom.app.data.FakePlaceSearchRepository
import ai.dejavroom.app.data.InMemoryRecentSearchStore
import ai.dejavroom.app.data.PlaceSearchRepository
import ai.dejavroom.app.data.RouteRepository
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import ai.dejavroom.app.model.PlaceInput
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.SearchField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSuggestionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private class RecordingRouteRepository : RouteRepository {
        var lastOrigin: PlaceInput? = null
        var lastDestination: PlaceInput? = null
        override suspend fun preview(origin: PlaceInput, destination: PlaceInput): RouteSnapshot {
            lastOrigin = origin
            lastDestination = destination
            return RouteSnapshot(origin.text, destination.text, 10, 5, segments = emptyList())
        }
    }

    private fun viewModel(
        search: PlaceSearchRepository = FakePlaceSearchRepository(),
        routes: RouteRepository = RecordingRouteRepository(),
        recents: InMemoryRecentSearchStore = InMemoryRecentSearchStore(),
    ) = DejaVroomViewModel(
        repository = routes,
        placeSearch = search,
        recentSearchStore = recents,
        suggestionDebounceMillis = 300,
    )

    @Test
    fun `typing quickly sends only the last query after the debounce`() = runTest(dispatcher) {
        val search = FakePlaceSearchRepository()
        val vm = viewModel(search)
        vm.updateDestination("강")
        vm.updateDestination("강남")
        advanceTimeBy(100)
        vm.updateDestination("강남역")
        advanceTimeBy(299)
        assertEquals(emptyList<String>(), search.queries)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("강남역"), search.queries)
        assertEquals(SuggestionStatus.RESULTS, vm.state.value.suggestions.status)
        assertEquals("강남역", vm.state.value.suggestions.items.single().name)
    }

    @Test
    fun `one character does not hit the network`() = runTest(dispatcher) {
        val search = FakePlaceSearchRepository()
        val vm = viewModel(search)
        vm.updateDestination("강")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), search.queries)
        assertEquals(SuggestionStatus.IDLE, vm.state.value.suggestions.status)
    }

    @Test
    fun `a slow stale request is cancelled and never shown`() = runTest(dispatcher) {
        val slowFirst = object : PlaceSearchRepository {
            override suspend fun suggest(query: String, bias: GeoCoordinate?): List<Place> {
                if (query == "서울") delay(5_000)
                return listOf(Place("$query 결과", coordinate = GeoCoordinate(37.5, 127.0)))
            }
        }
        val vm = viewModel(slowFirst)
        vm.updateDestination("서울")
        advanceTimeBy(400) // first request is now in flight
        vm.updateDestination("서울역")
        advanceUntilIdle()
        assertEquals(listOf("서울역 결과"), vm.state.value.suggestions.items.map { it.name })
    }

    @Test
    fun `picking a suggestion is passed to the route search without re-geocoding`() = runTest(dispatcher) {
        val routes = RecordingRouteRepository()
        val recents = InMemoryRecentSearchStore()
        val vm = viewModel(routes = routes, recents = recents)
        val gangnam = FakePlaceSearchRepository.DEMO_PLACES.first()
        vm.selectPlace(SearchField.DESTINATION, gangnam)
        vm.searchRoute()
        advanceUntilIdle()
        assertSame(gangnam, routes.lastDestination?.resolvedPlace)
        assertEquals(gangnam, recents.all().first())
        assertEquals(DejaVroomScreen.SUMMARY, vm.state.value.screen)
    }

    @Test
    fun `editing the text drops the picked place`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectPlace(SearchField.DESTINATION, FakePlaceSearchRepository.DEMO_PLACES.first())
        vm.updateDestination("강남역 2번 출구")
        assertNull(vm.state.value.destinationPlace)
    }

    @Test
    fun `swap exchanges both text and picked places`() = runTest(dispatcher) {
        val vm = viewModel()
        val seoul = FakePlaceSearchRepository.DEMO_PLACES[1]
        vm.selectPlace(SearchField.ORIGIN, seoul)
        vm.updateDestination("코엑스")
        vm.swapFields()
        assertEquals("코엑스", vm.state.value.originInput)
        assertEquals(seoul, vm.state.value.destinationPlace)
        assertNull(vm.state.value.originPlace)
    }

    @Test
    fun `edit route returns to search with inputs kept`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.updateOrigin("서울역")
        vm.updateDestination("코엑스")
        vm.searchRoute()
        advanceUntilIdle()
        vm.editRoute()
        assertEquals(DejaVroomScreen.DESTINATION, vm.state.value.screen)
        assertEquals("서울역", vm.state.value.originInput)
        assertEquals("코엑스", vm.state.value.destinationInput)
    }

    @Suppress("unused")
    private fun TestScope.unused() = Unit
}

package ai.dejavroom.app.feature

import ai.dejavroom.app.data.FakePlaceSearchRepository
import ai.dejavroom.app.data.PlaceSearchUnavailableException
import ai.dejavroom.app.data.RoutePreviewException
import ai.dejavroom.app.data.RoutePreviewFailure
import ai.dejavroom.app.data.RouteRepository
import ai.dejavroom.app.model.PlaceInput
import ai.dejavroom.app.model.RouteSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/** A route search that fails leaves a typed reason in the state, never the text of the error. */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteSearchProblemTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FailingRouteRepository(private val failure: Exception) : RouteRepository {
        override suspend fun preview(origin: PlaceInput, destination: PlaceInput): RouteSnapshot = throw failure
    }

    private fun searchFailingWith(failure: Exception): DejaVroomUiState {
        val vm = DejaVroomViewModel(repository = FailingRouteRepository(failure), placeSearch = FakePlaceSearchRepository())
        vm.searchRoute()
        dispatcher.scheduler.advanceUntilIdle()
        return vm.state.value
    }

    @Test
    fun `a typed route failure reaches the state as it is`() = runTest(dispatcher) {
        val state = searchFailingWith(RoutePreviewException(RoutePreviewFailure.OFFLINE))
        assertEquals(SearchProblem.RouteFailed(RoutePreviewFailure.OFFLINE), state.searchProblem)
        assertFalse(state.isSearchingRoute)
        assertEquals(DejaVroomScreen.DESTINATION, state.screen)
    }

    @Test
    fun `any other error becomes an unexpected failure and its text is nowhere in the state`() = runTest(dispatcher) {
        val text = "OSRM 서비스가 HTTP 503 응답을 반환했습니다."
        val state = searchFailingWith(IllegalStateException(text))
        assertEquals(SearchProblem.RouteFailed(RoutePreviewFailure.UNEXPECTED), state.searchProblem)
        assertFalse(state.toString().contains(text))
        assertFalse(state.toString().contains("OSRM"))
    }

    @Test
    fun `a place search that is not set up keeps its setup hint unchanged`() = runTest(dispatcher) {
        val hint = "Kakao 장소 검색 주소가 설정되지 않았습니다 · docs/place-search-setup.md 참고"
        val state = searchFailingWith(PlaceSearchUnavailableException(hint))
        assertEquals(SearchProblem.PlaceSearchUnavailable(hint), state.searchProblem)
    }
}

package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.PlaceInput
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/** Whatever the geocoder or router throws, the route preview fails with a reason the screen can word. */
class OsmRouteRepositoryTest {
    private val anywhere = DestinationGeocoder { query -> GeocodedDestination(GeoCoordinate(37.5, 127.0), query) }
    private val noRouter = DrivingRouteClient { _, _ -> error("router must not be asked") }

    private suspend fun failureOf(
        repository: OsmRouteRepository,
        origin: String = "수원역",
        destination: String = "강남역",
    ): Throwable? = runCatching { repository.preview(PlaceInput(origin), PlaceInput(destination)) }.exceptionOrNull()

    private fun Throwable?.reason(): RoutePreviewFailure? = (this as? RoutePreviewException)?.reason

    @Test
    fun `a router that cannot reach its server means the car is offline`() = runTest {
        val offline = DrivingRouteClient { _, _ -> throw UnknownHostException("router.project-osrm.org") }
        assertEquals(
            RoutePreviewFailure.OFFLINE,
            failureOf(OsmRouteRepository(geocoder = anywhere, router = offline)).reason(),
        )
    }

    @Test
    fun `a geocoder that breaks in any other way is an unexpected failure`() = runTest {
        val broken = DestinationGeocoder { error("Nominatim 응답을 읽을 수 없습니다.") }
        assertEquals(
            RoutePreviewFailure.UNEXPECTED,
            failureOf(OsmRouteRepository(geocoder = broken, router = noRouter)).reason(),
        )
    }

    @Test
    fun `a blank destination is asked for before anything is searched`() = runTest {
        assertEquals(
            RoutePreviewFailure.MISSING_DESTINATION,
            failureOf(OsmRouteRepository(geocoder = anywhere, router = noRouter), destination = "  ").reason(),
        )
    }

    @Test
    fun `the place search setup hint passes through untouched`() = runTest {
        val unconfigured =
            PlaceSearchGeocoder(UnconfiguredPlaceSearchRepository(KakaoPlaceSearchRepository.SETUP_MESSAGE))
        val failure = failureOf(OsmRouteRepository(geocoder = unconfigured, router = noRouter))
        assertTrue(failure is PlaceSearchUnavailableException)
        assertEquals(KakaoPlaceSearchRepository.SETUP_MESSAGE, failure?.message)
    }
}

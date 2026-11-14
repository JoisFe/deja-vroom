package ai.firstlook.app.data

import ai.firstlook.app.model.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Test

class OsmRouteRepositoryTest {
    @Test
    fun `preview geocodes the selected origin and destination before requesting route`() {
        val places = mapOf(
            "서울역" to GeocodedDestination(GeoCoordinate(37.5547, 126.9707), "서울역"),
            "강남역" to GeocodedDestination(GeoCoordinate(37.4979, 127.0276), "강남역"),
        )
        val resolvedQueries = mutableListOf<String>()
        var requestedOrigin: GeoCoordinate? = null
        var requestedDestination: GeoCoordinate? = null
        val geometry = listOf(
            GeoCoordinate(37.5547, 126.9707),
            GeoCoordinate(37.53, 126.99),
            GeoCoordinate(37.51, 127.01),
            GeoCoordinate(37.4979, 127.0276),
        )
        val repository = OsmRouteRepository(
            geocoder = DestinationGeocoder { query ->
                resolvedQueries += query
                requireNotNull(places[query])
            },
            router = DrivingRouteClient { origin, destination ->
                requestedOrigin = origin
                requestedDestination = destination
                DrivingRouteResult(
                    10_000.0,
                    1_200.0,
                    geometry,
                    listOf(
                        RouteIntersectionObservation(
                            coordinate = geometry[1],
                            branchCount = 4,
                            maneuverType = "fork",
                            roadName = "테스트 도로",
                            highwayJunction = true,
                        ),
                    ),
                )
            },
        )

        val route = kotlinx.coroutines.runBlocking { repository.preview("서울역", "강남역") }

        assertEquals(listOf("서울역", "강남역"), resolvedQueries)
        assertEquals(places.getValue("서울역").coordinate, requestedOrigin)
        assertEquals(places.getValue("강남역").coordinate, requestedDestination)
        assertEquals("서울역", route.origin)
        assertEquals("강남역", route.destination)
    }
}

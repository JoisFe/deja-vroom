package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.pleos.playground.navi.data.CurrentLocationInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** What NaviHelper reports is checked before the app uses it. The SDK call itself needs a Pleos vehicle. */
class PleosCurrentLocationTest {
    private fun info(latitude: Double, longitude: Double, address: String = "") =
        CurrentLocationInfo(latitude, longitude, 0.0, address, "", null)

    private val noPosition = CurrentLocation.Unavailable(CurrentLocation.Reason.NO_POSITION)

    @Test
    fun `a position from the navigation becomes a coordinate and its address`() {
        assertEquals(
            CurrentLocation.Found(GeoCoordinate(37.2386, 127.1057), "경기 용인시 기흥구 신갈동"),
            info(37.2386, 127.1057, "  경기 용인시 기흥구 신갈동 ").toCurrentLocation(),
        )
    }

    @Test
    fun `an answer that is not a position is refused`() {
        assertEquals(noPosition, info(0.0, 0.0).toCurrentLocation())
        assertEquals(noPosition, info(137.0, 127.0).toCurrentLocation())
        assertEquals(noPosition, info(Double.NaN, 127.0).toCurrentLocation())
    }
}

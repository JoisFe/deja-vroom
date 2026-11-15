package ai.dejavroom.app.data

import ai.dejavroom.app.domain.Trip
import ai.dejavroom.app.domain.TripCheckpoint
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripStoreTest {
    private val route = (0..40).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }
    private val trip = Trip(
        destination = Place("강남역", "서울 강남구", route.last()),
        leg = listOf(TripCheckpoint("r2-1", "양재대로 → 강남대로", route[30], 1_500.0)),
        route = route,
        startedAtMillis = 1_700_000_000_000,
        passedBefore = listOf(TripCheckpoint("r1-0", "중부대로 갈림길", GeoCoordinate(37.26, 127.07))),
        progressMeters = 812.5,
    )

    @Test
    fun `a saved trip comes back exactly as it was`() {
        assertEquals(trip, TripJson.decode(TripJson.encode(trip)))
    }

    @Test
    fun `saving a trip replaces the one before and clearing leaves none`() {
        val store = InMemoryTripStore()
        assertNull(store.load())
        store.save(trip)
        store.save(trip.copy(progressMeters = 900.0))
        assertEquals(900.0, store.load()!!.progressMeters, 0.0)
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun `a record that cannot be trusted gives no trip`() {
        assertNull(TripJson.decode("not json"))
        assertNull(TripJson.decode("{}"))
        fun broken(change: JSONObject.() -> Unit) = TripJson.decode(JSONObject(TripJson.encode(trip)).apply(change).toString())
        // A route with a latitude and no longitude, a position off the globe, progress that cannot be.
        assertNull(broken { getJSONArray("route").put(37.5) })
        assertNull(broken { getJSONObject("destination").getJSONArray("at").put(0, 137.5) })
        assertNull(broken { put("progress", -1.0) })
        assertNull(broken { remove("leg") })
    }

    @Test
    fun `the saved form holds no more than the trip needs`() {
        val keys = JSONObject(TripJson.encode(trip)).keySet()
        assertEquals(setOf("destination", "startedAt", "progress", "passedBefore", "leg", "route"), keys)
    }
}

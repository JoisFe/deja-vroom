package ai.dejavroom.app.data

import ai.dejavroom.app.domain.Trip
import ai.dejavroom.app.domain.TripCheckpoint
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Keeps the trip the driver is on. There is only ever one: saving a trip replaces the one before. */
interface TripStore {
    fun load(): Trip?
    fun save(trip: Trip)
    fun clear()
}

class InMemoryTripStore(private var trip: Trip? = null) : TripStore {
    override fun load(): Trip? = trip
    override fun save(trip: Trip) { this.trip = trip }
    override fun clear() { trip = null }
}

/** Keeps the trip on the device only; nothing is sent to a server. A record that cannot be read is dropped. */
class SharedPreferencesTripStore(context: Context) : TripStore {
    private val prefs = context.applicationContext.getSharedPreferences("trip", Context.MODE_PRIVATE)

    override fun load(): Trip? = prefs.getString(KEY, null)?.let(TripJson::decode)
    override fun save(trip: Trip) = prefs.edit().putString(KEY, TripJson.encode(trip)).apply()
    override fun clear() = prefs.edit().remove(KEY).apply()

    private companion object { const val KEY = "trip_v1" }
}

/** The saved form of a [Trip]. Reading is strict: anything missing or out of range gives no trip at all. */
internal object TripJson {
    fun encode(trip: Trip): String = JSONObject()
        .put("destination", JSONObject().put("name", trip.destination.name).put("address", trip.destination.address).put("at", point(requireNotNull(trip.destination.coordinate))))
        .put("startedAt", trip.startedAtMillis)
        .put("progress", trip.progressMeters)
        .put("passedBefore", JSONArray(trip.passedBefore.map(::checkpoint)))
        .put("leg", JSONArray(trip.leg.map(::checkpoint)))
        .put("route", JSONArray().apply { trip.route.forEach { put(it.latitude); put(it.longitude) } })
        .toString()

    fun decode(raw: String): Trip? = runCatching {
        val json = JSONObject(raw)
        val destination = json.getJSONObject("destination")
        val route = json.getJSONArray("route")
        Trip(
            destination = Place(destination.getString("name"), destination.optString("address"), coordinate(destination.getJSONArray("at"))),
            leg = checkpoints(json.getJSONArray("leg")),
            route = (0 until route.length() step 2).map { GeoCoordinate(route.getDouble(it), route.getDouble(it + 1)) },
            startedAtMillis = json.getLong("startedAt"),
            passedBefore = checkpoints(json.getJSONArray("passedBefore")),
            progressMeters = json.getDouble("progress"),
        )
    }.getOrNull()

    private fun point(coordinate: GeoCoordinate) = JSONArray().put(coordinate.latitude).put(coordinate.longitude)

    private fun coordinate(array: JSONArray) = GeoCoordinate(array.getDouble(0), array.getDouble(1))

    private fun checkpoint(checkpoint: TripCheckpoint) = JSONObject()
        .put("id", checkpoint.id).put("name", checkpoint.name).put("at", point(checkpoint.coordinate)).put("offset", checkpoint.offsetMeters)

    private fun checkpoints(array: JSONArray): List<TripCheckpoint> = (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        TripCheckpoint(item.getString("id"), item.getString("name"), coordinate(item.getJSONArray("at")), item.getDouble("offset"))
    }
}

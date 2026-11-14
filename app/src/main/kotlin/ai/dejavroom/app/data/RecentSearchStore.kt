package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Recently searched places, newest first. */
interface RecentSearchStore {
    fun all(): List<Place>
    fun add(place: Place): List<Place>
    fun remove(place: Place): List<Place>
    fun clear(): List<Place>
}

object RecentSearches {
    const val MAX = 8

    private fun key(place: Place) = place.name.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Puts [place] first, drops older entries with the same name, keeps at most [max]. */
    fun add(current: List<Place>, place: Place, max: Int = MAX): List<Place> {
        if (place.name.isBlank()) return current
        return (listOf(place) + current.filter { key(it) != key(place) }).take(max)
    }

    fun remove(current: List<Place>, place: Place): List<Place> = current.filter { key(it) != key(place) }
}

class InMemoryRecentSearchStore(initial: List<Place> = emptyList()) : RecentSearchStore {
    private var places = initial.take(RecentSearches.MAX)
    override fun all() = places
    override fun add(place: Place) = RecentSearches.add(places, place).also { places = it }
    override fun remove(place: Place) = RecentSearches.remove(places, place).also { places = it }
    override fun clear() = emptyList<Place>().also { places = it }
}

/** Keeps recent searches on the device only; nothing is sent to a server. */
class SharedPreferencesRecentSearchStore(context: Context) : RecentSearchStore {
    private val prefs = context.applicationContext.getSharedPreferences("recent_searches", Context.MODE_PRIVATE)

    override fun all(): List<Place> = runCatching { decode(prefs.getString(KEY, null)) }.getOrDefault(emptyList())
    override fun add(place: Place) = save(RecentSearches.add(all(), place))
    override fun remove(place: Place) = save(RecentSearches.remove(all(), place))
    override fun clear() = save(emptyList())

    private fun save(places: List<Place>): List<Place> {
        prefs.edit().putString(KEY, encode(places)).apply()
        return places
    }

    private companion object {
        const val KEY = "places_v1"

        fun encode(places: List<Place>): String = JSONArray().apply {
            places.forEach { place ->
                put(JSONObject().apply {
                    put("name", place.name)
                    put("address", place.address)
                    place.coordinate?.let { put("lat", it.latitude); put("lon", it.longitude) }
                })
            }
        }.toString()

        fun decode(raw: String?): List<Place> {
            if (raw.isNullOrBlank()) return emptyList()
            val array = JSONArray(raw)
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val coordinate = if (item.has("lat") && item.has("lon")) {
                    runCatching { GeoCoordinate(item.getDouble("lat"), item.getDouble("lon")) }.getOrNull()
                } else null
                Place(name, item.optString("address"), coordinate)
            }.take(RecentSearches.MAX)
        }
    }
}

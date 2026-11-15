package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val PHOTON_ATTRIBUTION = "© OpenStreetMap contributors (Photon)"
const val KAKAO_PLACES_ATTRIBUTION = "장소 정보 © Kakao"

/**
 * Search-as-you-type place suggestions. Which service answers is chosen in config/search.yml
 * (`placeSearch.provider`); see [PlaceSearchFactory] and docs/place-search-setup.md.
 */
interface PlaceSearchRepository {
    /** Whose data the suggestions are; shown under them. */
    val attribution: String get() = ""

    /**
     * Places matching [query], best first. [bias] says roughly where the driver is, for services that rank
     * nearby places higher. Throws [PlaceSearchUnavailableException] when the service is not set up or
     * cannot be reached, with a message that says what to do.
     */
    suspend fun suggest(query: String, bias: GeoCoordinate? = null): List<Place>

    /** Gets ready ahead of the first question, where that takes time. Never throws. */
    suspend fun prepare() = Unit

    /** Lets go of whatever [prepare] or [suggest] set up. */
    fun release() = Unit
}

/** The place search cannot answer at all, as opposed to finding nothing. The message is for the screen. */
class PlaceSearchUnavailableException(message: String) : Exception(message)

/** Stands in for a provider that is selected but not set up yet: every question gets the setup hint. */
class UnconfiguredPlaceSearchRepository(private val setupMessage: String) : PlaceSearchRepository {
    override suspend fun suggest(query: String, bias: GeoCoordinate?): List<Place> = throw PlaceSearchUnavailableException(setupMessage)
}

/**
 * Photon (komoot) geocoder, built for autocomplete over OpenStreetMap data. Limited to the Korean
 * peninsula. Needs no key, but knows few Korean shops and recent buildings. Nominatim is not used for
 * suggestions: its usage policy forbids autocomplete.
 */
class PhotonPlaceSearchRepository(
    private val baseUrl: String = "https://photon.komoot.io/api/",
    private val limit: Int = 6,
) : PlaceSearchRepository {
    override val attribution: String get() = PHOTON_ATTRIBUTION

    override suspend fun suggest(query: String, bias: GeoCoordinate?): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim().take(80)
        if (q.length < 2) return@withContext emptyList()
        val params = buildString {
            append("q=").append(URLEncoder.encode(q, Charsets.UTF_8.name()))
            append("&limit=").append(limit)
            append("&bbox=124.5,33.0,131.9,38.7")
            if (bias != null) append("&lat=").append(bias.latitude).append("&lon=").append(bias.longitude)
        }
        val connection = (URL("$baseUrl?$params").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DejaVroom-Android/0.1 (place suggestions prototype)")
        }
        try {
            val status = connection.responseCode
            require(status in 200..299) { "추천 검색어 서비스가 HTTP $status 응답을 반환했습니다." }
            parsePhotonResponse(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}

/** Parses a Photon GeoJSON FeatureCollection into places; malformed features are skipped. */
internal fun parsePhotonResponse(body: String): List<Place> {
    val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
    val places = mutableListOf<Place>()
    for (index in 0 until features.length()) {
        val feature = features.optJSONObject(index) ?: continue
        val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
        if (coordinates.length() < 2) continue
        val properties = feature.optJSONObject("properties") ?: JSONObject()
        val street = listOf(properties.optString("street"), properties.optString("housenumber"))
            .filter(String::isNotBlank).joinToString(" ")
        val name = properties.optString("name").ifBlank { street }.trim()
        if (name.isBlank()) continue
        val coordinate = runCatching { GeoCoordinate(coordinates.getDouble(1), coordinates.getDouble(0)) }.getOrNull() ?: continue
        val address = listOf("state", "city", "county", "district", "locality")
            .map { properties.optString(it).trim() }
            .plus(street)
            .filter { it.isNotBlank() && it != name }
            .distinct()
            .joinToString(" ")
        places += Place(name = name, address = address, coordinate = coordinate)
    }
    return places.distinctBy { "${it.name}|${it.address}" }
}

/** Deterministic suggestions for tests and offline demos. */
class FakePlaceSearchRepository(private val places: List<Place> = DEMO_PLACES) : PlaceSearchRepository {
    val queries = mutableListOf<String>()

    override suspend fun suggest(query: String, bias: GeoCoordinate?): List<Place> {
        queries += query
        val q = query.trim()
        return places.filter { it.name.contains(q, ignoreCase = true) || it.address.contains(q, ignoreCase = true) }
    }

    companion object {
        val DEMO_PLACES = listOf(
            Place("강남역", "서울 강남구 강남대로", GeoCoordinate(37.4979, 127.0276)),
            Place("서울역", "서울 용산구 한강대로", GeoCoordinate(37.5547, 126.9707)),
            Place("코엑스", "서울 강남구 영동대로", GeoCoordinate(37.5116, 127.0594)),
            Place("수원역", "경기 수원시 팔달구", GeoCoordinate(37.2659, 127.0000)),
        )
    }
}

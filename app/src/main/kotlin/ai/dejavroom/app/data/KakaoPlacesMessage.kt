package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import org.json.JSONObject

/**
 * What the place search page (web/roadview/places.js) says to the app, and what the app asks it.
 * Everything from the page is checked here before it becomes a [Place]: a result that is not a named
 * position on the globe is dropped, and anything that is not one of these messages is ignored.
 */
internal sealed interface KakaoPlacesMessage {
    /** The page has the Kakao services up and can take questions. */
    data object Ready : KakaoPlacesMessage

    /** The page cannot work at all. */
    data class Failed(val reason: Reason) : KakaoPlacesMessage

    data class Results(val id: Int, val places: List<Place>) : KakaoPlacesMessage

    /** Kakao answered question [id] with an error. */
    data class SearchFailed(val id: Int) : KakaoPlacesMessage

    enum class Reason { KEY_MISSING, SDK_NOT_LOADED }

    companion object {
        private const val MAX_PLACES = 15
        private const val MAX_TEXT = 120

        fun parse(message: String?): KakaoPlacesMessage? = when (message) {
            null -> null
            "ready" -> Ready
            "fail:config" -> Failed(Reason.KEY_MISSING)
            "fail:sdk-load" -> Failed(Reason.SDK_NOT_LOADED)
            else -> runCatching { parseAnswer(JSONObject(message)) }.getOrNull()
        }

        /** The question for the page: places named [query], tagged [id] so the answer can be matched. */
        fun searchRequest(id: Int, query: String): String =
            JSONObject().put("type", "search").put("id", id).put("query", query).toString()

        private fun parseAnswer(json: JSONObject): KakaoPlacesMessage? {
            val id = json.getInt("id")
            return when (json.getString("type")) {
                "places-error" -> SearchFailed(id)
                "places" -> {
                    val items = json.getJSONArray("items")
                    Results(id, (0 until minOf(items.length(), MAX_PLACES)).mapNotNull { index -> items.optJSONObject(index)?.let(::place) })
                }
                else -> null
            }
        }

        private fun place(item: JSONObject): Place? {
            val name = item.optString("name").trim().take(MAX_TEXT).ifBlank { return null }
            val coordinate = runCatching { GeoCoordinate(item.getDouble("latitude"), item.getDouble("longitude")) }.getOrNull() ?: return null
            return Place(name = name, address = item.optString("address").trim().take(MAX_TEXT), coordinate = coordinate)
        }
    }
}

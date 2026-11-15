package ai.dejavroom.app.model

import kotlin.math.round

/**
 * A place the user picked or typed. [coordinate] is null when the text has not been resolved yet.
 *
 * A [live] place is where the car was when it was read. It goes stale as the car moves, so it is read
 * again before a route is planned from it and it is never kept as a recent search.
 */
data class Place(
    val name: String,
    val address: String = "",
    val coordinate: GeoCoordinate? = null,
    val live: Boolean = false,
) {
    /**
     * Where to tell a place search the driver roughly is. The car's own position is given only to about
     * 10 km: enough to rank nearby places first, not enough to say where the car is.
     */
    val searchBias: GeoCoordinate?
        get() = coordinate?.let { if (live) GeoCoordinate(round(it.latitude * 10) / 10, round(it.longitude * 10) / 10) else it }

    companion object {
        const val CURRENT_LOCATION_NAME = "현재 위치"

        /** The car's own position. [address] is the navigation's wording for it; blank when it gives none. */
        fun currentLocation(coordinate: GeoCoordinate, address: String = ""): Place =
            Place(CURRENT_LOCATION_NAME, address.trim(), coordinate, live = true)
    }
}

/** Where routes can be planned: place search and routing both only cover South Korea. */
object ServiceArea {
    private val latitudes = 33.0..38.7
    private val longitudes = 124.5..131.9

    operator fun contains(coordinate: GeoCoordinate): Boolean =
        coordinate.latitude in latitudes && coordinate.longitude in longitudes
}

/** What the user entered in one route field: the raw text and, if picked from suggestions, the resolved place. */
data class PlaceInput(val text: String, val place: Place? = null) {
    /** A picked place is only trusted while the field still shows its name. */
    val resolvedPlace: Place?
        get() = place?.takeIf { it.coordinate != null && it.name == text.trim() }
}

enum class SearchField { ORIGIN, DESTINATION }

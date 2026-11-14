package ai.dejavroom.app.model

/** A place the user picked or typed. [coordinate] is null when the text has not been resolved yet. */
data class Place(
    val name: String,
    val address: String = "",
    val coordinate: GeoCoordinate? = null,
)

/** What the user entered in one route field: the raw text and, if picked from suggestions, the resolved place. */
data class PlaceInput(val text: String, val place: Place? = null) {
    /** A picked place is only trusted while the field still shows its name. */
    val resolvedPlace: Place?
        get() = place?.takeIf { it.coordinate != null && it.name == text.trim() }
}

enum class SearchField { ORIGIN, DESTINATION }

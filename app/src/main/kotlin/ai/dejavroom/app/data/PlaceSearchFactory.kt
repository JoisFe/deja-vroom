package ai.dejavroom.app.data

/** The place search services the app can use. Chosen in config/search.yml (`placeSearch.provider`). */
enum class PlaceSearchProviderId(val key: String) {
    /** Kakao place search through the road view site. Current Korean places; needs the site address. */
    KAKAO("kakao"),

    /** Photon for suggestions and Nominatim for typed text, both on OpenStreetMap data. Needs nothing. */
    PHOTON("photon");

    companion object {
        fun fromKey(key: String): PlaceSearchProviderId? = entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
    }
}

/**
 * The place search in use, in its two roles: suggestions while typing, and turning text that was typed
 * but not picked into a place. Both come from the same service, so a name means the same place either way.
 */
class PlaceSearch(val suggestions: PlaceSearchRepository, val geocoder: DestinationGeocoder)

/** Typed text resolved by the place search itself: the best match is the place. */
class PlaceSearchGeocoder(private val search: PlaceSearchRepository) : DestinationGeocoder {
    override suspend fun resolve(query: String): GeocodedDestination {
        val best = search.suggest(query).firstOrNull { it.coordinate != null }
            ?: error("장소를 찾지 못했습니다. 출발지와 목적지를 구체적으로 입력해 주세요.")
        return GeocodedDestination(requireNotNull(best.coordinate), best.name)
    }
}

/** The single place that maps the setting to an implementation. */
object PlaceSearchFactory {
    /**
     * [kakao] builds the Kakao implementation for a road view site; it is passed in because that one needs
     * an Android context. A provider that is selected but not set up is not swapped for another one: its
     * questions fail with the setup hint, so the setting always means what it says.
     */
    fun create(provider: PlaceSearchProviderId, roadView: RoadViewConfig, kakao: (RoadViewEndpoint) -> PlaceSearchRepository): PlaceSearch =
        when (provider) {
            PlaceSearchProviderId.KAKAO -> {
                val search = RoadViewEndpoint.parse(roadView.kakaoBaseUrl, roadView.pageRevision)?.let(kakao)
                    ?: UnconfiguredPlaceSearchRepository(KakaoPlaceSearchRepository.SETUP_MESSAGE)
                PlaceSearch(search, PlaceSearchGeocoder(search))
            }
            PlaceSearchProviderId.PHOTON -> PlaceSearch(PhotonPlaceSearchRepository(), NominatimDestinationGeocoder)
        }
}

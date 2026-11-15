package ai.dejavroom.app.data

/**
 * How the search screen behaves, read from config/search.yml (packaged as the `search.yml` asset): what
 * it shows when it first opens, and which service looks places up. A key missing from the file keeps its
 * default; a key nobody reads or a value outside its choices is an error, so a typo cannot quietly change
 * the demo.
 */
data class SearchSettings(
    val originSource: OriginSource = OriginSource.CURRENT,
    val origin: String = "수원시 영통구",
    val destination: String = "강남역",
    /** Which place search answers; [PlaceSearchFactory] turns it into an implementation. */
    val placeSearch: PlaceSearchProviderId = PlaceSearchProviderId.KAKAO,
) {
    /** Where the origin comes from when the driver has not chosen one. */
    enum class OriginSource {
        /** The car's own position when the vehicle gives one, otherwise [origin]. */
        CURRENT,

        /** Always [origin]: for rehearsals and recordings that must start from the same place. */
        PLACE,
    }

    init {
        require(origin.isNotBlank()) { "config/search.yml: origin.place가 비어 있다" }
        require(destination.isNotBlank()) { "config/search.yml: destination.place가 비어 있다" }
    }

    companion object {
        const val ASSET_NAME = "search.yml"
        private val KEYS = setOf("origin.source", "origin.place", "destination.place", "placeSearch.provider")

        /** [yaml] is the text of config/search.yml, or null when the asset is missing (defaults apply). */
        fun fromYaml(yaml: String?): SearchSettings {
            val values = yaml?.let(FlatYaml::parse).orEmpty()
            val unknown = values.keys - KEYS
            require(unknown.isEmpty()) { "config/search.yml: 알 수 없는 키 ${unknown.sorted().joinToString()}" }
            val defaults = SearchSettings()
            return SearchSettings(
                originSource = values["origin.source"]?.let { raw ->
                    requireNotNull(OriginSource.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }) {
                        "config/search.yml: origin.source는 current 또는 place여야 한다: $raw"
                    }
                } ?: defaults.originSource,
                origin = values["origin.place"] ?: defaults.origin,
                destination = values["destination.place"] ?: defaults.destination,
                placeSearch = values["placeSearch.provider"]?.let { raw ->
                    requireNotNull(PlaceSearchProviderId.fromKey(raw)) {
                        "config/search.yml: placeSearch.provider는 ${PlaceSearchProviderId.entries.joinToString(" 또는 ") { it.key }}여야 한다: $raw"
                    }
                } ?: defaults.placeSearch,
            )
        }
    }
}

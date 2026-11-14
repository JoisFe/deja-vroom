package ai.dejavroom.app.data

/**
 * Road view settings. Base values come from config/roadview.yml (packaged as the `roadview.yml` asset);
 * local.properties values, passed in through BuildConfig, override them for one machine. Secrets such as the
 * Mapillary client token only come from local.properties, never from the YAML file.
 */
data class RoadViewConfig(
    /** Raw `provider:` value; resolved by [RoadViewProviderFactory]. */
    val provider: String,
    val kakaoBaseUrl: String,
    val mapillaryClientToken: String,
    /** Content hash of web/roadview from the build; see [RoadViewEndpoint.detailUrl]. */
    val pageRevision: String = "",
) {
    companion object {
        const val ASSET_NAME = "roadview.yml"

        /**
         * Precedence: a non-blank override (local.properties) wins over the YAML value.
         * [yaml] is the text of config/roadview.yml, or null when the asset is missing.
         */
        fun resolve(
            yaml: String?,
            providerOverride: String = "",
            kakaoBaseUrlOverride: String = "",
            mapillaryClientToken: String = "",
            pageRevision: String = "",
        ): RoadViewConfig {
            val values = yaml?.let(FlatYaml::parse).orEmpty()
            return RoadViewConfig(
                provider = providerOverride.ifBlank { values["provider"].orEmpty() }.trim(),
                kakaoBaseUrl = kakaoBaseUrlOverride.ifBlank { values["kakao.baseUrl"].orEmpty() }.trim(),
                mapillaryClientToken = mapillaryClientToken.trim(),
                pageRevision = pageRevision.trim(),
            )
        }
    }
}

/** The single place that maps settings to an implementation. */
object RoadViewProviderFactory {
    const val KAKAO_SETUP_MESSAGE = "로드뷰 주소가 설정되지 않았습니다 · docs/roadview-setup.md 참고"
    const val MAPILLARY_SETUP_MESSAGE = "Mapillary 토큰이 설정되지 않았습니다 · docs/roadview-setup.md 참고"

    fun create(config: RoadViewConfig): RoadViewProvider =
        when (RoadViewProviderId.fromKey(config.provider)) {
            RoadViewProviderId.KAKAO -> RoadViewEndpoint.parse(config.kakaoBaseUrl, config.pageRevision)
                ?.let(::KakaoRoadViewProvider)
                ?: UnconfiguredRoadViewProvider(RoadViewProviderId.KAKAO, KAKAO_SETUP_MESSAGE)
            RoadViewProviderId.MAPILLARY -> config.mapillaryClientToken.takeIf(String::isNotBlank)
                ?.let { MapillaryRoadViewProvider(it) }
                ?: UnconfiguredRoadViewProvider(RoadViewProviderId.MAPILLARY, MAPILLARY_SETUP_MESSAGE)
            RoadViewProviderId.DEMO -> DemoRoadViewProvider()
            // The Gradle build rejects unknown values; this keeps a hand-edited asset from crashing the app.
            null -> UnconfiguredRoadViewProvider(
                RoadViewProviderId.DEMO,
                "알 수 없는 로드뷰 제공자 '${config.provider}' · config/roadview.yml 확인",
            )
        }
}

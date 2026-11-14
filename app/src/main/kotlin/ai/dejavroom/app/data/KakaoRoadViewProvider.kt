package ai.dejavroom.app.data

import ai.dejavroom.app.model.RoadViewContentOrigin

/**
 * Kakao Roadview through the web page in web/roadview (hosted on GitHub Pages, or local serve.py in development).
 * Coverage is checked inside the page, which reports "no panorama" back over the WebView bridge, so this lookup
 * only builds the page addresses and never touches the network itself.
 */
class KakaoRoadViewProvider(private val endpoint: RoadViewEndpoint) : RoadViewProvider {
    override val id = RoadViewProviderId.KAKAO
    override val attribution = "© Kakao Roadview"

    override suspend fun lookup(target: RoadViewTarget): RoadViewLookupResult =
        RoadViewLookupResult.Available(listOf(RoadViewResult(
            content = RoadViewContent.InteractivePage(
                endpoint = endpoint,
                thumbnailUrl = endpoint.thumbnailUrl(target.coordinate, target.headingDegrees),
            ),
            providerName = "Kakao Roadview",
            attribution = attribution,
            headingDegrees = target.headingDegrees,
            coordinate = target.coordinate,
            contentOrigin = RoadViewContentOrigin.LIVE_PROVIDER,
        )))
}

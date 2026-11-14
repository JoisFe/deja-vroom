package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RoadViewContentOrigin
import ai.dejavroom.app.model.RouteMatchStatus

/**
 * Street-level imagery for one briefing point. Every provider (Kakao, Mapillary, demo) implements this one
 * contract; the UI only sees [RoadViewLookupResult] and [RoadViewContent], never a concrete provider.
 * Which implementation runs is chosen by config/roadview.yml through [RoadViewProviderFactory].
 * Provider terms must permit the intended in-vehicle display.
 */
interface RoadViewProvider {
    val id: RoadViewProviderId

    /** Short credit line shown next to the imagery. */
    val attribution: String

    suspend fun lookup(target: RoadViewTarget): RoadViewLookupResult
}

/** Implementations selectable in config/roadview.yml (`provider:`). */
enum class RoadViewProviderId(val key: String) {
    KAKAO("kakao"),
    MAPILLARY("mapillary"),
    DEMO("demo");

    companion object {
        fun fromKey(key: String?): RoadViewProviderId? =
            entries.firstOrNull { it.key == key?.trim()?.lowercase() }
    }
}

/** Where to look and which way the driver will be facing. */
data class RoadViewTarget(
    val coordinate: GeoCoordinate,
    val headingDegrees: Double?,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
) {
    init { require(radiusMeters in 1..500) }

    companion object { const val DEFAULT_RADIUS_METERS = 80 }
}

/** How a found view is rendered. The UI switches on this type, not on the provider. */
sealed interface RoadViewContent {
    /** A provider page rendered in a WebView (Kakao Roadview). [thumbnailUrl] is a still, non-interactive variant. */
    data class InteractivePage(val endpoint: RoadViewEndpoint, val thumbnailUrl: String) : RoadViewContent

    /** A single photo (Mapillary). */
    data class StillImage(
        val imageUrl: String,
        val distanceMeters: Int,
        val headingDifferenceDegrees: Int,
    ) : RoadViewContent

    /** Locally drawn demo artwork; not a real photo. */
    data object Illustration : RoadViewContent
}

data class RoadViewResult(
    val content: RoadViewContent,
    val providerName: String,
    val attribution: String?,
    val capturedAt: String? = null,
    val headingDegrees: Double? = null,
    val coordinate: GeoCoordinate? = null,
    val viewId: String? = null,
    val contentOrigin: RoadViewContentOrigin = RoadViewContentOrigin.DEMO_ILLUSTRATION,
    val routeMatchStatus: RouteMatchStatus = RouteMatchStatus.UNVERIFIED,
) {
    val isVerifiedForRoute: Boolean
        get() = routeMatchStatus == RouteMatchStatus.VERIFIED
}

sealed interface RoadViewLookupResult {
    data class Available(val views: List<RoadViewResult>) : RoadViewLookupResult
    data object NoCoverage : RoadViewLookupResult

    /** The selected provider is missing a setting (URL, token). [message] tells the team what to set. */
    data class NotConfigured(val message: String) : RoadViewLookupResult
    data class Unavailable(val reason: RoadViewUnavailableReason) : RoadViewLookupResult
}

enum class RoadViewUnavailableReason { OFFLINE, TIMEOUT, QUOTA, PROVIDER_ERROR }

/** Locally drawn artwork for demos without network; deliberately not a real panorama. */
class DemoRoadViewProvider : RoadViewProvider {
    override val id = RoadViewProviderId.DEMO
    override val attribution = "자체 제작 데모 일러스트 · 실제 도로 사진 아님"

    override suspend fun lookup(target: RoadViewTarget): RoadViewLookupResult =
        RoadViewLookupResult.Available(listOf(RoadViewResult(
            content = RoadViewContent.Illustration,
            providerName = "Déjà Vroom demo artwork",
            attribution = attribution,
            coordinate = target.coordinate,
            contentOrigin = RoadViewContentOrigin.DEMO_ILLUSTRATION,
            routeMatchStatus = RouteMatchStatus.NOT_APPLICABLE,
        )))
}

/** Stands in for a selected provider whose settings are missing, so the app explains instead of failing. */
class UnconfiguredRoadViewProvider(
    override val id: RoadViewProviderId,
    private val message: String,
) : RoadViewProvider {
    override val attribution = ""

    override suspend fun lookup(target: RoadViewTarget): RoadViewLookupResult =
        RoadViewLookupResult.NotConfigured(message)
}

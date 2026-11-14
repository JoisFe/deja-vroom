package ai.firstlook.app.data

import ai.firstlook.app.model.BriefingSegment
import ai.firstlook.app.model.GeoCoordinate
import ai.firstlook.app.model.RoadViewContentOrigin
import ai.firstlook.app.model.RouteMatchStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RoadViewResult(
    val imageUrl: String?,
    val capturedAt: String?,
    val providerName: String,
    val headingDegrees: Double?,
    val attribution: String?,
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
    data object NotAuthorized : RoadViewLookupResult
    data class Unavailable(val reason: RoadViewUnavailableReason) : RoadViewLookupResult
}

enum class RoadViewUnavailableReason { OFFLINE, TIMEOUT, QUOTA, PROVIDER_ERROR }

/** Provider adapter contract. Provider terms must permit the intended in-vehicle display. */
interface RoadViewProvider {
    suspend fun findNearestViews(segment: BriefingSegment, radiusMeters: Int): RoadViewLookupResult
}

/** The demo renderer uses locally drawn artwork and deliberately does not represent a real panorama. */
class DemoRoadViewProvider : RoadViewProvider {
    override suspend fun findNearestViews(segment: BriefingSegment, radiusMeters: Int): RoadViewLookupResult =
        RoadViewLookupResult.Available(listOf(RoadViewResult(
            imageUrl = null,
            capturedAt = null,
            providerName = "FirstLook demo artwork",
            headingDegrees = null,
            attribution = "자체 제작 데모 일러스트 · 실제 도로 사진 아님",
            viewId = "${segment.id}-demo",
            contentOrigin = RoadViewContentOrigin.DEMO_ILLUSTRATION,
            routeMatchStatus = RouteMatchStatus.NOT_APPLICABLE,
        )))
}

/** Live providers stay disabled until route data and in-vehicle display rights are confirmed. */
class UnconfiguredRoadViewProvider : RoadViewProvider {
    override suspend fun findNearestViews(segment: BriefingSegment, radiusMeters: Int): RoadViewLookupResult =
        RoadViewLookupResult.NotAuthorized
}

interface BriefingSpeechOutput {
    val outputAvailable: Boolean get() = false
    suspend fun speak(text: String)
    fun stop()
}

class NoopBriefingSpeechOutput : BriefingSpeechOutput {
    override suspend fun speak(text: String) = Unit
    override fun stop() = Unit
}

/** Test/demo fake. It records requests but makes no audible output. */
class RecordingBriefingSpeechOutput : BriefingSpeechOutput {
    val spoken = mutableListOf<String>()
    var stopCount: Int = 0
        private set

    override suspend fun speak(text: String) { spoken += text }
    override fun stop() { stopCount++ }
}

class BriefingSpeechController(private val output: BriefingSpeechOutput) {
    val outputAvailable: Boolean get() = output.outputAvailable

    suspend fun announce(text: String, enabled: Boolean, restricted: Boolean, briefingVisible: Boolean) {
        if (enabled && !restricted && briefingVisible) output.speak(text)
    }

    fun stop() = output.stop()
}

/** Vehicle restrictions are supplied by an approved platform adapter in a Pleos build. */
interface DrivingRestrictionSource {
    val isRestricted: StateFlow<Boolean>
}

/** Mutable source for tests and the in-app safety demo; it is not a vehicle signal. */
class FakeDrivingRestrictionSource(initiallyRestricted: Boolean = false) : DrivingRestrictionSource {
    private val mutableIsRestricted = MutableStateFlow(initiallyRestricted)
    override val isRestricted: StateFlow<Boolean> = mutableIsRestricted

    fun setRestricted(restricted: Boolean) { mutableIsRestricted.value = restricted }
}

interface PleosNavigationGateway {
    suspend fun requestDestination(name: String): NavigationRequestResult
    suspend fun showNavigation(): NavigationRequestResult
}

sealed interface NavigationRequestResult {
    data object Submitted : NavigationRequestResult
    data class Unavailable(val reason: String) : NavigationRequestResult
}

class MockPleosNavigationGateway : PleosNavigationGateway {
    override suspend fun requestDestination(name: String) = NavigationRequestResult.Unavailable("NaviHelper 미연동")
    override suspend fun showNavigation() = NavigationRequestResult.Unavailable("Pleos 내비게이션 화면 전환 미연동")
}

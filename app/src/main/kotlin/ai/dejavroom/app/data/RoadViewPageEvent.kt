package ai.dejavroom.app.data

/** Only fixed status messages from the local Kakao Roadview page are accepted. */
/** JS object name the Roadview page (web/roadview/index.html) uses to post events back to the app. */
const val ROADVIEW_BRIDGE_NAME = "DejaVroomBridge"

sealed interface RoadViewPageEvent {
    data object Ready : RoadViewPageEvent
    data object ConfigReceived : RoadViewPageEvent
    data object SdkLoaded : RoadViewPageEvent
    data object SdkReady : RoadViewPageEvent
    data object CspBlocked : RoadViewPageEvent
    data object Shown : RoadViewPageEvent
    data class RouteCandidateSelected(val index: Int) : RoadViewPageEvent
    data object CandidateNoCoverage : RoadViewPageEvent
    data class NativeArrowCandidatesHidden(val count: Int) : RoadViewPageEvent
    data object NativeArrowLayerUnverified : RoadViewPageEvent
    data class NativeArrowScaled(val count: Int) : RoadViewPageEvent
    data class RouteOverlayReady(val count: Int) : RoadViewPageEvent
    data class JunctionOverlayReady(val count: Int) : RoadViewPageEvent
    data class OverlaySuppressed(val reason: String) : RoadViewPageEvent
    data class Failed(val reason: FailureReason) : RoadViewPageEvent

    /** The page is playing the approach; [remainingMeters] is negative once the point has been passed. */
    data class DriveProgress(val remainingMeters: Int) : RoadViewPageEvent
    data object DriveFinished : RoadViewPageEvent

    enum class FailureReason { CONFIG, SDK_LOAD, NO_PANO, TIMEOUT, RENDER }

    companion object {
        /** Cards are numbered from 0; a route never has more than 30 of them. */
        private const val MAX_CANDIDATE_INDEX = 29

        fun parse(message: String?): RoadViewPageEvent? = when {
            message == null -> null
            message.startsWith("native-arrow-candidates-hidden:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in 1..100 }?.let(::NativeArrowCandidatesHidden)
            message == "native-arrow-layer-unverified" -> NativeArrowLayerUnverified
            message.startsWith("native-arrow-scaled:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in 1..100 }?.let(::NativeArrowScaled)
            message == "candidate-no-coverage" -> CandidateNoCoverage
            message.startsWith("route-candidate-selected:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in 0..MAX_CANDIDATE_INDEX }?.let(::RouteCandidateSelected)
            message.startsWith("route-overlay-ready:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in 1..50 }?.let(::RouteOverlayReady)
            message.startsWith("junction-overlay-ready:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in 1..7 }?.let(::JunctionOverlayReady)
            message.startsWith("overlay-suppressed:") -> message.substringAfter(':').takeIf { it.matches(Regex("[a-z0-9-]{1,80}")) }?.let(::OverlaySuppressed)
            message.startsWith("drive-progress:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in -500..1000 }?.let(::DriveProgress)
            message == "drive-finished" -> DriveFinished
            message == "ready" -> Ready
            message == "config-received" -> ConfigReceived
            message == "sdk-loaded" -> SdkLoaded
            message == "sdk-ready" -> SdkReady
            message == "csp-blocked" -> CspBlocked
            message == "shown" -> Shown
            message == "fail:config" -> Failed(FailureReason.CONFIG)
            message == "fail:sdk-load" -> Failed(FailureReason.SDK_LOAD)
            message == "fail:no-pano" -> Failed(FailureReason.NO_PANO)
            message == "fail:timeout" -> Failed(FailureReason.TIMEOUT)
            message == "fail:render" -> Failed(FailureReason.RENDER)
            else -> null
        }
    }
}

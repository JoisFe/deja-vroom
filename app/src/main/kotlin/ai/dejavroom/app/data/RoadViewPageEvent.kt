package ai.dejavroom.app.data

/** Only fixed status messages from the local Kakao Roadview page are accepted. */
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

    enum class FailureReason { CONFIG, SDK_LOAD, NO_PANO, TIMEOUT, RENDER }

    companion object {
        fun parse(message: String?): RoadViewPageEvent? = when {
            message == null -> null
            message.startsWith("native-arrow-candidates-hidden:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in 1..100 }?.let(::NativeArrowCandidatesHidden)
            message == "native-arrow-layer-unverified" -> NativeArrowLayerUnverified
            message.startsWith("native-arrow-scaled:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in 1..100 }?.let(::NativeArrowScaled)
            message == "candidate-no-coverage" -> CandidateNoCoverage
            message.startsWith("route-candidate-selected:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in 0..4 }?.let(::RouteCandidateSelected)
            message.startsWith("route-overlay-ready:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in 1..50 }?.let(::RouteOverlayReady)
            message.startsWith("junction-overlay-ready:") -> message.substringAfter(':').toIntOrNull()?.takeIf { it in 1..7 }?.let(::JunctionOverlayReady)
            message.startsWith("overlay-suppressed:") -> message.substringAfter(':').takeIf { it.matches(Regex("[a-z0-9-]{1,80}")) }?.let(::OverlaySuppressed)
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

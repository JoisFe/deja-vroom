package ai.dejavroom.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

package ai.dejavroom.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

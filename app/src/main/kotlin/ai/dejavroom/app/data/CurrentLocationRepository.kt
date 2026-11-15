package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate

/** Where the car is now, as its own navigation reports it. */
interface CurrentLocationRepository {
    /** Never throws; a position that cannot be read comes back as [CurrentLocation.Unavailable]. */
    suspend fun current(): CurrentLocation

    /** Lets go of the platform connection, if there is one. */
    fun release() = Unit
}

sealed interface CurrentLocation {
    /** [address] is the navigation's wording for the place; blank when it gives none. */
    data class Found(val coordinate: GeoCoordinate, val address: String = "") : CurrentLocation

    data class Unavailable(val reason: Reason) : CurrentLocation

    enum class Reason {
        /** This device has no built-in navigation to ask, or it could not be reached. */
        NO_NAVIGATION,

        /** The navigation was reached but did not answer in time. */
        NO_ANSWER,

        /** The navigation answered without a usable position (no fix yet). */
        NO_POSITION,

        /** The app is not allowed to read it. */
        NO_PERMISSION,
    }
}

/** For builds and tests without a vehicle platform: there is nothing to ask. */
object NoCurrentLocationRepository : CurrentLocationRepository {
    override suspend fun current(): CurrentLocation = CurrentLocation.Unavailable(CurrentLocation.Reason.NO_NAVIGATION)
}

/** Test fake: answers with whatever [next] holds and counts the questions. */
class FakeCurrentLocationRepository(var next: CurrentLocation = CurrentLocation.Unavailable(CurrentLocation.Reason.NO_NAVIGATION)) :
    CurrentLocationRepository {
    var askCount: Int = 0
        private set
    var released: Boolean = false
        private set

    override suspend fun current(): CurrentLocation {
        askCount++
        return next
    }

    override fun release() { released = true }
}

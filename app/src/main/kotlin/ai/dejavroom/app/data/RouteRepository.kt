package ai.dejavroom.app.data

import ai.dejavroom.app.model.PlaceInput
import ai.dejavroom.app.model.RouteSnapshot

/** Turns an origin and a destination into a route and the points worth previewing on it. */
fun interface RouteRepository {
    /**
     * Uses a place picked from suggestions when available, so the route does not re-geocode it.
     * Throws [RoutePreviewException] for everything that goes wrong, except [PlaceSearchUnavailableException]
     * (the place search's setup hint) and cancellation.
     */
    suspend fun preview(origin: PlaceInput, destination: PlaceInput): RouteSnapshot
}

/**
 * Why a route preview did not come back. The screen turns each reason into driver wording; the
 * services' own messages (provider names, HTTP codes, parser text) never leave the data layer.
 */
enum class RoutePreviewFailure {
    MISSING_ORIGIN,
    MISSING_DESTINATION,
    PLACE_NOT_FOUND,
    NO_ROUTE,
    NOTHING_TO_PREVIEW,
    OFFLINE,
    SERVICE_UNAVAILABLE,
    UNEXPECTED,
}

/** A route preview that failed for [reason]; [cause] keeps the original error for debugging only. */
class RoutePreviewException(val reason: RoutePreviewFailure, cause: Throwable? = null) : Exception(reason.name, cause)

package ai.dejavroom.app.data

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

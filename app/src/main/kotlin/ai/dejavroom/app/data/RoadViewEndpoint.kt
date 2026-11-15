package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import java.net.URI
import java.util.Locale

/**
 * Where the Roadview page (web/roadview) is hosted: GitHub Pages in normal use, local serve.py in development.
 * Built from the `kakao.baseUrl` road view setting (config/roadview.yml or local.properties `roadviewBaseUrl`);
 * [parse] returns null when it is unset or invalid, and [RoadViewProviderFactory] then
 * shows a setup message instead of loading anything. See docs/roadview-setup.md.
 */
class RoadViewEndpoint private constructor(
    /** Page URL ending with `/`, e.g. `https://example.github.io/deja-vroom/roadview/`. */
    val pageUrl: String,
    /** `scheme://host[:port]` used to restrict WebView messaging to this page. */
    val origin: String,
    private val pagePath: String,
    /** Content hash of web/roadview; changes the URL whenever the page changes so no cache serves an old copy. */
    private val revision: String = "",
) {
    /** Interactive page for the detail screen. */
    val detailUrl: String
        get() = if (revision.isBlank()) pageUrl else "$pageUrl?rev=$revision"

    /** Still Roadview for a summary card, looked up near [coordinate] facing [headingDegrees]. */
    fun thumbnailUrl(coordinate: GeoCoordinate, headingDegrees: Double?): String =
        pageUrl + "?thumb=1" +
            "&lat=" + format(coordinate.latitude) +
            "&lng=" + format(coordinate.longitude) +
            "&heading=" + format(headingDegrees?.takeIf { it.isFinite() } ?: 0.0) +
            (if (revision.isBlank()) "" else "&rev=$revision")

    /** True only for this page itself (any query); every other navigation is blocked. */
    fun isRoadViewPage(url: String): Boolean {
        val path = pathOnSite(url) ?: return false
        return path == pagePath || path == pagePath.removeSuffix("/") || path == pagePath + "index.html"
    }

    /** The place search page hosted next to the road view page; the app loads it out of sight. */
    val placesUrl: String
        get() = pageUrl + PLACES_PAGE + (if (revision.isBlank()) "" else "?rev=$revision")

    fun isPlacesPage(url: String): Boolean = pathOnSite(url) == pagePath + PLACES_PAGE

    /** The path of [url] when it is on this site, else null. */
    private fun pathOnSite(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return if (originOf(uri) == origin) uri.rawPath.orEmpty() else null
    }

    companion object {
        private const val PLACES_PAGE = "places.html"

        fun parse(baseUrl: String, revision: String = ""): RoadViewEndpoint? {
            val trimmed = baseUrl.trim()
            if (trimmed.isEmpty()) return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (uri.host.isNullOrBlank() || uri.rawQuery != null || uri.rawFragment != null) return null
            val secure = scheme == "https"
            val localDev = scheme == "http" && uri.host == "localhost"
            if (!secure && !localDev) return null
            val path = uri.rawPath.orEmpty().ifEmpty { "/" }.let { if (it.endsWith("/")) it else "$it/" }
            val origin = originOf(uri) ?: return null
            val safeRevision = revision.trim().filter { it.isLetterOrDigit() }
            return RoadViewEndpoint(pageUrl = origin + path, origin = origin, pagePath = path, revision = safeRevision)
        }

        private fun originOf(uri: URI): String? {
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null
            val port = uri.port.takeIf { it != -1 && it != defaultPort(scheme) }
            return if (port == null) "$scheme://$host" else "$scheme://$host:$port"
        }

        private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80

        private fun format(value: Double) = String.format(Locale.ROOT, "%.6f", value)
    }
}

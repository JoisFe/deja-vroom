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
) {
    /** Still Roadview for a summary card, looked up near [coordinate] facing [headingDegrees]. */
    fun thumbnailUrl(coordinate: GeoCoordinate, headingDegrees: Double?): String =
        pageUrl + "?thumb=1" +
            "&lat=" + format(coordinate.latitude) +
            "&lng=" + format(coordinate.longitude) +
            "&heading=" + format(headingDegrees?.takeIf { it.isFinite() } ?: 0.0)

    /** True only for this page itself (any query); every other navigation is blocked. */
    fun isRoadViewPage(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (originOf(uri) != origin) return false
        val path = uri.rawPath.orEmpty()
        return path == pagePath || path == pagePath.removeSuffix("/") || path == pagePath + "index.html"
    }

    companion object {
        fun parse(baseUrl: String): RoadViewEndpoint? {
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
            return RoadViewEndpoint(pageUrl = origin + path, origin = origin, pagePath = path)
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

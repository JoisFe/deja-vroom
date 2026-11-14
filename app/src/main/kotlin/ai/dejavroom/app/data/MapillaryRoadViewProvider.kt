package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RoadViewContentOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Mapillary street photos (CC BY-SA, crowd-sourced) through the Graph API, called directly from the app with a
 * client token. Picks the photo closest to the point that also faces the driving direction.
 */
class MapillaryRoadViewProvider(
    private val clientToken: String,
    private val api: MapillaryImagesApi = HttpMapillaryImagesApi,
) : RoadViewProvider {
    init { require(clientToken.isNotBlank()) }

    override val id = RoadViewProviderId.MAPILLARY
    override val attribution = "© Mapillary contributors · CC BY-SA 4.0"

    override suspend fun lookup(target: RoadViewTarget): RoadViewLookupResult {
        val response = try {
            withContext(Dispatchers.IO) { api.imagesNear(target.coordinate, target.radiusMeters, clientToken) }
        } catch (_: UnknownHostException) {
            return RoadViewLookupResult.Unavailable(RoadViewUnavailableReason.OFFLINE)
        } catch (_: SocketTimeoutException) {
            return RoadViewLookupResult.Unavailable(RoadViewUnavailableReason.TIMEOUT)
        } catch (_: IOException) {
            return RoadViewLookupResult.Unavailable(RoadViewUnavailableReason.PROVIDER_ERROR)
        }
        return when (response.status) {
            in 200..299 -> {
                val best = MapillaryImageSelector.select(response.body, target)
                    ?: return RoadViewLookupResult.NoCoverage
                RoadViewLookupResult.Available(listOf(best.toResult()))
            }
            401, 403 -> RoadViewLookupResult.NotConfigured(TOKEN_REJECTED_MESSAGE)
            429 -> RoadViewLookupResult.Unavailable(RoadViewUnavailableReason.QUOTA)
            else -> RoadViewLookupResult.Unavailable(RoadViewUnavailableReason.PROVIDER_ERROR)
        }
    }

    private fun MapillaryImageSelector.Choice.toResult() = RoadViewResult(
        content = RoadViewContent.StillImage(imageUrl, distanceMeters, headingDifferenceDegrees),
        providerName = "Mapillary",
        attribution = listOfNotNull(attribution, creator?.let { "촬영 $it" }).joinToString(" · "),
        capturedAt = capturedAt,
        headingDegrees = compassAngle,
        coordinate = coordinate,
        viewId = imageId,
        contentOrigin = RoadViewContentOrigin.LIVE_PROVIDER,
    )

    companion object {
        const val TOKEN_REJECTED_MESSAGE = "Mapillary 토큰이 거부됐습니다 · docs/roadview-setup.md 참고"
    }
}

data class MapillaryResponse(val status: Int, val body: String)

/** Network seam so selection logic is testable without HTTP. */
fun interface MapillaryImagesApi {
    fun imagesNear(center: GeoCoordinate, radiusMeters: Int, clientToken: String): MapillaryResponse
}

object HttpMapillaryImagesApi : MapillaryImagesApi {
    private const val FIELDS =
        "id,thumb_1024_url,computed_geometry,geometry,captured_at,computed_compass_angle,compass_angle,creator{username}"

    override fun imagesNear(center: GeoCoordinate, radiusMeters: Int, clientToken: String): MapillaryResponse {
        val latRadius = radiusMeters / METERS_PER_DEGREE
        val lonRadius = latRadius / cos(Math.toRadians(center.latitude)).coerceAtLeast(0.15)
        val bbox = listOf(
            center.longitude - lonRadius, center.latitude - latRadius,
            center.longitude + lonRadius, center.latitude + latRadius,
        ).joinToString(",")
        val query = "fields=" + URLEncoder.encode(FIELDS, Charsets.UTF_8.name()) + "&bbox=$bbox&limit=100"
        val connection = (URL("https://graph.mapillary.com/images?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Authorization", "OAuth $clientToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return MapillaryResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

/** Pure ranking of a Graph API `images` response: nearest photo, penalised by how far it faces from the route. */
object MapillaryImageSelector {
    private const val HEADING_WEIGHT = 0.7

    data class Choice(
        val imageId: String,
        val imageUrl: String,
        val coordinate: GeoCoordinate,
        val distanceMeters: Int,
        val headingDifferenceDegrees: Int,
        val compassAngle: Double?,
        val capturedAt: String?,
        val creator: String?,
        val attribution: String = "© Mapillary contributors · CC BY-SA 4.0",
    )

    fun select(json: String, target: RoadViewTarget): Choice? {
        val data = runCatching { JSONObject(json).optJSONArray("data") }.getOrNull() ?: return null
        val center = target.coordinate
        return (0 until data.length()).mapNotNull { index ->
            val image = data.optJSONObject(index) ?: return@mapNotNull null
            val imageUrl = image.optString("thumb_1024_url").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val geometry = image.optJSONObject("computed_geometry") ?: image.optJSONObject("geometry") ?: return@mapNotNull null
            val coordinates = geometry.optJSONArray("coordinates") ?: return@mapNotNull null
            if (coordinates.length() < 2) return@mapNotNull null
            val point = runCatching { GeoCoordinate(coordinates.getDouble(1), coordinates.getDouble(0)) }.getOrNull()
                ?: return@mapNotNull null
            val distance = hypot(
                (point.latitude - center.latitude) * METERS_PER_DEGREE,
                (point.longitude - center.longitude) * METERS_PER_DEGREE * cos(Math.toRadians(center.latitude)),
            )
            if (distance > target.radiusMeters) return@mapNotNull null
            val angle = when {
                image.has("computed_compass_angle") -> image.optDouble("computed_compass_angle")
                image.has("compass_angle") -> image.optDouble("compass_angle")
                else -> Double.NaN
            }.takeIf { it.isFinite() }
            val headingDelta = if (angle == null || target.headingDegrees == null) 0.0
            else abs((angle - target.headingDegrees + 540.0) % 360.0 - 180.0)
            val choice = Choice(
                imageId = image.optString("id"),
                imageUrl = imageUrl,
                coordinate = point,
                distanceMeters = distance.roundToInt(),
                headingDifferenceDegrees = headingDelta.roundToInt(),
                compassAngle = angle,
                capturedAt = image.optLong("captured_at", 0L).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString().take(10) },
                creator = image.optJSONObject("creator")?.optString("username")?.takeIf(String::isNotBlank),
            )
            choice to (distance + headingDelta * HEADING_WEIGHT)
        }.minWithOrNull(compareBy<Pair<Choice, Double>> { it.second }.thenBy { it.first.imageId })?.first
    }
}

private const val METERS_PER_DEGREE = 111_320.0

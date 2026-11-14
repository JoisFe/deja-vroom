package ai.firstlook.app.data

import ai.firstlook.app.model.GeoCoordinate
import ai.firstlook.app.model.RouteGeometryStatus
import ai.firstlook.app.model.RouteSnapshot
import ai.firstlook.app.model.RouteSource
import ai.firstlook.app.model.RouteLane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

private const val OPENSTREETMAP_ATTRIBUTION = "© OpenStreetMap contributors · ODbL 1.0"

/** A user-submitted, one-shot destination lookup; this does not implement autocomplete. */
class OsmRouteRepository(
    private val geocoder: DestinationGeocoder = NominatimDestinationGeocoder,
    private val router: DrivingRouteClient = OsrmDrivingRouteClient,
    private val analyzer: RouteAnalyzer = RouteAnalyzer(maxSegments = 5),
) : RouteRepository {
    override suspend fun preview(origin: String, destination: String): RouteSnapshot = withContext(Dispatchers.IO) {
        val originQuery = origin.trim().take(120)
        val destinationQuery = destination.trim().take(120)
        require(originQuery.isNotBlank()) { "출발지를 입력하세요." }
        require(destinationQuery.isNotBlank()) { "목적지를 입력하세요." }
        val originPlace = geocoder.resolve(originQuery)
        val destinationPlace = geocoder.resolve(destinationQuery)
        val result = router.route(originPlace.coordinate, destinationPlace.coordinate)
        val routeId = "osrm-${Integer.toHexString("${originPlace.coordinate}:${destinationPlace.coordinate}".hashCode())}"
        val route = RouteSnapshot(
            origin = originPlace.displayName,
            destination = destinationPlace.displayName,
            durationMinutes = (result.durationSeconds / 60.0).toInt().coerceAtLeast(1),
            distanceKm = (result.distanceMeters / 1000.0).toInt().coerceAtLeast(1),
            segments = emptyList(),
            routeId = routeId,
            source = RouteSource.EXTERNAL_ROUTE,
            geometryStatus = RouteGeometryStatus.VERIFIED,
            geometry = result.geometry,
        )
        val candidates = result.intersections.mapIndexedNotNull { index, intersection ->
            intersection.toFeature("$routeId-junction-$index")
        }
        val selected = analyzer.analyze(route, candidates)
        require(selected.isNotEmpty()) { "이 경로에서 복잡한 교차로 후보를 찾지 못했습니다. 다른 목적지를 입력해 주세요." }
        route.copy(segments = selected)
    }
}

data class GeocodedDestination(val coordinate: GeoCoordinate, val displayName: String)

fun interface DestinationGeocoder {
    fun resolve(query: String): GeocodedDestination
}

data class RouteIntersectionObservation(
    val coordinate: GeoCoordinate,
    val branchCount: Int,
    val maneuverType: String,
    val roadName: String,
    val highwayJunction: Boolean,
    val routeLanes: List<RouteLane> = emptyList(),
)

data class DrivingRouteResult(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val geometry: List<GeoCoordinate>,
    val intersections: List<RouteIntersectionObservation>,
)

fun interface DrivingRouteClient {
    fun route(origin: GeoCoordinate, destination: GeoCoordinate): DrivingRouteResult
}

private object NominatimDestinationGeocoder : DestinationGeocoder {
    private val cache = LinkedHashMap<String, GeocodedDestination>(64, .75f, true)
    private var lastRequestAtMillis = 0L

    override fun resolve(query: String): GeocodedDestination {
        val key = query.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        synchronized(cache) { cache[key]?.let { return it } }
        synchronized(this) {
            val waitMillis = 1_050L - (System.currentTimeMillis() - lastRequestAtMillis)
            if (waitMillis > 0L) Thread.sleep(waitMillis)
            lastRequestAtMillis = System.currentTimeMillis()
        }
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val response = requestJson(
            URL("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=kr&q=$encoded"),
            mapOf("User-Agent" to "FirstLook-Android/0.1 (low-volume destination route preview)", "Accept-Language" to "ko,en"),
            serviceName = "Nominatim",
        ) as? JSONArray ?: error("장소 검색 응답을 읽을 수 없습니다.")
        val item = response.optJSONObject(0) ?: error("장소를 찾지 못했습니다. 출발지와 목적지를 구체적으로 입력해 주세요.")
        val coordinate = GeoCoordinate(item.getString("lat").toDouble(), item.getString("lon").toDouble())
        val result = GeocodedDestination(coordinate, item.optString("display_name").substringBefore(",").ifBlank { query })
        synchronized(cache) {
            cache[key] = result
            while (cache.size > 100) cache.remove(cache.keys.first())
        }
        return result
    }
}

private object OsrmDrivingRouteClient : DrivingRouteClient {
    override fun route(origin: GeoCoordinate, destination: GeoCoordinate): DrivingRouteResult {
        val coordinates = "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}"
        val url = URL("https://router.project-osrm.org/route/v1/driving/$coordinates?steps=true&alternatives=false&geometries=geojson&overview=full")
        val root = requestJson(url, serviceName = "OSRM") as? JSONObject ?: error("OSRM 경로 응답을 읽을 수 없습니다.")
        require(root.optString("code") == "Ok") { "도로 경로를 계산하지 못했습니다." }
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: error("목적지까지 이어지는 경로가 없습니다.")
        val rawCoordinates = route.optJSONObject("geometry")?.optJSONArray("coordinates") ?: JSONArray()
        require(rawCoordinates.length() in 2..20_000) { "경로 geometry가 비어 있거나 너무 큽니다." }
        val geometry = (0 until rawCoordinates.length()).map { index ->
            val coordinate = rawCoordinates.getJSONArray(index)
            GeoCoordinate(coordinate.getDouble(1), coordinate.getDouble(0))
        }
        val intersections = parseIntersections(route.optJSONArray("legs") ?: JSONArray())
        return DrivingRouteResult(route.getDouble("distance"), route.getDouble("duration"), geometry, intersections)
    }

    private fun parseIntersections(legs: JSONArray): List<RouteIntersectionObservation> {
        val results = mutableListOf<RouteIntersectionObservation>()
        for (legIndex in 0 until legs.length()) {
            val steps = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
            for (stepIndex in 0 until steps.length()) {
                val step = steps.optJSONObject(stepIndex) ?: continue
                val maneuver = step.optJSONObject("maneuver") ?: JSONObject()
                val maneuverType = maneuver.optString("type")
                val rampOrFork = maneuverType in setOf("fork", "merge", "on ramp", "off ramp", "roundabout", "rotary")
                val intersections = step.optJSONArray("intersections") ?: continue
                for (intersectionIndex in 0 until intersections.length()) {
                    val intersection = intersections.optJSONObject(intersectionIndex) ?: continue
                    val bearings = intersection.optJSONArray("bearings") ?: continue
                    val entry = intersection.optJSONArray("entry") ?: continue
                    val choices = (0 until minOf(bearings.length(), entry.length())).count { entry.optBoolean(it) }
                    val routeLanes = intersection.optJSONArray("lanes")
                    if (choices < 3 && !rampOrFork && (routeLanes?.length() ?: 0) == 0) continue
                    val location = intersection.optJSONArray("location") ?: continue
                    if (location.length() < 2) continue
                    val coordinate = runCatching { GeoCoordinate(location.getDouble(1), location.getDouble(0)) }.getOrNull() ?: continue
                    results += RouteIntersectionObservation(
                        coordinate = coordinate,
                        branchCount = choices,
                        maneuverType = maneuverType,
                        roadName = step.optString("name").ifBlank { "도로 분기점" },
                        highwayJunction = rampOrFork,
                        routeLanes = (0 until (routeLanes?.length() ?: 0)).mapNotNull { laneIndex ->
                            val lane = routeLanes?.optJSONObject(laneIndex) ?: return@mapNotNull null
                            RouteLane(
                                indications = lane.optJSONArray("indications")?.let { indications ->
                                    (0 until indications.length()).map { indications.optString(it) }.filter(String::isNotBlank)
                                }.orEmpty(),
                                validForRoute = lane.optBoolean("valid", false),
                            )
                        },
                    )
                }
            }
        }
        return results.distinctBy { "${(it.coordinate.latitude * 100_000).toInt()}:${(it.coordinate.longitude * 100_000).toInt()}" }
    }
}

private fun RouteIntersectionObservation.toFeature(id: String): RouteMapFeature {
    val topologyComplexity = ((branchCount - 2) / 3.0).coerceIn(0.0, 1.0)
    val maneuverSignal = if (maneuverType in setOf("fork", "merge", "on ramp", "off ramp", "roundabout", "rotary")) .9 else 0.0
    val junctionSignal = if (highwayJunction) .9 else 0.0
    val type = if (highwayJunction) RouteMapFeature.Type.HIGHWAY_JUNCTION else RouteMapFeature.Type.COMPLEX_INTERSECTION
    return RouteMapFeature(
        id = id,
        type = type,
        name = roadName,
        description = "$roadName · 진입 가능한 갈래 ${branchCount}개",
        coordinate = coordinate,
        source = "OSRM route steps / OSM road graph",
        confidence = .72,
        complexitySignal = topologyComplexity,
        maneuverDensitySignal = maneuverSignal,
        highwayJunctionSignal = junctionSignal,
        branchCount = branchCount,
        routeLanes = routeLanes,
    )
}

private fun requestJson(url: URL, headers: Map<String, String> = emptyMap(), serviceName: String = "OpenStreetMap"): Any {
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 12_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "FirstLook-Android/0.1 (low-volume route preview prototype)")
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
    }
    try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        require(status in 200..299) { "$serviceName 서비스가 HTTP $status 응답을 반환했습니다." }
        return if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
    } finally {
        connection.disconnect()
    }
}

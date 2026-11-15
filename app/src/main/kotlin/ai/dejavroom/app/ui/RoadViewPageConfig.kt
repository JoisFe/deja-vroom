package ai.dejavroom.app.ui

import ai.dejavroom.app.data.JunctionRecordMatch
import ai.dejavroom.app.data.JunctionRecordMatcher
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.JunctionDataSource
import ai.dejavroom.app.model.JunctionRecord
import ai.dejavroom.app.model.RouteGeometryStatus
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.RouteSource
import org.json.JSONArray
import org.json.JSONObject

// What the app hands the road view page once the page says it is ready: the point to show, where its
// view stands, and the drive up to it.

// Reviewed OSM junction fixture (debug assets only) for the developer entry point without a route.
private const val JUNCTION_FIXTURE = "junctions/hannam-daero-demo-record.json"

internal fun buildRoutePreviewConfig(route: RouteSnapshot, candidates: List<BriefingSegment>, selectedIndex: Int): String {
    require(route.geometryStatus == RouteGeometryStatus.VERIFIED && route.geometry.size >= 2) { "검증된 실제 경로 geometry가 없습니다." }
    val selected = candidates.getOrNull(selectedIndex) ?: error("선택한 복잡 구간 후보가 없습니다.")
    val eligible = listOf(selected).filter { segment ->
        segment.coordinate != null && segment.headingDegrees != null && segment.routeMatchStatus.name == "VERIFIED"
    }
    require(eligible.isNotEmpty()) { "선택한 구간이 route matcher 검증을 통과하지 못했습니다." }
    val first = eligible.first()
    val candidatesJson = JSONArray().apply {
        eligible.forEach { segment ->
            val coordinate = requireNotNull(segment.coordinate)
            val approach = segment.approach
            put(JSONObject()
                // The junction itself: where the page puts its marker.
                .put("latitude", coordinate.latitude)
                .put("longitude", coordinate.longitude)
                // Where the panorama starts and which way it faces: before the junction, along the route.
                .put("viewLatitude", approach?.coordinate?.latitude ?: coordinate.latitude)
                .put("viewLongitude", approach?.coordinate?.longitude ?: coordinate.longitude)
                .put("leadMeters", approach?.leadMeters ?: 0)
                // Frames of the drive from the viewpoint through the junction; the page plays them in order.
                .put("drive", JSONArray().apply {
                    segment.approachDrive.forEach { pose ->
                        put(JSONObject()
                            .put("latitude", pose.coordinate.latitude)
                            .put("longitude", pose.coordinate.longitude)
                            .put("headingDegrees", pose.headingDegrees)
                            .put("remainingMeters", pose.remainingMeters))
                    }
                })
                .put("headingDegrees", approach?.headingDegrees ?: segment.headingDegrees)
                .put("name", segment.name)
                .put("score", segment.selectionScore ?: 0.0)
                .put("displayIndex", selectedIndex)
                .put("evidence", JSONArray(segment.selectionEvidence.take(3)))
                .put("routeLanes", JSONArray().apply {
                    segment.routeLanes.forEach { lane ->
                        put(JSONObject().put("indications", JSONArray(lane.indications)).put("validForRoute", lane.validForRoute))
                    }
                }))
        }
    }
    return JSONObject()
        .put("type", "initialize")
        .put("latitude", requireNotNull(first.coordinate).latitude)
        .put("longitude", requireNotNull(first.coordinate).longitude)
        .put("heading", first.approach?.headingDegrees ?: first.headingDegrees)
        .put("routeId", route.routeId)
        .put("routeSource", "OSRM/OSM")
        .put("candidates", candidatesJson)
        .toString()
}

/** Loads the generated OSM fixture and matches it against a synthetic, route-scoped path.
 * This only exists in the debug source set; the fixed eligibility signals are not vehicle inputs. */
internal fun buildJunctionPreviewConfig(context: android.content.Context): String {
    val json = context.assets.open(JUNCTION_FIXTURE).bufferedReader().use { JSONObject(it.readText()) }
    fun coordinate(array: JSONArray, index: Int = 0) = GeoCoordinate(array.getDouble(index), array.getDouble(index + 1))
    fun path(name: String): List<GeoCoordinate> = json.getJSONArray(name).let { array ->
        (0 until array.length()).map { coordinate(array.getJSONArray(it)) }
    }

    val source = json.getJSONObject("source")
    val ways = source.getJSONObject("ways")
    val review = json.getJSONObject("review")
    val record = JunctionRecord(
        id = json.getString("id"),
        routeId = json.getString("routeId"),
        junction = coordinate(json.getJSONArray("junction")),
        approachPoint = coordinate(json.getJSONArray("at")),
        panDegrees = json.getDouble("pan"),
        cautionPoint = coordinate(json.getJSONArray("caution")),
        message = json.getString("message"),
        detourCost = if (json.isNull("cost")) null else json.getString("cost"),
        keepPath = path("keepPath"),
        trapPath = path("trapPath"),
        verifiedLaneMessage = if (json.isNull("verifiedLaneMessage")) null else json.getString("verifiedLaneMessage"),
        laneGuidanceVerified = json.getBoolean("laneGuidanceVerified"),
        source = JunctionDataSource(
            provider = source.getString("provider"),
            attribution = source.getString("attribution"),
            license = source.getString("license"),
            retrievedAt = source.optString("retrievedAt").takeIf { it.isNotBlank() },
            approachWayId = ways.getJSONObject("approach").getString("id"),
            keepWayId = ways.getJSONObject("keep").getString("id"),
            trapWayId = ways.getJSONObject("trap").getString("id"),
        ),
        manuallyVerified = review.getBoolean("manuallyVerified"),
        roadViewCoverageVerified = review.getBoolean("roadviewCoverageVerified"),
    )
    check(record.mayUseRoadView && json.getBoolean("useRoadview")) { "교차로와 Roadview 검수가 완료되지 않았습니다." }

    val route = RouteSnapshot(
        origin = "한남대로 북측 접근로",
        destination = "한남대로 남측",
        durationMinutes = 1,
        distanceKm = 1,
        segments = emptyList(),
        routeId = record.routeId,
        source = RouteSource.EXTERNAL_ROUTE,
        geometryStatus = RouteGeometryStatus.VERIFIED,
        geometry = listOf(record.approachPoint, record.junction) + record.keepPath,
    )
    val match = JunctionRecordMatcher().match(route, record)
    check(match is JunctionRecordMatch.Verified) { "교차로가 경로와 일치하지 않습니다: $match" }

    fun jsonPath(points: List<GeoCoordinate>) = JSONArray().apply {
        points.forEach { put(JSONObject().put("latitude", it.latitude).put("longitude", it.longitude)) }
    }
    val webRecord = JSONObject()
        .put("manuallyVerified", record.manuallyVerified)
        .put("roadViewCoverageVerified", record.roadViewCoverageVerified)
        .put("caution", JSONObject().put("latitude", record.cautionPoint.latitude).put("longitude", record.cautionPoint.longitude))
        .put("keepPath", jsonPath(record.keepPath))
        .put("trapPath", jsonPath(record.trapPath))

    return JSONObject()
        .put("type", "initialize")
        .put("latitude", record.approachPoint.latitude)
        .put("longitude", record.approachPoint.longitude)
        .put("heading", record.panDegrees)
        .put("routeVerified", true)
        .put("photoMatchVerified", true)
        .put("overlayAllowed", true)
        .put("developerFixture", true)
        .put("junctionRecord", webRecord)
        .toString()
}

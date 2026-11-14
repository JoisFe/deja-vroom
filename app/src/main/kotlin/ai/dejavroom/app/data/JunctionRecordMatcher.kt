package ai.dejavroom.app.data

import ai.dejavroom.app.domain.RouteGeometryMatcher
import ai.dejavroom.app.domain.RouteGeometryMatch
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.JunctionRecord
import ai.dejavroom.app.model.RouteSegmentCandidate
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.SegmentType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Matches a manually reviewed, route-scoped junction record to that route's verified geometry. */
class JunctionRecordMatcher(
    private val geometryMatcher: RouteGeometryMatcher = RouteGeometryMatcher(),
) {
    fun match(route: RouteSnapshot, record: JunctionRecord): JunctionRecordMatch {
        if (route.routeId != record.routeId) return JunctionRecordMatch.Rejected("route id mismatch")
        if (!record.manuallyVerified) return JunctionRecordMatch.Rejected("junction record is not manually verified")
        val offset = nearestOffset(route.geometry, record.junction)
            ?: return JunctionRecordMatch.Rejected("junction is outside or ambiguous on route geometry")
        val candidate = RouteSegmentCandidate(
            id = record.id,
            type = SegmentType.INTERSECTION,
            name = record.id,
            description = record.message,
            routeOffsetMeters = offset,
            coordinate = record.junction,
            approachCoordinate = record.approachPoint,
            headingDegrees = record.panDegrees,
            dataConfidence = 1.0,
            verifiedGuidance = record.verifiedLaneMessage,
            laneGuidanceVerified = record.laneGuidanceVerified,
            evidence = listOf(
                "수동 검수 교차로 기록",
                "${record.source.provider} way ${record.source.approachWayId}/${record.source.keepWayId}/${record.source.trapWayId}",
                record.source.attribution,
            ),
            sourceRouteId = record.routeId,
            junctionRecord = record,
        )
        return when (val result = geometryMatcher.match(route, candidate)) {
            is RouteGeometryMatch.Verified -> JunctionRecordMatch.Verified(
                candidate.copy(routeOffsetMeters = result.routeOffsetMeters, approachCoordinate = result.approachCoordinate),
                record,
                result.lateralDistanceMeters,
                result.headingDifferenceDegrees,
            )
            is RouteGeometryMatch.Rejected -> JunctionRecordMatch.Rejected(result.reason)
        }
    }

    private data class Projection(val offset: Double, val distance: Double)

    private fun nearestOffset(points: List<GeoCoordinate>, target: GeoCoordinate): Double? {
        if (points.size < 2) return null
        var traversed = 0.0
        val projections = mutableListOf<Projection>()
        for ((start, end) in points.zipWithNext()) {
            val length = distance(start, end)
            if (length <= 0.01) continue
            val latitude = Math.toRadians((start.latitude + end.latitude + target.latitude) / 3.0)
            val sx = Math.toRadians(start.longitude - target.longitude) * EARTH_RADIUS * cos(latitude)
            val sy = Math.toRadians(start.latitude - target.latitude) * EARTH_RADIUS
            val ex = Math.toRadians(end.longitude - target.longitude) * EARTH_RADIUS * cos(latitude)
            val ey = Math.toRadians(end.latitude - target.latitude) * EARTH_RADIUS
            val dx = ex - sx
            val dy = ey - sy
            val denominator = dx * dx + dy * dy
            val fraction = if (denominator <= 0.0) 0.0 else (-(sx * dx + sy * dy) / denominator).coerceIn(0.0, 1.0)
            projections += Projection(traversed + fraction * length, hypot(sx + fraction * dx, sy + fraction * dy))
            traversed += length
        }
        val nearest = projections.minByOrNull { it.distance } ?: return null
        val second = projections.sortedBy { it.distance }.getOrNull(1)
        if (second != null && second.distance - nearest.distance <= 12.0 && abs(second.offset - nearest.offset) >= 100.0) return null
        return nearest.offset
    }

    private fun distance(a: GeoCoordinate, b: GeoCoordinate): Double {
        val meanLat = Math.toRadians((a.latitude + b.latitude) / 2.0)
        return hypot(Math.toRadians(b.longitude - a.longitude) * EARTH_RADIUS * cos(meanLat),
            Math.toRadians(b.latitude - a.latitude) * EARTH_RADIUS)
    }

    private companion object { const val EARTH_RADIUS = 6_371_000.0 }
}

sealed interface JunctionRecordMatch {
    data class Verified(
        val candidate: RouteSegmentCandidate,
        val record: JunctionRecord,
        val lateralDistanceMeters: Double,
        val headingDifferenceDegrees: Double,
    ) : JunctionRecordMatch

    data class Rejected(val reason: String) : JunctionRecordMatch
}

enum class RoadViewGear { PARKED, NOT_PARKED, UNKNOWN }
enum class RoadViewUxState { ALLOWED, RESTRICTED, UNKNOWN }
enum class RoadViewTrafficState { CLEAR_RESPONSE, BLOCKING_EVENT, UNAVAILABLE }
enum class RoadViewPhotoMatch { MATCHED, MISMATCHED, UNKNOWN }
enum class RoadViewProviderPermission { ALLOWED, DENIED, UNKNOWN }
enum class RoadViewBlockReason {
    RECORD_UNVERIFIED, RECORD_DISABLED, ROUTE_MISMATCH, GEAR_NOT_PARKED, GEAR_UNKNOWN, UX_RESTRICTED, UX_UNKNOWN,
    TRAFFIC_EVENT, TRAFFIC_UNAVAILABLE, PHOTO_MISMATCH, PHOTO_MATCH_UNKNOWN, PROVIDER_DENIED, PROVIDER_UNKNOWN,
}

/** Fail-closed conjunction for displaying the road photo and route-specific overlays. */
object RoadViewEligibility {
    fun blockedBy(
        record: JunctionRecord,
        routeMatch: JunctionRecordMatch,
        gear: RoadViewGear,
        ux: RoadViewUxState,
        traffic: RoadViewTrafficState,
        photoMatch: RoadViewPhotoMatch,
        providerPermission: RoadViewProviderPermission,
    ): RoadViewBlockReason? {
        if (!record.manuallyVerified) return RoadViewBlockReason.RECORD_UNVERIFIED
        if (!record.mayUseRoadView) return RoadViewBlockReason.RECORD_DISABLED
        val verifiedMatch = routeMatch as? JunctionRecordMatch.Verified
            ?: return RoadViewBlockReason.ROUTE_MISMATCH
        if (verifiedMatch.record != record || verifiedMatch.candidate.sourceRouteId != record.routeId) return RoadViewBlockReason.ROUTE_MISMATCH
        if (gear != RoadViewGear.PARKED) return if (gear == RoadViewGear.UNKNOWN) RoadViewBlockReason.GEAR_UNKNOWN else RoadViewBlockReason.GEAR_NOT_PARKED
        if (ux != RoadViewUxState.ALLOWED) return if (ux == RoadViewUxState.UNKNOWN) RoadViewBlockReason.UX_UNKNOWN else RoadViewBlockReason.UX_RESTRICTED
        if (traffic != RoadViewTrafficState.CLEAR_RESPONSE) return if (traffic == RoadViewTrafficState.UNAVAILABLE) RoadViewBlockReason.TRAFFIC_UNAVAILABLE else RoadViewBlockReason.TRAFFIC_EVENT
        if (photoMatch != RoadViewPhotoMatch.MATCHED) return if (photoMatch == RoadViewPhotoMatch.UNKNOWN) RoadViewBlockReason.PHOTO_MATCH_UNKNOWN else RoadViewBlockReason.PHOTO_MISMATCH
        if (providerPermission != RoadViewProviderPermission.ALLOWED) return if (providerPermission == RoadViewProviderPermission.UNKNOWN) RoadViewBlockReason.PROVIDER_UNKNOWN else RoadViewBlockReason.PROVIDER_DENIED
        return null
    }
}

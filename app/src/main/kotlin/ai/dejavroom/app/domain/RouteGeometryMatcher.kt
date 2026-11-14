package ai.dejavroom.app.domain

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RouteGeometryStatus
import ai.dejavroom.app.model.RouteSegmentCandidate
import ai.dejavroom.app.model.RouteSource
import ai.dejavroom.app.model.RouteSnapshot
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Conservative geometry check. It cannot distinguish stacked roads without elevation data. */
class RouteGeometryMatcher(
    private val maxLateralDistanceMeters: Double = 80.0,
    private val maxOffsetDifferenceMeters: Double = 150.0,
    private val maxHeadingDifferenceDegrees: Double = 45.0,
    private val approachLeadMeters: Double = 200.0,
    private val ambiguityDistanceMeters: Double = 12.0,
    private val ambiguityOffsetMeters: Double = 100.0,
) {
    init {
        require(maxLateralDistanceMeters >= 0.0)
        require(maxOffsetDifferenceMeters >= 0.0)
        require(maxHeadingDifferenceDegrees in 0.0..180.0)
        require(approachLeadMeters >= 0.0)
        require(ambiguityDistanceMeters >= 0.0)
        require(ambiguityOffsetMeters >= 0.0)
    }

    fun match(route: RouteSnapshot, candidate: RouteSegmentCandidate): RouteGeometryMatch {
        if (route.source == RouteSource.CURATED_DEMO || route.geometryStatus != RouteGeometryStatus.VERIFIED) {
            return RouteGeometryMatch.Rejected("route geometry is not verified")
        }
        if (candidate.sourceRouteId != route.routeId) return RouteGeometryMatch.Rejected("route source mismatch")
        if (route.geometry.size < 2 || candidate.headingDegrees == null ||
            !candidate.headingDegrees.isFinite() || candidate.headingDegrees !in 0.0..360.0 ||
            !candidate.routeOffsetMeters.isFinite() || candidate.routeOffsetMeters < 0.0
        ) {
            return RouteGeometryMatch.Rejected("route geometry or candidate heading is missing")
        }

        var cumulativeOffsetMeters = 0.0
        val projections = route.geometry.zipWithNext().mapNotNull { (start, end) ->
            val segmentLength = distanceMeters(start, end)
            if (segmentLength <= 0.01) return@mapNotNull null
            val projection = project(candidate.coordinate, start, end)
            val offset = cumulativeOffsetMeters + segmentLength * projection.fraction
            cumulativeOffsetMeters += segmentLength
            Projection(
                distanceMeters = projection.distanceMeters,
                routeOffsetMeters = offset,
                headingDegrees = bearing(start, end),
            )
        }.sortedBy { it.distanceMeters }

        val nearest = projections.firstOrNull() ?: return RouteGeometryMatch.Rejected("route geometry has no usable segment")
        val second = projections.getOrNull(1)
        if (second != null && second.distanceMeters - nearest.distanceMeters <= ambiguityDistanceMeters &&
            abs(second.routeOffsetMeters - nearest.routeOffsetMeters) >= ambiguityOffsetMeters
        ) {
            return RouteGeometryMatch.Rejected("multiple route projections are ambiguous")
        }

        val headingDifference = angularDifference(candidate.headingDegrees, nearest.headingDegrees)
        if (nearest.distanceMeters > maxLateralDistanceMeters) return RouteGeometryMatch.Rejected("candidate is too far from route")
        if (abs(candidate.routeOffsetMeters - nearest.routeOffsetMeters) > maxOffsetDifferenceMeters) {
            return RouteGeometryMatch.Rejected("route offset does not match geometry")
        }
        if (headingDifference > maxHeadingDifferenceDegrees) return RouteGeometryMatch.Rejected("candidate heading does not match route")

        return RouteGeometryMatch.Verified(
            routeOffsetMeters = nearest.routeOffsetMeters,
            lateralDistanceMeters = nearest.distanceMeters,
            headingDifferenceDegrees = headingDifference,
            approachCoordinate = coordinateAtOffset(route.geometry, (nearest.routeOffsetMeters - approachLeadMeters).coerceAtLeast(0.0)),
        )
    }

    private data class Projection(val distanceMeters: Double, val routeOffsetMeters: Double, val headingDegrees: Double)
    private data class LocalProjection(val distanceMeters: Double, val fraction: Double)

    private fun project(point: GeoCoordinate, start: GeoCoordinate, end: GeoCoordinate): LocalProjection {
        val meanLatitude = Math.toRadians((start.latitude + end.latitude + point.latitude) / 3.0)
        val startX = Math.toRadians(start.longitude - point.longitude) * EARTH_RADIUS_METERS * cos(meanLatitude)
        val startY = Math.toRadians(start.latitude - point.latitude) * EARTH_RADIUS_METERS
        val endX = Math.toRadians(end.longitude - point.longitude) * EARTH_RADIUS_METERS * cos(meanLatitude)
        val endY = Math.toRadians(end.latitude - point.latitude) * EARTH_RADIUS_METERS
        val dx = endX - startX
        val dy = endY - startY
        val denominator = dx * dx + dy * dy
        val fraction = if (denominator == 0.0) 0.0 else (-(startX * dx + startY * dy) / denominator).coerceIn(0.0, 1.0)
        return LocalProjection(hypot(startX + fraction * dx, startY + fraction * dy), fraction)
    }

    private fun bearing(start: GeoCoordinate, end: GeoCoordinate): Double {
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        return (Math.toDegrees(atan2(sin(dLon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))) + 360.0) % 360.0
    }

    private fun distanceMeters(start: GeoCoordinate, end: GeoCoordinate): Double {
        val meanLatitude = Math.toRadians((start.latitude + end.latitude) / 2.0)
        val dx = Math.toRadians(end.longitude - start.longitude) * EARTH_RADIUS_METERS * cos(meanLatitude)
        val dy = Math.toRadians(end.latitude - start.latitude) * EARTH_RADIUS_METERS
        return hypot(dx, dy)
    }

    private fun coordinateAtOffset(points: List<GeoCoordinate>, requestedOffsetMeters: Double): GeoCoordinate {
        var traversedMeters = 0.0
        for ((start, end) in points.zipWithNext()) {
            val length = distanceMeters(start, end)
            if (traversedMeters + length >= requestedOffsetMeters && length > 0.0) {
                val fraction = ((requestedOffsetMeters - traversedMeters) / length).coerceIn(0.0, 1.0)
                return GeoCoordinate(
                    latitude = start.latitude + (end.latitude - start.latitude) * fraction,
                    longitude = start.longitude + (end.longitude - start.longitude) * fraction,
                )
            }
            traversedMeters += length
        }
        return points.last()
    }

    private fun angularDifference(first: Double, second: Double): Double {
        val normalizedFirst = ((first % 360.0) + 360.0) % 360.0
        val delta = abs(normalizedFirst - second)
        return minOf(delta, 360.0 - delta)
    }

    private companion object { const val EARTH_RADIUS_METERS = 6_371_000.0 }
}

sealed interface RouteGeometryMatch {
    data class Verified(
        val routeOffsetMeters: Double,
        val lateralDistanceMeters: Double,
        val headingDifferenceDegrees: Double,
        val approachCoordinate: GeoCoordinate,
    ) : RouteGeometryMatch

    data class Rejected(val reason: String) : RouteGeometryMatch
}

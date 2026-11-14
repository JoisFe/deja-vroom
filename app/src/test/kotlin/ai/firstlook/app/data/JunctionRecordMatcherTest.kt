package ai.firstlook.app.data

import ai.firstlook.app.model.GeoCoordinate
import ai.firstlook.app.model.JunctionDataSource
import ai.firstlook.app.model.JunctionRecord
import ai.firstlook.app.model.RouteGeometryStatus
import ai.firstlook.app.model.RouteSnapshot
import ai.firstlook.app.model.RouteSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JunctionRecordMatcherTest {
    private val junction = GeoCoordinate(37.5, 127.0)
    private val route = RouteSnapshot(
        origin = "origin", destination = "destination", durationMinutes = 10, distanceKm = 2,
        segments = emptyList(), routeId = "route-1", source = RouteSource.EXTERNAL_ROUTE,
        geometryStatus = RouteGeometryStatus.VERIFIED,
        geometry = listOf(GeoCoordinate(37.498, 127.0), junction, GeoCoordinate(37.502, 127.0)),
    )
    private val record = JunctionRecord(
        id = "fixture-junction", routeId = "route-1", junction = junction,
        approachPoint = GeoCoordinate(37.499, 127.0), panDegrees = 0.0,
        cautionPoint = GeoCoordinate(37.5001, 127.0), message = "분기점 표지를 미리 확인하세요.", detourCost = null,
        keepPath = listOf(GeoCoordinate(37.5, 127.0002), GeoCoordinate(37.5, 127.0004), GeoCoordinate(37.5, 127.0006)),
        trapPath = listOf(GeoCoordinate(37.5, 126.9998), GeoCoordinate(37.5, 126.9996), GeoCoordinate(37.5, 126.9994)),
        source = JunctionDataSource("OpenStreetMap", "© OpenStreetMap contributors", "ODbL-1.0", null, "1", "2", "3"),
        manuallyVerified = true, roadViewCoverageVerified = true,
    )
    private val matchingResult = JunctionRecordMatcher().match(route, record)

    @Test fun matchesReviewedRecordToSameVerifiedRoute() {
        val result = JunctionRecordMatcher().match(route, record)
        assertTrue(result is JunctionRecordMatch.Verified)
        result as JunctionRecordMatch.Verified
        assertEquals("fixture-junction", result.candidate.id)
        assertEquals("route-1", result.candidate.sourceRouteId)
        assertEquals(0.0, result.headingDifferenceDegrees, 0.01)
        assertTrue(result.lateralDistanceMeters < 1.0)
    }

    @Test fun rejectsDifferentRouteAndUnverifiedGeometry() {
        assertTrue(JunctionRecordMatcher().match(route.copy(routeId = "route-2"), record) is JunctionRecordMatch.Rejected)
        assertTrue(JunctionRecordMatcher().match(route.copy(geometryStatus = RouteGeometryStatus.UNKNOWN), record) is JunctionRecordMatch.Rejected)
    }

    @Test fun rejectsUnreviewedOrOffRouteRecord() {
        assertTrue(JunctionRecordMatcher().match(route, record.copy(manuallyVerified = false, roadViewCoverageVerified = false)) is JunctionRecordMatch.Rejected)
        assertTrue(JunctionRecordMatcher().match(route, record.copy(junction = GeoCoordinate(37.5, 127.01))) is JunctionRecordMatch.Rejected)
    }

    @Test fun keepsReviewedIntersectionForFallbackDiagramWithoutRoadviewCoverage() {
        val withoutCoverage = record.copy(roadViewCoverageVerified = false)
        assertTrue(JunctionRecordMatcher().match(route, withoutCoverage) is JunctionRecordMatch.Verified)
        assertFalse(withoutCoverage.mayUseRoadView)
    }

    @Test fun roadviewEligibilityFailsClosedForMissingSignals() {
        assertEquals(
            RoadViewBlockReason.GEAR_UNKNOWN,
            RoadViewEligibility.blockedBy(record, matchingResult, RoadViewGear.UNKNOWN, RoadViewUxState.ALLOWED,
                RoadViewTrafficState.CLEAR_RESPONSE, RoadViewPhotoMatch.MATCHED, RoadViewProviderPermission.ALLOWED),
        )
        assertEquals(
            RoadViewBlockReason.UX_RESTRICTED,
            RoadViewEligibility.blockedBy(record, matchingResult, RoadViewGear.PARKED, RoadViewUxState.RESTRICTED,
                RoadViewTrafficState.CLEAR_RESPONSE, RoadViewPhotoMatch.MATCHED, RoadViewProviderPermission.ALLOWED),
        )
        assertEquals(
            RoadViewBlockReason.TRAFFIC_UNAVAILABLE,
            RoadViewEligibility.blockedBy(record, matchingResult, RoadViewGear.PARKED, RoadViewUxState.ALLOWED,
                RoadViewTrafficState.UNAVAILABLE, RoadViewPhotoMatch.MATCHED, RoadViewProviderPermission.ALLOWED),
        )
        assertEquals(
            RoadViewBlockReason.PHOTO_MISMATCH,
            RoadViewEligibility.blockedBy(record, matchingResult, RoadViewGear.PARKED, RoadViewUxState.ALLOWED,
                RoadViewTrafficState.CLEAR_RESPONSE, RoadViewPhotoMatch.MISMATCHED, RoadViewProviderPermission.ALLOWED),
        )
        assertEquals(
            RoadViewBlockReason.PROVIDER_UNKNOWN,
            RoadViewEligibility.blockedBy(record, matchingResult, RoadViewGear.PARKED, RoadViewUxState.ALLOWED,
                RoadViewTrafficState.CLEAR_RESPONSE, RoadViewPhotoMatch.MATCHED, RoadViewProviderPermission.UNKNOWN),
        )
        assertFalse(record.copy(roadViewCoverageVerified = false).mayUseRoadView)
    }

    @Test fun allowsOnlyWhenEveryGateIsExplicitlySatisfied() {
        assertEquals(
            null,
            RoadViewEligibility.blockedBy(record, matchingResult, RoadViewGear.PARKED, RoadViewUxState.ALLOWED,
                RoadViewTrafficState.CLEAR_RESPONSE, RoadViewPhotoMatch.MATCHED, RoadViewProviderPermission.ALLOWED),
        )
    }

    @Test fun rejectsUnknownOrDifferentRouteMatch() {
        assertEquals(
            RoadViewBlockReason.ROUTE_MISMATCH,
            RoadViewEligibility.blockedBy(record, JunctionRecordMatch.Rejected("no match"), RoadViewGear.PARKED,
                RoadViewUxState.ALLOWED, RoadViewTrafficState.CLEAR_RESPONSE, RoadViewPhotoMatch.MATCHED,
                RoadViewProviderPermission.ALLOWED),
        )
        val other = record.copy(id = "other-junction")
        assertEquals(
            RoadViewBlockReason.ROUTE_MISMATCH,
            RoadViewEligibility.blockedBy(other, matchingResult, RoadViewGear.PARKED,
                RoadViewUxState.ALLOWED, RoadViewTrafficState.CLEAR_RESPONSE, RoadViewPhotoMatch.MATCHED,
                RoadViewProviderPermission.ALLOWED),
        )
    }
}

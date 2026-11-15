package ai.dejavroom.app.data

import ai.dejavroom.app.domain.RouteAnalyzer
import ai.dejavroom.app.domain.RouteMapFeature
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.PlaceInput
import ai.dejavroom.app.model.RouteGeometryStatus
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.RouteSource
import ai.dejavroom.app.model.SegmentType

interface RouteRepository {
    suspend fun preview(origin: String, destination: String): RouteSnapshot

    /**
     * Uses a place picked from suggestions when available, so the route does not re-geocode it.
     * Throws [RoutePreviewException] for everything that goes wrong, except [PlaceSearchUnavailableException]
     * (the place search's setup hint) and cancellation.
     */
    suspend fun preview(origin: PlaceInput, destination: PlaceInput): RouteSnapshot =
        preview(origin.text, destination.text)
}

interface RouteMapFeatureSource {
    suspend fun features(route: RouteSnapshot): List<RouteMapFeature>
}

/** Safe fallback until an approved route/map data source can provide candidates. */
class EmptyRouteMapFeatureSource : RouteMapFeatureSource {
    override suspend fun features(route: RouteSnapshot): List<RouteMapFeature> = emptyList()
}

/** Detects geometry turns and applies source-tagged map features to their matching route. */
class AnalyzingRouteRepository(
    private val routeRepository: RouteRepository,
    private val featureSource: RouteMapFeatureSource = EmptyRouteMapFeatureSource(),
    private val analyzer: RouteAnalyzer = RouteAnalyzer(),
) : RouteRepository {
    override suspend fun preview(origin: String, destination: String): RouteSnapshot {
        val route = routeRepository.preview(origin, destination)
        if (route.source == RouteSource.CURATED_DEMO) return route
        val segments = analyzer.analyze(route, featureSource.features(route))
        return route.copy(segments = segments)
    }
}

interface JunctionRecordSource {
    suspend fun records(route: RouteSnapshot): List<ai.dejavroom.app.model.JunctionRecord>
}

class EmptyJunctionRecordSource : JunctionRecordSource {
    override suspend fun records(route: RouteSnapshot): List<ai.dejavroom.app.model.JunctionRecord> = emptyList()
}

/** Adds only reviewed records that match the exact verified route; missing sources return no records. */
class JunctionAwareRouteRepository(
    private val routeRepository: RouteRepository,
    private val recordSource: JunctionRecordSource = EmptyJunctionRecordSource(),
    private val matcher: JunctionRecordMatcher = JunctionRecordMatcher(),
    private val analyzer: RouteAnalyzer = RouteAnalyzer(),
) : RouteRepository {
    override suspend fun preview(origin: String, destination: String): RouteSnapshot {
        val route = routeRepository.preview(origin, destination)
        if (route.source == RouteSource.CURATED_DEMO) return route
        val candidates = recordSource.records(route).mapNotNull { record ->
            (matcher.match(route, record) as? JunctionRecordMatch.Verified)?.candidate
        }
        return route.copy(segments = analyzer.analyze(route, additionalCandidates = candidates))
    }
}

class DemoRouteRepository : RouteRepository {
    override suspend fun preview(origin: String, destination: String): RouteSnapshot = RouteSnapshot(
        origin = origin.ifBlank { "수원시 영통구" },
        destination = destination.ifBlank { "강남역" },
        durationMinutes = 57,
        distanceKm = 48,
        segments = listOf(
            BriefingSegment("demo-1", SegmentType.INTERSECTION, "광교중앙로 복합 교차로", "여러 갈래로 나뉘는 교차로 구조를 미리 살펴봅니다.", "교차로 진입 전에 표지와 진행 방향을 확인하세요.", 8.2),
            BriefingSegment("demo-2", SegmentType.HIGHWAY_JUNCTION, "용인서울고속도로 연결 구간", "고속도로 연결 구간의 갈림 형태를 확인합니다.", "실제 진출 방향과 차로 정보는 연결된 내비게이션에서 확인하세요.", 19.6),
            BriefingSegment("demo-3", SegmentType.DESTINATION_ENTRY, "강남역 주변 진입 구간", "목적지 주변 도로가 복잡할 수 있어 진입 흐름을 확인합니다.", "목적지 인근에서는 내비게이션의 최신 안내를 따르세요.", 33.1),
        ),
        routeId = "curated-suwon-to-${destination.ifBlank { "gangnam" }}",
        source = RouteSource.CURATED_DEMO,
        geometryStatus = RouteGeometryStatus.UNAVAILABLE,
    )
}

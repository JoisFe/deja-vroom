package ai.dejavroom.app.domain

import ai.dejavroom.app.model.ApproachConditions
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RouteGeometryStatus
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.RouteSource
import ai.dejavroom.app.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAnalyzerTest {
    /** About 2.2 km straight north; one step is about 50 m, so index 20 is about 1 km in. */
    private val straight = (0..44).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }

    /** About 1 km north, then about 1 km east: one clear 90-degree turn near the 1 km mark. */
    private val lShaped = (0..20).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) } +
        (1..20).map { GeoCoordinate(37.50 + 20 * 0.00045, 127.00 + it * 0.000565) }

    private fun route(geometry: List<GeoCoordinate> = straight, source: RouteSource = RouteSource.EXTERNAL_ROUTE) = RouteSnapshot(
        origin = "A", destination = "B", durationMinutes = 5, distanceKm = 2, segments = emptyList(),
        routeId = "test-route", source = source, geometryStatus = RouteGeometryStatus.VERIFIED, geometry = geometry,
    )

    private fun junction(
        at: Int,
        complexity: Double = 0.7,
        errorCost: Double = 0.2,
        ambiguity: Double = 0.0,
        lanePressure: Double = 0.0,
        geometry: List<GeoCoordinate> = straight,
        approach: ApproachConditions = ApproachConditions(speedMps = 12.0),
    ) = RouteMapFeature(
        id = "j$at", type = RouteMapFeature.Type.COMPLEX_INTERSECTION, name = "j$at", description = "j$at",
        coordinate = geometry[at], source = "test", confidence = 0.7, complexitySignal = complexity, errorCostSignal = errorCost,
        ambiguitySignal = ambiguity, lanePressureSignal = lanePressure, reasons = listOf("이유 j$at"), approach = approach,
    )

    private val exit = { at: Int -> junction(at, complexity = 0.6, errorCost = 1.0) }

    private fun arrival(at: Int = 44) = RouteMapFeature(
        id = "arrival", type = RouteMapFeature.Type.DESTINATION_ACCESS, name = "도착 지점", description = "도착 지점",
        coordinate = straight[at], source = "test", confidence = 0.7, reasons = listOf("목적지는 진행 방향 오른쪽"),
        approach = ApproachConditions(arrival = true),
    )

    @Test
    fun `points come back in driving order and keep their rank`() {
        val segments = RouteAnalyzer().analyze(route(), listOf(junction(30), exit(10)))
        assertEquals(listOf("j10", "j30"), segments.map { it.id })
        assertEquals(listOf(1, 2), segments.map { it.selectionRank })
        assertTrue(segments[0].distanceFromStartKm < segments[1].distanceFromStartKm)
    }

    @Test
    fun `the less important point still comes first when the driver meets it first`() {
        val segments = RouteAnalyzer().analyze(route(), listOf(junction(10), exit(30)))
        assertEquals(listOf("j10", "j30"), segments.map { it.id })
        assertEquals(listOf(2, 1), segments.map { it.selectionRank })
    }

    @Test
    fun `the surroundings of the origin are skipped`() {
        val segments = RouteAnalyzer().analyze(route(), listOf(junction(3, complexity = 1.0, errorCost = 1.0), junction(20)))
        assertEquals(listOf("j20"), segments.map { it.id })
    }

    @Test
    fun `the final approach becomes one destination card`() {
        val segments = RouteAnalyzer().analyze(route(), listOf(junction(20), junction(41), arrival()))
        assertEquals(listOf("j20", "j41"), segments.map { it.id })
        assertEquals(listOf(SegmentType.INTERSECTION, SegmentType.DESTINATION_ENTRY), segments.map { it.type })
        val destination = segments.last()
        assertEquals("목적지 진입", destination.name)
        assertEquals(2, destination.selectionRank)
        assertEquals(listOf("j41", "도착 150m 전부터 진입"), destination.selectionEvidence.take(2))
        assertTrue("목적지는 진행 방향 오른쪽" in destination.selectionEvidence)
    }

    @Test
    fun `an arrival on a plain road is shown from close by`() {
        val segments = RouteAnalyzer().analyze(route(), listOf(arrival()))
        val destination = segments.single()
        assertEquals("도착 지점", destination.name)
        assertEquals(SegmentType.DESTINATION_ENTRY, destination.type)
        assertEquals(1, destination.selectionRank)
        assertEquals(80, destination.approach!!.leadMeters)
    }

    @Test
    fun `every point above the score is shown, however many there are`() {
        // 6 km with seven junctions 800 m apart that all matter, and one plain turn that does not.
        val long = (0..120).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }
        val worthSeeing = listOf(16, 32, 48, 64, 80, 96, 112).map { junction(it, geometry = long) }
        val plain = junction(40, complexity = 0.3, errorCost = 0.0, geometry = long)
        val segments = RouteAnalyzer().analyze(route(long), worthSeeing + plain)
        assertEquals(listOf("j16", "j32", "j48", "j64", "j80", "j96", "j112"), segments.map { it.id })
        // The ceiling only matters when a route would flood the screen.
        assertEquals(3, RouteAnalyzer(selection = SelectionRules(maxCards = 3)).analyze(route(long), worthSeeing).size)
    }

    @Test
    fun `the destination card always has a place next to the best junction`() {
        val analyzer = RouteAnalyzer(selection = SelectionRules(maxCards = 2))
        val segments = analyzer.analyze(route(), listOf(exit(10), junction(20), junction(30, complexity = 0.9), arrival()))
        assertEquals(listOf("j10", "arrival"), segments.map { it.id })
    }

    @Test
    fun `the preview stands before the junction and faces it`() {
        val segment = RouteAnalyzer().analyze(route(), listOf(junction(20))).single()
        val approach = segment.approach!!
        assertEquals(144, approach.leadMeters) // 12 m/s for 12 seconds
        assertTrue(approach.coordinate.latitude < straight[20].latitude)
        assertEquals(0.0, approach.headingDegrees, 1.0)
        assertEquals(approach.coordinate, segment.previewCoordinate)
        assertEquals(straight[20], segment.coordinate)
    }

    @Test
    fun `each point comes with the drive from its viewpoint up to the junction`() {
        val segment = RouteAnalyzer().analyze(route(), listOf(junction(20))).single()
        val drive = segment.approachDrive
        assertEquals(segment.approach!!.coordinate, drive.first().coordinate)
        assertEquals(144, drive.first().remainingMeters)
        // The drive ends on the junction: what lies beyond it is not this preview's to show.
        assertEquals(0, drive.last().remainingMeters)
        assertTrue(drive.size in 2..11)
        // Northbound traffic keeps to the east side, and so do the view and every frame of the drive.
        val junction = segment.coordinate!!
        (drive.map { it.coordinate } + segment.approach!!.coordinate).forEach { assertTrue(it.longitude > junction.longitude) }
        assertEquals(3.0, drive.last().coordinate.distanceMetersTo(junction), 0.3)
        // The marker for the junction itself stays on the junction.
        assertEquals(straight[20], junction)
        val arrivalDrive = RouteAnalyzer().analyze(route(), listOf(arrival())).single().approachDrive
        assertEquals(0, arrivalDrive.last().remainingMeters)
    }

    @Test
    fun `a sharp turn at a corner of the route is kept and viewed along the road leading in`() {
        val segment = RouteAnalyzer().analyze(route(lShaped), listOf(junction(20, geometry = lShaped))).single()
        assertEquals("j20", segment.id)
        assertEquals(90.0, segment.headingDegrees!!, 2.0) // leaves the corner eastward
        assertEquals(0.0, segment.approach!!.headingDegrees, 2.0) // but is previewed driving north toward it
    }

    @Test
    fun `when two decisions follow closely the one met first wins a near tie`() {
        val first = junction(20, complexity = 0.6, errorCost = 1.0, ambiguity = 0.7)
        val slightlyHarder = junction(24, complexity = 0.6, errorCost = 1.0, ambiguity = 0.7, lanePressure = 0.2)
        assertEquals(listOf("j20"), RouteAnalyzer().analyze(route(), listOf(first, slightlyHarder)).map { it.id })
    }

    @Test
    fun `a clearly harder decision is not displaced by an easier one just before it`() {
        val easy = junction(20)
        val hard = junction(24, complexity = 0.6, errorCost = 1.0, ambiguity = 0.7)
        assertEquals(listOf("j24"), RouteAnalyzer().analyze(route(), listOf(easy, hard)).map { it.id })
    }

    @Test
    fun `route shape is only a fallback when the source reports maneuvers`() {
        val withManeuvers = RouteAnalyzer().analyze(route(lShaped), listOf(junction(10, geometry = lShaped)))
        assertTrue(withManeuvers.none { it.type == SegmentType.COMPLEX_TURN })
    }

    @Test
    fun `a sharp turn on verified geometry becomes a briefing segment near the corner`() {
        val segments = RouteAnalyzer().analyze(route(lShaped))
        assertTrue("expected at least one segment", segments.isNotEmpty())
        val turn = segments.first()
        assertEquals(SegmentType.COMPLEX_TURN, turn.type)
        assertEquals(1.0, turn.distanceFromStartKm, 0.1)
    }

    @Test
    fun `curated demo routes are never analyzed`() {
        assertTrue(RouteAnalyzer().analyze(route(lShaped, RouteSource.CURATED_DEMO)).isEmpty())
    }

    @Test
    fun `a straight route has nothing to brief`() {
        assertTrue(RouteAnalyzer().analyze(route()).isEmpty())
    }
}

package ai.dejavroom.app.data

import ai.dejavroom.app.domain.ManeuverObservation
import ai.dejavroom.app.domain.ManeuverScorer
import ai.dejavroom.app.domain.RouteAnalyzer
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RouteGeometryStatus
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.RouteSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverRankingTest {
    /** About 2 km straight north; candidates are placed on it by index. */
    private val geometry = (0..44).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }
    private val route = RouteSnapshot(
        origin = "A", destination = "B", durationMinutes = 5, distanceKm = 2, segments = emptyList(),
        routeId = "r", source = RouteSource.EXTERNAL_ROUTE, geometryStatus = RouteGeometryStatus.VERIFIED, geometry = geometry,
    )

    private fun observation(
        at: Int,
        maneuverPoint: Boolean = true,
        type: String = "turn",
        modifier: String? = null,
        out: Int = 0,
        allowed: List<Int> = listOf(0, 90, 270),
        classes: Set<String> = emptySet(),
    ) = ManeuverObservation(geometry[at], "도로$at", maneuverPoint, type, modifier, classes, out, allowed)

    private fun rank(vararg observations: ManeuverObservation): List<String> =
        RouteAnalyzer(maxSegments = 5).analyze(route, observations.mapIndexedNotNull { i, o -> o.toFeature("r-junction-$i") }).map { it.name }

    @Test
    fun `driving straight through a big crossing is not a preview point`() {
        val passThrough = observation(at = 10, maneuverPoint = false, modifier = "left", out = 0, allowed = listOf(0, 90, 180, 270))
        assertNull(ManeuverScorer.assess(passThrough))
    }

    @Test
    fun `a real left turn outranks a straight pass-through crossing`() {
        val passThrough = observation(at = 10, maneuverPoint = false, modifier = "left", out = 0, allowed = listOf(0, 90, 180, 270))
        val left = observation(at = 30, modifier = "left", out = 270)
        assertEquals("도로30", rank(passThrough, left).first())
    }

    @Test
    fun `a motorway off-ramp fork outranks an urban right turn`() {
        val rightTurn = observation(at = 10, modifier = "right", out = 90)
        val offRamp = observation(at = 30, type = "off ramp", modifier = "slight right", out = 20, allowed = listOf(0, 20), classes = setOf("motorway"))
        assertEquals(listOf("도로30", "도로10"), rank(rightTurn, offRamp))
    }

    @Test
    fun `a look-alike branch raises the score and says why`() {
        val clear = ManeuverScorer.assess(observation(at = 10, type = "fork", modifier = "slight right", out = 45, allowed = listOf(0, 45)))!!
        val confusing = ManeuverScorer.assess(observation(at = 10, type = "fork", modifier = "slight right", out = 20, allowed = listOf(0, 20)))!!
        assertTrue(confusing.ambiguity > clear.ambiguity)
        assertTrue(confusing.evidence.any { it.startsWith("비슷한 방향 갈래") })
    }

    @Test
    fun `missing a motorway exit is explained in plain words`() {
        val offRamp = ManeuverScorer.assess(observation(at = 10, type = "off ramp", modifier = "slight right", out = 20, allowed = listOf(0, 20), classes = setOf("motorway")))!!
        assertEquals("진출로: 놓치면 되돌리기 어려움", offRamp.evidence.first())
    }

    @Test
    fun `ranking does not depend on input order`() {
        val a = observation(at = 8, modifier = "left", out = 270)
        val b = observation(at = 22, modifier = "right", out = 90)
        val c = observation(at = 36, type = "off ramp", modifier = "slight right", out = 20, allowed = listOf(0, 20), classes = setOf("motorway"))
        assertEquals(rank(a, b, c), rank(c, a, b))
        val ids = { list: List<ManeuverObservation> ->
            RouteAnalyzer(maxSegments = 5).analyze(route, list.map { it.toFeature("id-${it.roadName}")!! }).map { it.id }
        }
        assertEquals(ids(listOf(a, b, c)), ids(listOf(c, b, a)))
    }
}

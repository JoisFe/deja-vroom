package ai.dejavroom.app.data

import ai.dejavroom.app.domain.ManeuverObservation
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
    /** About 2 km straight north; candidates are placed on it by index (50 m apart). */
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

    /** Positions (geometry indices) of the picked points, most important first. */
    private fun rank(vararg observations: ManeuverObservation): List<Int> =
        RouteAnalyzer()
            .analyze(route, observations.mapIndexedNotNull { i, o -> o.toPreviewFeature("r-junction-$i") })
            .sortedBy { it.selectionRank }
            .map { geometry.indexOf(it.coordinate) }

    @Test
    fun `driving straight through a big crossing is not a preview point`() {
        val passThrough = observation(at = 10, maneuverPoint = false, modifier = "left", out = 0, allowed = listOf(0, 90, 180, 270))
        assertNull(passThrough.assess())
    }

    @Test
    fun `a real left turn outranks a straight pass-through crossing`() {
        val passThrough = observation(at = 10, maneuverPoint = false, modifier = "left", out = 0, allowed = listOf(0, 90, 180, 270))
        val left = observation(at = 30, modifier = "left", out = 270)
        assertEquals(30, rank(passThrough, left).first())
    }

    @Test
    fun `a motorway off-ramp outranks an urban left turn`() {
        val leftTurn = observation(at = 10, modifier = "left", out = 270)
        val offRamp = observation(at = 30, type = "off ramp", modifier = "slight right", out = 20, allowed = listOf(0, 20), classes = setOf("motorway"))
        assertEquals(listOf(30, 10), rank(leftTurn, offRamp))
    }

    @Test
    fun `an easy right turn does not pad the list next to a real decision`() {
        val rightTurn = observation(at = 10, modifier = "right", out = 90)
        val offRamp = observation(at = 30, type = "off ramp", modifier = "slight right", out = 20, allowed = listOf(0, 20), classes = setOf("motorway"))
        assertEquals(listOf(30), rank(rightTurn, offRamp))
    }

    @Test
    fun `the best point is shown even when it is an easy one`() {
        assertEquals(listOf(10), rank(observation(at = 10, modifier = "right", out = 90)))
    }

    @Test
    fun `a look-alike branch raises the score and says why`() {
        val clear = observation(at = 10, type = "fork", modifier = "slight right", out = 45, allowed = listOf(0, 45)).assess()!!
        val confusing = observation(at = 10, type = "fork", modifier = "slight right", out = 20, allowed = listOf(0, 20)).assess()!!
        assertTrue(confusing.ambiguity > clear.ambiguity)
        assertTrue(confusing.evidence.any { it.startsWith("비슷한 방향 갈래") })
    }

    @Test
    fun `missing a motorway exit is explained in plain words`() {
        val offRamp = observation(at = 10, type = "off ramp", modifier = "slight right", out = 20, allowed = listOf(0, 20), classes = setOf("motorway")).assess()!!
        assertEquals("진출로: 놓치면 되돌리기 어려움", offRamp.evidence.first())
    }

    @Test
    fun `a merge cannot be missed so it costs little`() {
        val merge = observation(at = 10, type = "merge", modifier = "slight left", out = 350, allowed = listOf(350)).assess()!!
        assertTrue(merge.errorCost < 0.2)
        assertEquals("본선 합류", merge.evidence.first())
    }

    @Test
    fun `a road that only changes its name asks nothing of the driver`() {
        val renamed = observation(at = 10, type = "new name", modifier = "slight right", out = 20, allowed = listOf(20))
        assertTrue(!renamed.requiresAction)
        assertNull(renamed.assess())
    }

    @Test
    fun `the sign is reduced to what a driver looks for`() {
        val exit = observation(at = 10, type = "off ramp", modifier = "slight right").copy(signText = "1: 서울, 신갈분기점, 판교")
        assertEquals("서울·신갈분기점", exit.sign)
        assertEquals("표지: 서울·신갈분기점", exit.assess()!!.evidence[1])
    }

    @Test
    fun `ranking does not depend on input order`() {
        val a = observation(at = 8, modifier = "left", out = 270)
        val b = observation(at = 22, modifier = "sharp left", out = 250)
        val c = observation(at = 36, type = "off ramp", modifier = "slight right", out = 20, allowed = listOf(0, 20), classes = setOf("motorway"))
        assertEquals(rank(a, b, c), rank(c, a, b))
        val ids = { list: List<ManeuverObservation> ->
            RouteAnalyzer().analyze(route, list.map { it.toPreviewFeature("id-${it.roadName}")!! }).map { it.id }
        }
        assertEquals(ids(listOf(a, b, c)), ids(listOf(c, b, a)))
    }
}

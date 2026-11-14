package ai.dejavroom.app.domain

import ai.dejavroom.app.model.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnByTurnRouteTest {
    private var position = 0
    private fun next() = GeoCoordinate(37.50 + (position++) * 0.001, 127.00)

    private fun step(
        type: String,
        modifier: String? = null,
        road: String = "",
        meters: Double = 500.0,
        seconds: Double = 40.0,
        sign: String = "",
        out: Int? = null,
        allowed: List<Int> = emptyList(),
    ) = ManeuverObservation(
        coordinate = next(), roadName = road, isManeuverPoint = true, maneuverType = type, modifier = modifier,
        outBearing = out, allowedBearings = allowed, roadNamed = road.isNotBlank(),
        stepDistanceMeters = meters, stepDurationSeconds = seconds, signText = sign,
    )

    private fun features(vararg steps: ManeuverObservation) = TurnByTurnRoute(steps.toList()).previewFeatures("t")

    @Test
    fun `getting out of the starting driveway is not a candidate`() {
        val features = features(
            step("depart", "left", road = "", meters = 98.0, seconds = 29.0),
            step("end of road", "left", road = "효원로", meters = 132.0, seconds = 12.0),
            step("turn", "left", road = "동탄원천로", meters = 1312.0, seconds = 104.0),
            step("arrive", "right", road = "동탄원천로", meters = 0.0, seconds = 0.0),
        )
        assertEquals(listOf("효원로 → 동탄원천로", "도착 지점"), features.map { it.name })
    }

    @Test
    fun `a fork that merges onto an expressway is a highway decision that is hard to undo`() {
        val features = features(
            step("depart", road = "중부대로", meters = 1000.0, seconds = 70.0),
            step("fork", "slight left", road = "", meters = 584.0, seconds = 66.0, sign = "1: 서울, 신갈분기점"),
            step("merge", "slight left", road = "경부고속도로", meters = 23_427.0, seconds = 959.0),
            step("arrive", road = "경부고속도로", meters = 0.0, seconds = 0.0),
        )
        val fork = features.first { it.name == "중부대로 갈림길" }
        assertEquals(RouteMapFeature.Type.HIGHWAY_JUNCTION, fork.type)
        assertEquals(1.0, fork.errorCostSignal, 0.0)
        assertEquals(listOf("고속도로 갈림길: 잘못 들면 되돌리기 어려움", "표지: 서울·신갈분기점"), fork.reasons.take(2))
        val merge = features.first { it.name == "경부고속도로 합류" }
        assertTrue(merge.errorCostSignal < 0.2)
    }

    @Test
    fun `leaving an expressway is recognised by its speed and name`() {
        val features = features(
            step("depart", road = "경부고속도로", meters = 23_427.0, seconds = 959.0),
            step("off ramp", "slight right", road = "", meters = 319.0, seconds = 38.0, sign = "47: 양재"),
            step("turn", "left", road = "강남대로", meters = 4220.0, seconds = 242.0),
            step("arrive", road = "강남대로", meters = 0.0, seconds = 0.0),
        )
        val exit = features.first { it.name == "경부고속도로 진출로" }
        assertTrue(exit.approach.onHighway)
        assertEquals(24.4, exit.approach.speedMps!!, 0.1)
        assertEquals(listOf("진출로: 놓치면 되돌리기 어려움", "표지: 양재"), exit.reasons.take(2))
    }

    @Test
    fun `where the route leaves the last named road is the destination entrance`() {
        val features = features(
            step("depart", road = "강남대로", meters = 3000.0, seconds = 200.0),
            step("turn", "left", road = "", meters = 18.0, seconds = 3.0),
            step("arrive", "right", road = "", meters = 0.0, seconds = 0.0),
        )
        assertEquals(listOf("목적지 진입로", "도착 지점"), features.map { it.name })
        assertTrue(features.all { it.type == RouteMapFeature.Type.DESTINATION_ACCESS })
        assertEquals("강남대로에서 빠지는 곳", features[0].reasons.first())
        assertFalse(features[0].approach.arrival)
        assertTrue(features[1].approach.arrival)
        assertEquals(listOf("목적지는 진행 방향 오른쪽"), features[1].reasons)
    }

    @Test
    fun `turns inside the destination driveway are not candidates`() {
        val features = features(
            step("depart", road = "공항대로", meters = 5230.0, seconds = 316.0),
            step("turn", "slight right", road = "", meters = 184.0, seconds = 44.0),
            step("fork", "slight left", road = "", meters = 634.0, seconds = 154.0),
            step("turn", "right", road = "", meters = 326.0, seconds = 78.0),
            step("arrive", "left", road = "", meters = 0.0, seconds = 0.0),
        )
        assertEquals(listOf("목적지 진입로", "도착 지점"), features.map { it.name })
    }

    @Test
    fun `a short unnamed link is named after the road it leads to`() {
        val features = features(
            step("depart", road = "퇴계로", meters = 2768.0, seconds = 193.0),
            step("turn", "slight right", road = "", meters = 78.0, seconds = 9.0, out = 82, allowed = listOf(70, 82)),
            step("turn", "slight right", road = "동호로", meters = 3443.0, seconds = 248.0),
            step("arrive", road = "동호로", meters = 0.0, seconds = 0.0),
        )
        assertNotNull(features.firstOrNull { it.name == "퇴계로 → 동호로" })
    }

    @Test
    fun `the approach is described by the road leading in and the previous maneuver`() {
        val features = features(
            step("depart", road = "가로", meters = 500.0, seconds = 50.0),
            step("turn", "right", road = "나로", meters = 40.0, seconds = 10.0),
            step("turn", "left", road = "다로", meters = 900.0, seconds = 60.0),
            step("arrive", road = "다로", meters = 0.0, seconds = 0.0),
        )
        val second = features.first { it.name == "나로 → 다로" }
        // The 40 m step is too short to measure speed on; the longer road before it is used instead.
        assertEquals(10.0, second.approach.speedMps!!, 0.01)
        assertEquals(40.0, second.approach.previousManeuverGapMeters!!, 0.01)
    }

    @Test
    fun `an intersection that is only driven through on an ordinary road is not a candidate`() {
        val passThrough = ManeuverObservation(next(), "가로", isManeuverPoint = false, maneuverType = "turn", modifier = "left", outBearing = 0, allowedBearings = listOf(0, 20))
        val features = features(step("depart", road = "가로", meters = 800.0, seconds = 60.0), passThrough, step("arrive", road = "가로", meters = 0.0, seconds = 0.0))
        assertEquals(listOf("도착 지점"), features.map { it.name })
    }
}

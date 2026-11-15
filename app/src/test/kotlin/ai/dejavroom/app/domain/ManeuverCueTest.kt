package ai.dejavroom.app.domain

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.ManeuverCue
import ai.dejavroom.app.model.RouteGeometryStatus
import ai.dejavroom.app.model.RouteSnapshot
import ai.dejavroom.app.model.RouteSource
import ai.dejavroom.app.model.TurnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ManeuverCueTest {
    private val geometry = (0..44).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }

    private fun maneuver(type: String, modifier: String?, sign: String = "", at: Int = 20, road: String = "도로") = ManeuverObservation(
        coordinate = geometry[at], roadName = road, isManeuverPoint = true, maneuverType = type, modifier = modifier,
        stepDistanceMeters = 500.0, stepDurationSeconds = 40.0, signText = sign,
    )

    @Test
    fun `each maneuver is told as a direction and an action in a driver's words`() {
        assertEquals(ManeuverCue(TurnDirection.SLIGHT_RIGHT, "오른쪽 진출로", "양재"), maneuver("off ramp", "slight right", sign = "47: 양재").cue)
        assertEquals(ManeuverCue(TurnDirection.SLIGHT_LEFT, "왼쪽 갈림길", "서울·신갈분기점"), maneuver("fork", "slight left", sign = "1: 서울, 신갈분기점").cue)
        assertEquals(ManeuverCue(TurnDirection.LEFT, "좌회전"), maneuver("turn", "left").cue)
        assertEquals(ManeuverCue(TurnDirection.RIGHT, "우회전"), maneuver("end of road", "right").cue)
        assertEquals(ManeuverCue(TurnDirection.SLIGHT_LEFT, "왼쪽으로 합류"), maneuver("merge", "slight left").cue)
        assertEquals(ManeuverCue(TurnDirection.SLIGHT_RIGHT, "오른쪽 진입로"), maneuver("on ramp", "slight right").cue)
        assertEquals(ManeuverCue(TurnDirection.UTURN, "유턴"), maneuver("continue", "uturn").cue)
        assertEquals(ManeuverCue(TurnDirection.SHARP_RIGHT, "급우회전"), maneuver("turn", "sharp right").cue)
    }

    @Test
    fun `a point that asks nothing of the driver has no cue`() {
        assertNull(maneuver("new name", "slight right").cue)
        assertNull(maneuver("continue", "straight").cue)
        assertNull(maneuver("turn", "left").copy(isManeuverPoint = false).cue)
    }

    @Test
    fun `the cue reaches the briefing segment and the destination says which side it is on`() {
        val route = RouteSnapshot(
            origin = "A", destination = "B", durationMinutes = 5, distanceKm = 2, segments = emptyList(),
            routeId = "r", source = RouteSource.EXTERNAL_ROUTE, geometryStatus = RouteGeometryStatus.VERIFIED, geometry = geometry,
        )
        val steps = listOf(
            maneuver("depart", null, at = 0, road = "가로").copy(stepDistanceMeters = 1000.0, stepDurationSeconds = 80.0),
            maneuver("turn", "left", at = 20, road = "나로").copy(stepDistanceMeters = 1200.0, stepDurationSeconds = 90.0),
            maneuver("arrive", "right", at = 44, road = "나로").copy(stepDistanceMeters = 0.0, stepDurationSeconds = 0.0),
        )
        val segments = RouteAnalyzer().analyze(route, TurnByTurnRoute(steps).previewFeatures("r"))
        assertEquals(listOf("가로 → 나로", "도착 지점"), segments.map { it.name })
        assertEquals(ManeuverCue(TurnDirection.LEFT, "좌회전"), segments[0].cue)
        assertEquals(ManeuverCue(TurnDirection.RIGHT, "목적지는 오른쪽", arrival = true), segments[1].cue)
        assertFalse(segments[0].cue!!.arrival)
    }
}

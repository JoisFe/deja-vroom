package ai.dejavroom.app.ui

import ai.dejavroom.app.model.ApproachViewpoint
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.ManeuverCue
import ai.dejavroom.app.model.SegmentType
import ai.dejavroom.app.model.TurnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextTest {
    private fun segment(
        cue: ManeuverCue? = null,
        evidence: List<String> = emptyList(),
        lead: Int? = null,
        km: Double = 30.03,
    ) = BriefingSegment(
        id = "s", type = SegmentType.HIGHWAY_JUNCTION, name = "경부고속도로 진출로", description = "d", guidance = "g",
        distanceFromStartKm = km, selectionScore = 0.549, coordinate = GeoCoordinate(37.46172, 127.0418),
        selectionEvidence = evidence, cue = cue,
        approach = lead?.let { ApproachViewpoint(GeoCoordinate(37.46, 127.04), 323.0, it) },
    )

    private val exit = ManeuverCue(TurnDirection.SLIGHT_RIGHT, "오른쪽 진출로", "양재")

    @Test
    fun `a card says what to do and which sign to follow`() {
        assertEquals("오른쪽 진출로 · 표지 양재", cardSubtitle(segment(cue = exit, evidence = listOf("진출로: 놓치면 되돌리기 어려움", "표지: 양재"))))
        assertEquals("좌회전", cardSubtitle(segment(cue = ManeuverCue(TurnDirection.LEFT, "좌회전"))))
    }

    @Test
    fun `card text never shows scores or coordinates`() {
        val text = cardSubtitle(segment(cue = exit, evidence = listOf("진출로: 놓치면 되돌리기 어려움", "도로 그래프: OSRM · OpenStreetMap"))) +
            recapLine(segment(cue = exit))
        assertFalse(text.contains("점수"))
        assertFalse(text.contains("54")) // the score, 0.549
        assertFalse(text.contains("37.4")) // the latitude
        assertFalse(text.contains("도로 그래프"))
    }

    @Test
    fun `the destination card also says how far before the arrival it starts`() {
        val destination = segment(
            cue = ManeuverCue(TurnDirection.RIGHT, "우회전"),
            evidence = listOf("봉은사로 → 봉은사로86길", "도착 230m 전부터 진입", "우회전"),
        )
        assertEquals("우회전 · 도착 230m 전부터 진입", cardSubtitle(destination))
    }

    @Test
    fun `without a cue the card falls back to its main reasons`() {
        assertEquals("방향 전환 87° · 300m 안에 전환 4회", cardSubtitle(segment(evidence = listOf("방향 전환 87°", "300m 안에 전환 4회", "경로 형상만으로 추정"))))
    }

    @Test
    fun `the preview distance is rounded and only mentioned when it is a real distance`() {
        assertEquals("140m", leadLabel(144))
        assertEquals("300m", leadLabel(300))
        assertNull(leadLabel(12))
        assertNull(leadLabel(null))
    }

    @Test
    fun `the banner over the road view leads with the distance and the action`() {
        assertEquals("300m 앞 · 오른쪽 진출로", bannerTitle(exit, 300))
        assertEquals("오른쪽 진출로", bannerTitle(exit, null))
    }

    @Test
    fun `the final screen lists each point with its distance from the start`() {
        assertEquals("출발 후 30.0km · 오른쪽 진출로 · 표지 양재", recapLine(segment(cue = exit)))
    }

    @Test
    fun `arrows point the way they are named`() {
        fun tip(direction: TurnDirection) = arrowShape(direction).head[1]
        assertTrue(tip(TurnDirection.RIGHT).x > 0.8f && tip(TurnDirection.LEFT).x < 0.2f)
        assertTrue(tip(TurnDirection.SLIGHT_RIGHT).x > 0.5f && tip(TurnDirection.SLIGHT_RIGHT).y < 0.5f)
        assertTrue(tip(TurnDirection.SHARP_LEFT).x < 0.5f && tip(TurnDirection.SHARP_LEFT).y > 0.6f)
        assertEquals(0.5f, tip(TurnDirection.STRAIGHT).x, 0.001f)
        assertTrue(tip(TurnDirection.STRAIGHT).y < 0.2f)
        // A U-turn comes back down on the left of where it went up.
        val uTurn = arrowShape(TurnDirection.UTURN)
        assertTrue(uTurn.curved && uTurn.head[1].x < uTurn.stem.first().x && uTurn.head[1].y > 0.6f)
        // Every arrow stays inside its box.
        TurnDirection.entries.forEach { direction ->
            val shape = arrowShape(direction)
            (shape.stem + shape.head).forEach { point -> assertTrue("$direction $point", point.x in 0.05f..0.95f && point.y in 0.05f..0.95f) }
        }
    }
}

package ai.dejavroom.app.ui

import ai.dejavroom.app.domain.Trip
import ai.dejavroom.app.domain.TripCheckpoint
import ai.dejavroom.app.feature.LocationNotice
import ai.dejavroom.app.model.BriefingSegment
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import ai.dejavroom.app.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripLabelsTest {
    private val north = (0..40).map { GeoCoordinate(37.50 + it * 0.00045, 127.00) }
    private fun point(name: String, index: Int) = BriefingSegment(
        id = name, type = SegmentType.INTERSECTION, name = name, description = name, guidance = name,
        distanceFromStartKm = index * 0.05, coordinate = north[index],
    )
    private val trip = Trip.startedWith(
        Place("강남역", coordinate = north.last()), north,
        listOf(point("중부대로 갈림길", 10), point("영덕교 갈림길", 20), point("경부고속도로 진출로", 30)), nowMillis = 0,
    )

    @Test
    fun `a trip that can be picked up again says where it was going and how far it got`() {
        assertEquals("강남역까지 가던 길 · 미리 본 3곳이 남았습니다", tripResumeLine(trip))
        assertEquals("강남역까지 가던 길 · 미리 본 3곳 중 2곳을 지나왔습니다", tripResumeLine(trip.advancedTo(north[24])))
        assertEquals("강남역까지 가던 길", tripResumeLine(Trip.startedWith(trip.destination, north, emptyList(), nowMillis = 0)))
    }

    @Test
    fun `what is behind the car is listed by name in the order it was passed`() {
        assertEquals("지나온 곳 2 · 중부대로 갈림길 · 영덕교 갈림길", passedLine(trip.advancedTo(north[24]).passed))
    }

    @Test
    fun `every reason for not finding the car tells the driver what to do next and shows no coordinates`() {
        LocationNotice.entries.forEach { notice ->
            val text = locationNoticeText(notice)
            assertTrue(text, text.endsWith("직접 입력해 주세요.") || text.endsWith("다시 눌러 주세요."))
            assertFalse(text, text.any(Char::isDigit))
        }
        val line = passedLine(listOf(TripCheckpoint("id-37.5", "영덕교 갈림길", north[20], 1_000.0)))
        assertFalse(line.contains("37.5") || line.contains("1000") || line.contains("id-"))
    }
}

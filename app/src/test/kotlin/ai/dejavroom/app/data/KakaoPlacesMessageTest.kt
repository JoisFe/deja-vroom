package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The app only trusts what the place search page says after checking it. */
class KakaoPlacesMessageTest {
    @Test
    fun `the page says when it is ready or cannot work`() {
        assertEquals(KakaoPlacesMessage.Ready, KakaoPlacesMessage.parse("ready"))
        assertEquals(KakaoPlacesMessage.Failed(KakaoPlacesMessage.Reason.KEY_MISSING), KakaoPlacesMessage.parse("fail:config"))
        assertEquals(KakaoPlacesMessage.Failed(KakaoPlacesMessage.Reason.SDK_NOT_LOADED), KakaoPlacesMessage.parse("fail:sdk-load"))
    }

    @Test
    fun `an answer becomes places with a name, an address and a position`() {
        val answer = """{"type":"places","id":7,"items":[
            {"name":"경기 수원시 영통구","address":"","latitude":37.2596,"longitude":127.0465},
            {"name":" 강남역 2호선 ","address":"서울 강남구 강남대로 지하 396","latitude":37.49794,"longitude":127.02762}]}"""
        assertEquals(
            KakaoPlacesMessage.Results(
                7,
                listOf(
                    Place("경기 수원시 영통구", "", GeoCoordinate(37.2596, 127.0465)),
                    Place("강남역 2호선", "서울 강남구 강남대로 지하 396", GeoCoordinate(37.49794, 127.02762)),
                ),
            ),
            KakaoPlacesMessage.parse(answer),
        )
        assertEquals(KakaoPlacesMessage.Results(3, emptyList()), KakaoPlacesMessage.parse("""{"type":"places","id":3,"items":[]}"""))
        assertEquals(KakaoPlacesMessage.SearchFailed(4), KakaoPlacesMessage.parse("""{"type":"places-error","id":4}"""))
    }

    @Test
    fun `a result that is not a named position on the globe is dropped`() {
        val answer = """{"type":"places","id":1,"items":[
            {"name":"","address":"이름 없음","latitude":37.5,"longitude":127.0},
            {"name":"좌표 없음","address":""},
            {"name":"지구 밖","address":"","latitude":137.5,"longitude":127.0},
            "문자열",
            {"name":"코엑스","address":"서울 강남구 영동대로 513","latitude":37.5126,"longitude":127.0589}]}"""
        val results = KakaoPlacesMessage.parse(answer) as KakaoPlacesMessage.Results
        assertEquals(listOf("코엑스"), results.places.map { it.name })
    }

    @Test
    fun `a flood of results or of text is cut down to size`() {
        val items = (1..40).joinToString(",") { """{"name":"${"가".repeat(300)}$it","address":"","latitude":37.5,"longitude":127.0}""" }
        val results = KakaoPlacesMessage.parse("""{"type":"places","id":1,"items":[$items]}""") as KakaoPlacesMessage.Results
        assertEquals(15, results.places.size)
        assertEquals(120, results.places.first().name.length)
    }

    @Test
    fun `anything else from the page is ignored`() {
        listOf(null, "", "sdk-loaded", "{", "[]", """{"type":"places"}""", """{"type":"places","id":"x","items":[]}""",
            """{"type":"other","id":1}""", """{"type":"places","id":1,"items":{}}""").forEach { assertNull(it, KakaoPlacesMessage.parse(it)) }
    }

    @Test
    fun `a question carries its number and the text exactly as typed`() {
        val question = JSONObject(KakaoPlacesMessage.searchRequest(12, "강남역 \"2번\" 출구"))
        assertEquals("search", question.getString("type"))
        assertEquals(12, question.getInt("id"))
        assertEquals("강남역 \"2번\" 출구", question.getString("query"))
    }
}

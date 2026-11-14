package ai.dejavroom.app.data

import ai.dejavroom.app.model.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSearchDataTest {
    @Test
    fun `photon response is parsed into name, address and coordinate`() {
        val body = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[127.0276,37.4979]},
               "properties":{"name":"강남역","state":"서울","district":"강남구","street":"강남대로"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[127.0594,37.5116]},
               "properties":{"street":"영동대로","housenumber":"513","city":"서울"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[127.0]},"properties":{"name":"좌표 없음"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[127.0,37.5]},"properties":{}}
            ]}
        """.trimIndent()
        val places = parsePhotonResponse(body)
        assertEquals(2, places.size)
        assertEquals("강남역", places[0].name)
        assertEquals("서울 강남구 강남대로", places[0].address)
        assertEquals(37.4979, places[0].coordinate!!.latitude, 1e-6)
        assertEquals(127.0276, places[0].coordinate!!.longitude, 1e-6)
        // No name: the street address becomes the title.
        assertEquals("영동대로 513", places[1].name)
        assertEquals("서울", places[1].address)
    }

    @Test
    fun `empty or missing features give no places`() {
        assertEquals(emptyList<Place>(), parsePhotonResponse("""{"type":"FeatureCollection"}"""))
    }

    @Test
    fun `recent searches put the newest first and drop duplicates`() {
        var list = emptyList<Place>()
        list = RecentSearches.add(list, Place("강남역"))
        list = RecentSearches.add(list, Place("서울역"))
        list = RecentSearches.add(list, Place(" 강남역 ", "서울 강남구"))
        assertEquals(listOf(" 강남역 ", "서울역"), list.map { it.name })
    }

    @Test
    fun `recent searches keep at most eight`() {
        var list = emptyList<Place>()
        (1..12).forEach { list = RecentSearches.add(list, Place("장소 $it")) }
        assertEquals(RecentSearches.MAX, list.size)
        assertEquals("장소 12", list.first().name)
        assertEquals("장소 5", list.last().name)
    }

    @Test
    fun `in-memory store removes one and clears all`() {
        val store = InMemoryRecentSearchStore(listOf(Place("A"), Place("B"), Place("C")))
        assertEquals(listOf("A", "C"), store.remove(Place("b")).map { it.name })
        assertEquals(emptyList<Place>(), store.clear())
        assertEquals(emptyList<Place>(), store.all())
    }

    @Test
    fun `blank names are never stored`() {
        assertEquals(emptyList<Place>(), RecentSearches.add(emptyList(), Place("  ")))
    }
}

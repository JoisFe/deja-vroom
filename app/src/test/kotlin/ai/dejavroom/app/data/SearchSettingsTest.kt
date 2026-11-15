package ai.dejavroom.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSettingsTest {
    @Test
    fun `the committed config file is valid`() {
        val file = listOf("../config/search.yml", "config/search.yml").map(::File).first { it.exists() }
        val defaults = SearchSettings.fromYaml(file.readText())
        assertTrue(defaults.origin.isNotBlank() && defaults.destination.isNotBlank())
        // The repository ships with Kakao place search selected.
        assertEquals(PlaceSearchProviderId.KAKAO, defaults.placeSearch)
    }

    @Test
    fun `a missing file or a missing key keeps the defaults`() {
        assertEquals(SearchSettings(), SearchSettings.fromYaml(null))
        assertEquals(SearchSettings(destination = "코엑스"), SearchSettings.fromYaml("destination:\n  place: 코엑스\n"))
    }

    @Test
    fun `the origin can be fixed to a place for rehearsals`() {
        val defaults = SearchSettings.fromYaml("origin:\n  source: place   # 녹화용\n  place: 판교역\n")
        assertEquals(SearchSettings.OriginSource.PLACE, defaults.originSource)
        assertEquals("판교역", defaults.origin)
        assertEquals(SearchSettings.OriginSource.CURRENT, SearchSettings.fromYaml("origin:\n  source: Current\n").originSource)
    }

    @Test
    fun `the place search is chosen by name`() {
        assertEquals(PlaceSearchProviderId.KAKAO, SearchSettings().placeSearch)
        assertEquals(PlaceSearchProviderId.PHOTON, SearchSettings.fromYaml("placeSearch:\n  provider: photon\n").placeSearch)
        assertEquals(PlaceSearchProviderId.KAKAO, SearchSettings.fromYaml("placeSearch:\n  provider: kakao   # 로드뷰와 같은 키\n").placeSearch)
    }

    @Test
    fun `a typo is an error instead of a silent default`() {
        val unknownKey = assertThrows(IllegalArgumentException::class.java) { SearchSettings.fromYaml("origin:\n  sorce: place\n") }
        assertTrue(unknownKey.message!!.contains("origin.sorce"))
        assertThrows(IllegalArgumentException::class.java) { SearchSettings.fromYaml("origin:\n  source: gps\n") }
        assertThrows(IllegalArgumentException::class.java) { SearchSettings.fromYaml("destination:\n  place: \"\"\n") }
    }
}

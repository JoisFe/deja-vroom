package ai.dejavroom.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchDefaultsTest {
    @Test
    fun `the committed config file is valid`() {
        val file = listOf("../config/search.yml", "config/search.yml").map(::File).first { it.exists() }
        val defaults = SearchDefaults.fromYaml(file.readText())
        assertTrue(defaults.origin.isNotBlank() && defaults.destination.isNotBlank())
    }

    @Test
    fun `a missing file or a missing key keeps the defaults`() {
        assertEquals(SearchDefaults(), SearchDefaults.fromYaml(null))
        assertEquals(SearchDefaults(destination = "코엑스"), SearchDefaults.fromYaml("destination:\n  place: 코엑스\n"))
    }

    @Test
    fun `the origin can be fixed to a place for rehearsals`() {
        val defaults = SearchDefaults.fromYaml("origin:\n  source: place   # 녹화용\n  place: 판교역\n")
        assertEquals(SearchDefaults.OriginSource.PLACE, defaults.originSource)
        assertEquals("판교역", defaults.origin)
        assertEquals(SearchDefaults.OriginSource.CURRENT, SearchDefaults.fromYaml("origin:\n  source: Current\n").originSource)
    }

    @Test
    fun `a typo is an error instead of a silent default`() {
        val unknownKey = assertThrows(IllegalArgumentException::class.java) { SearchDefaults.fromYaml("origin:\n  sorce: place\n") }
        assertTrue(unknownKey.message!!.contains("origin.sorce"))
        assertThrows(IllegalArgumentException::class.java) { SearchDefaults.fromYaml("origin:\n  source: gps\n") }
        assertThrows(IllegalArgumentException::class.java) { SearchDefaults.fromYaml("destination:\n  place: \"\"\n") }
    }
}

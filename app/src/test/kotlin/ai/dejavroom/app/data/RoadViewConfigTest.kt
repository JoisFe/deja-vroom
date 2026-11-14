package ai.dejavroom.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RoadViewConfigTest {
    @Test
    fun `parses nested keys, quotes and comments`() {
        val values = RoadViewYaml.parse(
            """
            # comment
            provider: mapillary   # trailing comment
            kakao:
              baseUrl: "https://example.github.io/roadview/#not-a-comment"
            mapillary:
              # only a comment here
            other:
              nested:
                value: 'x'
            """.trimIndent(),
        )
        assertEquals("mapillary", values["provider"])
        assertEquals("https://example.github.io/roadview/#not-a-comment", values["kakao.baseUrl"])
        assertEquals("x", values["other.nested.value"])
    }

    @Test
    fun `committed config selects kakao and leaves the page url empty`() {
        val file = listOf("../config/roadview.yml", "config/roadview.yml").map(::File).first { it.exists() }
        val config = RoadViewConfig.resolve(file.readText())
        assertEquals("kakao", config.provider)
        assertEquals("", config.kakaoBaseUrl)
        assertEquals("", config.mapillaryClientToken)
    }

    @Test
    fun `local overrides win over the yaml values`() {
        val yaml = "provider: kakao\nkakao:\n  baseUrl: \"https://a.example/roadview/\"\n"
        val config = RoadViewConfig.resolve(
            yaml,
            providerOverride = "mapillary",
            kakaoBaseUrlOverride = "http://localhost:8080/roadview/",
            mapillaryClientToken = "MLY|1|abc",
        )
        assertEquals("mapillary", config.provider)
        assertEquals("http://localhost:8080/roadview/", config.kakaoBaseUrl)
        assertEquals("MLY|1|abc", config.mapillaryClientToken)
    }

    @Test
    fun `blank overrides fall back to the yaml values`() {
        val config = RoadViewConfig.resolve("provider: demo\n", providerOverride = "  ")
        assertEquals("demo", config.provider)
    }

    @Test
    fun `missing asset resolves to an empty provider`() {
        assertEquals("", RoadViewConfig.resolve(null).provider)
    }
}

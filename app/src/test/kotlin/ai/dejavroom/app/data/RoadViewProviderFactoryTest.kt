package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadViewProviderFactoryTest {
    private val target = RoadViewTarget(GeoCoordinate(37.5, 127.0), headingDegrees = 90.0)

    private fun config(provider: String, url: String = "", token: String = "") =
        RoadViewConfig(provider = provider, kakaoBaseUrl = url, mapillaryClientToken = token)

    @Test
    fun `kakao with a page url builds the Kakao provider`() {
        val provider = RoadViewProviderFactory.create(config("kakao", url = "https://example.github.io/deja-vroom/roadview/"))
        assertTrue(provider is KakaoRoadViewProvider)
        assertEquals(RoadViewProviderId.KAKAO, provider.id)
    }

    @Test
    fun `kakao without a page url explains the missing setting`() = runTest {
        val provider = RoadViewProviderFactory.create(config("kakao"))
        assertEquals(RoadViewLookupResult.NotConfigured(RoadViewProviderFactory.KAKAO_SETUP_MESSAGE), provider.lookup(target))
    }

    @Test
    fun `mapillary with a token builds the Mapillary provider`() {
        assertTrue(RoadViewProviderFactory.create(config("mapillary", token = "MLY|1|abc")) is MapillaryRoadViewProvider)
    }

    @Test
    fun `mapillary without a token explains the missing setting`() = runTest {
        val provider = RoadViewProviderFactory.create(config("mapillary"))
        assertEquals(RoadViewProviderId.MAPILLARY, provider.id)
        assertEquals(RoadViewLookupResult.NotConfigured(RoadViewProviderFactory.MAPILLARY_SETUP_MESSAGE), provider.lookup(target))
    }

    @Test
    fun `demo needs no settings`() {
        assertTrue(RoadViewProviderFactory.create(config("demo")) is DemoRoadViewProvider)
    }

    @Test
    fun `provider names are case and space insensitive`() {
        assertTrue(RoadViewProviderFactory.create(config(" Demo ")) is DemoRoadViewProvider)
    }

    @Test
    fun `unknown or empty provider never crashes`() = runTest {
        listOf("", "naver").forEach { name ->
            val result = RoadViewProviderFactory.create(config(name)).lookup(target)
            assertTrue("$name → $result", result is RoadViewLookupResult.NotConfigured)
        }
    }

    @Test
    fun `kakao lookup returns an interactive page with a thumbnail url`() = runTest {
        val provider = RoadViewProviderFactory.create(config("kakao", url = "https://example.github.io/deja-vroom/roadview/"))
        val view = (provider.lookup(target) as RoadViewLookupResult.Available).views.single()
        val content = view.content as RoadViewContent.InteractivePage
        assertEquals(
            "https://example.github.io/deja-vroom/roadview/?thumb=1&lat=37.500000&lng=127.000000&heading=90.000000",
            content.thumbnailUrl,
        )
    }
}

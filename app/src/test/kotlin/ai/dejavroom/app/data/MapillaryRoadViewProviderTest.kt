package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapillaryRoadViewProviderTest {
    private val center = GeoCoordinate(37.5, 127.0)
    private val target = RoadViewTarget(center, headingDegrees = 90.0, radiusMeters = 80)

    /** [dLat] metres north of the centre, facing [angle]. */
    private fun image(id: String, dNorthMeters: Double, angle: Double?): String {
        val lat = center.latitude + dNorthMeters / 111_320.0
        val compass = angle?.let { "\"computed_compass_angle\": $it," }.orEmpty()
        return """{"id": "$id", "thumb_1024_url": "https://images.example/$id.jpg", $compass
            "computed_geometry": {"type": "Point", "coordinates": [${center.longitude}, $lat]},
            "captured_at": 1700000000000, "creator": {"username": "rider"}}"""
    }

    private fun response(vararg images: String) = """{"data": [${images.joinToString(",")}]}"""

    @Test
    fun `prefers a photo facing the driving direction over a slightly closer one facing away`() {
        val json = response(image("away", 10.0, 270.0), image("ahead", 25.0, 85.0))
        assertEquals("ahead", MapillaryImageSelector.select(json, target)?.imageId)
    }

    @Test
    fun `ignores photos outside the radius`() {
        assertNull(MapillaryImageSelector.select(response(image("far", 200.0, 90.0)), target))
    }

    @Test
    fun `uses distance alone when the photo has no compass angle`() {
        val json = response(image("b", 30.0, null), image("a", 5.0, null))
        assertEquals("a", MapillaryImageSelector.select(json, target)?.imageId)
    }

    @Test
    fun `empty or malformed responses select nothing`() {
        assertNull(MapillaryImageSelector.select("""{"data": []}""", target))
        assertNull(MapillaryImageSelector.select("not json", target))
    }

    @Test
    fun `lookup returns the chosen photo with credit and capture date`() = runTest {
        val provider = MapillaryRoadViewProvider("MLY|1|abc") { _, _, _ ->
            MapillaryResponse(200, response(image("ahead", 25.0, 85.0)))
        }
        val view = (provider.lookup(target) as RoadViewLookupResult.Available).views.single()
        val content = view.content as RoadViewContent.StillImage
        assertEquals("https://images.example/ahead.jpg", content.imageUrl)
        assertEquals(25, content.distanceMeters)
        assertEquals("2023-11-14", view.capturedAt)
        assertTrue(view.attribution.orEmpty().contains("CC BY-SA"))
    }

    @Test
    fun `http status maps to a lookup result`() = runTest {
        fun providerReturning(status: Int) = MapillaryRoadViewProvider("MLY|1|abc") { _, _, _ -> MapillaryResponse(status, "") }
        assertEquals(
            RoadViewLookupResult.NotConfigured(MapillaryRoadViewProvider.TOKEN_REJECTED_MESSAGE),
            providerReturning(401).lookup(target),
        )
        assertEquals(RoadViewLookupResult.Unavailable(RoadViewUnavailableReason.QUOTA), providerReturning(429).lookup(target))
        assertEquals(RoadViewLookupResult.Unavailable(RoadViewUnavailableReason.PROVIDER_ERROR), providerReturning(500).lookup(target))
        assertEquals(RoadViewLookupResult.NoCoverage, providerReturning(200).lookup(target))
    }
}

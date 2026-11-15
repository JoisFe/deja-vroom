package ai.dejavroom.app.data

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.Place
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchFactoryTest {
    private val configured = RoadViewConfig(provider = "kakao", kakaoBaseUrl = "https://example.github.io/deja-vroom/roadview/", mapillaryClientToken = "", pageRevision = "abc123")
    private val blank = configured.copy(kakaoBaseUrl = "")
    private val kakaoStub = FakePlaceSearchRepository(listOf(Place("강남역 2호선", "서울 강남구", GeoCoordinate(37.4979, 127.0276))))

    @Test
    fun `kakao is asked through the road view site, at its place search page`() {
        var site: RoadViewEndpoint? = null
        val search = PlaceSearchFactory.create(PlaceSearchProviderId.KAKAO, configured) { site = it; kakaoStub }
        assertSame(kakaoStub, search.suggestions)
        assertEquals("https://example.github.io/deja-vroom/roadview/places.html?rev=abc123", site!!.placesUrl)
        assertTrue(site!!.isPlacesPage("https://example.github.io/deja-vroom/roadview/places.html?rev=abc123"))
    }

    @Test
    fun `with kakao typed text is resolved by the same search as the suggestions`() = runTest {
        val search = PlaceSearchFactory.create(PlaceSearchProviderId.KAKAO, configured) { kakaoStub }
        assertEquals(GeocodedDestination(GeoCoordinate(37.4979, 127.0276), "강남역 2호선"), search.geocoder.resolve("강남역"))
        assertEquals(listOf("강남역"), kakaoStub.queries)
        val nothing = runCatching { search.geocoder.resolve("없는곳") }.exceptionOrNull()
        assertTrue(nothing!!.message!!.contains("장소를 찾지 못했습니다"))
    }

    @Test
    fun `kakao without a site address says how to set it up instead of quietly using another service`() = runTest {
        val search = PlaceSearchFactory.create(PlaceSearchProviderId.KAKAO, blank) { error("no site, so nothing to build") }
        val suggesting = runCatching { search.suggestions.suggest("강남역") }.exceptionOrNull()
        assertTrue(suggesting is PlaceSearchUnavailableException)
        assertEquals(KakaoPlaceSearchRepository.SETUP_MESSAGE, suggesting!!.message)
        assertEquals(KakaoPlaceSearchRepository.SETUP_MESSAGE, runCatching { search.geocoder.resolve("강남역") }.exceptionOrNull()!!.message)
    }

    @Test
    fun `photon needs nothing and never touches the kakao site`() {
        val search = PlaceSearchFactory.create(PlaceSearchProviderId.PHOTON, blank) { error("kakao must not be built") }
        assertTrue(search.suggestions is PhotonPlaceSearchRepository)
        assertSame(NominatimDestinationGeocoder, search.geocoder)
        assertEquals(PHOTON_ATTRIBUTION, search.suggestions.attribution)
    }

    @Test
    fun `providers are named in the config the way they are written here`() {
        assertEquals(PlaceSearchProviderId.KAKAO, PlaceSearchProviderId.fromKey(" Kakao "))
        assertEquals(PlaceSearchProviderId.PHOTON, PlaceSearchProviderId.fromKey("photon"))
        assertEquals(null, PlaceSearchProviderId.fromKey("naver"))
        assertThrows(IllegalArgumentException::class.java) { SearchSettings.fromYaml("placeSearch:\n  provider: naver\n") }
    }
}

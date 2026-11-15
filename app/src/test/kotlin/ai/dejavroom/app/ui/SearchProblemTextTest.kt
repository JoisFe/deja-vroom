package ai.dejavroom.app.ui

import ai.dejavroom.app.data.RoutePreviewFailure
import ai.dejavroom.app.feature.SearchProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchProblemTextTest {
    @Test
    fun `every route failure has driver wording without service names or error codes`() {
        RoutePreviewFailure.entries.forEach { reason ->
            val text = searchProblemText(SearchProblem.RouteFailed(reason))
            assertTrue("$reason has no text", text.isNotBlank())
            listOf("OSRM", "Nominatim", "Kakao", "HTTP", "geometry").forEach { word ->
                assertFalse("$reason says $word", text.contains(word, ignoreCase = true))
            }
        }
    }

    @Test
    fun `a place search setup hint is shown as it is, and a missing one asks for a clearer place`() {
        assertEquals("설정 안내", searchProblemText(SearchProblem.PlaceSearchUnavailable("설정 안내")))
        val notFound = searchProblemText(SearchProblem.RouteFailed(RoutePreviewFailure.PLACE_NOT_FOUND))
        assertEquals(notFound, searchProblemText(SearchProblem.PlaceSearchUnavailable(null)))
        assertEquals(notFound, searchProblemText(SearchProblem.PlaceSearchUnavailable("  ")))
    }
}

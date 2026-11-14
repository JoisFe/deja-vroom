package ai.dejavroom.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class OsrmIntersectionParsingTest {
    @Test
    fun `only the first intersection of a step is a maneuver point`() {
        val legs = JSONArray(
            """
            [{"steps":[{"name":"테헤란로","maneuver":{"type":"turn","modifier":"left"},
              "intersections":[
                {"location":[127.0,37.50],"bearings":[0,90,180,270],"entry":[true,true,false,true],"out":3,"classes":["primary"]},
                {"location":[127.0,37.51],"bearings":[0,90,180,270],"entry":[true,true,false,true],"out":0}
              ]}]}]
            """.trimIndent(),
        )
        val parsed = OsrmDrivingRouteClient.parseIntersections(legs)
        assertEquals(listOf(true, false), parsed.map { it.isManeuverPoint })
        assertEquals(270, parsed.first().outBearing)
        assertEquals(listOf(0, 90, 270), parsed.first().allowedBearings)
        assertEquals(setOf("primary"), parsed.first().roadClasses)
    }
}

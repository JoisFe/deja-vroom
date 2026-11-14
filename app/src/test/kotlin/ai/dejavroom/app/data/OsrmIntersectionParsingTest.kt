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

    @Test
    fun `step facts travel with every intersection and maneuver points are never dropped`() {
        val legs = JSONArray(
            """
            [{"steps":[
              {"name":"","ref":"","distance":98.0,"duration":29.0,"maneuver":{"type":"depart","modifier":"left"},
               "intersections":[{"location":[127.0,37.50],"bearings":[190],"entry":[true],"out":0}]},
              {"name":"","ref":"","destinations":"47: 양재","distance":319.0,"duration":38.0,"maneuver":{"type":"off ramp","modifier":"slight right"},
               "intersections":[{"location":[127.0,37.50],"bearings":[0,20],"entry":[true,true],"out":1}]},
              {"name":"","ref":"47","exits":"1A","distance":176.0,"duration":15.0,"maneuver":{"type":"turn","modifier":"straight"},
               "intersections":[{"location":[127.0,37.51],"bearings":[0],"entry":[true],"out":0}]}
            ]}]
            """.trimIndent(),
        )
        val parsed = OsrmDrivingRouteClient.parseIntersections(legs)
        // The second maneuver shares the first one's coordinate and must still be there.
        assertEquals(listOf("depart", "off ramp", "turn"), parsed.map { it.maneuverType })
        assertEquals(listOf(false, false, true), parsed.map { it.roadNamed })
        assertEquals(98.0, parsed[0].stepDistanceMeters, 0.0)
        assertEquals(38.0, parsed[1].stepDurationSeconds, 0.0)
        assertEquals("47: 양재", parsed[1].signText)
        assertEquals("출구 1A", parsed[2].signText)
    }
}

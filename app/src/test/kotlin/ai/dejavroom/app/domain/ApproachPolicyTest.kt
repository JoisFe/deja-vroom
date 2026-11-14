package ai.dejavroom.app.domain

import ai.dejavroom.app.model.ApproachConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApproachPolicyTest {
    private val policy = ApproachPolicy()
    private fun lead(conditions: ApproachConditions, offset: Double = 10_000.0) = policy.leadMeters(conditions, offset)

    @Test
    fun `faster roads are previewed from farther back`() {
        val street = lead(ApproachConditions(speedMps = 8.0))
        val arterial = lead(ApproachConditions(speedMps = 13.0))
        val expressway = lead(ApproachConditions(speedMps = 24.0))
        assertEquals(96.0, street, 0.01)
        assertEquals(156.0, arterial, 0.01)
        assertEquals(288.0, expressway, 0.01)
        assertTrue(street < arterial && arterial < expressway)
    }

    @Test
    fun `the distance stays within sensible bounds`() {
        assertEquals(70.0, lead(ApproachConditions(speedMps = 2.0)), 0.01)
        assertEquals(350.0, lead(ApproachConditions(speedMps = 40.0)), 0.01)
        assertEquals(150.0, lead(ApproachConditions(speedMps = null)), 0.01)
    }

    @Test
    fun `a highway decision is shown from at least 300 m back`() {
        assertEquals(300.0, lead(ApproachConditions(speedMps = 22.0, onHighway = true)), 0.01)
        assertEquals(336.0, lead(ApproachConditions(speedMps = 28.0, onHighway = true)), 0.01)
    }

    @Test
    fun `the preview never starts before the previous maneuver`() {
        assertEquals(85.0, lead(ApproachConditions(speedMps = 13.0, previousManeuverGapMeters = 100.0)), 0.01)
        // Two turns almost on top of each other: all that can be seen is the short road between them.
        assertEquals(10.0, lead(ApproachConditions(speedMps = 13.0, previousManeuverGapMeters = 20.0)), 0.01)
        // A previous maneuver far away changes nothing.
        assertEquals(156.0, lead(ApproachConditions(speedMps = 13.0, previousManeuverGapMeters = 900.0)), 0.01)
    }

    @Test
    fun `an arrival point is shown from close by whatever the road`() {
        assertEquals(80.0, lead(ApproachConditions(speedMps = 20.0, onHighway = true, arrival = true)), 0.01)
    }

    @Test
    fun `the preview never starts before the route does`() {
        assertEquals(50.0, lead(ApproachConditions(speedMps = 13.0), offset = 50.0), 0.01)
    }
}

package ai.dejavroom.app.data

import ai.dejavroom.app.domain.ManeuverRules
import ai.dejavroom.app.domain.SelectionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.seconds

class BriefingSettingsTest {
    @Test
    fun `the committed config file is valid`() {
        val file = listOf("../config/briefing.yml", "config/briefing.yml").map(::File).first { it.exists() }
        val settings = BriefingSettings.fromYaml(file.readText())
        assertTrue(settings.selection.maxCards in 1..30)
        assertTrue(settings.selection.minimumScore in 0.0..1.0)
        assertTrue(settings.approach.minLeadMeters <= settings.approach.highwayMinLeadMeters)
    }

    @Test
    fun `a missing file or a missing key keeps the defaults`() {
        assertEquals(BriefingSettings(), BriefingSettings.fromYaml(null))
        val partial = BriefingSettings.fromYaml("selection:\n  maxCards: 3\n")
        assertEquals(SelectionRules(maxCards = 3), partial.selection)
        assertEquals(BriefingSettings().weights, partial.weights)
    }

    @Test
    fun `values from the file reach the rules`() {
        val settings = BriefingSettings.fromYaml(
            """
            selection:
              minimumScore: 0.3   # stricter
            weights:
              errorCost: 0.4
            approach:
              leadSeconds: 15
              highwayMinLeadMeters: 320
              driveStepMeters: 30
              driveMaxSteps: 8
            maneuver:
              lookAlikeAngleDegrees: 20
            """.trimIndent(),
        )
        assertEquals(0.3, settings.selection.minimumScore, 0.0)
        assertEquals(0.4, settings.weights.errorCost, 0.0)
        assertEquals(15.seconds, settings.approach.leadTime)
        assertEquals(320.0, settings.approach.highwayMinLeadMeters, 0.0)
        assertEquals(30.0, settings.approach.driveStepMeters, 0.0)
        assertEquals(8, settings.approach.driveMaxSteps)
        assertEquals(ManeuverRules(lookAlikeAngleDegrees = 20), settings.maneuver)
    }

    @Test
    fun `a typo cannot silently change the algorithm`() {
        val unknownKey = assertThrows(IllegalArgumentException::class.java) { BriefingSettings.fromYaml("selection:\n  maxCard: 3\n") }
        assertTrue(unknownKey.message!!.contains("selection.maxCard"))
        assertThrows(IllegalArgumentException::class.java) { BriefingSettings.fromYaml("weights:\n  errorCost: high\n") }
        assertThrows(IllegalArgumentException::class.java) { BriefingSettings.fromYaml("selection:\n  maxCards: 99\n") }
        assertThrows(IllegalArgumentException::class.java) { BriefingSettings.fromYaml("approach:\n  minLeadMeters: 500\n") }
        // A drive needs a hop to the junction and a hop away from it.
        assertThrows(IllegalArgumentException::class.java) { BriefingSettings.fromYaml("approach:\n  driveMaxSteps: 1\n") }
    }
}

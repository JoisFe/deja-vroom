package ai.dejavroom.app.domain

import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RouteLane
import kotlin.math.abs

/**
 * One intersection the route passes, as reported by a turn-by-turn routing source (OSRM steps).
 * [isManeuverPoint] is true only for the step's first intersection, where the driver acts;
 * every other intersection in a step is driven straight through.
 */
data class ManeuverObservation(
    val coordinate: GeoCoordinate,
    val roadName: String,
    val isManeuverPoint: Boolean,
    val maneuverType: String,
    val modifier: String?,
    val roadClasses: Set<String> = emptySet(),
    val outBearing: Int? = null,
    val allowedBearings: List<Int> = emptyList(),
    val lanes: List<RouteLane> = emptyList(),
)

/** What makes a point worth previewing. Every signal is 0..1; [evidence] explains the strongest ones in Korean. */
data class ManeuverAssessment(
    val difficulty: Double,
    val ambiguity: Double,
    val errorCost: Double,
    val lanePressure: Double,
    val highway: Boolean,
    val evidence: List<String>,
)

/**
 * Turns a raw observation into scoring signals. A pass-through intersection (no maneuver, no
 * look-alike branch, no lane squeeze) returns null: driving straight through a big crossing is
 * not something a first-time driver needs to preview.
 */
object ManeuverScorer {
    private val HIGHWAY_CLASSES = setOf("motorway", "trunk")
    private val RAMP_TYPES = setOf("on ramp", "off ramp", "fork", "merge")
    private val ROUNDABOUT_TYPES = setOf("roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary")
    private val NO_ACTION_TYPES = setOf("depart", "arrive", "continue", "new name", "notification")

    /** Branches within this angle of the chosen exit look like the same road. */
    const val AMBIGUITY_DEGREES = 30

    fun assess(observation: ManeuverObservation): ManeuverAssessment? {
        val modifier = observation.modifier?.lowercase()
        val type = observation.maneuverType.lowercase()
        val acts = observation.isManeuverPoint &&
            !(type in NO_ACTION_TYPES && (modifier == null || modifier == "straight"))
        val highway = observation.roadClasses.any { it in HIGHWAY_CLASSES } || type in setOf("on ramp", "off ramp")

        val difficulty = if (acts) difficultyOf(type, modifier) else 0.0
        val similarBranches = similarBranchCount(observation)
        val ambiguity = when (similarBranches) {
            0 -> 0.0
            1 -> 0.7
            else -> 1.0
        }
        val errorCost = if (acts) errorCostOf(type, highway) else 0.0
        val (lanePressure, laneEvidence) = lanePressureOf(observation.lanes)

        if (difficulty == 0.0 && ambiguity == 0.0 && lanePressure < 0.5) return null

        val reasons = buildList {
            if (errorCost >= 0.9) add(errorCost to "진출로: 놓치면 되돌리기 어려움")
            else if (errorCost >= 0.6) add(errorCost to if (type in ROUNDABOUT_TYPES) "회전교차로 출구 선택" else "갈림길: 놓치면 우회")
            if (ambiguity > 0.0) add(ambiguity to "비슷한 방향 갈래 ${similarBranches + 1}개")
            if (difficulty >= 0.6) add(difficulty to maneuverLabel(type, modifier))
            if (laneEvidence != null) add(lanePressure to laneEvidence)
            if (!acts && ambiguity > 0.0) add(0.1 to "직진이지만 갈래가 헷갈림")
        }.sortedByDescending { it.first }.map { it.second }

        return ManeuverAssessment(difficulty, ambiguity, errorCost, lanePressure, highway, reasons)
    }

    private fun difficultyOf(type: String, modifier: String?): Double = when {
        modifier == "uturn" -> 1.0
        modifier == "sharp left" || modifier == "sharp right" -> 0.9
        type in ROUNDABOUT_TYPES -> 0.7
        modifier == "left" -> 0.7 // Korea drives on the right: left turns cross oncoming traffic.
        type == "fork" || type == "off ramp" || type == "on ramp" -> 0.6
        modifier == "slight left" || modifier == "slight right" -> 0.5
        modifier == "right" -> 0.4
        type == "merge" -> 0.4
        type == "end of road" -> 0.5
        else -> 0.2
    }

    private fun errorCostOf(type: String, highway: Boolean): Double = when {
        highway && type in RAMP_TYPES -> 1.0
        highway -> 0.8
        type in ROUNDABOUT_TYPES -> 0.6
        type == "fork" -> 0.6
        else -> 0.2
    }

    private fun similarBranchCount(observation: ManeuverObservation): Int {
        val out = observation.outBearing ?: return 0
        return observation.allowedBearings
            .filter { it != out }
            .count { angularDifference(it, out) <= AMBIGUITY_DEGREES }
    }

    /** Pressure is high when only a small share of lanes continue on the route. */
    private fun lanePressureOf(lanes: List<RouteLane>): Pair<Double, String?> {
        if (lanes.size < 3) return 0.0 to null
        val valid = lanes.count { it.validForRoute }
        if (valid == 0 || valid * 2 > lanes.size) return 0.0 to null
        val pressure = 1.0 - valid.toDouble() / lanes.size
        return pressure to "차로 ${lanes.size}개 중 ${valid}개만 진행 방향"
    }

    private fun maneuverLabel(type: String, modifier: String?): String = when {
        modifier == "uturn" -> "유턴"
        modifier?.startsWith("sharp") == true -> "급회전"
        type in ROUNDABOUT_TYPES -> "회전교차로"
        modifier == "left" -> "좌회전"
        type == "fork" -> "갈림길 선택"
        else -> "방향 전환"
    }

    private fun angularDifference(a: Int, b: Int): Int {
        val d = abs(a - b) % 360
        return minOf(d, 360 - d)
    }
}

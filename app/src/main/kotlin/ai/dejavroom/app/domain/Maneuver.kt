package ai.dejavroom.app.domain

import ai.dejavroom.app.model.ApproachConditions
import ai.dejavroom.app.model.GeoCoordinate
import ai.dejavroom.app.model.RouteLane
import kotlin.math.abs

/** Where a maneuver sits in the trip. Only [EN_ROUTE] points compete for preview slots. */
enum class ManeuverRole {
    EN_ROUTE,

    /** The start itself, or getting out of the driveway/parking area the route starts in. */
    DEPARTURE, DEPARTURE_ACCESS,

    /** Where the route leaves the last real road toward the destination. */
    DESTINATION_ENTRANCE,

    /** Inside the driveway/parking area the route ends in. */
    ARRIVAL_ACCESS,
    ARRIVAL,
}

/** What the rest of the route says about one maneuver; [TurnByTurnRoute] derives it. */
data class ManeuverContext(
    /** Speed and grade of the road leading in, and how far back the previous maneuver was. */
    val approach: ApproachConditions = ApproachConditions.UNKNOWN,
    /** The chosen branch is, or directly merges onto, a highway-grade road. */
    val leadsOntoHighway: Boolean = false,
    /** Name of the road leading in; blank when unknown. */
    val approachRoadName: String = "",
    /** Name of the road this maneuver leads onto, looking past a short unnamed link; blank when unknown. */
    val exitRoadName: String = "",
    val role: ManeuverRole = ManeuverRole.EN_ROUTE,
)

/** What makes a point worth previewing. Every signal is 0..1; [evidence] explains the strongest ones in Korean. */
data class ManeuverAssessment(
    val difficulty: Double,
    val ambiguity: Double,
    val errorCost: Double,
    val lanePressure: Double,
    val highway: Boolean,
    val evidence: List<String>,
    /** False for an intersection that is only driven through. */
    val requiresAction: Boolean = true,
)

/**
 * One intersection the route passes, as reported by a turn-by-turn routing source (OSRM steps).
 * [isManeuverPoint] is true only for the step's first intersection, where the driver acts; every other
 * intersection in a step is driven straight through.
 *
 * The observation judges itself: whether it asks anything of the driver ([requiresAction]), how hard and
 * how costly it is ([assess]), and how it reads as a preview candidate ([toPreviewFeature]).
 */
data class ManeuverObservation(
    val coordinate: GeoCoordinate,
    /** Name of the road leaving this point; blank when it has none. */
    val roadName: String,
    val isManeuverPoint: Boolean,
    val maneuverType: String,
    val modifier: String?,
    val roadClasses: Set<String> = emptySet(),
    val outBearing: Int? = null,
    val allowedBearings: List<Int> = emptyList(),
    val lanes: List<RouteLane> = emptyList(),
    /** False for ramps, links, driveways and parking aisles: roads with neither a name nor a number. */
    val roadNamed: Boolean = true,
    /** Length and travel time of the step this intersection belongs to. A maneuver point starts its step. */
    val stepDistanceMeters: Double = 0.0,
    val stepDurationSeconds: Double = 0.0,
    /** Signposted destinations of the road leaving this point, as recorded in map data ("47: 양재"). */
    val signText: String = "",
) {
    private val kind: String get() = maneuverType.lowercase()
    private val turn: String? get() = modifier?.lowercase()

    /** True when the driver has to do something here, as opposed to following the road. */
    val requiresAction: Boolean
        get() = isManeuverPoint &&
            kind !in NEVER_ACT_KINDS &&
            !(kind in STRAIGHT_IS_NO_ACTION_KINDS && (turn == null || turn == "straight"))

    /**
     * The sign as a driver looks for it: map data's "1: 서울, 신갈분기점" becomes "서울·신갈분기점".
     * The leading route number is dropped and at most two destinations are kept.
     */
    val sign: String
        get() = signText
            .replace(SIGN_ROUTE_PREFIX, "")
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("·")
            .take(MAX_SIGN_LENGTH)

    /**
     * Scoring signals for this point, or null when it is not worth previewing. A pass-through intersection
     * only qualifies when it squeezes lanes or sits on a highway next to a look-alike branch: driving
     * straight through a big crossing is not something a first-time driver needs to see in advance.
     */
    fun assess(context: ManeuverContext = ManeuverContext(), rules: ManeuverRules = ManeuverRules()): ManeuverAssessment? {
        val acts = requiresAction
        val onHighwayNow = context.approach.onHighway || roadClasses.any { it in HIGHWAY_CLASSES }
        val highway = onHighwayNow || context.leadsOntoHighway || (acts && kind in RAMP_KINDS)

        val difficulty = if (acts) this.difficulty else 0.0
        val lookAlikes = lookAlikeBranches(rules.lookAlikeAngleDegrees)
        val rawAmbiguity = when (lookAlikes) {
            0 -> 0.0
            1 -> 0.7
            else -> 1.0
        }
        val ambiguity = when {
            acts -> rawAmbiguity
            onHighwayNow -> rawAmbiguity * PASS_THROUGH_AMBIGUITY_FACTOR
            else -> 0.0
        }
        val errorCost = if (acts) errorCost(onHighwayNow, context.leadsOntoHighway) else 0.0
        val (lanePressure, laneEvidence) = this.lanePressure

        if (!acts && ambiguity == 0.0 && lanePressure < 0.5) return null

        val costReason = if (acts) costReason(errorCost) else null
        val reasons = buildList {
            if (costReason != null) add(errorCost to costReason)
            if (ambiguity > 0.0) add(ambiguity to "비슷한 방향 갈래 ${lookAlikes + 1}개")
            // A ramp or fork is already named by its cost reason; other maneuvers are named by their turn.
            if (acts && !(costReason != null && kind in FORK_AND_RAMP_KINDS)) add(difficulty to maneuverLabel)
            if (laneEvidence != null) add(lanePressure to laneEvidence)
            if (!acts && ambiguity > 0.0) add(0.1 to "분기점 통과: 본선 유지")
        }.sortedByDescending { it.first }.map { it.second }.toMutableList()
        // The sign is what the driver actually looks for, so it comes right after the main reason.
        if (acts && sign.isNotBlank()) reasons.add(minOf(1, reasons.size), "표지: $sign")

        return ManeuverAssessment(difficulty, ambiguity, errorCost, lanePressure, highway, reasons, acts)
    }

    /** This point as a preview candidate in its [context], or null when it is not worth previewing. */
    fun toPreviewFeature(
        id: String,
        context: ManeuverContext = ManeuverContext(),
        rules: ManeuverRules = ManeuverRules(),
    ): RouteMapFeature? = when (context.role) {
        ManeuverRole.DEPARTURE, ManeuverRole.DEPARTURE_ACCESS, ManeuverRole.ARRIVAL_ACCESS -> null
        ManeuverRole.ARRIVAL -> arrivalFeature(id, context)
        ManeuverRole.DESTINATION_ENTRANCE -> entranceFeature(id, context, rules)
        ManeuverRole.EN_ROUTE -> enRouteFeature(id, context, rules)
    }

    private fun enRouteFeature(id: String, context: ManeuverContext, rules: ManeuverRules): RouteMapFeature? {
        val assessment = assess(context, rules) ?: return null
        val title = title(context, assessment.requiresAction)
        return RouteMapFeature(
            id = id,
            type = if (assessment.highway) RouteMapFeature.Type.HIGHWAY_JUNCTION else RouteMapFeature.Type.COMPLEX_INTERSECTION,
            name = title,
            description = assessment.evidence.firstOrNull()?.let { "$title · $it" } ?: title,
            coordinate = coordinate,
            source = SOURCE,
            confidence = CONFIDENCE,
            complexitySignal = assessment.difficulty,
            highwayJunctionSignal = if (assessment.highway) 1.0 else 0.0,
            branchCount = allowedBearings.size,
            routeLanes = lanes,
            ambiguitySignal = assessment.ambiguity,
            errorCostSignal = assessment.errorCost,
            lanePressureSignal = assessment.lanePressure,
            reasons = assessment.evidence,
            approach = context.approach,
            passThrough = !assessment.requiresAction,
        )
    }

    private fun entranceFeature(id: String, context: ManeuverContext, rules: ManeuverRules): RouteMapFeature {
        val assessment = assess(context, rules)
        val from = context.approachRoadName
        return RouteMapFeature(
            id = id,
            type = RouteMapFeature.Type.DESTINATION_ACCESS,
            name = "목적지 진입로",
            description = if (from.isBlank()) "목적지로 들어가는 진입로" else "${from}에서 목적지로 들어가는 진입로",
            coordinate = coordinate,
            source = SOURCE,
            confidence = CONFIDENCE,
            complexitySignal = assessment?.difficulty ?: 0.0,
            routeLanes = lanes,
            reasons = listOf(if (from.isBlank()) "큰길에서 빠지는 곳" else "${from}에서 빠지는 곳") + assessment?.evidence.orEmpty(),
            approach = context.approach,
        )
    }

    private fun arrivalFeature(id: String, context: ManeuverContext): RouteMapFeature {
        val side = when (turn) {
            "right", "slight right", "sharp right" -> "목적지는 진행 방향 오른쪽"
            "left", "slight left", "sharp left" -> "목적지는 진행 방향 왼쪽"
            else -> null
        }
        return RouteMapFeature(
            id = id,
            type = RouteMapFeature.Type.DESTINATION_ACCESS,
            name = "도착 지점",
            description = side ?: "도착 지점 앞 모습",
            coordinate = coordinate,
            source = SOURCE,
            confidence = CONFIDENCE,
            reasons = listOfNotNull(side),
            approach = context.approach.copy(onHighway = false, arrival = true),
        )
    }

    /** Card title: where this is, in the words a driver would use for it. */
    private fun title(context: ManeuverContext, acts: Boolean): String {
        val from = context.approachRoadName
        val to = context.exitRoadName.ifBlank { if (roadNamed) roadName else "" }
        val fromOrSign = from.ifBlank { if (sign.isNotBlank()) "$sign 방면" else "" }
        return when {
            !acts -> label(to.ifBlank { from }, "분기점 통과")
            kind == "off ramp" -> label(fromOrSign, "진출로")
            kind == "on ramp" -> label(fromOrSign, "진입로")
            kind == "fork" -> label(fromOrSign.ifBlank { to }, "갈림길")
            kind == "merge" -> label(to.ifBlank { from }, "합류")
            kind in ROUNDABOUT_KINDS -> label(to.ifBlank { from }, "회전교차로")
            from.isNotBlank() && to.isNotBlank() && from != to -> "$from → $to"
            else -> label(to.ifBlank { from }, "교차로")
        }
    }

    private fun label(place: String, what: String) = if (place.isBlank()) what else "$place $what"

    private val difficulty: Double
        get() = when {
            turn == "uturn" -> 1.0
            turn == "sharp left" || turn == "sharp right" -> 0.9
            kind in ROUNDABOUT_KINDS -> 0.7
            kind == "merge" -> 0.3 // No choice to make, only traffic to join.
            turn == "left" -> 0.7 // Korea drives on the right: left turns cross oncoming traffic.
            kind in FORK_AND_RAMP_KINDS -> 0.6
            turn == "slight left" || turn == "slight right" -> 0.5
            turn == "right" -> 0.4
            kind == "end of road" -> 0.5
            else -> 0.2
        }

    /** How much a miss costs. Leaving or splitting a highway is the hardest to undo; a merge cannot be missed. */
    private fun errorCost(onHighwayNow: Boolean, leadsOntoHighway: Boolean): Double = when {
        kind == "merge" -> 0.1
        kind == "off ramp" -> if (onHighwayNow) 1.0 else 0.8
        kind == "fork" -> if (onHighwayNow || leadsOntoHighway) 1.0 else 0.6
        kind == "on ramp" -> 0.7
        kind in ROUNDABOUT_KINDS -> 0.6
        onHighwayNow -> 0.8
        else -> 0.2
    }

    private fun costReason(errorCost: Double): String? = when {
        kind == "off ramp" -> if (errorCost >= 0.9) "진출로: 놓치면 되돌리기 어려움" else "진출로: 놓치면 우회"
        kind == "fork" -> if (errorCost >= 0.9) "고속도로 갈림길: 잘못 들면 되돌리기 어려움" else "갈림길: 놓치면 우회"
        kind == "on ramp" -> "진입로: 놓치면 우회"
        kind in ROUNDABOUT_KINDS -> "회전교차로 출구 선택"
        else -> null
    }

    /** Other allowed branches that point almost the same way as the chosen exit. */
    private fun lookAlikeBranches(withinDegrees: Int): Int {
        val out = outBearing ?: return 0
        return allowedBearings.count { it != out && angularDifference(it, out) <= withinDegrees }
    }

    /** Pressure is high when only a small share of lanes continue on the route. */
    private val lanePressure: Pair<Double, String?>
        get() {
            if (lanes.size < 3) return 0.0 to null
            val valid = lanes.count { it.validForRoute }
            if (valid == 0 || valid * 2 > lanes.size) return 0.0 to null
            return (1.0 - valid.toDouble() / lanes.size) to "차로 ${lanes.size}개 중 ${valid}개만 진행 방향"
        }

    private val maneuverLabel: String
        get() = when {
            turn == "uturn" -> "유턴"
            turn?.startsWith("sharp") == true -> "급회전"
            kind in ROUNDABOUT_KINDS -> "회전교차로"
            kind == "merge" -> "본선 합류"
            kind == "fork" -> "갈림길 선택"
            kind == "off ramp" -> "진출"
            kind == "on ramp" -> "진입"
            turn == "left" -> "좌회전"
            turn == "right" -> "우회전"
            turn == "slight left" -> "왼쪽 방향"
            turn == "slight right" -> "오른쪽 방향"
            else -> "방향 전환"
        }

    companion object {
        const val SOURCE = "OSRM · OpenStreetMap"

        private const val CONFIDENCE = 0.72

        /** A look-alike branch that is merely driven past matters less than one at a maneuver. */
        private const val PASS_THROUGH_AMBIGUITY_FACTOR = 0.6
        private const val MAX_SIGN_LENGTH = 24
        private val SIGN_ROUTE_PREFIX = Regex("^\\s*[0-9A-Za-z;\\s]{1,10}:\\s*")

        private val HIGHWAY_CLASSES = setOf("motorway", "trunk")
        private val RAMP_KINDS = setOf("on ramp", "off ramp")
        private val FORK_AND_RAMP_KINDS = setOf("fork", "on ramp", "off ramp")
        private val ROUNDABOUT_KINDS = setOf("roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary")

        /** The road only starts, ends or changes its name here; there is nothing to choose. */
        private val NEVER_ACT_KINDS = setOf("depart", "arrive", "new name", "notification")

        /** These only count as an action when they come with a real turn. */
        private val STRAIGHT_IS_NO_ACTION_KINDS = setOf("continue", "use lane")

        private fun angularDifference(a: Int, b: Int): Int {
            val difference = abs(a - b) % 360
            return minOf(difference, 360 - difference)
        }
    }
}

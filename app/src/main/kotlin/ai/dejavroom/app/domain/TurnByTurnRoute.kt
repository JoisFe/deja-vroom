package ai.dejavroom.app.domain

import ai.dejavroom.app.model.ApproachConditions
import kotlin.math.max

/**
 * A route the way its routing source describes it: steps in driving order, each starting with a maneuver.
 *
 * A single intersection says little; what matters is where it sits in the trip. The route knows that, so it
 * is the one that hands every maneuver its context: how fast the road leading in is, whether a branch leads
 * onto a highway, how far back the previous maneuver was, and whether the point belongs to the trip itself
 * or to the driveway/parking area the route starts or ends in.
 *
 * - Getting out of the starting driveway is never a candidate: the driver is already there.
 * - Turns inside the destination's driveway are never candidates either. The point where the route leaves
 *   the last real road, and the arrival point, become destination features shown as one final card.
 */
class TurnByTurnRoute(observations: List<ManeuverObservation>, private val rules: ManeuverRules = ManeuverRules()) {
    private val steps: List<RouteStep> = buildList {
        val groups = mutableListOf<MutableList<ManeuverObservation>>()
        observations.forEach { observation ->
            if (observation.isManeuverPoint || groups.isEmpty()) groups += mutableListOf(observation) else groups.last() += observation
        }
        groups.forEach { add(RouteStep(maneuver = it.first(), passThroughs = it.drop(1), rules = rules)) }
    }

    /** Number of leading steps on unnamed roads: the driveway or parking area the route starts in. */
    private val departureAccessSteps: Int = run {
        if (steps.firstOrNull()?.kind != "depart") return@run 0
        val drivenSteps = steps.count { it.kind != "arrive" }
        val unnamedLead = steps.takeWhile { !it.isNamed && it.kind != "arrive" }
        if (unnamedLead.isEmpty() || unnamedLead.size >= drivenSteps) return@run 0
        if (unnamedLead.sumOf { it.lengthMeters } <= MAX_ACCESS_LEG_METERS) unnamedLead.size else 0
    }

    /** Index of the first trailing step on unnamed roads before the arrival, or [List.size] when there is none. */
    private val arrivalAccessStart: Int = run {
        val arriveIndex = steps.lastIndex
        if (arriveIndex <= 0 || steps[arriveIndex].kind != "arrive") return@run steps.size
        var start = arriveIndex
        while (start - 1 >= max(departureAccessSteps, 1) && !steps[start - 1].isNamed) start--
        if (start == arriveIndex) return@run steps.size
        if (steps.subList(start, arriveIndex).sumOf { it.lengthMeters } <= MAX_ACCESS_LEG_METERS) start else steps.size
    }

    /** Every point worth previewing, in driving order, with the context that decides how it is scored and shown. */
    fun previewFeatures(idPrefix: String): List<RouteMapFeature> = buildList {
        var number = 0
        steps.forEachIndexed { index, step ->
            step.maneuver.toPreviewFeature("$idPrefix-junction-${number++}", contextOf(index), rules)?.let(::add)
            val drivenThrough = ManeuverContext(
                approach = ApproachConditions(speedMps = step.speedMps, onHighway = step.isHighwayGrade),
                role = when {
                    index < departureAccessSteps -> ManeuverRole.DEPARTURE_ACCESS
                    index >= arrivalAccessStart -> ManeuverRole.ARRIVAL_ACCESS
                    else -> ManeuverRole.EN_ROUTE
                },
            )
            step.passThroughs.forEach { it.toPreviewFeature("$idPrefix-junction-${number++}", drivenThrough, rules)?.let(::add) }
        }
    }

    private fun contextOf(index: Int): ManeuverContext {
        val step = steps[index]
        val incoming = steps.getOrNull(index - 1)
        val next = steps.getOrNull(index + 1)
        return ManeuverContext(
            approach = ApproachConditions(
                speedMps = approachSpeed(index),
                onHighway = incoming?.isHighwayGrade == true,
                previousManeuverGapMeters = previousManeuverGap(index),
            ),
            // Taking this branch puts the driver on a highway, directly or after one short ramp.
            leadsOntoHighway = step.isHighwayGrade ||
                (next != null && next.kind == "merge" && next.isHighwayGrade && step.lengthMeters <= HIGHWAY_HOP_METERS),
            approachRoadName = (index - 1 downTo max(0, index - 2))
                .map { steps[it].roadName }
                .firstOrNull { it.isNotBlank() }.orEmpty(),
            exitRoadName = exitRoadName(index),
            role = when {
                step.kind == "depart" -> ManeuverRole.DEPARTURE
                step.kind == "arrive" -> ManeuverRole.ARRIVAL
                departureAccessSteps > 0 && index <= departureAccessSteps -> ManeuverRole.DEPARTURE_ACCESS
                index == arrivalAccessStart -> ManeuverRole.DESTINATION_ENTRANCE
                index > arrivalAccessStart -> ManeuverRole.ARRIVAL_ACCESS
                else -> ManeuverRole.EN_ROUTE
            },
        )
    }

    /** Speed of the road leading in, skipping steps too short to give a stable number. */
    private fun approachSpeed(index: Int): Double? {
        val before = (index - 1 downTo max(0, index - 3)).map { steps[it] }
        return before.firstOrNull { it.lengthMeters >= MIN_SPEED_SAMPLE_METERS && it.speedMps != null }?.speedMps
            ?: before.firstOrNull()?.speedMps
    }

    /** Distance back to the last maneuver that needed an action, or to the start of the route. */
    private fun previousManeuverGap(index: Int): Double? {
        var gap = 0.0
        for (cursor in index - 1 downTo 0) {
            gap += steps[cursor].lengthMeters
            if (steps[cursor].maneuver.requiresAction) break
        }
        return gap.takeIf { index > 0 && it > 0.0 }
    }

    /** The named road this maneuver ends up on, looking past a short slip road or link. */
    private fun exitRoadName(index: Int): String {
        var travelled = 0.0
        for (cursor in index..minOf(index + 2, steps.lastIndex)) {
            val step = steps[cursor]
            if (step.kind == "arrive") break
            if (step.roadName.isNotBlank()) return step.roadName
            travelled += step.lengthMeters
            if (travelled > MAX_LINK_METERS) break
        }
        return ""
    }

    private companion object {
        /** A fork counts as choosing a highway direction when the highway is joined within this distance. */
        const val HIGHWAY_HOP_METERS = 1_500.0

        /** Unnamed roads at either end count as a driveway only up to this length. */
        const val MAX_ACCESS_LEG_METERS = 1_500.0

        /** An unnamed stretch up to this long is a link to the next named road rather than a road of its own. */
        const val MAX_LINK_METERS = 400.0

        /** Speed of a step shorter than this is too noisy to plan with. */
        const val MIN_SPEED_SAMPLE_METERS = 80.0
    }
}

/** One step of a route: the maneuver that starts it and the intersections driven through until the next one. */
class RouteStep(
    val maneuver: ManeuverObservation,
    val passThroughs: List<ManeuverObservation>,
    rules: ManeuverRules = ManeuverRules(),
) {
    val kind: String = maneuver.maneuverType.lowercase()
    val roadName: String get() = maneuver.roadName
    val isNamed: Boolean get() = maneuver.roadNamed
    val lengthMeters: Double get() = maneuver.stepDistanceMeters

    /** Average speed the routing source assumes here; null when the step has no usable duration. */
    val speedMps: Double? =
        if (maneuver.stepDurationSeconds > 0.5 && lengthMeters > 0.0) lengthMeters / maneuver.stepDurationSeconds else null

    /** Expressway by road class, by name, or by a speed only expressways reach over a meaningful distance. */
    val isHighwayGrade: Boolean = maneuver.roadClasses.any { it in HIGHWAY_CLASSES } ||
        HIGHWAY_NAME.containsMatchIn(roadName) ||
        ((speedMps ?: 0.0) >= rules.highwaySpeedMps && lengthMeters >= HIGHWAY_MIN_STEP_METERS)

    private companion object {
        /** A short step's speed is too noisy to tell an expressway by. */
        const val HIGHWAY_MIN_STEP_METERS = 300.0
        val HIGHWAY_NAME = Regex("고속도로|고속화도로")
        val HIGHWAY_CLASSES = setOf("motorway", "trunk")
    }
}

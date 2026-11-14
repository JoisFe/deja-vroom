package ai.dejavroom.app.data

import ai.dejavroom.app.domain.ApproachPolicy
import ai.dejavroom.app.domain.ManeuverRules
import ai.dejavroom.app.domain.RouteAnalyzer
import ai.dejavroom.app.domain.SelectionRules
import ai.dejavroom.app.model.RouteScoringWeights
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Tuning values of the preview algorithm, read from config/briefing.yml (packaged as the `briefing.yml` asset).
 *
 * The rules themselves, their defaults and their valid ranges live in the domain types; this only maps the
 * file onto them. A key missing from the file keeps its default. A value that is not a number, a value out
 * of range or a key nobody reads is an error, so a typo cannot silently change how points are picked.
 */
data class BriefingSettings(
    val selection: SelectionRules = SelectionRules(),
    val weights: RouteScoringWeights = RouteScoringWeights(),
    val approach: ApproachPolicy = ApproachPolicy(),
    val maneuver: ManeuverRules = ManeuverRules(),
) {
    fun routeAnalyzer(): RouteAnalyzer = RouteAnalyzer(weights = weights, selection = selection, approachPolicy = approach)

    companion object {
        const val ASSET_NAME = "briefing.yml"

        /** [yaml] is the text of config/briefing.yml, or null when the asset is missing (defaults apply). */
        fun fromYaml(yaml: String?): BriefingSettings {
            val reader = Reader(yaml?.let(FlatYaml::parse).orEmpty())
            val selection = SelectionRules()
            val weights = RouteScoringWeights()
            val approach = ApproachPolicy()
            val maneuver = ManeuverRules()
            return BriefingSettings(
                selection = SelectionRules(
                    maxCards = reader.int("selection.maxCards", selection.maxCards),
                    minimumScore = reader.double("selection.minimumScore", selection.minimumScore),
                    duplicateDistanceMeters = reader.double("selection.duplicateDistanceMeters", selection.duplicateDistanceMeters),
                    departureGuardMeters = reader.double("selection.departureGuardMeters", selection.departureGuardMeters),
                    arrivalZoneMeters = reader.double("selection.arrivalZoneMeters", selection.arrivalZoneMeters),
                    nearTieScore = reader.double("selection.nearTieScore", selection.nearTieScore),
                ),
                weights = RouteScoringWeights(
                    complexity = reader.double("weights.complexity", weights.complexity),
                    ambiguity = reader.double("weights.ambiguity", weights.ambiguity),
                    errorCost = reader.double("weights.errorCost", weights.errorCost),
                    lanePressure = reader.double("weights.lanePressure", weights.lanePressure),
                    maneuverDensity = reader.double("weights.maneuverDensity", weights.maneuverDensity),
                    destinationProximity = reader.double("weights.destinationProximity", weights.destinationProximity),
                    historicIncidentSignal = reader.double("weights.historicIncident", weights.historicIncidentSignal),
                ),
                approach = ApproachPolicy(
                    leadTime = reader.double("approach.leadSeconds", approach.leadTime.toDouble(DurationUnit.SECONDS)).seconds,
                    minLeadMeters = reader.double("approach.minLeadMeters", approach.minLeadMeters),
                    maxLeadMeters = reader.double("approach.maxLeadMeters", approach.maxLeadMeters),
                    highwayMinLeadMeters = reader.double("approach.highwayMinLeadMeters", approach.highwayMinLeadMeters),
                    defaultLeadMeters = reader.double("approach.defaultLeadMeters", approach.defaultLeadMeters),
                    arrivalLeadMeters = reader.double("approach.arrivalLeadMeters", approach.arrivalLeadMeters),
                    previousManeuverMarginMeters = approach.previousManeuverMarginMeters,
                ),
                maneuver = ManeuverRules(
                    lookAlikeAngleDegrees = reader.int("maneuver.lookAlikeAngleDegrees", maneuver.lookAlikeAngleDegrees),
                    highwaySpeedKmh = reader.double("maneuver.highwaySpeedKmh", maneuver.highwaySpeedKmh),
                ),
            ).also { reader.requireAllKeysRead() }
        }
    }

    /** Typed access to the flattened YAML that also remembers which keys were asked for. */
    private class Reader(private val values: Map<String, String>) {
        private val read = mutableSetOf<String>()

        fun double(key: String, default: Double): Double {
            read += key
            val raw = values[key] ?: return default
            return requireNotNull(raw.toDoubleOrNull()?.takeIf { it.isFinite() }) { "config/briefing.yml: $key 값이 숫자가 아니다: $raw" }
        }

        fun int(key: String, default: Int): Int {
            read += key
            val raw = values[key] ?: return default
            return requireNotNull(raw.toIntOrNull()) { "config/briefing.yml: $key 값이 정수가 아니다: $raw" }
        }

        fun requireAllKeysRead() {
            val unknown = values.keys - read
            require(unknown.isEmpty()) { "config/briefing.yml: 알 수 없는 키 ${unknown.sorted().joinToString()}" }
        }
    }
}

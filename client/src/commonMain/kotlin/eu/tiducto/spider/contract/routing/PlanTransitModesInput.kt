package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Transit modes the search may use: either the modes it may ride (`transit`) or the modes it leaves out (`exclude`). Both non-empty in one request is a 400 `modes.transit is invalid`.
 */
@Serializable
internal data class PlanTransitModesInput(
    /** The modes an itinerary may ride, each with an optional reluctance. Absent or empty means every mode. */
    val transit: List<PlanTransitModePreferenceInput>? = null,
    /** The modes no itinerary may ride; every other mode stays usable, including one `TransitMode` gains later. Absent or empty excludes nothing. A value outside `TransitMode` is a 400 `modes.transit.exclude is invalid`. Never with a non-empty `transit`. */
    val exclude: List<TransitMode>? = null
)

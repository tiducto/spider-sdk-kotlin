package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Transit modes the search may use.
 */
@Serializable
internal data class PlanTransitModesInput(
    /** The modes an itinerary may ride, each with an optional reluctance. Absent means every mode. */
    val transit: List<PlanTransitModePreferenceInput>? = null
)

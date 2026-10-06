package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * A transit mode the search may use.
 */
@Serializable
internal data class PlanTransitModePreferenceInput(
    val mode: TransitMode,
    val cost: TransitModePreferenceCostInput? = null
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanTransitModePreferenceInput(
    val mode: TransitMode,
    val cost: TransitModePreferenceCostInput? = null
)

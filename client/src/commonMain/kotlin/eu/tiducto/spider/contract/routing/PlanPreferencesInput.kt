package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanPreferencesInput(
    val accessibility: AccessibilityPreferencesInput? = null,
    val street: PlanStreetPreferencesInput? = null,
    val transit: TransitPreferencesInput? = null
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Routing preferences. An absent member keeps the environment's default.
 */
@Serializable
internal data class PlanPreferencesInput(
    val street: PlanStreetPreferencesInput? = null,
    val transit: TransitPreferencesInput? = null,
    val accessibility: AccessibilityPreferencesInput? = null
)

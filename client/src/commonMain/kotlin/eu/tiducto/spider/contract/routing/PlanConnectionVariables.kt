package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanConnectionVariables(
    val dateTime: PlanDateTimeInput,
    val origin: PlanLabeledLocationInput,
    val destination: PlanLabeledLocationInput,
    val via: List<PlanViaLocationInput>? = null,
    val modes: PlanModesInput? = null,
    val preferences: PlanPreferencesInput? = null,
    val searchWindow: String,
    val before: String? = null,
    val after: String? = null
)

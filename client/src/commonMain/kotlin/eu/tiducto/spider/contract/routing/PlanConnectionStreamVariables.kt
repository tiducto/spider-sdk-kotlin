package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanConnectionStreamVariables(
    val dateTime: PlanDateTimeInput,
    val origin: PlanLabeledLocationInput,
    val destination: PlanLabeledLocationInput,
    val via: List<PlanViaLocationInput>? = null,
    val modes: PlanModesInput? = null,
    val preferences: PlanPreferencesInput? = null,
    val targetResults: Int? = null,
    val maxWindow: String? = null,
    val before: String? = null,
    val after: String? = null
)

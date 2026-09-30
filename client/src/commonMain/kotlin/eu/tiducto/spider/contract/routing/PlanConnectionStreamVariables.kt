package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanConnectionStreamVariables(
    val dateTime: PlanDateTimeInput,
    val origin: PlanLabeledLocationInput,
    val destination: PlanLabeledLocationInput,
    val targetResults: Int,
    val maxWindow: String,
    val via: List<PlanViaLocationInput>? = null,
    val modes: PlanModesInput? = null,
    val preferences: PlanPreferencesInput? = null,
    val before: String? = null,
    val after: String? = null
)

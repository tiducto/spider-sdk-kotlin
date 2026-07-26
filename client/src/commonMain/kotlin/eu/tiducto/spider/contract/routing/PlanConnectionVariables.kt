package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanConnectionVariables(
    val dateTime: PlanDateTimeInput,
    val origin: PlanLabeledLocationInput,
    val destination: PlanLabeledLocationInput,
    val via: List<PlanViaLocationInput>? = null,
    val first: Int? = null,
    val before: String? = null,
    val after: String? = null
)

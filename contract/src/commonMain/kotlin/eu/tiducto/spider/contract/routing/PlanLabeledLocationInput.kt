package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanLabeledLocationInput(
    val location: PlanLocationInput,
    val label: String? = null
)

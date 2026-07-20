package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanStopLocationInput(
    val stopLocationId: String
)

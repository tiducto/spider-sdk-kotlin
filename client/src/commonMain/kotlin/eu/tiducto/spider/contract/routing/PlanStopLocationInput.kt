package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanStopLocationInput(
    val stopLocationId: String
)

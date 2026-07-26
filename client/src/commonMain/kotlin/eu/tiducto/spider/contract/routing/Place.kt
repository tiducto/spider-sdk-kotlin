package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Place(
    val name: String? = null,
    val stop: PlanConnectionStop? = null
)

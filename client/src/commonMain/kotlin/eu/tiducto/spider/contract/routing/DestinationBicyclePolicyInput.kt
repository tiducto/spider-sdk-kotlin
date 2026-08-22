package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class DestinationBicyclePolicyInput(
    val allowKeeping: Boolean? = null,
    val keepingCost: Int? = null
)

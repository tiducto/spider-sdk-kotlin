package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class DestinationScooterPolicyInput(
    val allowKeeping: Boolean? = null,
    val keepingCost: Int? = null
)

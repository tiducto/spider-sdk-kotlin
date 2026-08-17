package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class BicycleRentalPreferencesInput(
    val allowedNetworks: List<String>? = null,
    val bannedNetworks: List<String>? = null,
    val destinationBicyclePolicy: DestinationBicyclePolicyInput? = null
)

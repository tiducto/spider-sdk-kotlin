package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class BicycleWalkPreferencesCostInput(
    val mountDismountCost: Int? = null,
    val reluctance: Double? = null
)

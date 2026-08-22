package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class BicycleWalkPreferencesInput(
    val cost: BicycleWalkPreferencesCostInput? = null,
    val mountDismountTime: String? = null,
    val speed: Double? = null
)

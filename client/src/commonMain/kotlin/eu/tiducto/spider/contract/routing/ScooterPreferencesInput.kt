package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class ScooterPreferencesInput(
    val optimization: ScooterOptimizationInput? = null,
    val reluctance: Double? = null,
    val rental: ScooterRentalPreferencesInput? = null,
    val speed: Double? = null
)

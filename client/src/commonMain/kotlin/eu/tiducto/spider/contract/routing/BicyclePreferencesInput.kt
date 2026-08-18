package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class BicyclePreferencesInput(
    val boardCost: Int? = null,
    val optimization: CyclingOptimizationInput? = null,
    val parking: BicycleParkingPreferencesInput? = null,
    val reluctance: Double? = null,
    val rental: BicycleRentalPreferencesInput? = null,
    val speed: Double? = null,
    val walk: BicycleWalkPreferencesInput? = null
)

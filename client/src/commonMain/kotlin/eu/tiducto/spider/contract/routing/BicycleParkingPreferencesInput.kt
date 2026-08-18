package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class BicycleParkingPreferencesInput(
    val filters: List<ParkingFilter>? = null,
    val preferred: List<ParkingFilter>? = null,
    val unpreferredCost: Int? = null
)

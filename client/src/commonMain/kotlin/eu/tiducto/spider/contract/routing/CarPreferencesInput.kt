package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class CarPreferencesInput(
    val boardCost: Int? = null,
    val parking: CarParkingPreferencesInput? = null,
    val reluctance: Double? = null,
    val rental: CarRentalPreferencesInput? = null
)

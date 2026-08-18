package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanStreetPreferencesInput(
    val bicycle: BicyclePreferencesInput? = null,
    val car: CarPreferencesInput? = null,
    val scooter: ScooterPreferencesInput? = null,
    val walk: WalkPreferencesInput? = null
)

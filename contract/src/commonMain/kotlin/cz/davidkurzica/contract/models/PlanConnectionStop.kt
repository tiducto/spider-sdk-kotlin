package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionStop(
    val wheelchairBoarding: WheelchairBoarding? = null
)

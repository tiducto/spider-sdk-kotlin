package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanLocationInput(
    val coordinate: PlanCoordinateInput? = null,
    val stopLocation: PlanStopLocationInput? = null
)

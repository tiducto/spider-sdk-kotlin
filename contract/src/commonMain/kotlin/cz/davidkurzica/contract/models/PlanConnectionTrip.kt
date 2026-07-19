package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnectionTrip(
    val gtfsId: String,
    val bikesAllowed: BikesAllowed? = null
)

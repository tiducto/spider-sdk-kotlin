package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class TripData(
    val trip: TripTrip? = null
)

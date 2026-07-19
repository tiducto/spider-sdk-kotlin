package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class Stop(
    val gtfsId: String,
    val name: String,
    val wheelchairBoarding: WheelchairBoarding? = null,
    val stoptimesWithoutPatterns: List<Stoptime>? = null
)

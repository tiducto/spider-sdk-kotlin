package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    val gtfsId: String,
    val route: Route,
    val bikesAllowed: BikesAllowed? = null
)

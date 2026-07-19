package cz.davidkurzica.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class LegTime(
    val scheduledTime: String
)

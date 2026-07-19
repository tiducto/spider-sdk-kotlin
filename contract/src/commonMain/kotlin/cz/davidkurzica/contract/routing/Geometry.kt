package cz.davidkurzica.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class Geometry(
    val points: String? = null,
    val length: Int? = null
)

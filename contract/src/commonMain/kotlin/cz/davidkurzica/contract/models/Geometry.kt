package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class Geometry(
    val points: String? = null,
    val length: Int? = null
)

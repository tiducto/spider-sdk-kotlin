package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class Route(
    val shortName: String? = null,
    val longName: String? = null,
    val mode: TransitMode? = null
)

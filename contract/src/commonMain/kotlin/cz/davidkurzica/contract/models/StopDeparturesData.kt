package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class StopDeparturesData(
    val asStop: Stop? = null,
    val asStation: Stop? = null
)

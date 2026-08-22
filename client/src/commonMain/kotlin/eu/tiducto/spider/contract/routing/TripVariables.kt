package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TripVariables(
    val id: String,
    val serviceDate: String? = null
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TransitFilterSelectInput(
    val agencies: List<String>? = null,
    val routes: List<String>? = null
)

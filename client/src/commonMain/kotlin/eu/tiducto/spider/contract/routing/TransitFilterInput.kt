package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TransitFilterInput(
    val exclude: List<TransitFilterSelectInput>? = null,
    val include: List<TransitFilterSelectInput>? = null
)

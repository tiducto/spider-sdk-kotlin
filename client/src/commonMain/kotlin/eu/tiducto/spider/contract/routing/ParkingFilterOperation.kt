package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class ParkingFilterOperation(
    val tags: List<String>? = null
)

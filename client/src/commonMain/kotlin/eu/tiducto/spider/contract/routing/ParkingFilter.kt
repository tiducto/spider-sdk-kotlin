package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class ParkingFilter(
    val not: List<ParkingFilterOperation>? = null,
    val select: List<ParkingFilterOperation>? = null
)

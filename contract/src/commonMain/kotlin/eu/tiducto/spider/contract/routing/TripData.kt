package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class TripData(
    val trip: TripTrip? = null
)

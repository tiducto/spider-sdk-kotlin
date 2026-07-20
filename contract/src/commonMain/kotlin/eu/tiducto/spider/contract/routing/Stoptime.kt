package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class Stoptime(
    val serviceDay: Long? = null,
    val scheduledDeparture: Int? = null,
    val realtimeDeparture: Int? = null,
    val realtime: Boolean? = null,
    val realtimeState: RealtimeState? = null,
    val headsign: String? = null,
    val trip: Trip? = null
)

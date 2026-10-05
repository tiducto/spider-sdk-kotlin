package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Stoptime(
    val serviceDay: Long? = null,
    val scheduledArrival: Int? = null,
    val scheduledDeparture: Int? = null,
    val realtimeArrival: Int? = null,
    val realtimeDeparture: Int? = null,
    val realtime: Boolean? = null,
    val realtimeState: RealtimeState? = null,
    /** Typical (p50) delay at this stop in seconds for this trip on the service date's day type; null when unknown. */
    val typicalDelay: Int? = null,
    val stop: TripStop? = null
)

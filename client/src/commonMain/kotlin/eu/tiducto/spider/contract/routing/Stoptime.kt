package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Stoptime(
    /** Unix seconds at the start of the trip's service day; it plus a seconds field gives that time as Unix seconds. */
    val serviceDay: Long,
    /** Seconds after `serviceDay`. */
    val scheduledArrival: Int,
    /** Seconds after `serviceDay`. */
    val scheduledDeparture: Int,
    /** Seconds after `serviceDay`, realtime merged in; the schedule when there is none. */
    val realtimeArrival: Int,
    /** Seconds after `serviceDay`, realtime merged in; the schedule when there is none. */
    val realtimeDeparture: Int,
    /** True when `realtimeArrival` and `realtimeDeparture` come from realtime. */
    val realtime: Boolean,
    val realtimeState: RealtimeState,
    /** The trip's usual delay at this stop in seconds: the median (p50) recorded on the service date's day type, from the environment's realtime history, never below 0 and never decreasing along the trip's pattern. Null when the trip has live realtime or there is no history. */
    val typicalDelay: Int?,
    val stop: TripStop
)

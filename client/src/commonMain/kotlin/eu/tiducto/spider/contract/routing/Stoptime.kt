package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Stoptime(
    /** Unix seconds at the start of the trip's service day; it plus a seconds field gives that time as Unix seconds. */
    val serviceDay: Long? = null,
    /** Seconds after `serviceDay`. */
    val scheduledArrival: Int? = null,
    /** Seconds after `serviceDay`. */
    val scheduledDeparture: Int? = null,
    /** Seconds after `serviceDay`, realtime merged in; the schedule when there is none. */
    val realtimeArrival: Int? = null,
    /** Seconds after `serviceDay`, realtime merged in; the schedule when there is none. */
    val realtimeDeparture: Int? = null,
    /** True when `realtimeArrival` and `realtimeDeparture` come from realtime. */
    val realtime: Boolean? = null,
    val realtimeState: RealtimeState? = null,
    /** The usual delay at this stop in seconds: the median recorded for that trip on the service date's day type, from the environment's realtime history. Null when there is no history. */
    val typicalDelay: Int? = null,
    val stop: TripStop? = null
)

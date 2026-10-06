package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesStoptime(
    /** Unix seconds at the start of the trip's service day; it plus a seconds field gives that time as Unix seconds. */
    val serviceDay: Long? = null,
    /** Seconds after `serviceDay`. */
    val scheduledDeparture: Int? = null,
    /** Seconds after `serviceDay`, realtime merged in; the schedule when there is none. */
    val realtimeDeparture: Int? = null,
    /** True when `realtimeDeparture` comes from realtime. */
    val realtime: Boolean? = null,
    val realtimeState: RealtimeState? = null,
    /** The usual delay at this stop in seconds: the median recorded for that trip on the service date's day type, from the environment's realtime history. Null when there is no history. */
    val typicalDelay: Int? = null,
    val headsign: String? = null,
    /** The platform or stand the departure leaves from, which tells a station's platforms apart. */
    val stop: StopDeparturesStop? = null,
    val trip: StopDeparturesTrip? = null
)

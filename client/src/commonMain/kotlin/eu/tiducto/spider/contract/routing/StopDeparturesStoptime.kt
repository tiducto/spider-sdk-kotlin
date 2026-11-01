package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class StopDeparturesStoptime(
    /** Unix seconds at the start of the trip's service day; it plus a seconds field gives that time as Unix seconds. */
    val serviceDay: Long,
    /** The GTFS service date of the departure's trip, `YYYY-MM-DD`, as `Leg.serviceDate`. For a departure past 24:00 of its service day, it is the day before the departure's calendar date. */
    val serviceDate: String,
    /** Seconds after `serviceDay`. */
    val scheduledDeparture: Int,
    /** Seconds after `serviceDay`, realtime merged in; the schedule when there is none. */
    val realtimeDeparture: Int,
    /** True when `realtimeDeparture` comes from realtime. */
    val realtime: Boolean,
    val realtimeState: RealtimeState,
    /** Seconds of delay at the requested `reliability`: that level's percentile of the trip's recorded delay at this stop on the service date's day type, from the environment's realtime history. Never below 0. At each level, no trip arrives at a stop, delay applied, before an earlier trip of the same pattern does. Null when `reliability` is omitted, when the trip has live realtime, a canceled trip included, or when there is no history. */
    val typicalDelay: Int?,
    /** Null when the feed has none. */
    val headsign: String?,
    /** The position in the trip of this departure, as the trip's `stopPosition`. */
    val stopPosition: Int?,
    /** The platform or stand the departure leaves from, which tells a station's platforms apart. */
    val stop: StopDeparturesStop,
    val trip: StopDeparturesTrip
)

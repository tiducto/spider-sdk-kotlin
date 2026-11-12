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
    /** Seconds of delay at the requested `reliability`: that level's percentile of the trip's recorded delay at this stop on the service date's day type, from the environment's realtime history. Never below 0. At each level, no trip arrives at a stop, delay applied, before an earlier trip of the same pattern does. Null when `reliability` is omitted, when the trip has live realtime, a canceled trip included, or when there is no history. */
    val typicalDelay: Int?,
    /** How a rider boards here. Null when the stop is canceled: the trip is canceled or realtime skips the stop. */
    val pickupType: PickupDropoffType?,
    /** How a rider alights here. Null when the stop is canceled: the trip is canceled or realtime skips the stop. */
    val dropoffType: PickupDropoffType?,
    /** The stop time's position in the trip: increasing along it, not necessarily from 0 or consecutive. Realtime that adds or removes stops can change it. */
    val stopPosition: Int?,
    /** Index into the decoded `tripGeometry` points of where this stop sits on the trip's line. Null when `tripGeometry` is null or the stop has no point on it. */
    val geometryIndex: Int?,
    val stop: TripStop
)

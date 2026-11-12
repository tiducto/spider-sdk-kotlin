package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * One walk or ride.
 */
@Serializable
internal data class Leg(
    val mode: Mode,
    val start: LegTime,
    val end: LegTime,
    /** Seconds of delay applied to this leg's arrival at the requested `reliability`: that level's percentile of the trip's recorded delay at the alighting stop on the service date's day type, from the environment's realtime history. Never below 0. At each level, no trip arrives at a stop, delay applied, before an earlier trip of the same pattern does. Null when `reliability` is omitted, when the trip has live realtime, a canceled trip included, or when there is no history, and on a walk leg. */
    val typicalArrivalDelay: Int?,
    /** The realtime state of the leg's trip; `SCHEDULED` on a walk leg. */
    val realtimeState: RealtimeState,
    /** True when the leg's times include realtime. */
    val realTime: Boolean,
    /** The GTFS service date of the leg's trip, `YYYY-MM-DD`; null on a walk leg. */
    val serviceDate: String?,
    val from: Place,
    val to: Place,
    /** Null on a walk leg. */
    val route: Route?,
    /** Null on a walk leg and when the feed has none. */
    val headsign: String?,
    /** Metres. */
    val distance: Double,
    /** Seconds. */
    val duration: Long,
    /** Null on a walk leg. */
    val trip: Trip?,
    /** How the rider boards the leg's trip at `from`. `SCHEDULED` on a walk leg and on a leg with `interlineWithPreviousLeg`, where the rider is already on board. */
    val pickupType: PickupDropoffType,
    /** How the rider alights from the leg's trip at `to`. `SCHEDULED` on a walk leg and on a leg the next leg continues with `interlineWithPreviousLeg`, where the rider stays on board. */
    val dropoffType: PickupDropoffType,
    /** True on a transit leg ridden in the same vehicle as the previous leg: the vehicle carries on as another trip, often under another line number, and the rider stays on board. That change is not counted in `numberOfTransfers`. False on every other leg. */
    val interlineWithPreviousLeg: Boolean,
    val legGeometry: Geometry
)

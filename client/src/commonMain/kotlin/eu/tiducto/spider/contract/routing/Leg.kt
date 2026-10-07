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
    /** Seconds of delay applied to this leg's arrival at the requested `reliability`: that level's percentile (p50, p70 or p90) of the trip's recorded delay at the alighting stop on the service date's day type, from the environment's realtime history, never below 0 and never decreasing along the trip's pattern. Null when `reliability` is omitted, the trip has live realtime or there is no history, and on a walk leg. */
    val typicalArrivalDelay: Int?,
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
    /** True on a transit leg ridden in the same vehicle as the previous leg: the vehicle carries on as another trip, often under another line number, and the rider stays on board. That change is not counted in `numberOfTransfers`. False on every other leg. */
    val interlineWithPreviousLeg: Boolean,
    val legGeometry: Geometry
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * One walk or ride.
 */
@Serializable
internal data class Leg(
    val start: LegTime,
    val end: LegTime,
    val from: Place,
    val to: Place,
    val mode: Mode? = null,
    /** Seconds of delay applied to this leg's arrival at the requested `reliability`: that trip's typical delay at the stop on the service date's day type, from the environment's realtime history. Null when `reliability` is omitted or there is no history. */
    val typicalArrivalDelay: Int? = null,
    val realtimeState: RealtimeState? = null,
    /** True when the leg's times include realtime. */
    val realTime: Boolean? = null,
    /** The GTFS service date of the leg's trip, `YYYY-MM-DD`; null on a walk leg. */
    val serviceDate: String? = null,
    /** Null on a walk leg. */
    val route: Route? = null,
    val headsign: String? = null,
    /** Metres. */
    val distance: Double? = null,
    /** Seconds. */
    val duration: Double? = null,
    /** Null on a walk leg. */
    val trip: Trip? = null,
    /** True on a transit leg ridden in the same vehicle as the previous leg: the vehicle carries on as another trip, often under another line number, and the rider stays on board. That change is not counted in `numberOfTransfers`. False on every other leg. */
    val interlineWithPreviousLeg: Boolean? = null,
    val legGeometry: Geometry? = null
)

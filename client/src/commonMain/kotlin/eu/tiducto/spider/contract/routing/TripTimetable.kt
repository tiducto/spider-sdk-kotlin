package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * One trip on one service date: its stops and times, realtime merged in. On a date without realtime the rows carry the schedule alone, and on a date the trip does not run they carry its schedule on that date.
 */
@Serializable
internal data class TripTimetable(
    val gtfsId: String,
    /** `0` or `1`, as the feed gives it; null when it gives none. */
    val directionId: String?,
    /** Null when the feed has none. */
    val tripHeadsign: String?,
    val bikesAllowed: BikesAllowed,
    val wheelchairAccessible: WheelchairBoarding,
    val route: TripRoute,
    val stoptimesForDate: List<Stoptime>,
    /** The trip's path; null when the feed has no shapes. */
    val tripGeometry: TripGeometry?
)

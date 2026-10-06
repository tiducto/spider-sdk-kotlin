package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * One trip on one service date: its stops and times, realtime merged in. On a date without realtime, the rows carry the schedule alone.
 */
@Serializable
internal data class TripTimetable(
    val gtfsId: String,
    val route: TripRoute,
    val directionId: String? = null,
    val tripHeadsign: String? = null,
    val bikesAllowed: BikesAllowed? = null,
    val wheelchairAccessible: WheelchairBoarding? = null,
    val stoptimesForDate: List<Stoptime>? = null,
    /** The trip's path; null when the feed has no shapes. */
    val tripGeometry: TripGeometry? = null
)

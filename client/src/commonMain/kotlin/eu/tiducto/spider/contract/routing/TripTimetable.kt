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
    val tripGeometry: TripGeometry?,
    /** The vehicle running the trip run answered (the requested `serviceDate`, and `startTime` for a frequency-based trip), as the realtime trip update names it. A Spider extension: OTP 2.10 has no vehicle on a trip. Null until a vehicle is matched to the run. Its `wheelchairAccessible` is the vehicle's realtime access, separate from this trip's `wheelchairAccessible`, which stays the timetable's. */
    val vehicle: TripVehicle?
)

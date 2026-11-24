package eu.tiducto.spider.client

import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Live GTFS-RT data. Reachable as `client.realtime` on any [SpiderClient].
 *
 * Everything here is **best-effort and volatile** — vehicles come and go, feeds go stale, and a
 * trip may have no live vehicle at all. Every result carries [FeedFreshness] so the UI can tell the
 * rider how current the data is ("updated Ns ago") and de-emphasise stale snapshots. Failures are
 * returned as [SpiderResult.Error] and should degrade the surface, never break it.
 *
 * ```kotlin
 * // Live position + delay for the trip the rider is looking at.
 * when (val v = client.realtime.vehicleForTrip(tripGtfsId, serviceDate)) {
 *     is SpiderResult.Success -> v.data.vehicle?.let { drawOnMap(it.latitude, it.longitude, it.bearing) }
 *     is SpiderResult.Error -> Unit // keep the last known position
 * }
 *
 * // Trip updates for trips running on one service date (pass the leg's serviceDate through).
 * val updates = client.realtime.tripUpdates(serviceDate = "2026-09-22", tripIds = listOf("1:1012_7_260922"))
 * ```
 */
class SpiderRealtime(
    private val baseUrl: String,
    apiKey: String,
    retry: RetryConfig? = null,
    logging: LoggingConfig = LoggingConfig(),
) {
    private val log = logging.buildLog()
    private val realtime = RealtimeClient(baseUrl, apiKey, retry, log)

    /**
     * Live positions for the runs of [tripIds] on one GTFS [serviceDate] (`YYYY-MM-DD`; pass the [Leg.serviceDate]
     * or [Departure.serviceDate] through), up to 50 per call (comma-batched in one request). A vehicle on another
     * run of the same trip isn't returned. A malformed date or more than 50 ids returns [SpiderError.BadRequest]
     * without a request; otherwise an empty list returns an empty result without a request.
     */
    suspend fun vehicles(serviceDate: String, tripIds: List<String>): SpiderResult<VehiclePositions> =
        runCatchingRealtime("vehicles(${tripIds.size})") { realtime.vehicles(serviceDate, tripIds) }

    /**
     * Live position for one trip run: [tripId] on the GTFS [serviceDate] (`YYYY-MM-DD`; pass the [Leg.serviceDate]
     * or [Departure.serviceDate] through). A run with no vehicle currently reporting returns a
     * [LiveVehicleUpdate] with a null [LiveVehicleUpdate.vehicle] — a normal state, not an error. A malformed date
     * returns [SpiderError.BadRequest] without a request.
     *
     * [startTime] picks the run of a frequency-based trip (`frequencies.txt`) by the start time the feed reports
     * it with ([LiveVehicle.startTime]): a GTFS time `H:MM:SS` or `HH:MM:SS`, hours up to 99. Null answers the run
     * with the earliest start time, so a trip that isn't frequency-based answers as without it. Any other value
     * returns [SpiderError.BadRequest] without a request.
     */
    suspend fun vehicleForTrip(
        tripId: String,
        serviceDate: String,
        startTime: String? = null,
    ): SpiderResult<LiveVehicleUpdate> =
        runCatchingRealtime("vehicleForTrip($tripId)") { realtime.vehicleForTrip(tripId, serviceDate, startTime) }

    /**
     * Live trip updates for [tripIds] running on one GTFS [serviceDate] (`YYYY-MM-DD`), one [TripUpdate] per trip
     * run: its delay, its stop-time updates and the [TripUpdate.modifications] (detours) that select it. Pass the
     * [Leg.serviceDate] or [Departure.serviceDate] through, and the trip ids exactly as routing returns them
     * (`<feedId>:<id>`). A frequency-based trip has one item per live run, told apart by [TripUpdate.startTime].
     * Trips on several dates take one call per date. Duplicate ids are sent once and the ids are sent sorted, so
     * equal calls share one cached response. No ids, a blank id, more than 50 distinct ids or a malformed date
     * returns [SpiderError.BadRequest] without a request. The successor of [delays].
     */
    suspend fun tripUpdates(serviceDate: String, tripIds: List<String>): SpiderResult<TripUpdates> =
        runCatchingRealtime("tripUpdates(${tripIds.size})") { realtime.tripUpdates(serviceDate, tripIds) }

    /**
     * Live delays for [tripIds] running on one GTFS [serviceDate] (`YYYY-MM-DD`); pass the [Leg.serviceDate] or
     * [Departure.serviceDate] through, and the trip ids exactly as routing returns them (`<feedId>:<id>`). Trips
     * on several dates take one call per date. Duplicate ids are sent once and the ids are sent sorted, so equal
     * calls share one cached response. No ids, a blank id, more than 50 distinct ids or a malformed date returns
     * [SpiderError.BadRequest] without a request. A frequency-based trip answers its run with the earliest start
     * time; [tripUpdates] lists every run.
     *
     * Deprecated with the API's `GET /realtime/v1/delays` in contract 1.10, which keeps working until it is
     * retired with notice, not before 2027-05-24.
     */
    @Deprecated(
        "Use tripUpdates, which answers every trip run with its trip modifications; /realtime/v1/delays retires no earlier than 2027-05-24.",
        ReplaceWith("tripUpdates(serviceDate, tripIds)"),
    )
    suspend fun delays(serviceDate: String, tripIds: List<String>): SpiderResult<TripDelays> =
        runCatchingRealtime("delays(${tripIds.size})") { realtime.delays(serviceDate, tripIds) }

    /** All active service alerts for the environment. */
    suspend fun alerts(): SpiderResult<ServiceAlerts> =
        runCatchingRealtime("alerts") { realtime.alerts() }

    private suspend inline fun <T> runCatchingRealtime(
        op: String,
        crossinline block: suspend () -> T,
    ): SpiderResult<T> =
        context(log) {
            spiderCall(tag = "SpiderRealtime", message = { "$op failed against $baseUrl" }, block = block)
        }
}

/** Optional configuration for the realtime surface. Apply it via `SpiderClient(...) { realtime { … } }`. */
class RealtimeConfig : SurfaceConfig()

/**
 * How current a realtime snapshot is.
 *
 * [feedTimestamp] is when the upstream feed was last produced; [staleSeconds] is how old that feed
 * was when the gateway answered. Both are null when the feed has never reported (e.g. a trip with no
 * live data yet). Note this measures the *feed's* age, not how long ago the client fetched — the UI
 * typically shows its own fetch time for "updated Ns ago" and uses [staleSeconds] to flag a lagging feed.
 */
data class FeedFreshness(
    val feedTimestamp: Instant?,
    val staleSeconds: Int?,
)

/**
 * A vehicle's live position. [tripId], [latitude] and [longitude] are always present; the other fields are
 * null when the producer doesn't report them. The ids ([tripId], [routeId], [vehicleId], [stopId]) are
 * feed-prefixed (`<feedId>:<id>`), exactly as routing returns them. [bearing] is degrees clockwise from north; [currentStatus] is the raw GTFS-RT
 * `VehicleStopStatus` string (`INCOMING_AT` / `STOPPED_AT` / `IN_TRANSIT_TO`), passed through
 * unchanged so a producer's newer values still surface. [serviceDate] is the GTFS service date (`YYYY-MM-DD`) of
 * the trip run the vehicle is on.
 *
 * [startTime] is the run's start time as the feed's GTFS-RT `start_time` gives it (`HH:MM:SS`, may exceed
 * `24:00:00`); it tells apart the runs of a frequency-based trip, and is null when the feed gives none.
 * [wheelchairAccessible], [airConditioning], [wifi] and [cardPayment] describe the vehicle the feed reports for
 * the trip, each null when the feed doesn't say. [wheelchairAccessible] maps GTFS-RT
 * `VehicleDescriptor.WheelchairAccessible`: `WHEELCHAIR_ACCESSIBLE` is [WheelchairBoarding.POSSIBLE],
 * `WHEELCHAIR_INACCESSIBLE` [WheelchairBoarding.NOT_POSSIBLE], and `UNKNOWN` (no information) null.
 */
data class LiveVehicle(
    val tripId: String,
    val serviceDate: String,
    val routeId: String?,
    val vehicleId: String?,
    val label: String?,
    val latitude: Double,
    val longitude: Double,
    val bearing: Double?,
    val speed: Double?,
    val stopId: String?,
    val currentStatus: String?,
    val occupancy: OccupancyStatus?,
    val timestamp: Instant?,
    val startTime: String? = null,
    val wheelchairAccessible: WheelchairBoarding? = null,
    val airConditioning: Boolean? = null,
    val wifi: Boolean? = null,
    val cardPayment: Boolean? = null,
)

/**
 * Passenger occupancy for a vehicle, from GTFS-RT `VehiclePosition.occupancy_status`. Feeds that
 * don't report occupancy (Brno's among them) leave it null — treat that as "not reported" and hide
 * the affordance, never as [EMPTY]. [UNKNOWN] is a producer value this SDK version doesn't recognise.
 */
enum class OccupancyStatus {
    EMPTY,
    MANY_SEATS_AVAILABLE,
    FEW_SEATS_AVAILABLE,
    STANDING_ROOM_ONLY,
    CRUSHED_STANDING_ROOM_ONLY,
    FULL,
    NOT_ACCEPTING_PASSENGERS,
    NOT_BOARDABLE,
    UNKNOWN,
    ;

    companion object {
        // NO_DATA_AVAILABLE and null both mean "not reported" → null (hidden).
        fun fromWire(raw: String?): OccupancyStatus? = when (raw) {
            null, "NO_DATA_AVAILABLE" -> null
            "EMPTY" -> EMPTY
            "MANY_SEATS_AVAILABLE" -> MANY_SEATS_AVAILABLE
            "FEW_SEATS_AVAILABLE" -> FEW_SEATS_AVAILABLE
            "STANDING_ROOM_ONLY" -> STANDING_ROOM_ONLY
            "CRUSHED_STANDING_ROOM_ONLY" -> CRUSHED_STANDING_ROOM_ONLY
            "FULL" -> FULL
            "NOT_ACCEPTING_PASSENGERS" -> NOT_ACCEPTING_PASSENGERS
            "NOT_BOARDABLE" -> NOT_BOARDABLE
            else -> UNKNOWN
        }
    }
}

/** Result of [SpiderRealtime.vehicleForTrip]: the vehicle (or null if none reporting) plus freshness. */
data class LiveVehicleUpdate(
    val vehicle: LiveVehicle?,
    val freshness: FeedFreshness,
)

/** Result of [SpiderRealtime.vehicles]: positions found, [missing] trip ids, and freshness. */
data class VehiclePositions(
    val vehicles: ImmutableList<LiveVehicle>,
    val missing: ImmutableList<String>,
    val freshness: FeedFreshness,
) {
    internal companion object {
        val EMPTY = VehiclePositions(persistentListOf(), persistentListOf(), FeedFreshness(null, null))
    }
}

/**
 * Live schedule deviation for a trip. [tripId] and [routeId] are feed-prefixed (`<feedId>:<id>`).
 * [delaySeconds] is the trip-level delay (positive = late, negative = early), null when the updates give only
 * absolute times; [scheduleRelationship] is the raw GTFS-RT value (`SCHEDULED` / `CANCELED` / …).
 */
data class TripDelay(
    val tripId: String,
    val routeId: String?,
    val delaySeconds: Int?,
    val scheduleRelationship: String?,
    val stopTimeUpdates: ImmutableList<StopTimeUpdate>,
)

data class StopTimeUpdate(
    val stopId: String?,
    val stopSequence: Int?,
    val arrivalDelay: Int?,
    val departureDelay: Int?,
    val scheduleRelationship: String?,
)

/**
 * Result of [SpiderRealtime.delays] for one [serviceDate]: the delays the feed reported, the [missing] trip ids
 * it didn't (exactly as sent), and feed freshness. Look up with [delayFor].
 */
data class TripDelays(
    val serviceDate: String,
    val delays: ImmutableList<TripDelay>,
    val missing: ImmutableList<String>,
    val freshness: FeedFreshness,
) {
    /** The delay for [tripId] on [serviceDate], if the feed reported one. */
    fun delayFor(tripId: String): TripDelay? = delays.firstOrNull { it.tripId == tripId }
}

/**
 * Live update for one trip run, from [SpiderRealtime.tripUpdates]. [tripId] and [routeId] are feed-prefixed
 * (`<feedId>:<id>`). [startTime] is the run's GTFS-RT `start_time` (`HH:MM:SS`, may exceed `24:00:00`), which
 * tells apart the runs of a frequency-based trip; null when the feed gives none. [delaySeconds] is the trip-level
 * delay (positive = late), null when the updates give only absolute times; [scheduleRelationship] is the raw
 * GTFS-RT value (`SCHEDULED` / `CANCELED` / …). A run that only [modifications] select has empty
 * [stopTimeUpdates] and null [routeId], [delaySeconds] and [scheduleRelationship].
 *
 * [modifications] is **experimental**, as GTFS-RT `TripModifications` is: the detours that apply to this run, in
 * feed order, their delays accumulating along the trip; empty when none applies. Its shape may change in a
 * minor version as the GTFS-RT spec settles.
 */
data class TripUpdate(
    val tripId: String,
    val startTime: String?,
    val routeId: String?,
    val delaySeconds: Int?,
    val scheduleRelationship: String?,
    val stopTimeUpdates: ImmutableList<StopTimeUpdate>,
    val modifications: ImmutableList<TripModification>,
)

/**
 * **Experimental**, as GTFS-RT `TripModifications.Modification`: a span of the trip's stop times, from [startStop]
 * to [endStop] inclusive, replaced by [replacementStops]. [id] is the feed-prefixed id of the entity holding the
 * modifications. [startTimes] are the runs of a frequency-based trip it applies to; empty when it applies to every
 * run. [endStop] is null when no stop time is replaced. [propagatedModificationDelay] is the seconds added to every
 * arrival and departure after the modification. [alertId] is the feed-prefixed id of the [ServiceAlert] describing
 * it. [shape] is the modified trip's path, empty when the feed has no realtime shape for it.
 */
data class TripModification(
    val id: String,
    val startTimes: ImmutableList<String>,
    val startStop: StopSelector?,
    val endStop: StopSelector?,
    val replacementStops: ImmutableList<ReplacementStop>,
    val propagatedModificationDelay: Int,
    val alertId: String?,
    val lastModifiedTime: Instant?,
    val shape: ImmutableList<LatLon>,
)

/** **Experimental**, as GTFS-RT `StopSelector`: an original stop time of the trip, by [stopSequence] and/or [stopId]. */
data class StopSelector(
    val stopSequence: Int?,
    val stopId: String?,
)

/**
 * **Experimental**, as GTFS-RT `ReplacementStop`: a stop a modified trip visits. [stopId] is feed-prefixed.
 * [travelTimeToStop] is the seconds from the arrival at the stop before [TripModification.startStop] to the arrival
 * here. [name], [latitude] and [longitude] are set only for a stop the feed defines in realtime; a static stop is
 * found through stop search.
 */
data class ReplacementStop(
    val stopId: String?,
    val travelTimeToStop: Int?,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
)

/**
 * Result of [SpiderRealtime.tripUpdates] for one [serviceDate]: one [TripUpdate] per trip run, in order of
 * [TripUpdate.startTime] within a trip (a run without one first), the [missing] trip ids with neither a live update
 * nor a trip modification (exactly as sent), and feed freshness.
 */
data class TripUpdates(
    val serviceDate: String,
    val tripUpdates: ImmutableList<TripUpdate>,
    val missing: ImmutableList<String>,
    val freshness: FeedFreshness,
) {
    /** Every run of [tripId] the feed reported, in order of [TripUpdate.startTime]. */
    fun updatesFor(tripId: String): List<TripUpdate> = tripUpdates.filter { it.tripId == tripId }

    /** The first run of [tripId] the feed reported (the one [SpiderRealtime.delays] answers), if any. */
    fun updateFor(tripId: String): TripUpdate? = tripUpdates.firstOrNull { it.tripId == tripId }
}

/**
 * A service alert. [id] and the informed entities' ids are feed-prefixed (`<feedId>:<id>`). Text fields are
 * already resolved to a single language by the gateway. [cause]/[effect]/[severityLevel] are raw GTFS-RT enum
 * strings, passed through unchanged.
 */
data class ServiceAlert(
    val id: String,
    val cause: String?,
    val effect: String?,
    val severityLevel: String?,
    val headerText: String?,
    val descriptionText: String?,
    val url: String?,
    val activePeriods: ImmutableList<AlertActivePeriod>,
    val informedEntities: ImmutableList<AlertInformedEntity>,
)

data class AlertActivePeriod(val start: Instant?, val end: Instant?)

data class AlertInformedEntity(
    val agencyId: String?,
    val routeId: String?,
    val tripId: String?,
    val stopId: String?,
)

/** Result of [SpiderRealtime.alerts]: the active alerts plus freshness. */
data class ServiceAlerts(
    val alerts: ImmutableList<ServiceAlert>,
    val freshness: FeedFreshness,
)

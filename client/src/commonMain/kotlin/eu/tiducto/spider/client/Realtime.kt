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
 * when (val v = client.realtime.vehicleForTrip(tripGtfsId)) {
 *     is SpiderResult.Success -> v.data.vehicle?.let { drawOnMap(it.latitude, it.longitude, it.bearing) }
 *     is SpiderResult.Error -> Unit // keep the last known position
 * }
 *
 * // Batch delays for a departures board.
 * val delays = client.realtime.delays(visibleTripIds)
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

    /** Live positions for the given [tripIds] (comma-batched in one request). Empty input skips the call. */
    suspend fun vehicles(tripIds: List<String>): SpiderResult<VehiclePositions> {
        if (tripIds.isEmpty()) return SpiderResult.Success(VehiclePositions.EMPTY)
        return runCatchingRealtime("vehicles(${tripIds.size})") { realtime.vehicles(tripIds) }
    }

    /**
     * Live position for a single trip. A trip with no vehicle currently reporting returns a
     * [LiveVehicleUpdate] with a null [LiveVehicleUpdate.vehicle] — a normal state, not an error.
     */
    suspend fun vehicleForTrip(tripId: String): SpiderResult<LiveVehicleUpdate> =
        runCatchingRealtime("vehicleForTrip($tripId)") { realtime.vehicleForTrip(tripId) }

    /** Live delays for the given [tripIds] (comma-batched in one request). Empty input skips the call. */
    suspend fun delays(tripIds: List<String>): SpiderResult<TripDelays> {
        if (tripIds.isEmpty()) return SpiderResult.Success(TripDelays.EMPTY)
        return runCatchingRealtime("delays(${tripIds.size})") { realtime.delays(tripIds) }
    }

    /** All active service alerts for the environment. */
    suspend fun alerts(): SpiderResult<ServiceAlerts> =
        runCatchingRealtime("alerts") { realtime.alerts() }

    private inline fun <T> runCatchingRealtime(op: String, block: () -> T): SpiderResult<T> =
        context(log) {
            spiderCatch(tag = "SpiderRealtime", message = { "$op failed against $baseUrl" }, block = block)
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
 * A vehicle's live position. All fields are nullable — GTFS-RT producers populate wildly different
 * subsets. [latitude]/[longitude] are the only fields worth much without the others; guard on them
 * before drawing. [bearing] is degrees clockwise from north; [currentStatus] is the raw GTFS-RT
 * `VehicleStopStatus` string (`INCOMING_AT` / `STOPPED_AT` / `IN_TRANSIT_TO`), passed through
 * unchanged so a producer's newer values still surface.
 */
data class LiveVehicle(
    val tripId: String?,
    val routeId: String?,
    val vehicleId: String?,
    val label: String?,
    val latitude: Double?,
    val longitude: Double?,
    val bearing: Double?,
    val speed: Double?,
    val stopId: String?,
    val currentStatus: String?,
    val occupancy: OccupancyStatus?,
    val timestamp: Instant?,
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
 * Live schedule deviation for a trip. [delaySeconds] is the trip-level delay (positive = late,
 * negative = early); [scheduleRelationship] is the raw GTFS-RT value (`SCHEDULED` / `CANCELED` / …).
 */
data class TripDelay(
    val tripId: String?,
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

/** Result of [SpiderRealtime.delays]: delays found, [missing] trip ids, and freshness. */
data class TripDelays(
    val delays: ImmutableList<TripDelay>,
    val missing: ImmutableList<String>,
    val freshness: FeedFreshness,
) {
    /** The trip-level delay for [tripId], if the feed reported one. */
    fun delayFor(tripId: String): TripDelay? = delays.firstOrNull { it.tripId == tripId }

    internal companion object {
        val EMPTY = TripDelays(persistentListOf(), persistentListOf(), FeedFreshness(null, null))
    }
}

/**
 * A service alert. Text fields are already resolved to a single language by the gateway.
 * [cause]/[effect]/[severityLevel] are raw GTFS-RT enum strings, passed through unchanged.
 */
data class ServiceAlert(
    val id: String?,
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

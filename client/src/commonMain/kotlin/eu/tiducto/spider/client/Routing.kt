package eu.tiducto.spider.client

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

class SpiderRouting(
    private val baseUrl: String,
    apiKey: String,
    retry: RetryConfig? = null,
    logging: LoggingConfig = LoggingConfig(),
) {
    private val log = logging.buildLog()
    private val routing = RoutingClient(baseUrl, apiKey, retry, log)

    suspend fun plan(
        origin: Location,
        destination: Location,
        time: RouteTime = RouteTime.DepartAt(Clock.System.now()),
        first: Int? = DEFAULT_FIRST,
        via: List<ViaLocation> = emptyList(),
        allowedTransitModes: Set<TransitMode>? = null,
        maxTransfers: Int? = null,
        searchWindow: Duration = 1.hours,
        wheelchairAccessible: Boolean = false,
    ): SpiderResult<Route> = page(
        RouteRequest(
            origin, destination, time, via,
            allowedTransitModes, maxTransfers, searchWindow, wheelchairAccessible,
        ),
        first = first,
    )

    /**
     * Loads the next page of itineraries (later departures). Returns null if no next page is available.
     */
    suspend fun nextPage(prev: Route, first: Int? = DEFAULT_FIRST): SpiderResult<Route>? =
        if (!prev.pageInfo.hasNextPage) null
        else page(prev.request, first = first, after = prev.pageInfo.endCursor)

    /**
     * Loads the previous page of itineraries (earlier departures). Returns null if no previous page is available.
     */
    suspend fun previousPage(prev: Route, last: Int? = DEFAULT_FIRST): SpiderResult<Route>? =
        // Relay backward paging = last + before (not first + before) — earlier itineraries, correct page size.
        if (!prev.pageInfo.hasPreviousPage) null
        else page(prev.request, last = last, before = prev.pageInfo.startCursor)

    private suspend fun page(
        request: RouteRequest,
        first: Int? = null,
        last: Int? = null,
        before: String? = null,
        after: String? = null,
    ): SpiderResult<Route> = context(log) {
        spiderCatch(
            tag = "SpiderRouting",
            message = {
                "plan failed against $baseUrl (origin=${request.origin} destination=${request.destination} first=$first last=$last before=$before after=$after)"
            },
        ) {
            routing.planConnection(request, first = first, last = last, before = before, after = after)
        }
    }

    private companion object {
        const val DEFAULT_FIRST = 5
    }

    suspend fun departures(
        id: String,
        numberOfDepartures: Int = 30,
        startTime: Instant? = null,
        timeRange: Duration = 24.hours,
    ): SpiderResult<ImmutableList<Departure>> = context(log) {
        spiderCatch(tag = "SpiderRouting", message = { "departures failed against $baseUrl (stopId=$id)" }) {
            routing.stopDepartures(id, numberOfDepartures, startTime, timeRange)
        }
    }

    /** [serviceDate] is GTFS calendar date, formatted "YYYY-MM-DD". Null defaults to today. */
    suspend fun trip(
        tripId: String,
        serviceDate: String? = null,
    ): SpiderResult<TripDetails> = context(log) {
        spiderCatch(tag = "SpiderRouting", message = { "trip failed against $baseUrl (tripId=$tripId)" }) {
            routing.trip(tripId, serviceDate)
        }
    }
}

class RoutingConfig : FeatureConfig()

object Routing : SpiderFeature<RoutingConfig, SpiderRouting> {
    override fun newConfig() = RoutingConfig()
    override fun build(baseUrl: String, apiKey: String, config: RoutingConfig, logging: LoggingConfig): SpiderRouting =
        SpiderRouting(baseUrl = baseUrl, apiKey = apiKey, retry = config.retry, logging = logging)
}

sealed interface Location {
    data class Coordinate(val latitude: Double, val longitude: Double) : Location
    data class Stop(val id: String) : Location
}

sealed interface ViaLocation {
    /**
     * Vehicle's path must traverse one of [stopIds], but the passenger isn't
     * required to alight. A single through-route leg satisfies the constraint.
     */
    data class PassThrough(val stopIds: List<String>) : ViaLocation {
        constructor(stopId: String) : this(listOf(stopId))
    }

    /**
     * Passenger must be at [location] (becomes a leg boundary). [minimumWaitTime]
     * forces at least that dwell between arriving and leaving the via stop.
     */
    data class Visit(
        val location: Location,
        val minimumWaitTime: Duration = Duration.ZERO,
    ) : ViaLocation
}

sealed interface RouteTime {
    val time: Instant
    data class DepartAt(override val time: Instant) : RouteTime
    data class ArriveBy(override val time: Instant) : RouteTime
}

data class RouteRequest(
    val origin: Location,
    val destination: Location,
    val time: RouteTime,
    val via: List<ViaLocation> = emptyList(),
    // null = no filter (all modes); a non-empty set restricts routing to those. WALK/UNKNOWN aren't transit
    // modes and drop out. Empty and null both mean "no filter" — never emptySet-as-all.
    val allowedTransitModes: Set<TransitMode>? = null,
    val maxTransfers: Int? = null,
    // Always sent (default 1h) so OTP never uses its dynamic, route-dependent window — predictable cost + paging.
    // Normalized to whole minutes on the wire (floored, min 1m): sub-minute windows return almost nothing.
    val searchWindow: Duration = 1.hours,
    val wheelchairAccessible: Boolean = false,
)

data class Route(
    val request: RouteRequest,
    val edges: ImmutableList<RouteEdge>,
    val pageInfo: RoutePageInfo,
    val routingErrors: ImmutableList<RoutingError>,
    val searchDateTime: String?,
)

data class RouteEdge(
    val cursor: String,
    val itinerary: Itinerary,
)

@Serializable
data class Itinerary(
    val start: String?,
    val end: String?,
    val durationSeconds: Long,
    val waitingTimeSeconds: Long?,
    val numberOfTransfers: Int,
    val accessibilityScore: Double? = null,
    @Serializable(with = LegListSerializer::class)
    val legs: ImmutableList<Leg>,
) {
    val stableKey by lazy {
        val legs = legs.joinToString("|") { leg ->
            "${leg.mode}:${leg.tripGtfsId.orEmpty()}:${leg.startScheduled}:${leg.endScheduled}"
        }
        "$start/$end/$legs"
    }
    fun isSameTripAs(other: Itinerary?): Boolean =
        other != null && stableKey == other.stableKey
}

@Serializable
data class Leg(
    val mode: TransitMode?,
    val startScheduled: String,
    val endScheduled: String,
    val startEstimated: String? = null,
    val endEstimated: String? = null,
    val startDelay: Duration? = null,
    val endDelay: Duration? = null,
    val isRealtime: Boolean = false,
    val realtimeState: String? = null,
    val fromName: String?,
    val toName: String?,
    val fromGtfsId: String? = null,
    val toGtfsId: String? = null,
    val routeShortName: String?,
    val routeLongName: String?,
    val headsign: String?,
    val distanceMeters: Double?,
    val durationSeconds: Double?,
    val tripGtfsId: String?,
    val bikesAllowed: BikesAllowed? = null,
    val accessibilityScore: Double? = null,
    val fromWheelchair: WheelchairBoarding? = null,
    val toWheelchair: WheelchairBoarding? = null,
    @Serializable(with = LatLonListSerializer::class)
    val geometry: ImmutableList<LatLon> = persistentListOf(),
)

@Serializable
data class LatLon(val lat: Double, val lon: Double)

private object LegListSerializer : ImmutableListSerializer<Leg>(Leg.serializer())
private object LatLonListSerializer : ImmutableListSerializer<LatLon>(LatLon.serializer())

data class RoutePageInfo(
    val startCursor: String?,
    val endCursor: String?,
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val searchWindowUsed: String?,
)

data class RoutingError(
    val code: String,
    val description: String,
    val inputField: String?,
)

data class Departure(
    val scheduledTime: Instant,
    val realtimeTime: Instant?,
    val isRealtime: Boolean,
    val realtimeState: String?,
    val headsign: String?,
    val tripGtfsId: String?,
    val routeShortName: String?,
    val routeLongName: String?,
    val mode: TransitMode?,
)

data class TripDetails(
    val gtfsId: String,
    val routeShortName: String?,
    val routeLongName: String?,
    val mode: TransitMode?,
    val headsign: String?,
    val directionId: String?,
    val bikesAllowed: BikesAllowed? = null,
    val stops: ImmutableList<TripStop>,
    val geometry: ImmutableList<LatLon> = persistentListOf(),
)

data class TripStop(
    val gtfsId: String,
    val name: String,
    val lat: Double?,
    val lon: Double?,
    val scheduledArrival: Instant?,
    val scheduledDeparture: Instant?,
    val realtimeArrival: Instant?,
    val realtimeDeparture: Instant?,
    val isRealtime: Boolean,
    val wheelchairBoarding: WheelchairBoarding? = null,
)

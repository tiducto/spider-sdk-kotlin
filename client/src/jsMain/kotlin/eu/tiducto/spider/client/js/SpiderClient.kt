@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("unused")

package eu.tiducto.spider.client.js

import eu.tiducto.spider.client.AdminLevel
import eu.tiducto.spider.client.Realtime
import eu.tiducto.spider.client.RouteTime
import eu.tiducto.spider.client.Routing
import eu.tiducto.spider.client.Stops
import eu.tiducto.spider.client.SpiderClient as CoreSpiderClient
import eu.tiducto.spider.client.SpiderRealtime as CoreSpiderRealtime
import eu.tiducto.spider.client.SpiderRouting as CoreSpiderRouting
import eu.tiducto.spider.client.SpiderStops as CoreSpiderStops
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * JS/TS entry point for the Spider SDK. Constructs the full Kotlin client with routing, stop search
 * and realtime installed, and exposes each surface through an export-safe facade.
 *
 * ```ts
 * const client = new SpiderClient("https://brno.api.transitapi.eu", apiKey)
 * const res = await client.routing.plan(Location.coordinate(49.19, 16.61), Location.stop("U1146N1"))
 * if (res.isSuccess) drawItineraries(res.data.edges)
 * ```
 */
@JsExport
class SpiderClient(baseUrl: String, apiKey: String) {
    private val delegate = CoreSpiderClient(baseUrl = baseUrl, apiKey = apiKey) {
        install(Routing)
        install(Stops)
        install(Realtime)
    }

    val routing: SpiderRouting = SpiderRouting(delegate.routing)
    val stops: SpiderStops = SpiderStops(delegate.stops)
    val realtime: SpiderRealtime = SpiderRealtime(delegate.realtime)

    /** The wire-contract version this SDK speaks; sent on every request. */
    val contractVersion: String get() = delegate.contractVersion
}

/** Trip planning, departures and trip detail. Times are epoch milliseconds; durations are seconds. */
@JsExport
class SpiderRouting internal constructor(private val delegate: CoreSpiderRouting) {

    /** Plan a trip departing at [departAtEpochMs] (null = now). [first] caps the number of itineraries. */
    suspend fun plan(
        origin: SpiderLocation,
        destination: SpiderLocation,
        departAtEpochMs: Double? = null,
        first: Int = DEFAULT_FIRST,
    ): SpiderResult<Route> {
        val time = RouteTime.DepartAt(departAtEpochMs?.toInstant() ?: Clock.System.now())
        return delegate.plan(origin = origin.domain, destination = destination.domain, time = time, first = first)
            .toJs { Route(it) }
    }

    /** Plan a trip that must arrive by [arriveByEpochMs]. */
    suspend fun planArriveBy(
        origin: SpiderLocation,
        destination: SpiderLocation,
        arriveByEpochMs: Double,
        first: Int = DEFAULT_FIRST,
    ): SpiderResult<Route> {
        val time = RouteTime.ArriveBy(arriveByEpochMs.toInstant())
        return delegate.plan(origin = origin.domain, destination = destination.domain, time = time, first = first)
            .toJs { Route(it) }
    }

    /** Next page of itineraries (later departures), or null if there is none. */
    suspend fun nextPage(route: Route, first: Int = DEFAULT_FIRST): SpiderResult<Route>? =
        delegate.nextPage(route.domain, first)?.toJs { Route(it) }

    /** Previous page of itineraries (earlier departures), or null if there is none. */
    suspend fun previousPage(route: Route, first: Int = DEFAULT_FIRST): SpiderResult<Route>? =
        delegate.previousPage(route.domain, first)?.toJs { Route(it) }

    /** Upcoming departures from a stop (or station) id. */
    suspend fun departures(
        stopId: String,
        numberOfDepartures: Int = DEFAULT_DEPARTURES,
        startTimeEpochMs: Double? = null,
        timeRangeSeconds: Double = DEFAULT_TIME_RANGE_SECONDS,
    ): SpiderResult<Array<Departure>> =
        delegate.departures(
            id = stopId,
            numberOfDepartures = numberOfDepartures,
            startTime = startTimeEpochMs?.toInstant(),
            timeRange = timeRangeSeconds.seconds,
        ).toJs { list -> list.map { Departure(it) }.toTypedArray() }

    /** Full detail for a trip. [serviceDate] is a GTFS calendar date "YYYY-MM-DD"; null = today. */
    suspend fun trip(tripId: String, serviceDate: String? = null): SpiderResult<TripDetails> =
        delegate.trip(tripId, serviceDate).toJs { TripDetails(it) }

    private companion object {
        const val DEFAULT_FIRST = 5
        const val DEFAULT_DEPARTURES = 30
        const val DEFAULT_TIME_RANGE_SECONDS = 86_400.0
    }
}

/** Stop text search + administrative-geography filtering. */
@JsExport
class SpiderStops internal constructor(private val delegate: CoreSpiderStops) {

    /**
     * Search stops. [nameQuery] is a fuzzy free-text match; the admin-level params (each an exact
     * match against boundary enrichment the deployment has, e.g. [city] = "Brno") combine with AND.
     * All null = unconstrained. Filtering a level the deployment wasn't enriched with is an error.
     */
    suspend fun search(
        nameQuery: String? = null,
        country: String? = null,
        region: String? = null,
        district: String? = null,
        city: String? = null,
        suburb: String? = null,
    ): SpiderResult<Array<Stop>> =
        delegate.search {
            filter {
                nameQuery?.let { name eq it }
                country?.let { AdminLevel.COUNTRY eq it }
                region?.let { AdminLevel.REGION eq it }
                district?.let { AdminLevel.DISTRICT eq it }
                city?.let { AdminLevel.CITY eq it }
                suburb?.let { AdminLevel.SUBURB eq it }
            }
        }.toJs { list -> list.map { Stop(it) }.toTypedArray() }
}

/** Live GTFS-RT data — vehicle positions, delays and service alerts. */
@JsExport
class SpiderRealtime internal constructor(private val delegate: CoreSpiderRealtime) {

    /** Live positions for a batch of trip ids. */
    suspend fun vehicles(tripIds: Array<String>): SpiderResult<VehiclePositions> =
        delegate.vehicles(tripIds.toList()).toJs { VehiclePositions(it) }

    /** Live position for a single trip (vehicle is null when none is reporting). */
    suspend fun vehicleForTrip(tripId: String): SpiderResult<LiveVehicleUpdate> =
        delegate.vehicleForTrip(tripId).toJs { LiveVehicleUpdate(it) }

    /** Live delays for a batch of trip ids. */
    suspend fun delays(tripIds: Array<String>): SpiderResult<TripDelays> =
        delegate.delays(tripIds.toList()).toJs { TripDelays(it) }

    /** All active service alerts for the environment. */
    suspend fun alerts(): SpiderResult<ServiceAlerts> =
        delegate.alerts().toJs { ServiceAlerts(it) }
}

private fun Double.toInstant(): Instant = Instant.fromEpochMilliseconds(this.toLong())

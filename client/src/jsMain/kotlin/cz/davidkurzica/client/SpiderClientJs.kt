@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("unused")

package cz.davidkurzica.client

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * JS/TS entry point for the Spider SDK. Constructs the full Kotlin [SpiderClient] with routing, stop
 * search and realtime installed, and exposes each surface through an export-safe facade.
 *
 * ```ts
 * const client = new SpiderClientJs("https://brno.api.transitapi.eu", apiKey)
 * const res = await client.routing.plan(coordinateLocation(49.19, 16.61), coordinateLocation(49.23, 16.58))
 * if (res.isSuccess) drawItineraries(res.data.edges)
 * ```
 */
@JsExport
class SpiderClientJs(baseUrl: String, apiKey: String) {
    private val delegate = SpiderClient(baseUrl = baseUrl, apiKey = apiKey) {
        install(Routing)
        install(Stops)
        install(Realtime)
    }

    val routing: SpiderRoutingJs = SpiderRoutingJs(delegate.routing)
    val stops: SpiderStopsJs = SpiderStopsJs(delegate.stops)
    val realtime: SpiderRealtimeJs = SpiderRealtimeJs(delegate.realtime)

    /** The wire-contract version this SDK speaks; sent on every request. */
    val contractVersion: String get() = delegate.contractVersion
}

/** Trip planning, departures and trip detail. Times are epoch milliseconds; durations are seconds. */
@JsExport
class SpiderRoutingJs internal constructor(private val delegate: SpiderRouting) {

    /** Plan a trip departing at [departAtEpochMs] (null = now). [first] caps the number of itineraries. */
    suspend fun plan(
        from: RouteLocationJs,
        to: RouteLocationJs,
        departAtEpochMs: Double? = null,
        first: Int = DEFAULT_FIRST,
    ): SpiderResultJs<RouteJs> {
        val time = RouteTime.DepartAt(departAtEpochMs?.toInstant() ?: Clock.System.now())
        return delegate.route(from = from.domain, to = to.domain, time = time, first = first).toJs { RouteJs(it) }
    }

    /** Plan a trip that must arrive by [arriveByEpochMs]. */
    suspend fun planArriveBy(
        from: RouteLocationJs,
        to: RouteLocationJs,
        arriveByEpochMs: Double,
        first: Int = DEFAULT_FIRST,
    ): SpiderResultJs<RouteJs> {
        val time = RouteTime.ArriveBy(arriveByEpochMs.toInstant())
        return delegate.route(from = from.domain, to = to.domain, time = time, first = first).toJs { RouteJs(it) }
    }

    /** Next page of itineraries (later departures), or null if there is none. */
    suspend fun nextPage(route: RouteJs, first: Int = DEFAULT_FIRST): SpiderResultJs<RouteJs>? =
        delegate.nextPage(route.domain, first)?.toJs { RouteJs(it) }

    /** Previous page of itineraries (earlier departures), or null if there is none. */
    suspend fun previousPage(route: RouteJs, first: Int = DEFAULT_FIRST): SpiderResultJs<RouteJs>? =
        delegate.previousPage(route.domain, first)?.toJs { RouteJs(it) }

    /** Upcoming departures from a stop (or station) id. */
    suspend fun departures(
        stopId: String,
        numberOfDepartures: Int = DEFAULT_DEPARTURES,
        startTimeEpochMs: Double? = null,
        timeRangeSeconds: Double = DEFAULT_TIME_RANGE_SECONDS,
    ): SpiderResultJs<Array<DepartureJs>> =
        delegate.departures(
            id = stopId,
            numberOfDepartures = numberOfDepartures,
            startTime = startTimeEpochMs?.toInstant(),
            timeRange = timeRangeSeconds.seconds,
        ).toJs { list -> list.map { DepartureJs(it) }.toTypedArray() }

    /** Full detail for a trip. [serviceDate] is a GTFS calendar date "YYYY-MM-DD"; null = today. */
    suspend fun trip(tripId: String, serviceDate: String? = null): SpiderResultJs<TripDetailsJs> =
        delegate.trip(tripId, serviceDate).toJs { TripDetailsJs(it) }

    private companion object {
        const val DEFAULT_FIRST = 5
        const val DEFAULT_DEPARTURES = 30
        const val DEFAULT_TIME_RANGE_SECONDS = 86_400.0
    }
}

/** Stop text search + administrative-geography filtering. */
@JsExport
class SpiderStopsJs internal constructor(private val delegate: SpiderStops) {

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
    ): SpiderResultJs<Array<StopJs>> =
        delegate.search {
            filter {
                nameQuery?.let { name eq it }
                country?.let { AdminLevel.COUNTRY eq it }
                region?.let { AdminLevel.REGION eq it }
                district?.let { AdminLevel.DISTRICT eq it }
                city?.let { AdminLevel.CITY eq it }
                suburb?.let { AdminLevel.SUBURB eq it }
            }
        }.toJs { list -> list.map { StopJs(it) }.toTypedArray() }
}

/** Live GTFS-RT data — vehicle positions, delays and service alerts. */
@JsExport
class SpiderRealtimeJs internal constructor(private val delegate: SpiderRealtime) {

    /** Live positions for a batch of trip ids. */
    suspend fun vehicles(tripIds: Array<String>): SpiderResultJs<VehiclePositionsJs> =
        delegate.vehicles(tripIds.toList()).toJs { VehiclePositionsJs(it) }

    /** Live position for a single trip (vehicle is null when none is reporting). */
    suspend fun vehicleForTrip(tripId: String): SpiderResultJs<LiveVehicleUpdateJs> =
        delegate.vehicleForTrip(tripId).toJs { LiveVehicleUpdateJs(it) }

    /** Live delays for a batch of trip ids. */
    suspend fun delays(tripIds: Array<String>): SpiderResultJs<TripDelaysJs> =
        delegate.delays(tripIds.toList()).toJs { TripDelaysJs(it) }

    /** All active service alerts for the environment. */
    suspend fun alerts(): SpiderResultJs<ServiceAlertsJs> =
        delegate.alerts().toJs { ServiceAlertsJs(it) }
}

private fun Double.toInstant(): Instant = Instant.fromEpochMilliseconds(this.toLong())

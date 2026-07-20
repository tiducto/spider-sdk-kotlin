@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("unused")

package cz.davidkurzica.client

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/** A single geographic point (WGS84 degrees). */
@JsExport
class LatLonJs internal constructor(val lat: Double, val lon: Double)

/** One transit/walk leg of an itinerary. Enum fields are the upstream name (e.g. "BUS"), or null. */
@JsExport
class LegJs internal constructor(domain: Leg) {
    val mode: String? = domain.mode?.name
    val startScheduled: String = domain.startScheduled
    val endScheduled: String = domain.endScheduled
    val fromName: String? = domain.fromName
    val toName: String? = domain.toName
    val routeShortName: String? = domain.routeShortName
    val routeLongName: String? = domain.routeLongName
    val headsign: String? = domain.headsign
    val distanceMeters: Double? = domain.distanceMeters
    val durationSeconds: Double? = domain.durationSeconds
    val tripGtfsId: String? = domain.tripGtfsId
    val bikesAllowed: String? = domain.bikesAllowed?.name
    val accessibilityScore: Double? = domain.accessibilityScore
    val fromWheelchair: String? = domain.fromWheelchair?.name
    val toWheelchair: String? = domain.toWheelchair?.name

    /** Decoded leg geometry — the SDK already decodes the wire polyline, so consumers draw directly. */
    val geometry: Array<LatLonJs> = domain.geometry.map { LatLonJs(it.lat, it.lon) }.toTypedArray()
}

/** One itinerary (a full origin→destination option). */
@JsExport
class ItineraryJs internal constructor(domain: Itinerary) {
    val start: String? = domain.start
    val end: String? = domain.end
    val durationSeconds: Double = domain.durationSeconds.toDouble()
    val waitingTimeSeconds: Double? = domain.waitingTimeSeconds?.toDouble()
    val numberOfTransfers: Int = domain.numberOfTransfers
    val accessibilityScore: Double? = domain.accessibilityScore
    val legs: Array<LegJs> = domain.legs.map { LegJs(it) }.toTypedArray()
}

/** An itinerary plus its pagination cursor. */
@JsExport
class RouteEdgeJs internal constructor(domain: RouteEdge) {
    val cursor: String = domain.cursor
    val itinerary: ItineraryJs = ItineraryJs(domain.itinerary)
}

@JsExport
class RoutePageInfoJs internal constructor(domain: RoutePageInfo) {
    val startCursor: String? = domain.startCursor
    val endCursor: String? = domain.endCursor
    val hasNextPage: Boolean = domain.hasNextPage
    val hasPreviousPage: Boolean = domain.hasPreviousPage
    val searchWindowUsed: String? = domain.searchWindowUsed
}

@JsExport
class RoutingErrorJs internal constructor(domain: RoutingError) {
    val code: String = domain.code
    val description: String = domain.description
    val inputField: String? = domain.inputField
}

/**
 * A page of trip-planning results. Retains the underlying domain route so
 * [SpiderRoutingJs.nextPage]/[SpiderRoutingJs.previousPage] can page from it.
 */
@JsExport
class RouteJs internal constructor(internal val domain: Route) {
    val edges: Array<RouteEdgeJs> = domain.edges.map { RouteEdgeJs(it) }.toTypedArray()
    val pageInfo: RoutePageInfoJs = RoutePageInfoJs(domain.pageInfo)
    val routingErrors: Array<RoutingErrorJs> = domain.routingErrors.map { RoutingErrorJs(it) }.toTypedArray()
    val searchDateTime: String? = domain.searchDateTime
}

/** One upcoming departure from a stop. Times are epoch milliseconds. */
@JsExport
class DepartureJs internal constructor(domain: Departure) {
    val scheduledTimeEpochMs: Double = domain.scheduledTime.toEpochMilliseconds().toDouble()
    val realtimeTimeEpochMs: Double? = domain.realtimeTime?.toEpochMilliseconds()?.toDouble()
    val isRealtime: Boolean = domain.isRealtime
    val realtimeState: String? = domain.realtimeState
    val headsign: String? = domain.headsign
    val tripGtfsId: String? = domain.tripGtfsId
    val routeShortName: String? = domain.routeShortName
    val routeLongName: String? = domain.routeLongName
    val mode: String? = domain.mode?.name
}

/** A stop on a trip's schedule. Times are epoch milliseconds. */
@JsExport
class TripStopJs internal constructor(domain: TripStop) {
    val gtfsId: String = domain.gtfsId
    val name: String = domain.name
    val lat: Double? = domain.lat
    val lon: Double? = domain.lon
    val scheduledArrivalEpochMs: Double? = domain.scheduledArrival?.toEpochMilliseconds()?.toDouble()
    val scheduledDepartureEpochMs: Double? = domain.scheduledDeparture?.toEpochMilliseconds()?.toDouble()
    val realtimeArrivalEpochMs: Double? = domain.realtimeArrival?.toEpochMilliseconds()?.toDouble()
    val realtimeDepartureEpochMs: Double? = domain.realtimeDeparture?.toEpochMilliseconds()?.toDouble()
    val isRealtime: Boolean = domain.isRealtime
    val wheelchairBoarding: String? = domain.wheelchairBoarding?.name
}

/** Full detail for a single trip. */
@JsExport
class TripDetailsJs internal constructor(domain: TripDetails) {
    val gtfsId: String = domain.gtfsId
    val routeShortName: String? = domain.routeShortName
    val routeLongName: String? = domain.routeLongName
    val mode: String? = domain.mode?.name
    val headsign: String? = domain.headsign
    val directionId: String? = domain.directionId
    val bikesAllowed: String? = domain.bikesAllowed?.name
    val stops: Array<TripStopJs> = domain.stops.map { TripStopJs(it) }.toTypedArray()
    val geometry: Array<LatLonJs> = domain.geometry.map { LatLonJs(it.lat, it.lon) }.toTypedArray()
}

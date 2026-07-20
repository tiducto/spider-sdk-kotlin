@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("unused")

package cz.davidkurzica.client.js

import cz.davidkurzica.client.Departure as CoreDeparture
import cz.davidkurzica.client.Itinerary as CoreItinerary
import cz.davidkurzica.client.Leg as CoreLeg
import cz.davidkurzica.client.Route as CoreRoute
import cz.davidkurzica.client.RouteEdge as CoreRouteEdge
import cz.davidkurzica.client.RoutePageInfo as CoreRoutePageInfo
import cz.davidkurzica.client.RoutingError as CoreRoutingError
import cz.davidkurzica.client.TripDetails as CoreTripDetails
import cz.davidkurzica.client.TripStop as CoreTripStop
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/** A single geographic point (WGS84 degrees). */
@JsExport
class LatLon internal constructor(val lat: Double, val lon: Double)

/** One transit/walk leg of an itinerary. Enum fields are the upstream name (e.g. "BUS"), or null. */
@JsExport
class Leg internal constructor(domain: CoreLeg) {
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
    val geometry: Array<LatLon> = domain.geometry.map { LatLon(it.lat, it.lon) }.toTypedArray()
}

/** One itinerary (a full origin→destination option). */
@JsExport
class Itinerary internal constructor(domain: CoreItinerary) {
    val start: String? = domain.start
    val end: String? = domain.end
    val durationSeconds: Double = domain.durationSeconds.toDouble()
    val waitingTimeSeconds: Double? = domain.waitingTimeSeconds?.toDouble()
    val numberOfTransfers: Int = domain.numberOfTransfers
    val accessibilityScore: Double? = domain.accessibilityScore
    val legs: Array<Leg> = domain.legs.map { Leg(it) }.toTypedArray()
}

/** An itinerary plus its pagination cursor. */
@JsExport
class RouteEdge internal constructor(domain: CoreRouteEdge) {
    val cursor: String = domain.cursor
    val itinerary: Itinerary = Itinerary(domain.itinerary)
}

@JsExport
class RoutePageInfo internal constructor(domain: CoreRoutePageInfo) {
    val startCursor: String? = domain.startCursor
    val endCursor: String? = domain.endCursor
    val hasNextPage: Boolean = domain.hasNextPage
    val hasPreviousPage: Boolean = domain.hasPreviousPage
    val searchWindowUsed: String? = domain.searchWindowUsed
}

@JsExport
class RoutingError internal constructor(domain: CoreRoutingError) {
    val code: String = domain.code
    val description: String = domain.description
    val inputField: String? = domain.inputField
}

/**
 * A page of trip-planning results. Retains the underlying domain route so
 * [SpiderRouting.nextPage]/[SpiderRouting.previousPage] can page from it.
 */
@JsExport
class Route internal constructor(internal val domain: CoreRoute) {
    val edges: Array<RouteEdge> = domain.edges.map { RouteEdge(it) }.toTypedArray()
    val pageInfo: RoutePageInfo = RoutePageInfo(domain.pageInfo)
    val routingErrors: Array<RoutingError> = domain.routingErrors.map { RoutingError(it) }.toTypedArray()
    val searchDateTime: String? = domain.searchDateTime
}

/** One upcoming departure from a stop. Times are epoch milliseconds. */
@JsExport
class Departure internal constructor(domain: CoreDeparture) {
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
class TripStop internal constructor(domain: CoreTripStop) {
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
class TripDetails internal constructor(domain: CoreTripDetails) {
    val gtfsId: String = domain.gtfsId
    val routeShortName: String? = domain.routeShortName
    val routeLongName: String? = domain.routeLongName
    val mode: String? = domain.mode?.name
    val headsign: String? = domain.headsign
    val directionId: String? = domain.directionId
    val bikesAllowed: String? = domain.bikesAllowed?.name
    val stops: Array<TripStop> = domain.stops.map { TripStop(it) }.toTypedArray()
    val geometry: Array<LatLon> = domain.geometry.map { LatLon(it.lat, it.lon) }.toTypedArray()
}

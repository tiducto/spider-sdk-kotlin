@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("unused")

package cz.davidkurzica.client

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/** How current a realtime snapshot is. [feedTimestampEpochMs] is when the feed was last produced. */
@JsExport
class FeedFreshnessJs internal constructor(domain: FeedFreshness) {
    val feedTimestampEpochMs: Double? = domain.feedTimestamp?.toEpochMilliseconds()?.toDouble()
    val staleSeconds: Int? = domain.staleSeconds
}

/** A vehicle's live position. All fields are best-effort; guard on lat/lon before drawing. */
@JsExport
class LiveVehicleJs internal constructor(domain: LiveVehicle) {
    val tripId: String? = domain.tripId
    val routeId: String? = domain.routeId
    val vehicleId: String? = domain.vehicleId
    val label: String? = domain.label
    val latitude: Double? = domain.latitude
    val longitude: Double? = domain.longitude
    val bearing: Double? = domain.bearing
    val speed: Double? = domain.speed
    val stopId: String? = domain.stopId
    val currentStatus: String? = domain.currentStatus
    val occupancy: String? = domain.occupancy?.name
    val timestampEpochMs: Double? = domain.timestamp?.toEpochMilliseconds()?.toDouble()
}

/** Live position for a single trip; [vehicle] is null when none is currently reporting. */
@JsExport
class LiveVehicleUpdateJs internal constructor(domain: LiveVehicleUpdate) {
    val vehicle: LiveVehicleJs? = domain.vehicle?.let { LiveVehicleJs(it) }
    val freshness: FeedFreshnessJs = FeedFreshnessJs(domain.freshness)
}

/** Positions found for a batch of trips, plus the [missing] trip ids. */
@JsExport
class VehiclePositionsJs internal constructor(domain: VehiclePositions) {
    val vehicles: Array<LiveVehicleJs> = domain.vehicles.map { LiveVehicleJs(it) }.toTypedArray()
    val missing: Array<String> = domain.missing.toTypedArray()
    val freshness: FeedFreshnessJs = FeedFreshnessJs(domain.freshness)
}

@JsExport
class StopTimeUpdateJs internal constructor(domain: StopTimeUpdate) {
    val stopId: String? = domain.stopId
    val stopSequence: Int? = domain.stopSequence
    val arrivalDelay: Int? = domain.arrivalDelay
    val departureDelay: Int? = domain.departureDelay
    val scheduleRelationship: String? = domain.scheduleRelationship
}

/** Live schedule deviation for a trip. [delaySeconds] positive = late, negative = early. */
@JsExport
class TripDelayJs internal constructor(domain: TripDelay) {
    val tripId: String? = domain.tripId
    val routeId: String? = domain.routeId
    val delaySeconds: Int? = domain.delaySeconds
    val scheduleRelationship: String? = domain.scheduleRelationship
    val stopTimeUpdates: Array<StopTimeUpdateJs> = domain.stopTimeUpdates.map { StopTimeUpdateJs(it) }.toTypedArray()
}

/** Delays found for a batch of trips, plus the [missing] trip ids. */
@JsExport
class TripDelaysJs internal constructor(private val domain: TripDelays) {
    val delays: Array<TripDelayJs> = domain.delays.map { TripDelayJs(it) }.toTypedArray()
    val missing: Array<String> = domain.missing.toTypedArray()
    val freshness: FeedFreshnessJs = FeedFreshnessJs(domain.freshness)

    /** The trip-level delay for [tripId], if the feed reported one. */
    fun delayFor(tripId: String): TripDelayJs? = domain.delayFor(tripId)?.let { TripDelayJs(it) }
}

@JsExport
class AlertActivePeriodJs internal constructor(domain: AlertActivePeriod) {
    val startEpochMs: Double? = domain.start?.toEpochMilliseconds()?.toDouble()
    val endEpochMs: Double? = domain.end?.toEpochMilliseconds()?.toDouble()
}

@JsExport
class AlertInformedEntityJs internal constructor(domain: AlertInformedEntity) {
    val agencyId: String? = domain.agencyId
    val routeId: String? = domain.routeId
    val tripId: String? = domain.tripId
    val stopId: String? = domain.stopId
}

/** A service alert. Text is already resolved to a single language by the gateway. */
@JsExport
class ServiceAlertJs internal constructor(domain: ServiceAlert) {
    val id: String? = domain.id
    val cause: String? = domain.cause
    val effect: String? = domain.effect
    val severityLevel: String? = domain.severityLevel
    val headerText: String? = domain.headerText
    val descriptionText: String? = domain.descriptionText
    val url: String? = domain.url
    val activePeriods: Array<AlertActivePeriodJs> = domain.activePeriods.map { AlertActivePeriodJs(it) }.toTypedArray()
    val informedEntities: Array<AlertInformedEntityJs> = domain.informedEntities.map { AlertInformedEntityJs(it) }.toTypedArray()
}

/** Active service alerts for the environment, plus freshness. */
@JsExport
class ServiceAlertsJs internal constructor(domain: ServiceAlerts) {
    val alerts: Array<ServiceAlertJs> = domain.alerts.map { ServiceAlertJs(it) }.toTypedArray()
    val freshness: FeedFreshnessJs = FeedFreshnessJs(domain.freshness)
}

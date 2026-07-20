@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("unused")

package cz.davidkurzica.client.js

import cz.davidkurzica.client.AlertActivePeriod as CoreAlertActivePeriod
import cz.davidkurzica.client.AlertInformedEntity as CoreAlertInformedEntity
import cz.davidkurzica.client.FeedFreshness as CoreFeedFreshness
import cz.davidkurzica.client.LiveVehicle as CoreLiveVehicle
import cz.davidkurzica.client.LiveVehicleUpdate as CoreLiveVehicleUpdate
import cz.davidkurzica.client.ServiceAlert as CoreServiceAlert
import cz.davidkurzica.client.ServiceAlerts as CoreServiceAlerts
import cz.davidkurzica.client.StopTimeUpdate as CoreStopTimeUpdate
import cz.davidkurzica.client.TripDelay as CoreTripDelay
import cz.davidkurzica.client.TripDelays as CoreTripDelays
import cz.davidkurzica.client.VehiclePositions as CoreVehiclePositions
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/** How current a realtime snapshot is. [feedTimestampEpochMs] is when the feed was last produced. */
@JsExport
class FeedFreshness internal constructor(domain: CoreFeedFreshness) {
    val feedTimestampEpochMs: Double? = domain.feedTimestamp?.toEpochMilliseconds()?.toDouble()
    val staleSeconds: Int? = domain.staleSeconds
}

/** A vehicle's live position. All fields are best-effort; guard on lat/lon before drawing. */
@JsExport
class LiveVehicle internal constructor(domain: CoreLiveVehicle) {
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
class LiveVehicleUpdate internal constructor(domain: CoreLiveVehicleUpdate) {
    val vehicle: LiveVehicle? = domain.vehicle?.let { LiveVehicle(it) }
    val freshness: FeedFreshness = FeedFreshness(domain.freshness)
}

/** Positions found for a batch of trips, plus the [missing] trip ids. */
@JsExport
class VehiclePositions internal constructor(domain: CoreVehiclePositions) {
    val vehicles: Array<LiveVehicle> = domain.vehicles.map { LiveVehicle(it) }.toTypedArray()
    val missing: Array<String> = domain.missing.toTypedArray()
    val freshness: FeedFreshness = FeedFreshness(domain.freshness)
}

@JsExport
class StopTimeUpdate internal constructor(domain: CoreStopTimeUpdate) {
    val stopId: String? = domain.stopId
    val stopSequence: Int? = domain.stopSequence
    val arrivalDelay: Int? = domain.arrivalDelay
    val departureDelay: Int? = domain.departureDelay
    val scheduleRelationship: String? = domain.scheduleRelationship
}

/** Live schedule deviation for a trip. [delaySeconds] positive = late, negative = early. */
@JsExport
class TripDelay internal constructor(domain: CoreTripDelay) {
    val tripId: String? = domain.tripId
    val routeId: String? = domain.routeId
    val delaySeconds: Int? = domain.delaySeconds
    val scheduleRelationship: String? = domain.scheduleRelationship
    val stopTimeUpdates: Array<StopTimeUpdate> = domain.stopTimeUpdates.map { StopTimeUpdate(it) }.toTypedArray()
}

/** Delays found for a batch of trips, plus the [missing] trip ids. */
@JsExport
class TripDelays internal constructor(private val domain: CoreTripDelays) {
    val delays: Array<TripDelay> = domain.delays.map { TripDelay(it) }.toTypedArray()
    val missing: Array<String> = domain.missing.toTypedArray()
    val freshness: FeedFreshness = FeedFreshness(domain.freshness)

    /** The trip-level delay for [tripId], if the feed reported one. */
    fun delayFor(tripId: String): TripDelay? = domain.delayFor(tripId)?.let { TripDelay(it) }
}

@JsExport
class AlertActivePeriod internal constructor(domain: CoreAlertActivePeriod) {
    val startEpochMs: Double? = domain.start?.toEpochMilliseconds()?.toDouble()
    val endEpochMs: Double? = domain.end?.toEpochMilliseconds()?.toDouble()
}

@JsExport
class AlertInformedEntity internal constructor(domain: CoreAlertInformedEntity) {
    val agencyId: String? = domain.agencyId
    val routeId: String? = domain.routeId
    val tripId: String? = domain.tripId
    val stopId: String? = domain.stopId
}

/** A service alert. Text is already resolved to a single language by the gateway. */
@JsExport
class ServiceAlert internal constructor(domain: CoreServiceAlert) {
    val id: String? = domain.id
    val cause: String? = domain.cause
    val effect: String? = domain.effect
    val severityLevel: String? = domain.severityLevel
    val headerText: String? = domain.headerText
    val descriptionText: String? = domain.descriptionText
    val url: String? = domain.url
    val activePeriods: Array<AlertActivePeriod> = domain.activePeriods.map { AlertActivePeriod(it) }.toTypedArray()
    val informedEntities: Array<AlertInformedEntity> = domain.informedEntities.map { AlertInformedEntity(it) }.toTypedArray()
}

/** Active service alerts for the environment, plus freshness. */
@JsExport
class ServiceAlerts internal constructor(domain: CoreServiceAlerts) {
    val alerts: Array<ServiceAlert> = domain.alerts.map { ServiceAlert(it) }.toTypedArray()
    val freshness: FeedFreshness = FeedFreshness(domain.freshness)
}

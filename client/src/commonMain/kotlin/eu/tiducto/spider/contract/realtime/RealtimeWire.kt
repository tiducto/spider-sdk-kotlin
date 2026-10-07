package eu.tiducto.spider.contract.realtime

import kotlinx.serialization.Serializable

/**
 * The Realtime (GTFS-RT) wire contract.
 *
 * **Hand-written, not generated** — same rationale as `eu.tiducto.spider.contract.stops`: kept in its own
 * package, away from the generator-owned `routing/`. The upstream owner is the GTFS-RT gateway
 * serializer; this file mirrors it and is pinned by `RealtimeWireContractTest`.
 *
 * Members the contract requires are non-null; every other member is nullable, because GTFS-RT producers
 * populate different subsets and an absent member is normal. The enum-ish strings ([VehicleDto.currentStatus],
 * [DelayDto.scheduleRelationship], [AlertDto.cause]/[effect][AlertDto.severityLevel],
 * [VehicleDto.occupancyStatus]) are deliberately kept as raw `String` — they are passed through unchanged
 * so a producer's newer values still surface. Timestamps are epoch **seconds** (`Long`), mapped to
 * `Instant` in `:client`.
 *
 * `:client` consumes these internally and maps them to the public domain types (`LiveVehicle`,
 * `TripDelay`, `ServiceAlert`, …); they must not appear in `:client`'s public API.
 */

@Serializable
internal data class VehiclesResponseDto(
    val vehicles: List<VehicleDto>,
    val missing: List<String>,
    val feedTimestamp: Long? = null,
    val staleSeconds: Int? = null,
)

@Serializable
internal data class VehicleByTripResponseDto(
    val vehicle: VehicleDto? = null,
    val feedTimestamp: Long? = null,
    val staleSeconds: Int? = null,
)

@Serializable
internal data class VehicleDto(
    val tripId: String,
    val routeId: String? = null,
    val vehicleId: String? = null,
    val label: String? = null,
    val latitude: Double,
    val longitude: Double,
    val bearing: Double? = null,
    val speed: Double? = null,
    val stopId: String? = null,
    val currentStatus: String? = null,
    val occupancyStatus: String? = null,
    val timestamp: Long? = null,
)

@Serializable
internal data class DelaysResponseDto(
    val serviceDate: String,
    val delays: List<DelayDto>,
    val missing: List<String>,
    val feedTimestamp: Long? = null,
    val staleSeconds: Int? = null,
)

@Serializable
internal data class DelayDto(
    val tripId: String,
    val routeId: String? = null,
    val delaySeconds: Int? = null,
    val scheduleRelationship: String? = null,
    val stopTimeUpdates: List<StopTimeUpdateDto>,
)

@Serializable
internal data class StopTimeUpdateDto(
    val stopId: String? = null,
    val stopSequence: Int? = null,
    val arrivalDelay: Int? = null,
    val departureDelay: Int? = null,
    val scheduleRelationship: String? = null,
)

@Serializable
internal data class AlertsResponseDto(
    val alerts: List<AlertDto>,
    val feedTimestamp: Long? = null,
    val staleSeconds: Int? = null,
)

@Serializable
internal data class AlertDto(
    val id: String,
    val cause: String? = null,
    val effect: String? = null,
    val severityLevel: String? = null,
    val headerText: String? = null,
    val descriptionText: String? = null,
    val url: String? = null,
    val activePeriods: List<ActivePeriodDto>,
    val informedEntities: List<InformedEntityDto>,
)

@Serializable
internal data class ActivePeriodDto(val start: Long? = null, val end: Long? = null)

@Serializable
internal data class InformedEntityDto(
    val agencyId: String? = null,
    val routeId: String? = null,
    val tripId: String? = null,
    val stopId: String? = null,
)

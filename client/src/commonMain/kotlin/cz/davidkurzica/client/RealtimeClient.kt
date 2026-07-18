package cz.davidkurzica.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Live GTFS-RT read API, served from the same gateway as OTP/Meili under `$baseUrl/realtime/...`.
// Plain REST GETs with the raw key in the `apikey` header (Kong key-auth), mirroring MeiliClient.
// Ids (tripId/routeId/stopId) are opaque, feed-prefixed and passed through unchanged, exactly like
// the OTP gtfsIds — a tripId from OTP departures/plan/trip feeds straight back into these calls.
internal class RealtimeClient(
    private val baseUrl: String,
    private val apiKey: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    co.touchlab.kermit.Logger.d(tag = "RealtimeClient") { message }
                }
            }
        }
    }

    suspend fun vehicles(tripIds: List<String>): VehiclePositions {
        val response = http.get {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "vehicles") }
            parameter("tripIds", tripIds.joinToString(","))
            headers { append("apikey", apiKey) }
        }
        val dto: VehiclesResponseDto = response.decodeOrThrow("realtime/vehicles")
        return VehiclePositions(
            vehicles = dto.vehicles.map { it.toDomain() }.toImmutableList(),
            missing = dto.missing.toImmutableList(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    suspend fun vehicleForTrip(tripId: String): LiveVehicleUpdate {
        val response = http.get {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "vehicles", "by-trip", tripId) }
            headers { append("apikey", apiKey) }
        }
        // No vehicle currently reporting for this trip is a normal state, not a failure.
        if (response.status == HttpStatusCode.NotFound) {
            return LiveVehicleUpdate(vehicle = null, freshness = FeedFreshness(null, null))
        }
        val dto: VehicleByTripResponseDto = response.decodeOrThrow("realtime/vehicles/by-trip")
        return LiveVehicleUpdate(
            vehicle = dto.vehicle?.toDomain(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    suspend fun delays(tripIds: List<String>): TripDelays {
        val response = http.get {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "delays") }
            parameter("tripIds", tripIds.joinToString(","))
            headers { append("apikey", apiKey) }
        }
        val dto: DelaysResponseDto = response.decodeOrThrow("realtime/delays")
        return TripDelays(
            delays = dto.delays.map { it.toDomain() }.toImmutableList(),
            missing = dto.missing.toImmutableList(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    suspend fun alerts(): ServiceAlerts {
        val response = http.get {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "alerts") }
            headers { append("apikey", apiKey) }
        }
        val dto: AlertsResponseDto = response.decodeOrThrow("realtime/alerts")
        return ServiceAlerts(
            alerts = dto.alerts.map { it.toDomain() }.toImmutableList(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    private suspend inline fun <reified T> HttpResponse.decodeOrThrow(where: String): T {
        if (!status.isSuccess()) {
            throw SpiderTransportException.Http(status.value, "GET $where → ${status.value}: ${bodyAsText().take(300)}")
        }
        return body()
    }
}

// Epoch seconds → Instant; nulls (feed hasn't reported a timestamp) stay null.
private fun Long?.toInstantOrNull(): Instant? = this?.let { Instant.fromEpochSeconds(it) }

private fun VehicleDto.toDomain(): LiveVehicle = LiveVehicle(
    tripId = tripId,
    routeId = routeId,
    vehicleId = vehicleId,
    label = label,
    latitude = latitude,
    longitude = longitude,
    bearing = bearing,
    speed = speed,
    stopId = stopId,
    currentStatus = currentStatus,
    occupancy = OccupancyStatus.fromWire(occupancyStatus),
    timestamp = timestamp.toInstantOrNull(),
)

private fun DelayDto.toDomain(): TripDelay = TripDelay(
    tripId = tripId,
    routeId = routeId,
    delaySeconds = delaySeconds,
    scheduleRelationship = scheduleRelationship,
    stopTimeUpdates = stopTimeUpdates.map { it.toDomain() }.toImmutableList(),
)

private fun StopTimeUpdateDto.toDomain(): StopTimeUpdate = StopTimeUpdate(
    stopId = stopId,
    stopSequence = stopSequence,
    arrivalDelay = arrivalDelay,
    departureDelay = departureDelay,
    scheduleRelationship = scheduleRelationship,
)

private fun AlertDto.toDomain(): ServiceAlert = ServiceAlert(
    id = id,
    cause = cause,
    effect = effect,
    severityLevel = severityLevel,
    headerText = headerText,
    descriptionText = descriptionText,
    url = url,
    activePeriods = activePeriods.map { AlertActivePeriod(it.start.toInstantOrNull(), it.end.toInstantOrNull()) }
        .toImmutableList(),
    informedEntities = informedEntities.map {
        AlertInformedEntity(agencyId = it.agencyId, routeId = it.routeId, tripId = it.tripId, stopId = it.stopId)
    }.toImmutableList(),
)

// --- wire shapes (subset of the JSON actually mapped to domain types) ---

@Serializable
private data class VehiclesResponseDto(
    val vehicles: List<VehicleDto> = emptyList(),
    val missing: List<String> = emptyList(),
    val feedTimestamp: Long? = null,
    val staleSeconds: Int? = null,
)

@Serializable
private data class VehicleByTripResponseDto(
    val vehicle: VehicleDto? = null,
    val feedTimestamp: Long? = null,
    val staleSeconds: Int? = null,
)

@Serializable
private data class VehicleDto(
    val tripId: String? = null,
    val routeId: String? = null,
    val vehicleId: String? = null,
    val label: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val bearing: Double? = null,
    val speed: Double? = null,
    val stopId: String? = null,
    val currentStatus: String? = null,
    val occupancyStatus: String? = null,
    val timestamp: Long? = null,
)

@Serializable
private data class DelaysResponseDto(
    val delays: List<DelayDto> = emptyList(),
    val missing: List<String> = emptyList(),
    val feedTimestamp: Long? = null,
    val staleSeconds: Int? = null,
)

@Serializable
private data class DelayDto(
    val tripId: String? = null,
    val routeId: String? = null,
    val delaySeconds: Int? = null,
    val scheduleRelationship: String? = null,
    val stopTimeUpdates: List<StopTimeUpdateDto> = emptyList(),
)

@Serializable
private data class StopTimeUpdateDto(
    val stopId: String? = null,
    val stopSequence: Int? = null,
    val arrivalDelay: Int? = null,
    val departureDelay: Int? = null,
    val scheduleRelationship: String? = null,
)

@Serializable
private data class AlertsResponseDto(
    val alerts: List<AlertDto> = emptyList(),
    val feedTimestamp: Long? = null,
    val staleSeconds: Int? = null,
)

@Serializable
private data class AlertDto(
    val id: String? = null,
    val cause: String? = null,
    val effect: String? = null,
    val severityLevel: String? = null,
    val headerText: String? = null,
    val descriptionText: String? = null,
    val url: String? = null,
    val activePeriods: List<ActivePeriodDto> = emptyList(),
    val informedEntities: List<InformedEntityDto> = emptyList(),
)

@Serializable
private data class ActivePeriodDto(val start: Long? = null, val end: Long? = null)

@Serializable
private data class InformedEntityDto(
    val agencyId: String? = null,
    val routeId: String? = null,
    val tripId: String? = null,
    val stopId: String? = null,
)

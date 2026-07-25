package eu.tiducto.spider.client

import eu.tiducto.spider.contract.realtime.AlertDto
import eu.tiducto.spider.contract.realtime.AlertsResponseDto
import eu.tiducto.spider.contract.realtime.DelayDto
import eu.tiducto.spider.contract.realtime.DelaysResponseDto
import eu.tiducto.spider.contract.realtime.StopTimeUpdateDto
import eu.tiducto.spider.contract.realtime.VehicleByTripResponseDto
import eu.tiducto.spider.contract.realtime.VehicleDto
import eu.tiducto.spider.contract.realtime.VehiclesResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
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
import kotlinx.serialization.json.Json

// Live GTFS-RT read API, served from the same gateway as routing/stops under `$baseUrl/realtime/...`.
// Ids (tripId/routeId/stopId) are opaque, feed-prefixed and passed through unchanged, exactly like
// the routing gtfsIds — a tripId from routing departures/plan/trip feeds straight back into these calls.
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
        val response = rtGet {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "vehicles") }
            parameter("tripIds", tripIds.joinToString(","))
        }
        val dto: VehiclesResponseDto = response.decodeOrThrow("realtime/vehicles")
        return VehiclePositions(
            vehicles = dto.vehicles.map { it.toDomain() }.toImmutableList(),
            missing = dto.missing.toImmutableList(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    suspend fun vehicleForTrip(tripId: String): LiveVehicleUpdate {
        val response = rtGet {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "vehicles", "by-trip", tripId) }
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
        val response = rtGet {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "delays") }
            parameter("tripIds", tripIds.joinToString(","))
        }
        val dto: DelaysResponseDto = response.decodeOrThrow("realtime/delays")
        return TripDelays(
            delays = dto.delays.map { it.toDomain() }.toImmutableList(),
            missing = dto.missing.toImmutableList(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    suspend fun alerts(): ServiceAlerts {
        val response = rtGet {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "alerts") }
        }
        val dto: AlertsResponseDto = response.decodeOrThrow("realtime/alerts")
        return ServiceAlerts(
            alerts = dto.alerts.map { it.toDomain() }.toImmutableList(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    // Every realtime GET goes through here: the raw key in `apikey`, the contract
    // version so the gateway can enforce compatibility, and the inbound contract guard — run once per
    // request, before status handling, so even a 404/by-trip miss still checks the declared version.
    private suspend fun rtGet(block: HttpRequestBuilder.() -> Unit): HttpResponse {
        val response = http.get {
            block()
            headers {
                append("apikey", apiKey)
                append(SpiderContract.HEADER, SpiderContract.VERSION)
            }
        }
        ContractGuard.check(response.headers[SpiderContract.HEADER])
        return response
    }

    private suspend inline fun <reified T> HttpResponse.decodeOrThrow(where: String): T {
        if (!status.isSuccess()) {
            val body = bodyAsText()
            val envelope = parseErrorEnvelope(body)
            val detail = envelope.message ?: body.take(300)
            throw SpiderTransportException.Http(status.value, "GET $where → ${status.value}: $detail", envelope.code)
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

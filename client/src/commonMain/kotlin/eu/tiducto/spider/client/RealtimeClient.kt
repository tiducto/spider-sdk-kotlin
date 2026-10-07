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
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
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

// Live GTFS-RT read API, served from the same gateway as routing/stops under `$baseUrl/realtime/v1/...`.
// Ids (tripId/routeId/stopId/vehicleId) are opaque, feed-prefixed and passed through unchanged, exactly like
// the routing gtfsIds — a tripId from routing departures/plan/trip feeds straight back into these calls.
internal class RealtimeClient(
    private val baseUrl: String,
    private val apiKey: String,
    retry: RetryConfig? = null,
    log: SpiderLog,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient = HttpClient {
        installApiKey(apiKey)
        installAutoRetry(retry)
        install(ContentNegotiation) {
            json(json)
        }
        installSpiderLogging(log, "SpiderRealtime")
    }

    suspend fun vehicles(tripIds: List<String>): VehiclePositions {
        requireInRange("tripIds", tripIds.size <= MAX_TRIP_IDS)
        val response = rtGet {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "v1", "vehicles") }
            parameter("tripIds", tripIds.joinToString(","))
        }
        val dto: VehiclesResponseDto = response.decodeOrThrow("realtime/v1/vehicles")
        return VehiclePositions(
            vehicles = dto.vehicles.map { it.toDomain() }.toImmutableList(),
            missing = dto.missing.toImmutableList(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    suspend fun vehicleForTrip(tripId: String): LiveVehicleUpdate {
        val response = rtGet {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "v1", "vehicles", "by-trip", tripId) }
        }
        // No vehicle currently reporting for this trip is a normal state, not a failure; a plan-limit code in
        // the body still decides, whatever status a proxy passes on.
        if (response.status == HttpStatusCode.NotFound) {
            val body = response.bodyAsText()
            if (parseErrorEnvelope(body).planLimitCode != null) throw httpFailure(BY_TRIP, response.status, body)
            return LiveVehicleUpdate(vehicle = null, freshness = FeedFreshness(null, null))
        }
        val dto: VehicleByTripResponseDto = response.decodeOrThrow(BY_TRIP)
        return LiveVehicleUpdate(
            vehicle = dto.vehicle?.toDomain(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    suspend fun delays(serviceDate: String, tripIds: List<String>): TripDelays {
        requireServiceDate(serviceDate)
        val ids = delayTripIdsParam(tripIds)
        val response = rtGet {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "v1", "delays") }
            parameter("serviceDate", serviceDate)
            parameter("tripIds", ids)
        }
        val dto: DelaysResponseDto = response.decodeOrThrow("realtime/v1/delays")
        return TripDelays(
            serviceDate = dto.serviceDate,
            delays = dto.delays.map { it.toDomain() }.toImmutableList(),
            missing = dto.missing.toImmutableList(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    suspend fun alerts(): ServiceAlerts {
        val response = rtGet {
            url { takeFrom(baseUrl); appendPathSegments("realtime", "v1", "alerts") }
        }
        val dto: AlertsResponseDto = response.decodeOrThrow("realtime/v1/alerts")
        return ServiceAlerts(
            alerts = dto.alerts.map { it.toDomain() }.toImmutableList(),
            freshness = FeedFreshness(dto.feedTimestamp.toInstantOrNull(), dto.staleSeconds),
        )
    }

    private suspend fun rtGet(block: HttpRequestBuilder.() -> Unit): HttpResponse =
        http.get {
            block()
            spiderHeaders()
        }

    private suspend inline fun <reified T> HttpResponse.decodeOrThrow(where: String): T {
        if (!status.isSuccess()) throw httpFailure(where, status, bodyAsText())
        return body()
    }

    private fun httpFailure(where: String, status: HttpStatusCode, body: String): SpiderTransportException {
        val envelope = parseErrorEnvelope(body)
        envelope.planLimitCode?.let { return planLimitFailure(where, status.value, it, envelope.message) }
        val detail = envelope.message ?: body.take(300).trim()
        return SpiderTransportException.Http(status.value, "$where → ${status.value}: $detail", envelope.code, detail, envelope.field)
    }
}

private const val MAX_TRIP_IDS = 50

// Distinct, ordinal-sorted and comma-joined, so equal requests share one URL and its CDN cache entry.
internal fun delayTripIdsParam(tripIds: List<String>): String {
    if (tripIds.isEmpty()) throw SpiderTransportException.BadRequest("tripIds", "tripIds is required")
    if (tripIds.any { it.isBlank() }) throw SpiderTransportException.BadRequest("tripIds", "tripIds is invalid")
    val distinct = tripIds.distinct().sorted()
    requireInRange("tripIds", distinct.size <= MAX_TRIP_IDS)
    return distinct.joinToString(",")
}
private const val BY_TRIP = "realtime/v1/vehicles/by-trip"

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

package eu.tiducto.spider.client

import eu.tiducto.spider.client.util.decodePolyline
import eu.tiducto.spider.contract.routing.AccessibilityPreferencesInput
import eu.tiducto.spider.contract.routing.Itinerary as WireItinerary
import eu.tiducto.spider.contract.routing.Leg as WireLeg
import eu.tiducto.spider.contract.routing.PlanConnectionData
import eu.tiducto.spider.contract.routing.PlanConnectionStreamVariables
import eu.tiducto.spider.contract.routing.PlanConnectionVariables
import eu.tiducto.spider.contract.routing.PlanCoordinateInput
import eu.tiducto.spider.contract.routing.PlanDateTimeInput
import eu.tiducto.spider.contract.routing.PlanLabeledLocationInput
import eu.tiducto.spider.contract.routing.PlanLocationInput
import eu.tiducto.spider.contract.routing.PlanModesInput
import eu.tiducto.spider.contract.routing.PlanPassThroughViaLocationInput
import eu.tiducto.spider.contract.routing.PlanPreferencesInput
import eu.tiducto.spider.contract.routing.PlanStopLocationInput
import eu.tiducto.spider.contract.routing.PlanTransitModePreferenceInput
import eu.tiducto.spider.contract.routing.PlanTransitModesInput
import eu.tiducto.spider.contract.routing.PlanViaLocationInput
import eu.tiducto.spider.contract.routing.PlanVisitViaLocationInput
import eu.tiducto.spider.contract.routing.RoutingError as WireRoutingError
import eu.tiducto.spider.contract.routing.StopDeparturesData
import eu.tiducto.spider.contract.routing.StopDeparturesVariables
import eu.tiducto.spider.contract.routing.TransferPreferencesInput
import eu.tiducto.spider.contract.routing.TransitMode as WireTransitMode
import eu.tiducto.spider.contract.routing.TransitPreferencesInput
import eu.tiducto.spider.contract.routing.TripData
import eu.tiducto.spider.contract.routing.TripVariables
import eu.tiducto.spider.contract.routing.WheelchairPreferencesInput
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// gtfsIds are opaque and feed-prefixed ("1:U…"); the SDK never re-prefixes them — a stop id from search
// feeds straight into plan()/departures()/trip() (re-prefixing is what once 404'd).
internal class RoutingClient(
    private val baseUrl: String,
    private val apiKey: String,
    retry: RetryConfig? = null,
    private val log: SpiderLog,
) {
    // explicitNulls=false so an omitted optional reads as "unset" upstream. Unknown enum values decode to
    // UNKNOWN via the generated enums' serializers (coercion doesn't — it throws).
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val http = HttpClient {
        install(SSE)
        installApiKey(apiKey)
        installAutoRetry(retry)
        installSpiderLogging(log, "SpiderRouting")
    }

    suspend fun planConnection(
        request: PlanRequest,
        before: String? = null,
        after: String? = null,
    ): Route {
        request.requireValidVia()
        val dateTime = when (val time = request.time) {
            is RouteTime.DepartAt -> PlanDateTimeInput(earliestDeparture = time.time.toString())
            is RouteTime.ArriveBy -> PlanDateTimeInput(latestArrival = time.time.toString())
        }
        val variables = PlanConnectionVariables(
            dateTime = dateTime,
            origin = request.origin.toInput(),
            destination = request.destination.toInput(),
            via = request.via.takeIf { it.isNotEmpty() }?.map { it.toInput() },
            modes = request.toModesInput(),
            preferences = request.toPreferencesInput(),
            searchWindow = request.searchWindow.toIsoString(),
            before = before,
            after = after,
        )
        val data: PlanConnectionData = execute(PersistedQueries.PLAN, variables)
        val plan = data.planConnection ?: throw SpiderTransportException.NoData("routing returned no plan data")

        return Route(
            request = request,
            edges = plan.edges.orEmpty().map { edge ->
                RouteEdge(cursor = edge.cursor, itinerary = edge.node.toDomainItinerary())
            }.toImmutableList(),
            pageInfo = RoutePageInfo(
                startCursor = plan.pageInfo.startCursor,
                endCursor = plan.pageInfo.endCursor,
                hasNextPage = plan.pageInfo.hasNextPage,
                hasPreviousPage = plan.pageInfo.hasPreviousPage,
                searchWindowUsed = plan.pageInfo.searchWindowUsed,
            ),
            routingErrors = plan.routingErrors.map { it.toDomainRoutingError() }.toImmutableList(),
            searchDateTime = plan.searchDateTime,
        )
    }

    // Streaming plan over the SSE `plan-stream` route, via Ktor's client SSE plugin. Same persisted-query
    // transport as the batch plan (a POST of {id, variables}), but the router streams `chunk`/`pageInfo`
    // (and a terminal `error`) events as it sweeps the window, which this maps to a cold Flow of
    // [PlanStreamEvent]: each `chunk` → [PlanStreamEvent.Result], the `pageInfo` → a terminal
    // [PlanStreamEvent.Done] (with any routing errors). Any failure — an input the SDK rejects, a
    // non-event-stream HTTP response, a server `error` event, or a decoding slip — surfaces as a terminal
    // [PlanStreamEvent.Failure], never a throw. Auto-reconnection is left off (the plugin's default), so the
    // stream ends when the sweep does.
    fun planConnectionStream(
        request: PlanRequest,
        targetResults: Int,
        maxWindow: Duration,
        before: String? = null,
        after: String? = null,
    ): Flow<PlanStreamEvent> = flow {
        try {
            val variables = request.toStreamVariables(targetResults, maxWindow, before, after)
            val payload = json.encodeToString(PersistedRequest(id = PersistedQueries.PLAN_STREAM.id, variables = variables))
            http.sse(
                request = {
                    method = HttpMethod.Post
                    url("$baseUrl/routing/${PersistedQueries.PLAN_STREAM.path}")
                    contentType(ContentType.Application.Json)
                    spiderHeaders()
                    setBody(payload)
                },
            ) {
                incoming.collect { event ->
                    parsePlanStreamRecord(event.event ?: SSE_DEFAULT_EVENT, event.data.orEmpty(), json)
                        ?.let { emit(it) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SSEClientException) {
            // The plugin throws this when the response isn't a 2xx text/event-stream (e.g. 401/403/410/429,
            // or the gateway's JSON answer to a missing variable); recover the status and body off the
            // carried response so it maps to the right SpiderError.
            emit(PlanStreamEvent.Failure(e.toStreamFailure(json)))
        } catch (e: Exception) {
            emit(PlanStreamEvent.Failure(e.toSpiderError()))
        }
    }

    suspend fun stopDepartures(
        id: String,
        numberOfDepartures: Int,
        startTime: Instant?,
        timeRange: Duration,
    ): ImmutableList<Departure> {
        val timeRangeSeconds = timeRange.inWholeSeconds
        requireInRange("timeRange", timeRangeSeconds in 1..MAX_TIME_RANGE_SECONDS)
        val variables = StopDeparturesVariables(
            id = id,
            numberOfDepartures = numberOfDepartures,
            timeRange = timeRangeSeconds.toInt(),
            startTime = startTime?.epochSeconds,
        )
        val data: StopDeparturesData = execute(PersistedQueries.DEPARTURES, variables)
        val stop = data.asStop ?: data.asStation
            ?: throw SpiderTransportException.NoData("routing returned no stop or station for id=$id")

        return stop.stoptimesWithoutPatterns.orEmpty().mapNotNull { st ->
            val serviceDay = st.serviceDay ?: return@mapNotNull null
            val scheduledOffset = st.scheduledDeparture ?: return@mapNotNull null
            val route = st.trip?.route
            Departure(
                scheduledTime = Instant.fromEpochSeconds(serviceDay + scheduledOffset),
                realtimeTime = st.realtimeDeparture?.let { Instant.fromEpochSeconds(serviceDay + it) },
                isRealtime = st.realtime ?: false,
                realtimeState = realtimeStateFromWire(st.realtimeState?.value),
                headsign = st.headsign,
                tripGtfsId = st.trip?.gtfsId,
                serviceDate = serviceDateOf(serviceDay),
                routeShortName = route?.shortName,
                routeLongName = route?.longName,
                mode = transitModeFromWire(route?.mode?.value),
                routeGtfsId = route?.gtfsId,
                routeColor = route?.color,
                routeTextColor = route?.textColor,
                stopGtfsId = st.stop?.gtfsId,
                platformCode = st.stop?.platformCode,
                wheelchairAccessible = wheelchairFromWire(st.trip?.wheelchairAccessible?.value),
            )
        }.toImmutableList()
    }

    /** [serviceDate] is the GTFS service date, formatted "YYYY-MM-DD". Null defaults to today. */
    suspend fun trip(tripId: String, serviceDate: String? = null): TripDetails {
        serviceDate?.let(::requireServiceDate)
        val variables = TripVariables(id = tripId, serviceDate = serviceDate)
        val data: TripData = execute(PersistedQueries.TRIP, variables)
        val trip = data.trip ?: throw SpiderTransportException.NoData("routing returned no trip for id=$tripId")

        val stops = trip.stoptimesForDate.orEmpty().mapNotNull { st ->
            val s = st.stop ?: return@mapNotNull null
            val day = st.serviceDay
            fun maybe(offset: Int?): Instant? =
                if (offset != null && day != null) Instant.fromEpochSeconds(day + offset) else null
            TripStop(
                gtfsId = s.gtfsId,
                name = s.name,
                lat = s.lat,
                lon = s.lon,
                scheduledArrival = maybe(st.scheduledArrival),
                scheduledDeparture = maybe(st.scheduledDeparture),
                realtimeArrival = maybe(st.realtimeArrival),
                realtimeDeparture = maybe(st.realtimeDeparture),
                isRealtime = st.realtime ?: false,
                wheelchairBoarding = wheelchairFromWire(s.wheelchairBoarding?.value),
                platformCode = s.platformCode,
                zoneId = s.zoneId,
            )
        }

        return TripDetails(
            gtfsId = trip.gtfsId,
            serviceDate = trip.stoptimesForDate.orEmpty().firstNotNullOfOrNull { it.serviceDay }
                ?.let(::serviceDateOf) ?: serviceDate,
            routeShortName = trip.route.shortName,
            routeLongName = trip.route.longName,
            mode = transitModeFromWire(trip.route.mode?.value),
            headsign = trip.tripHeadsign,
            directionId = trip.directionId,
            bikesAllowed = bikesAllowedFromWire(trip.bikesAllowed?.value),
            stops = stops.toImmutableList(),
            geometry = trip.tripGeometry?.points?.let { decodePolyline(it).toImmutableList() }
                ?: persistentListOf(),
            routeGtfsId = trip.route.gtfsId,
            routeColor = trip.route.color,
            routeTextColor = trip.route.textColor,
            wheelchairAccessible = wheelchairFromWire(trip.wheelchairAccessible?.value),
        )
    }

    // Best-effort connection pre-warm. One bare GET to {baseUrl}/ping through *this* surface's HttpClient, so
    // the TLS connection it opens lands in the pool planConnection reuses. The client apikey rides on it via
    // the client's DefaultRequest (shared setup), not a per-call header; no contract/sdk headers (/ping isn't
    // contract-gated) — a keyed gateway requires the apikey, a keyless one ignores it. Never throws: any
    // transport error or non-2xx (incl. 401/403/404) still warms the connection, so we swallow it and return
    // the elapsed.
    internal suspend fun warmup(): Duration = measureTime {
        runCatching { http.get(baseUrl.trimEnd('/') + "/ping") }
            .onSuccess { log.d(tag = "SpiderRouting") { "warmup GET /ping → ${it.status.value}" } }
            .onFailure { e ->
                // runCatching also catches CancellationException; rethrow it so a cancelled warmup
                // propagates instead of being swallowed as "connection still warmed".
                if (e is CancellationException) throw e
                log.d(tag = "SpiderRouting") { "warmup GET /ping failed (connection still warmed): ${e.message}" }
            }
    }

    private suspend inline fun <reified V, reified D> execute(
        op: PersistedQueries.Op,
        variables: V,
    ): D {
        val payload = json.encodeToString(PersistedRequest(id = op.id, variables = variables))
        val response = http.post {
            url("$baseUrl/routing/${op.path}")
            contentType(ContentType.Application.Json)
            spiderHeaders()
            setBody(payload)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw routingHttpFailure(op.path, response.status.value, text)
        val envelope = json.decodeFromString<GraphQLResponse<D>>(text)
        envelope.errors?.takeIf { it.isNotEmpty() }?.let { errors ->
            throw errors.toTransportException(op.path)
        }
        return envelope.data ?: throw SpiderTransportException.NoData("routing ${op.path} returned no data")
    }
}

internal fun transitModeFromWire(raw: String?): TransitMode? = when (raw) {
    null -> null
    "WALK" -> TransitMode.WALK
    "BUS" -> TransitMode.BUS
    "COACH" -> TransitMode.COACH
    "TROLLEYBUS" -> TransitMode.TROLLEYBUS
    "CARPOOL" -> TransitMode.CARPOOL
    "TRAM" -> TransitMode.TRAM
    "RAIL" -> TransitMode.RAIL
    "SUBWAY" -> TransitMode.SUBWAY
    "MONORAIL" -> TransitMode.MONORAIL
    "FERRY" -> TransitMode.FERRY
    "AIRPLANE" -> TransitMode.AIRPLANE
    "TAXI" -> TransitMode.TAXI
    "CABLE_CAR" -> TransitMode.CABLE_CAR
    "GONDOLA" -> TransitMode.GONDOLA
    "FUNICULAR" -> TransitMode.FUNICULAR
    "SNOW_AND_ICE" -> TransitMode.SNOW_AND_ICE
    // TRANSIT + any value the upstream engine adds later.
    else -> TransitMode.UNKNOWN
}

private fun realtimeStateFromWire(raw: String?): RealtimeState? =
    raw?.let { RealtimeState.entries.firstOrNull { e -> e.name == it } ?: RealtimeState.UNKNOWN }

private fun WireRoutingError.toDomainRoutingError(): RoutingError = RoutingError(
    code = RoutingErrorCode.entries.firstOrNull { it.name == code.value } ?: RoutingErrorCode.UNKNOWN,
    description = description,
    inputField = inputField?.let { field -> InputField.entries.firstOrNull { it.name == field.value } ?: InputField.UNKNOWN },
)

private fun wheelchairFromWire(raw: String?): WheelchairBoarding? = when (raw) {
    null, "NO_INFORMATION" -> null
    "POSSIBLE" -> WheelchairBoarding.POSSIBLE
    "NOT_POSSIBLE" -> WheelchairBoarding.NOT_POSSIBLE
    else -> WheelchairBoarding.UNKNOWN
}

private fun bikesAllowedFromWire(raw: String?): BikesAllowed? = when (raw) {
    null, "NO_INFORMATION" -> null
    "ALLOWED" -> BikesAllowed.ALLOWED
    "NOT_ALLOWED" -> BikesAllowed.NOT_ALLOWED
    else -> BikesAllowed.UNKNOWN
}

private fun durationFromWire(raw: String?): Duration? {
    if (raw.isNullOrBlank()) return null
    return runCatching { Duration.parseIsoString(raw) }.getOrNull()
        ?: raw.toLongOrNull()?.seconds
}

// Shared wire-node → domain mapping, used by both the batch plan and the SSE stream (a stream `chunk`'s
// `results` are the same itinerary nodes as `planConnection.edges[].node`). Realtime delays ride here:
// each leg's estimated time + delay and its realtime state come straight off the wire node.
private fun WireItinerary.toDomainItinerary(): Itinerary = Itinerary(
    start = start,
    end = end,
    durationSeconds = duration ?: 0L,
    waitingTimeSeconds = waitingTime,
    numberOfTransfers = numberOfTransfers,
    accessibilityScore = accessibilityScore,
    legs = legs.map { it.toDomainLeg() }.toImmutableList(),
)

private fun WireLeg.toDomainLeg(): Leg = Leg(
    mode = transitModeFromWire(mode?.value),
    startScheduled = start.scheduledTime,
    endScheduled = end.scheduledTime,
    startEstimated = start.estimated?.time,
    endEstimated = end.estimated?.time,
    startDelay = durationFromWire(start.estimated?.delay),
    endDelay = durationFromWire(end.estimated?.delay),
    isRealtime = realTime ?: false,
    realtimeState = realtimeStateFromWire(realtimeState?.value),
    serviceDate = serviceDate,
    fromName = from.name,
    toName = to.name,
    fromGtfsId = from.stop?.gtfsId,
    toGtfsId = to.stop?.gtfsId,
    routeShortName = route?.shortName,
    routeLongName = route?.longName,
    headsign = headsign,
    distanceMeters = distance,
    durationSeconds = duration,
    tripGtfsId = trip?.gtfsId,
    bikesAllowed = bikesAllowedFromWire(trip?.bikesAllowed?.value),
    accessibilityScore = accessibilityScore,
    fromWheelchair = wheelchairFromWire(from.stop?.wheelchairBoarding?.value),
    toWheelchair = wheelchairFromWire(to.stop?.wheelchairBoarding?.value),
    geometry = legGeometry?.points?.let { decodePolyline(it).toImmutableList() } ?: persistentListOf(),
    routeGtfsId = route?.gtfsId,
    routeColor = route?.color,
    routeTextColor = route?.textColor,
    fromPlatformCode = from.stop?.platformCode,
    toPlatformCode = to.stop?.platformCode,
    fromZoneId = from.stop?.zoneId,
    toZoneId = to.stop?.zoneId,
)

private const val SSE_DEFAULT_EVENT = "message"

@Serializable
private data class StreamChunkData(
    val results: List<WireItinerary> = emptyList(),
)

@Serializable
private data class StreamPageInfoData(
    val startCursor: String? = null,
    val endCursor: String? = null,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false,
    val searchWindowUsed: String? = null,
    val routingErrors: List<WireRoutingError> = emptyList(),
)

@Serializable
private data class StreamErrorData(
    val errors: List<GraphQlErrorPayload>? = null,
    val message: String? = null,
)

// Parses one finished SSE record (event name + accumulated data) into a [PlanStreamEvent]; returns null for
// records the SDK doesn't surface (heartbeats, unknown events). A malformed payload becomes a terminal
// Failure rather than tearing the coroutine down. `internal` so the wire-contract test exercises it directly.
internal fun parsePlanStreamRecord(event: String, data: String, json: Json): PlanStreamEvent? {
    if (data.isBlank()) return null
    return when (event) {
        "chunk" -> runCatching {
            val chunk = json.decodeFromString<StreamChunkData>(data)
            PlanStreamEvent.Result(chunk.results.map { it.toDomainItinerary() }.toImmutableList())
        }.getOrElse { PlanStreamEvent.Failure(it.toSpiderError()) }

        "pageInfo" -> runCatching {
            val page = json.decodeFromString<StreamPageInfoData>(data)
            PlanStreamEvent.Done(
                RoutePageInfo(
                    startCursor = page.startCursor,
                    endCursor = page.endCursor,
                    hasNextPage = page.hasNextPage,
                    hasPreviousPage = page.hasPreviousPage,
                    searchWindowUsed = page.searchWindowUsed,
                ),
                routingErrors = page.routingErrors.map { it.toDomainRoutingError() }.toImmutableList(),
            )
        }.getOrElse { PlanStreamEvent.Failure(it.toSpiderError()) }

        "error" -> PlanStreamEvent.Failure(streamErrorToSpiderError(data, json))
        // The trailing `done` telemetry frame just ends the stream; the terminal event is `pageInfo` → Done.
        else -> null
    }
}

// A stream `error` record is the same GraphQL error envelope the batch path returns, so it maps through the
// same taxonomy — a top-level BAD_REQUEST becomes SpiderError.BadRequest (with its field), anything else Server.
private fun streamErrorToSpiderError(data: String, json: Json): SpiderError {
    val payloads = runCatching { json.decodeFromString<StreamErrorData>(data) }.getOrNull()
    payloads?.errors?.takeIf { it.isNotEmpty() }?.let { return it.toTransportException("plan-stream").toSpiderError() }
    return SpiderTransportException.Upstream("plan-stream error: ${payloads?.message ?: data.take(300)}").toSpiderError()
}

// The SSE plugin raises this when the response isn't a 2xx text/event-stream. Recover the HTTP status (and the
// body, when still readable) from the carried response so 401/403/410/429/5xx map to the same SpiderError the
// batch path returns. A 2xx JSON body is the gateway answering before the router streams (a missing required
// variable), shaped like the batch GraphQL envelope, so its errors map the same way.
private suspend fun SSEClientException.toStreamFailure(json: Json): SpiderError {
    val response = response ?: return (cause ?: this).toSpiderError()
    val body = runCatching { response.bodyAsText() }
        .onFailure { if (it is CancellationException) throw it }
        .getOrNull()?.takeIf { it.isNotBlank() }
        ?: (message ?: "stream failed")
    if (response.status.isSuccess()) {
        runCatching { json.decodeFromString<StreamErrorData>(body) }.getOrNull()?.errors
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.toTransportException(PersistedQueries.PLAN_STREAM.path).toSpiderError() }
    }
    return routingHttpFailure(PersistedQueries.PLAN_STREAM.path, response.status.value, body).toSpiderError()
}

// The gateway answers a retired query id with 410 {"error":"query_retired"}, an id it never had with
// 403 {"error":"persisted_query_rejected"}, and a key a plan limit refuses with 403 and that limit's code; each
// keeps the gateway's code so the SpiderError can tell them apart.
internal fun routingHttpFailure(path: String, status: Int, body: String): SpiderTransportException {
    val envelope = parseErrorEnvelope(body)
    envelope.planLimitCode?.let { return planLimitFailure("routing $path", status, it, envelope.message) }
    val serverCode = when {
        envelope.error == QUERY_RETIRED_SERVER_CODE || status == 410 -> QUERY_RETIRED_SERVER_CODE
        status == 403 && envelope.error == UNKNOWN_QUERY_SERVER_CODE -> UNKNOWN_QUERY_SERVER_CODE
        else -> envelope.code
    }
    val detail = envelope.message
        ?: if (serverCode == QUERY_RETIRED_SERVER_CODE) "persisted query is retired" else body.take(300).trim()
    return SpiderTransportException.Http(status, "routing $path → $status: $detail", serverCode, detail)
}

internal fun PlanRequest.toStreamVariables(
    targetResults: Int,
    maxWindow: Duration,
    before: String?,
    after: String?,
): PlanConnectionStreamVariables {
    requireInRange("maxWindow", maxWindow >= MIN_STREAM_WINDOW)
    requireValidVia()
    return PlanConnectionStreamVariables(
        dateTime = when (val time = time) {
            is RouteTime.DepartAt -> PlanDateTimeInput(earliestDeparture = time.time.toString())
            is RouteTime.ArriveBy -> PlanDateTimeInput(latestArrival = time.time.toString())
        },
        origin = origin.toInput(),
        destination = destination.toInput(),
        via = via.takeIf { it.isNotEmpty() }?.map { it.toInput() },
        modes = toModesInput(),
        preferences = toPreferencesInput(),
        targetResults = targetResults,
        maxWindow = maxWindow.toIsoString(),
        before = before,
        after = after,
    )
}

// The fixed platform limits on each via location. How many via locations are allowed is an environment
// setting, which the API checks.
internal fun PlanRequest.requireValidVia() = via.forEach { location ->
    val valid = when (location) {
        is ViaLocation.PassThrough -> location.stopIds.size in 1..MAX_VIA_STOP_IDS
        is ViaLocation.Visit -> location.minimumWaitTime in Duration.ZERO..MAX_VIA_WAIT
    }
    requireInRange("via", valid)
}

private val MIN_STREAM_WINDOW = 2.hours
private val MAX_VIA_WAIT = 24.hours
private const val MAX_VIA_STOP_IDS = 10
private const val MAX_TIME_RANGE_SECONDS = 86_400L

// Curated PlanRequest → OTP's nested modes/preferences inputs. Only the fields the SDK exposes are set;
// everything else stays null so the environment's routing defaults apply.
internal fun PlanRequest.toModesInput(): PlanModesInput? {
    // WALK/UNKNOWN have no transit-mode wire value (WALK is a street mode) and drop out; empty ⇒ no filter.
    val transit = allowedTransitModes
        ?.mapNotNull { it.toWireTransitMode() }
        ?.map { PlanTransitModePreferenceInput(mode = it) }
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return PlanModesInput(transit = PlanTransitModesInput(transit = transit))
}

internal fun PlanRequest.toPreferencesInput(): PlanPreferencesInput? {
    // The router indexes legs with leg 0 = the initial access (walk, or nothing), so its wire
    // `maximumTransfers` counts boardings = transfers + 1 (wire 0 = walk-only, not exposed here).
    // `maxTransfers` is a transfer count, so map it to boardings: 0 transfers = 1 boarding (direct).
    val transit = maxTransfers?.let {
        TransitPreferencesInput(transfer = TransferPreferencesInput(maximumTransfers = it + 1))
    }
    val accessibility = if (wheelchairAccessible) {
        AccessibilityPreferencesInput(wheelchair = WheelchairPreferencesInput(enabled = true))
    } else {
        null
    }
    return if (transit == null && accessibility == null) null
    else PlanPreferencesInput(transit = transit, accessibility = accessibility)
}

private fun TransitMode.toWireTransitMode(): WireTransitMode? =
    WireTransitMode.entries.firstOrNull { it.name == name && it != WireTransitMode.UNKNOWN }

private fun Location.toInput(): PlanLabeledLocationInput = PlanLabeledLocationInput(
    location = when (this) {
        is Location.Stop -> PlanLocationInput(stopLocation = PlanStopLocationInput(stopLocationId = id))
        is Location.Coordinate -> PlanLocationInput(coordinate = PlanCoordinateInput(latitude = latitude, longitude = longitude))
    },
)

private fun ViaLocation.toInput(): PlanViaLocationInput = when (this) {
    is ViaLocation.PassThrough -> PlanViaLocationInput(passThrough = PlanPassThroughViaLocationInput(stopLocationIds = stopIds))
    is ViaLocation.Visit -> {
        val wait = minimumWaitTime.takeIf { it > Duration.ZERO }?.toIsoString()
        val visit = when (val loc = location) {
            is Location.Stop -> PlanVisitViaLocationInput(stopLocationIds = listOf(loc.id), minimumWaitTime = wait)
            is Location.Coordinate -> PlanVisitViaLocationInput(
                coordinate = PlanCoordinateInput(latitude = loc.latitude, longitude = loc.longitude),
                minimumWaitTime = wait,
            )
        }
        PlanViaLocationInput(visit = visit)
    }
}

@Serializable
private data class PersistedRequest<V>(val id: String, val variables: V)

@Serializable
private data class GraphQLResponse<T>(val data: T? = null, val errors: List<GraphQlErrorPayload>? = null)

// Top-level GraphQL error, incl. the `extensions` the gateway/router stamp on validation failures.
@Serializable
internal data class GraphQlErrorPayload(
    val message: String = "",
    val extensions: GraphQlErrorExtensions? = null,
)

@Serializable
internal data class GraphQlErrorExtensions(val code: String? = null, val field: String? = null)

// A top-level BAD_REQUEST error becomes a typed BadRequest; anything else stays a generic Upstream.
internal fun List<GraphQlErrorPayload>.toTransportException(path: String): SpiderTransportException {
    firstOrNull { it.extensions?.code == "BAD_REQUEST" }?.let {
        return SpiderTransportException.BadRequest(it.extensions?.field, it.message)
    }
    return SpiderTransportException.Upstream("routing $path errors: ${map { it.message }}")
}

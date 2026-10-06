package eu.tiducto.spider.client

import eu.tiducto.spider.client.util.decodePolyline
import eu.tiducto.spider.contract.routing.AccessibilityPreferencesInput
import eu.tiducto.spider.contract.routing.DeparturesRequest
import eu.tiducto.spider.contract.routing.DeparturesResponse
import eu.tiducto.spider.contract.routing.Itinerary as WireItinerary
import eu.tiducto.spider.contract.routing.Leg as WireLeg
import eu.tiducto.spider.contract.routing.PlanCoordinateInput
import eu.tiducto.spider.contract.routing.PlanDateTimeInput
import eu.tiducto.spider.contract.routing.PlanLabeledLocationInput
import eu.tiducto.spider.contract.routing.PlanLocationInput
import eu.tiducto.spider.contract.routing.PlanModesInput
import eu.tiducto.spider.contract.routing.PlanPassThroughViaLocationInput
import eu.tiducto.spider.contract.routing.PlanPreferencesInput
import eu.tiducto.spider.contract.routing.PlanStopLocationInput
import eu.tiducto.spider.contract.routing.PlanStreamChunkEvent
import eu.tiducto.spider.contract.routing.PlanStreamPageInfoEvent
import eu.tiducto.spider.contract.routing.PlanStreamRequest
import eu.tiducto.spider.contract.routing.PlanTransitModePreferenceInput
import eu.tiducto.spider.contract.routing.PlanTransitModesInput
import eu.tiducto.spider.contract.routing.PlanTripRequest
import eu.tiducto.spider.contract.routing.PlanTripResponse
import eu.tiducto.spider.contract.routing.PlanViaLocationInput
import eu.tiducto.spider.contract.routing.PlanVisitViaLocationInput
import eu.tiducto.spider.contract.routing.Reliability as WireReliability
import eu.tiducto.spider.contract.routing.RoutingError as WireRoutingError
import eu.tiducto.spider.contract.routing.TransferPreferencesInput
import eu.tiducto.spider.contract.routing.TransitMode as WireTransitMode
import eu.tiducto.spider.contract.routing.TransitPreferencesInput
import eu.tiducto.spider.contract.routing.TripRequest
import eu.tiducto.spider.contract.routing.TripResponse
import eu.tiducto.spider.contract.routing.WheelchairPreferencesInput
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.accept
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
        val plan: PlanTripResponse = postJson(PLAN, request.toPlanTripRequest(before, after))

        return Route(
            request = request,
            edges = plan.itineraries.map { RouteEdge(cursor = NO_CURSOR, itinerary = it.toDomainItinerary()) }
                .toImmutableList(),
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

    fun planConnectionStream(
        request: PlanRequest,
        targetResults: Int,
        maxWindow: Duration,
        before: String? = null,
        after: String? = null,
    ): Flow<PlanStreamEvent> = flow {
        var ended = false
        try {
            val payload = json.encodeToString(request.toPlanStreamRequest(targetResults, maxWindow, before, after))
            http.sse(
                request = {
                    method = HttpMethod.Post
                    url("$baseUrl/routing/$PLAN_STREAM")
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Text.EventStream)
                    spiderHeaders()
                    setBody(payload)
                },
            ) {
                incoming.collect { record ->
                    // Read on past Done: the gateway meters the stream from its closing `done`.
                    if (ended) return@collect
                    val event = parsePlanStreamRecord(record.event ?: SSE_DEFAULT_EVENT, record.data.orEmpty(), json)
                        ?: return@collect
                    ended = event !is PlanStreamEvent.Result
                    emit(event)
                }
            }
            if (!ended) {
                val cut = SpiderTransportException.Upstream("routing $PLAN_STREAM ended before pageInfo")
                emit(PlanStreamEvent.Failure(cut.toSpiderError()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SSEClientException) {
            if (!ended) emit(PlanStreamEvent.Failure(e.toStreamFailure()))
        } catch (e: Exception) {
            if (!ended) emit(PlanStreamEvent.Failure(e.toSpiderError()))
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
        val body = DeparturesRequest(
            id = id,
            numberOfDepartures = numberOfDepartures,
            timeRange = timeRangeSeconds.toInt(),
            startTime = startTime?.epochSeconds,
        )
        val stop = postJson<DeparturesRequest, DeparturesResponse>(DEPARTURES, body).stop
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
                typicalDelay = st.typicalDelay?.seconds,
            )
        }.toImmutableList()
    }

    /** [serviceDate] is the GTFS service date, formatted "YYYY-MM-DD". Null defaults to today. */
    suspend fun trip(tripId: String, serviceDate: String? = null): TripDetails {
        serviceDate?.let(::requireServiceDate)
        val trip = postJson<TripRequest, TripResponse>(TRIP, TripRequest(id = tripId, serviceDate = serviceDate)).trip
            ?: throw SpiderTransportException.NoData("routing returned no trip for id=$tripId")

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
                typicalDelay = st.typicalDelay?.seconds,
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

    private suspend inline fun <reified B, reified R> postJson(op: String, body: B): R {
        val response = http.post {
            url("$baseUrl/routing/$op")
            contentType(ContentType.Application.Json)
            spiderHeaders()
            setBody(json.encodeToString(body))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw routingHttpFailure(op, response.status.value, text)
        return json.decodeFromString<R>(text)
    }
}

private const val PLAN = "plan"
private const val PLAN_STREAM = "plan-stream"
private const val DEPARTURES = "departures"
private const val TRIP = "trip"
private const val NO_CURSOR = "NoCursor"

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

// Shared by the batch plan and the stream: a `chunk`'s `results` are the same itineraries as a plan's.
private fun WireItinerary.toDomainItinerary(): Itinerary = Itinerary(
    start = start,
    end = end,
    durationSeconds = duration ?: 0L,
    waitingTimeSeconds = waitingTime,
    numberOfTransfers = numberOfTransfers,
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
    typicalArrivalDelay = typicalArrivalDelay?.seconds,
    interlineWithPreviousLeg = interlineWithPreviousLeg ?: false,
)

private const val SSE_DEFAULT_EVENT = "message"

// Null for heartbeats, `done` and event names the SDK does not know; a malformed payload is a terminal Failure.
internal fun parsePlanStreamRecord(event: String, data: String, json: Json): PlanStreamEvent? {
    if (data.isBlank()) return null
    return when (event) {
        "chunk" -> runCatching {
            val chunk = json.decodeFromString<PlanStreamChunkEvent>(data)
            PlanStreamEvent.Result(chunk.results.map { it.toDomainItinerary() }.toImmutableList())
        }.getOrElse { PlanStreamEvent.Failure(it.toSpiderError()) }

        "pageInfo" -> runCatching {
            val page = json.decodeFromString<PlanStreamPageInfoEvent>(data)
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

        else -> null
    }
}

// The SSE plugin raises this for a response that isn't a 2xx event stream, and for a failure mid-stream.
private suspend fun SSEClientException.toStreamFailure(): SpiderError {
    val response = response?.takeUnless { it.status.isSuccess() } ?: return (cause ?: this).toSpiderError()
    val body = runCatching { response.bodyAsText() }
        .onFailure { if (it is CancellationException) throw it }
        .getOrNull()?.takeIf { it.isNotBlank() }
        ?: (message ?: "stream failed")
    return routingHttpFailure(PLAN_STREAM, response.status.value, body).toSpiderError()
}

// A 410 (or a `query_retired` code) means the API part this call uses is retired; a plan limit keeps its own code.
internal fun routingHttpFailure(path: String, status: Int, body: String): SpiderTransportException {
    val envelope = parseErrorEnvelope(body)
    envelope.planLimitCode?.let { return planLimitFailure("routing $path", status, it, envelope.message) }
    val retired = status == 410 || (envelope.code ?: envelope.error) == QUERY_RETIRED_SERVER_CODE
    val serverCode = if (retired) QUERY_RETIRED_SERVER_CODE else envelope.code
    val detail = envelope.message ?: if (retired) RETIRED_MESSAGE else body.take(300).trim()
    return SpiderTransportException.Http(status, "routing $path → $status: $detail", serverCode, detail, envelope.field)
}

private const val RETIRED_MESSAGE = "the API this call uses is retired"

internal fun PlanRequest.toPlanTripRequest(before: String?, after: String?): PlanTripRequest {
    requireValidVia()
    return PlanTripRequest(
        dateTime = toDateTimeInput(),
        origin = origin.toInput(),
        destination = destination.toInput(),
        searchWindow = searchWindow.toIsoString(),
        via = toViaInputs(),
        modes = toModesInput(),
        preferences = toPreferencesInput(),
        before = before,
        after = after,
        reliability = reliability?.toWire(),
    )
}

internal fun PlanRequest.toPlanStreamRequest(
    targetResults: Int,
    maxWindow: Duration,
    before: String?,
    after: String?,
): PlanStreamRequest {
    requireInRange("maxWindow", maxWindow >= MIN_STREAM_WINDOW)
    requireValidVia()
    return PlanStreamRequest(
        dateTime = toDateTimeInput(),
        origin = origin.toInput(),
        destination = destination.toInput(),
        targetResults = targetResults,
        maxWindow = maxWindow.toIsoString(),
        via = toViaInputs(),
        modes = toModesInput(),
        preferences = toPreferencesInput(),
        before = before,
        after = after,
        reliability = reliability?.toWire(),
    )
}

// The fixed platform limits on each via location. How many via locations are allowed is an environment
// setting, which the API checks.
internal fun PlanRequest.requireValidVia() = via.forEach { location ->
    when (location) {
        is ViaLocation.PassThrough -> requireInRange("via", location.stopIds.size in 1..MAX_VIA_STOP_IDS)
        is ViaLocation.Visit ->
            requireInRange("via.visit.minimumWaitTime", location.minimumWaitTime in Duration.ZERO..MAX_VIA_WAIT)
    }
}

private val MIN_STREAM_WINDOW = 2.hours
private val MAX_VIA_WAIT = 1.hours
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

private fun Reliability.toWire(): WireReliability = when (this) {
    Reliability.STANDARD -> WireReliability.STANDARD
    Reliability.SAFE -> WireReliability.SAFE
    Reliability.VERY_SAFE -> WireReliability.VERY_SAFE
}

private fun TransitMode.toWireTransitMode(): WireTransitMode? =
    WireTransitMode.entries.firstOrNull { it.name == name && it != WireTransitMode.UNKNOWN }

private fun PlanRequest.toDateTimeInput(): PlanDateTimeInput = when (val time = time) {
    is RouteTime.DepartAt -> PlanDateTimeInput(earliestDeparture = time.time.toString())
    is RouteTime.ArriveBy -> PlanDateTimeInput(latestArrival = time.time.toString())
}

private fun PlanRequest.toViaInputs(): List<PlanViaLocationInput>? = via.takeIf { it.isNotEmpty() }?.map { it.toInput() }

private fun Location.toInput(): PlanLabeledLocationInput = PlanLabeledLocationInput(
    location = when (this) {
        is Location.Stop -> PlanLocationInput(stopLocation = PlanStopLocationInput(stopLocationId = id))
        is Location.Coordinate -> PlanLocationInput(coordinate = PlanCoordinateInput(latitude = latitude, longitude = longitude))
    },
)

private fun ViaLocation.toInput(): PlanViaLocationInput = when (this) {
    is ViaLocation.PassThrough -> PlanViaLocationInput(passThrough = PlanPassThroughViaLocationInput(stopLocationIds = stopIds))
    is ViaLocation.Visit -> when (val loc = location) {
        is Location.Stop -> PlanViaLocationInput(
            visit = PlanVisitViaLocationInput(
                stopLocationIds = listOf(loc.id),
                minimumWaitTime = minimumWaitTime.takeIf { it > Duration.ZERO }?.toIsoString(),
            ),
        )
        is Location.Coordinate -> throw SpiderTransportException.BadRequest("via", "via is invalid")
    }
}

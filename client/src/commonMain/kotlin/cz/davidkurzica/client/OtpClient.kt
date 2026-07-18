package cz.davidkurzica.client

import cz.davidkurzica.client.util.decodePolyline
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// gtfsIds are opaque, canonical, feed-prefixed ids ("1:U…") used identically by Meili and OTP, so the
// SDK passes them through unchanged — a stop id from search feeds straight back into route()/
// departures()/trip() with no add/strip step (which is what previously double-prefixed and 404'd).
//
// The transport is a plain persisted-query POST: the gateway allow-lists query ids, so the client never
// sends GraphQL — it sends {"id": "<sha256 of the query doc>", "variables": {…}} to a per-operation route
// and gets back the standard GraphQL {data, errors} envelope. The query documents live under
// src/commonMain/graphql/ purely as the canonical text the ids are hashed from (see PersistedQueries).
internal class OtpClient(
    private val baseUrl: String,
    private val apiKey: String,
) {
    // explicitNulls=false drops absent optionals from the request variables (the "field omitted" that OTP
    // reads as unset); ignoreUnknownKeys lets the response carry fields we don't model without failing.
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val http = HttpClient {
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    co.touchlab.kermit.Logger.d(tag = "OtpClient") { message }
                }
            }
        }
    }

    suspend fun planConnection(
        request: RouteRequest,
        first: Int? = null,
        before: String? = null,
        after: String? = null,
    ): Route {
        val dateTime = when (val time = request.time) {
            is RouteTime.DepartAt -> PlanDateTimeVar(earliestDeparture = time.time.toString())
            is RouteTime.ArriveBy -> PlanDateTimeVar(latestArrival = time.time.toString())
        }
        val variables = PlanVariables(
            dateTime = dateTime,
            origin = request.from.toVar(),
            destination = request.to.toVar(),
            via = request.via.takeIf { it.isNotEmpty() }?.map { it.toVar() },
            first = first,
            before = before,
            after = after,
        )
        val data = execute(PersistedQueries.PLAN, PlanVariables.serializer(), variables, PlanData.serializer())
        val plan = data.planConnection ?: throw SpiderTransportException.NoData("OTP returned no planConnection")

        return Route(
            request = request,
            edges = plan.edges.orEmpty().filterNotNull().map { edge ->
                val node = edge.node
                RouteEdge(
                    cursor = edge.cursor,
                    itinerary = Itinerary(
                        start = node.start,
                        end = node.end,
                        durationSeconds = node.duration,
                        waitingTimeSeconds = node.waitingTime,
                        numberOfTransfers = node.numberOfTransfers,
                        accessibilityScore = node.accessibilityScore,
                        legs = node.legs.filterNotNull().map { leg ->
                            Leg(
                                mode = transitModeFromWire(leg.mode),
                                startScheduled = leg.start.scheduledTime,
                                endScheduled = leg.end.scheduledTime,
                                fromName = leg.from.name,
                                toName = leg.to.name,
                                routeShortName = leg.route?.shortName,
                                routeLongName = leg.route?.longName,
                                headsign = leg.headsign,
                                distanceMeters = leg.distance,
                                durationSeconds = leg.duration,
                                tripGtfsId = leg.trip?.gtfsId,
                                bikesAllowed = bikesAllowedFromWire(leg.trip?.bikesAllowed),
                                accessibilityScore = leg.accessibilityScore,
                                fromWheelchair = wheelchairFromWire(leg.from.stop?.wheelchairBoarding),
                                toWheelchair = wheelchairFromWire(leg.to.stop?.wheelchairBoarding),
                                geometry = leg.legGeometry?.points?.let { decodePolyline(it).toImmutableList() }
                                    ?: persistentListOf(),
                            )
                        }.toImmutableList(),
                    ),
                )
            }.toImmutableList(),
            pageInfo = RoutePageInfo(
                startCursor = plan.pageInfo.startCursor,
                endCursor = plan.pageInfo.endCursor,
                hasNextPage = plan.pageInfo.hasNextPage,
                hasPreviousPage = plan.pageInfo.hasPreviousPage,
                searchWindowUsed = plan.pageInfo.searchWindowUsed,
            ),
            routingErrors = plan.routingErrors.map {
                RoutingError(code = it.code, description = it.description, inputField = it.inputField)
            }.toImmutableList(),
            searchDateTime = plan.searchDateTime,
        )
    }

    suspend fun stopDepartures(
        id: String,
        numberOfDepartures: Int,
        startTime: Instant?,
        timeRange: Duration,
    ): ImmutableList<Departure> {
        val variables = DeparturesVariables(
            id = id,
            numberOfDepartures = numberOfDepartures,
            startTime = startTime?.epochSeconds,
            timeRange = timeRange.inWholeSeconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        )
        val data = execute(PersistedQueries.DEPARTURES, DeparturesVariables.serializer(), variables, DeparturesData.serializer())
        val stop = data.asStop ?: data.asStation
            ?: throw SpiderTransportException.NoData("OTP returned no stop or station for id=$id")

        return stop.stoptimesWithoutPatterns.mapNotNull { st ->
            val serviceDay = st.serviceDay ?: return@mapNotNull null
            val scheduledOffset = st.scheduledDeparture ?: return@mapNotNull null
            // Drop trips that terminate at a sibling stop sharing this station's name —
            // omitNonPickups doesn't catch these since boarding here is allowed.
            if (st.headsign?.trim().equals(stop.name.trim(), ignoreCase = true)) {
                return@mapNotNull null
            }
            val route = st.trip?.route
            Departure(
                scheduledTime = Instant.fromEpochSeconds(serviceDay + scheduledOffset),
                realtimeTime = st.realtimeDeparture?.let { Instant.fromEpochSeconds(serviceDay + it) },
                isRealtime = st.realtime ?: false,
                realtimeState = st.realtimeState,
                headsign = st.headsign,
                tripGtfsId = st.trip?.gtfsId,
                routeShortName = route?.shortName,
                routeLongName = route?.longName,
                mode = transitModeFromWire(route?.mode),
            )
        }.toImmutableList()
    }

    /** [serviceDate] is GTFS calendar date, formatted "YYYY-MM-DD". Null defaults to today. */
    suspend fun trip(tripId: String, serviceDate: String? = null): TripDetails {
        val variables = TripVariables(id = tripId, serviceDate = serviceDate)
        val data = execute(PersistedQueries.TRIP, TripVariables.serializer(), variables, TripData.serializer())
        val trip = data.trip ?: throw SpiderTransportException.NoData("OTP returned no trip for id=$tripId")

        val stops = trip.stoptimesForDate.orEmpty().filterNotNull().mapNotNull { st ->
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
                wheelchairBoarding = wheelchairFromWire(s.wheelchairBoarding),
            )
        }

        return TripDetails(
            gtfsId = trip.gtfsId,
            routeShortName = trip.route.shortName,
            routeLongName = trip.route.longName,
            mode = transitModeFromWire(trip.route.mode),
            headsign = trip.tripHeadsign,
            directionId = trip.directionId,
            bikesAllowed = bikesAllowedFromWire(trip.bikesAllowed),
            stops = stops.toImmutableList(),
            geometry = trip.tripGeometry?.points?.let { decodePolyline(it).toImmutableList() }
                ?: persistentListOf(),
        )
    }

    private suspend fun <V, D> execute(
        op: PersistedQueries.Op,
        variablesSerializer: KSerializer<V>,
        variables: V,
        dataSerializer: KSerializer<D>,
    ): D {
        val payload = json.encodeToString(
            PersistedRequest.serializer(),
            PersistedRequest(id = op.id, variables = json.encodeToJsonElement(variablesSerializer, variables)),
        )
        val response = http.post {
            url("$baseUrl/otp/${op.path}")
            contentType(ContentType.Application.Json)
            // Kong key-auth expects the raw key in an `apikey` header (not Authorization: Bearer).
            headers { append("apikey", apiKey) }
            setBody(payload)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // A 403 here means the id isn't allow-listed at the gateway (contract/SDK hash mismatch).
            throw SpiderTransportException.Http(response.status.value, "OTP ${op.path} → ${response.status.value}: ${text.take(300)}")
        }
        val envelope = json.decodeFromString(GraphQLResponse.serializer(dataSerializer), text)
        envelope.errors?.takeIf { it.isNotEmpty() }?.let { errors ->
            throw SpiderTransportException.Upstream("OTP ${op.path} errors: ${errors.mapNotNull { it.message }}")
        }
        return envelope.data ?: throw SpiderTransportException.NoData("OTP ${op.path} returned no data")
    }
}

/**
 * Each OTP operation's persisted-query id and its gateway route suffix. The id is the lowercase hex
 * sha256 of the canonical query document the Spider contract registers — the contract MUST register
 * these exact `.graphql` documents so the ids match; a mismatch is a 403 at the gateway. See
 * `docs/CONTRACT_MAPPING.md`.
 */
internal object PersistedQueries {
    data class Op(val id: String, val path: String)

    val PLAN = Op("f19608964d423831b485ccc878cb25eff56c720585d4423ee617c864e2b3102e", "plan")
    val DEPARTURES = Op("70a644fe3c6b2cbf5b2d70cef8230c1428bea6357ae1766772162d86469563d0", "departures")
    val TRIP = Op("e8959a8d47a8e8437ee3ec740cd9c3e28bd401efdd236dde0502559daea53920", "trip")
}

// --- transit-mode / accessibility mapping (wire enum strings → domain) ---

private fun transitModeFromWire(raw: String?): TransitMode? = when (raw) {
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
    // TRANSIT, CABLE_CAR, FUNICULAR, GONDOLA, SNOW_AND_ICE + any value OTP adds later.
    else -> TransitMode.UNKNOWN
}

private fun wheelchairFromWire(raw: String?): WheelchairBoarding? = when (raw) {
    "POSSIBLE" -> WheelchairBoarding.Possible
    "NOT_POSSIBLE" -> WheelchairBoarding.NotPossible
    // NO_INFORMATION / absent / unknown → null (callers treat "absent" and "unknown" the same).
    else -> null
}

private fun bikesAllowedFromWire(raw: String?): BikesAllowed? = when (raw) {
    "ALLOWED" -> BikesAllowed.Allowed
    "NOT_ALLOWED" -> BikesAllowed.NotAllowed
    else -> null
}

// --- request variable shapes (mirror the query's variable definitions) ---

private fun RouteLocation.toVar(): PlanLabeledLocationVar = PlanLabeledLocationVar(
    location = when (this) {
        is RouteLocation.StopId -> PlanLocationVar(stopLocation = PlanStopLocationVar(stopLocationId = id))
        is RouteLocation.Coordinates -> PlanLocationVar(coordinate = PlanCoordinateVar(latitude = lat, longitude = lon))
    },
)

private fun ViaLocation.toVar(): PlanViaLocationVar = when (this) {
    is ViaLocation.PassThrough -> PlanViaLocationVar(passThrough = PlanPassThroughVar(stopLocationIds = stopIds))
    is ViaLocation.Visit -> {
        val wait = minimumWaitTime.takeIf { it > Duration.ZERO }?.toIsoString()
        val visit = when (val loc = location) {
            is RouteLocation.StopId -> PlanVisitVar(stopLocationIds = listOf(loc.id), minimumWaitTime = wait)
            is RouteLocation.Coordinates -> PlanVisitVar(
                coordinate = PlanCoordinateVar(latitude = loc.lat, longitude = loc.lon),
                minimumWaitTime = wait,
            )
        }
        PlanViaLocationVar(visit = visit)
    }
}

@Serializable
private data class PersistedRequest(val id: String, val variables: JsonElement)

@Serializable
private data class PlanVariables(
    val dateTime: PlanDateTimeVar,
    val origin: PlanLabeledLocationVar,
    val destination: PlanLabeledLocationVar,
    val via: List<PlanViaLocationVar>? = null,
    val first: Int? = null,
    val before: String? = null,
    val after: String? = null,
)

@Serializable
private data class PlanDateTimeVar(val earliestDeparture: String? = null, val latestArrival: String? = null)

@Serializable
private data class PlanLabeledLocationVar(val location: PlanLocationVar)

@Serializable
private data class PlanLocationVar(
    val stopLocation: PlanStopLocationVar? = null,
    val coordinate: PlanCoordinateVar? = null,
)

@Serializable
private data class PlanStopLocationVar(val stopLocationId: String)

@Serializable
private data class PlanCoordinateVar(val latitude: Double, val longitude: Double)

@Serializable
private data class PlanViaLocationVar(
    val passThrough: PlanPassThroughVar? = null,
    val visit: PlanVisitVar? = null,
)

@Serializable
private data class PlanPassThroughVar(val stopLocationIds: List<String>)

@Serializable
private data class PlanVisitVar(
    val stopLocationIds: List<String>? = null,
    val coordinate: PlanCoordinateVar? = null,
    val minimumWaitTime: String? = null,
)

@Serializable
private data class DeparturesVariables(
    val id: String,
    val numberOfDepartures: Int,
    val startTime: Long? = null,
    val timeRange: Int,
)

@Serializable
private data class TripVariables(val id: String, val serviceDate: String? = null)

// --- response shapes (subset of the query selection actually mapped to domain types) ---

@Serializable
private data class GraphQLResponse<T>(val data: T? = null, val errors: List<GraphQLError>? = null)

@Serializable
private data class GraphQLError(val message: String? = null)

@Serializable
private data class PlanData(val planConnection: PlanConnectionDto? = null)

@Serializable
private data class PlanConnectionDto(
    val edges: List<PlanEdgeDto?>? = null,
    val pageInfo: PageInfoDto = PageInfoDto(),
    val routingErrors: List<RoutingErrorDto> = emptyList(),
    val searchDateTime: String? = null,
)

@Serializable
private data class PlanEdgeDto(val cursor: String, val node: PlanNodeDto)

@Serializable
private data class PlanNodeDto(
    val start: String? = null,
    val end: String? = null,
    val duration: Long,
    val waitingTime: Long? = null,
    val numberOfTransfers: Int,
    val accessibilityScore: Double? = null,
    val legs: List<LegDto?> = emptyList(),
)

@Serializable
private data class LegDto(
    val mode: String? = null,
    val start: ScheduledTimeDto,
    val end: ScheduledTimeDto,
    val from: PlaceDto,
    val to: PlaceDto,
    val route: RouteDto? = null,
    val headsign: String? = null,
    val distance: Double? = null,
    val duration: Double? = null,
    val accessibilityScore: Double? = null,
    val trip: TripRefDto? = null,
    val legGeometry: GeometryDto? = null,
)

@Serializable
private data class ScheduledTimeDto(val scheduledTime: String)

@Serializable
private data class PlaceDto(val name: String? = null, val stop: StopRefDto? = null)

@Serializable
private data class StopRefDto(val wheelchairBoarding: String? = null)

@Serializable
private data class RouteDto(val shortName: String? = null, val longName: String? = null, val mode: String? = null)

@Serializable
private data class TripRefDto(val gtfsId: String? = null, val bikesAllowed: String? = null)

@Serializable
private data class GeometryDto(val points: String? = null, val length: Double? = null)

@Serializable
private data class PageInfoDto(
    val startCursor: String? = null,
    val endCursor: String? = null,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false,
    val searchWindowUsed: String? = null,
)

@Serializable
private data class RoutingErrorDto(
    val code: String,
    val description: String,
    val inputField: String? = null,
)

@Serializable
private data class DeparturesData(val asStop: StopDto? = null, val asStation: StopDto? = null)

@Serializable
private data class StopDto(
    val gtfsId: String? = null,
    val name: String = "",
    val wheelchairBoarding: String? = null,
    val stoptimesWithoutPatterns: List<StoptimeDto> = emptyList(),
)

@Serializable
private data class StoptimeDto(
    val serviceDay: Long? = null,
    val scheduledDeparture: Int? = null,
    val realtimeDeparture: Int? = null,
    val realtime: Boolean? = null,
    val realtimeState: String? = null,
    val headsign: String? = null,
    val trip: DepartureTripDto? = null,
)

@Serializable
private data class DepartureTripDto(val gtfsId: String? = null, val route: RouteDto? = null)

@Serializable
private data class TripData(val trip: TripDto? = null)

@Serializable
private data class TripDto(
    val gtfsId: String,
    val directionId: String? = null,
    val tripHeadsign: String? = null,
    val bikesAllowed: String? = null,
    val route: RouteDto = RouteDto(),
    val stoptimesForDate: List<TripStoptimeDto?>? = null,
    val tripGeometry: GeometryDto? = null,
)

@Serializable
private data class TripStoptimeDto(
    val serviceDay: Long? = null,
    val scheduledArrival: Int? = null,
    val scheduledDeparture: Int? = null,
    val realtimeArrival: Int? = null,
    val realtimeDeparture: Int? = null,
    val realtime: Boolean? = null,
    val stop: TripStopDto? = null,
)

@Serializable
private data class TripStopDto(
    val gtfsId: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val wheelchairBoarding: String? = null,
)

package eu.tiducto.spider.client

import eu.tiducto.spider.client.util.decodePolyline
import eu.tiducto.spider.contract.routing.GraphQLError
import eu.tiducto.spider.contract.routing.PlanConnectionData
import eu.tiducto.spider.contract.routing.PlanConnectionVariables
import eu.tiducto.spider.contract.routing.PlanCoordinateInput
import eu.tiducto.spider.contract.routing.PlanDateTimeInput
import eu.tiducto.spider.contract.routing.PlanLabeledLocationInput
import eu.tiducto.spider.contract.routing.PlanLocationInput
import eu.tiducto.spider.contract.routing.PlanPassThroughViaLocationInput
import eu.tiducto.spider.contract.routing.PlanStopLocationInput
import eu.tiducto.spider.contract.routing.PlanViaLocationInput
import eu.tiducto.spider.contract.routing.PlanVisitViaLocationInput
import eu.tiducto.spider.contract.routing.StopDeparturesData
import eu.tiducto.spider.contract.routing.StopDeparturesVariables
import eu.tiducto.spider.contract.routing.TripData
import eu.tiducto.spider.contract.routing.TripVariables
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

// gtfsIds are opaque and feed-prefixed ("1:U…"); the SDK never re-prefixes them — a stop id from search
// feeds straight into route()/departures()/trip() (re-prefixing is what once 404'd).
internal class RoutingClient(
    private val baseUrl: String,
    private val apiKey: String,
) {
    // explicitNulls=false so an omitted optional reads as "unset" upstream. Unknown enum values decode to
    // UNKNOWN via the generated enums' serializers (coercion doesn't — it throws).
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val http = HttpClient {
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    co.touchlab.kermit.Logger.d(tag = "RoutingClient") { message }
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
            is RouteTime.DepartAt -> PlanDateTimeInput(earliestDeparture = time.time.toString())
            is RouteTime.ArriveBy -> PlanDateTimeInput(latestArrival = time.time.toString())
        }
        val variables = PlanConnectionVariables(
            dateTime = dateTime,
            origin = request.from.toInput(),
            destination = request.to.toInput(),
            via = request.via.takeIf { it.isNotEmpty() }?.map { it.toInput() },
            first = first,
            before = before,
            after = after,
        )
        val data = execute(PersistedQueries.PLAN, PlanConnectionVariables.serializer(), variables, PlanConnectionData.serializer())
        val plan = data.planConnection ?: throw SpiderTransportException.NoData("routing returned no plan data")

        return Route(
            request = request,
            edges = plan.edges.orEmpty().map { edge ->
                val node = edge.node
                RouteEdge(
                    cursor = edge.cursor,
                    itinerary = Itinerary(
                        start = node.start,
                        end = node.end,
                        durationSeconds = node.duration ?: 0L,
                        waitingTimeSeconds = node.waitingTime,
                        numberOfTransfers = node.numberOfTransfers,
                        accessibilityScore = node.accessibilityScore,
                        legs = node.legs.map { leg ->
                            Leg(
                                mode = transitModeFromWire(leg.mode?.value),
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
                                bikesAllowed = bikesAllowedFromWire(leg.trip?.bikesAllowed?.value),
                                accessibilityScore = leg.accessibilityScore,
                                fromWheelchair = wheelchairFromWire(leg.from.stop?.wheelchairBoarding?.value),
                                toWheelchair = wheelchairFromWire(leg.to.stop?.wheelchairBoarding?.value),
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
                RoutingError(code = it.code.value, description = it.description, inputField = it.inputField?.value)
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
        val variables = StopDeparturesVariables(
            id = id,
            numberOfDepartures = numberOfDepartures,
            startTime = startTime?.epochSeconds,
            timeRange = timeRange.inWholeSeconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        )
        val data = execute(PersistedQueries.DEPARTURES, StopDeparturesVariables.serializer(), variables, StopDeparturesData.serializer())
        val stop = data.asStop ?: data.asStation
            ?: throw SpiderTransportException.NoData("routing returned no stop or station for id=$id")

        return stop.stoptimesWithoutPatterns.orEmpty().mapNotNull { st ->
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
                realtimeState = st.realtimeState?.value,
                headsign = st.headsign,
                tripGtfsId = st.trip?.gtfsId,
                routeShortName = route?.shortName,
                routeLongName = route?.longName,
                mode = transitModeFromWire(route?.mode?.value),
            )
        }.toImmutableList()
    }

    /** [serviceDate] is GTFS calendar date, formatted "YYYY-MM-DD". Null defaults to today. */
    suspend fun trip(tripId: String, serviceDate: String? = null): TripDetails {
        val variables = TripVariables(id = tripId, serviceDate = serviceDate)
        val data = execute(PersistedQueries.TRIP, TripVariables.serializer(), variables, TripData.serializer())
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
            )
        }

        return TripDetails(
            gtfsId = trip.gtfsId,
            routeShortName = trip.route.shortName,
            routeLongName = trip.route.longName,
            mode = transitModeFromWire(trip.route.mode?.value),
            headsign = trip.tripHeadsign,
            directionId = trip.directionId,
            bikesAllowed = bikesAllowedFromWire(trip.bikesAllowed?.value),
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
            url("$baseUrl/routing/${op.path}")
            contentType(ContentType.Application.Json)
            // Kong key-auth expects the raw key in an `apikey` header (not Authorization: Bearer).
            headers {
                append("apikey", apiKey)
                append(SpiderContract.HEADER, SpiderContract.VERSION)
            }
            setBody(payload)
        }
        // Crash on an incompatible contract before we try to parse a shape we may no longer understand.
        ContractGuard.check(response.headers[SpiderContract.HEADER])
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // A 403 here means the id isn't allow-listed at the gateway (contract/SDK hash mismatch).
            throw SpiderTransportException.Http(response.status.value, "routing ${op.path} → ${response.status.value}: ${text.take(300)}")
        }
        val envelope = json.decodeFromString(GraphQLResponse.serializer(dataSerializer), text)
        envelope.errors?.takeIf { it.isNotEmpty() }?.let { errors ->
            throw SpiderTransportException.Upstream("routing ${op.path} errors: ${errors.map { it.message }}")
        }
        return envelope.data ?: throw SpiderTransportException.NoData("routing ${op.path} returned no data")
    }
}

/**
 * Each routing operation's persisted-query id and its gateway route suffix. The id is the lowercase hex
 * sha256 of the canonical query document under src/commonMain/graphql/ — the SDK owns these documents
 * (direction is contract ← SDK), and the Spider contract registers the same text so the ids match; a
 * mismatch is a 403 at the gateway. See `docs/CONTRACT_MAPPING.md`.
 */
internal object PersistedQueries {
    data class Op(val id: String, val path: String)

    val PLAN = Op("f19608964d423831b485ccc878cb25eff56c720585d4423ee617c864e2b3102e", "plan")
    val DEPARTURES = Op("70a644fe3c6b2cbf5b2d70cef8230c1428bea6357ae1766772162d86469563d0", "departures")
    val TRIP = Op("e8959a8d47a8e8437ee3ec740cd9c3e28bd401efdd236dde0502559daea53920", "trip")
}

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
    // TRANSIT, CABLE_CAR, FUNICULAR, GONDOLA, SNOW_AND_ICE + any value the upstream engine adds later.
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

private fun RouteLocation.toInput(): PlanLabeledLocationInput = PlanLabeledLocationInput(
    location = when (this) {
        is RouteLocation.StopId -> PlanLocationInput(stopLocation = PlanStopLocationInput(stopLocationId = id))
        is RouteLocation.Coordinates -> PlanLocationInput(coordinate = PlanCoordinateInput(latitude = lat, longitude = lon))
    },
)

private fun ViaLocation.toInput(): PlanViaLocationInput = when (this) {
    is ViaLocation.PassThrough -> PlanViaLocationInput(passThrough = PlanPassThroughViaLocationInput(stopLocationIds = stopIds))
    is ViaLocation.Visit -> {
        val wait = minimumWaitTime.takeIf { it > Duration.ZERO }?.toIsoString()
        val visit = when (val loc = location) {
            is RouteLocation.StopId -> PlanVisitViaLocationInput(stopLocationIds = listOf(loc.id), minimumWaitTime = wait)
            is RouteLocation.Coordinates -> PlanVisitViaLocationInput(
                coordinate = PlanCoordinateInput(latitude = loc.lat, longitude = loc.lon),
                minimumWaitTime = wait,
            )
        }
        PlanViaLocationInput(visit = visit)
    }
}

@Serializable
private data class PersistedRequest(val id: String, val variables: JsonElement)

@Serializable
private data class GraphQLResponse<T>(val data: T? = null, val errors: List<GraphQLError>? = null)

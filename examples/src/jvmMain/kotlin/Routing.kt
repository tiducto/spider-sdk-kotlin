package examples.routing

import eu.tiducto.spider.client.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

fun setup() {
    val client = SpiderClient(
        baseUrl = "https://your-env-slug.api.tiducto.eu",
        apiKey = "your-api-key",
    )
}

suspend fun planTrip(client: SpiderClient) {
    // The recommended shape: a departure time plus a search window, not "N results from now".
    val result = client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        time = RouteTime.DepartAt(Clock.System.now()),
        searchWindow = 60.minutes,
    )

    when (result) {
        is SpiderResult.Success -> {
            for (edge in result.data.edges) {
                val itinerary = edge.itinerary
                println("${itinerary.start} → ${itinerary.end}  ·  ${itinerary.numberOfTransfers} transfers")
                for (leg in itinerary.legs) {
                    println("  ${leg.mode} ${leg.routeShortName ?: "walk"}: ${leg.fromName} → ${leg.toName} (${leg.durationSeconds}s)")
                }
            }
        }
        is SpiderResult.Error -> println("Planning failed: ${result.error}")
    }
}

suspend fun planForTime(client: SpiderClient) {
    client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        time = RouteTime.DepartAt(Instant.parse("2026-07-20T08:00:00Z")),
        searchWindow = 30.minutes,
    )
}

suspend fun laterItineraries(client: SpiderClient) {
    val firstPage = when (val result = client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
    )) {
        is SpiderResult.Success -> result.data
        is SpiderResult.Error -> {
            println("Planning failed: ${result.error}")
            return
        }
    }

    when (val later = client.routing.planNext(firstPage)) {
        null -> println("No later itineraries — that was the last window")
        is SpiderResult.Success ->
            later.data.edges.forEach { edge ->
                println("${edge.itinerary.start} → ${edge.itinerary.end}")
            }
        is SpiderResult.Error -> println("Paging failed: ${later.error}")
    }
}

suspend fun departures(client: SpiderClient) {
    val result = client.routing.departures(
        id = "U123Z1",
        numberOfDepartures = 5,
    )

    when (result) {
        is SpiderResult.Success ->
            result.data.forEach { departure ->
                // realtimeTime is null until the feed reports; fall back to the schedule.
                val time = departure.realtimeTime ?: departure.scheduledTime
                val status = if (departure.isRealtime) "live (${departure.realtimeState})" else "scheduled"
                val line = departure.routeShortName ?: departure.routeLongName
                println("${departure.mode} $line → ${departure.headsign} at $time [$status] · trip ${departure.tripGtfsId}")
            }
        is SpiderResult.Error -> println(result.error)
    }
}

suspend fun tripLookup(client: SpiderClient) {
    val result = client.routing.trip(tripId = "1:12345")

    when (result) {
        is SpiderResult.Success ->
            result.data.stops.forEach { stop ->
                println("${stop.name}: arr ${stop.scheduledArrival}, dep ${stop.scheduledDeparture}")
            }
        is SpiderResult.Error -> println(result.error)
    }
}

suspend fun planWithModes(client: SpiderClient) {
    val result = client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        allowedTransitModes = setOf(TransitMode.TRAM, TransitMode.SUBWAY),
    )

    when (result) {
        is SpiderResult.Success ->
            result.data.edges.forEach { edge ->
                println("${edge.itinerary.start} → ${edge.itinerary.end}  ·  ${edge.itinerary.numberOfTransfers} transfers")
            }
        is SpiderResult.Error -> println("Planning failed: ${result.error}")
    }
}

suspend fun planWithLimits(client: SpiderClient) {
    val result = client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        // Widen the window scanned for departures, and cap connections at two transfers.
        searchWindow = 2.hours,
        maxTransfers = 2,
    )

    when (result) {
        is SpiderResult.Success ->
            result.data.edges.forEach { edge ->
                println("${edge.itinerary.start} → ${edge.itinerary.end}  ·  ${edge.itinerary.numberOfTransfers} transfers")
            }
        is SpiderResult.Error -> println("Planning failed: ${result.error}")
    }
}

suspend fun arriveBy(client: SpiderClient) {
    val result = client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        time = RouteTime.ArriveBy(Instant.parse("2026-07-20T08:00:00Z")),
        searchWindow = 60.minutes,
    )

    when (result) {
        is SpiderResult.Success ->
            result.data.edges.forEach { edge ->
                println("depart ${edge.itinerary.start} → arrive ${edge.itinerary.end}")
            }
        is SpiderResult.Error -> println("Planning failed: ${result.error}")
    }
}

suspend fun earlierItineraries(client: SpiderClient) {
    val firstPage = when (val result = client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
    )) {
        is SpiderResult.Success -> result.data
        is SpiderResult.Error -> {
            println("Planning failed: ${result.error}")
            return
        }
    }

    when (val earlier = client.routing.planPrevious(firstPage)) {
        null -> println("No earlier itineraries — that was the first window")
        is SpiderResult.Success ->
            earlier.data.edges.forEach { edge ->
                println("${edge.itinerary.start} → ${edge.itinerary.end}")
            }
        is SpiderResult.Error -> println("Paging failed: ${earlier.error}")
    }
}

suspend fun planVia(client: SpiderClient) {
    val result = client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        via = listOf(
            ViaLocation.Visit(
                location = Location.Coordinate(49.2100, 16.5900),
                minimumWaitTime = 10.minutes,
            ),
        ),
    )

    when (result) {
        is SpiderResult.Success ->
            result.data.edges.forEach { edge ->
                println("${edge.itinerary.start} → ${edge.itinerary.end}  ·  ${edge.itinerary.numberOfTransfers} transfers")
            }
        is SpiderResult.Error -> println("Planning failed: ${result.error}")
    }
}

suspend fun streamPlan(client: SpiderClient) {
    // Itineraries arrive as the router sweeps the window, instead of one batched page. A terminal Done
    // carries the continuation cursors; a Failure is delivered as an event, never thrown.
    client.routing.planStream(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        targetResults = 5,
        maxWindow = 3.hours,
    ).collect { event ->
        when (event) {
            is PlanStreamEvent.Result ->
                event.itineraries.forEach { itinerary ->
                    println("${itinerary.start} → ${itinerary.end}  ·  ${itinerary.numberOfTransfers} transfers")
                }
            is PlanStreamEvent.Done ->
                println("done · more later: ${event.pageInfo.hasNextPage} (endCursor=${event.pageInfo.endCursor})")
            is PlanStreamEvent.Failure -> println("stream failed: ${event.error}")
        }
    }
}

suspend fun streamMoreItineraries(client: SpiderClient) {
    val origin = Location.Coordinate(49.1951, 16.6068)
    val destination = Location.Coordinate(49.2246, 16.5747)

    // Stream the first window, keeping the terminal Done to continue from.
    var done: PlanStreamEvent.Done? = null
    client.routing.planStream(origin = origin, destination = destination).collect { event ->
        when (event) {
            is PlanStreamEvent.Result -> event.itineraries.forEach { println("${it.start} → ${it.end}") }
            is PlanStreamEvent.Done -> done = event
            is PlanStreamEvent.Failure -> println("stream failed: ${event.error}")
        }
    }

    // Continue into the next window from the endCursor — only when Done says there is one.
    val next = done?.takeIf { it.pageInfo.hasNextPage }?.pageInfo?.endCursor ?: return
    client.routing.planStreamNext(origin = origin, destination = destination, after = next).collect { event ->
        when (event) {
            is PlanStreamEvent.Result -> event.itineraries.forEach { println("later: ${it.start} → ${it.end}") }
            is PlanStreamEvent.Done -> println("reached the last window: ${!event.pageInfo.hasNextPage}")
            is PlanStreamEvent.Failure -> println("stream failed: ${event.error}")
        }
    }
}

suspend fun wheelchairPlan(client: SpiderClient) {
    val result = client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        wheelchairAccessible = true,
    )

    when (result) {
        is SpiderResult.Success ->
            result.data.edges.forEach { edge ->
                val itinerary = edge.itinerary
                println("accessibility ${itinerary.accessibilityScore ?: "n/a"}  ·  ${itinerary.numberOfTransfers} transfers")
                for (leg in itinerary.legs) {
                    println("  ${leg.mode}: board ${leg.fromWheelchair ?: "unknown"} → alight ${leg.toWheelchair ?: "unknown"}")
                }
            }
        is SpiderResult.Error -> println("Planning failed: ${result.error}")
    }
}

suspend fun streamForTime(client: SpiderClient) {
    // Streamed form of planForTime.
    client.routing.planStream(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        time = RouteTime.DepartAt(Instant.parse("2026-07-20T08:00:00Z")),
        targetResults = 5,
        maxWindow = 2.hours,
    ).collect { event ->
        when (event) {
            is PlanStreamEvent.Result -> event.itineraries.forEach { println("${it.start} → ${it.end}") }
            is PlanStreamEvent.Done -> {}
            is PlanStreamEvent.Failure -> println("stream failed: ${event.error}")
        }
    }
}

suspend fun streamArriveBy(client: SpiderClient) {
    // Streamed form of arriveBy.
    client.routing.planStream(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        time = RouteTime.ArriveBy(Instant.parse("2026-07-20T08:00:00Z")),
        targetResults = 5,
        maxWindow = 2.hours,
    ).collect { event ->
        when (event) {
            is PlanStreamEvent.Result -> event.itineraries.forEach { println("${it.start} → ${it.end}") }
            is PlanStreamEvent.Done -> {}
            is PlanStreamEvent.Failure -> println("stream failed: ${event.error}")
        }
    }
}

suspend fun streamWithModes(client: SpiderClient) {
    // Streamed form of planWithModes.
    client.routing.planStream(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        allowedTransitModes = setOf(TransitMode.TRAM, TransitMode.SUBWAY),
        targetResults = 5,
        maxWindow = 2.hours,
    ).collect { event ->
        when (event) {
            is PlanStreamEvent.Result -> event.itineraries.forEach { println("${it.start} → ${it.end}") }
            is PlanStreamEvent.Done -> {}
            is PlanStreamEvent.Failure -> println("stream failed: ${event.error}")
        }
    }
}

suspend fun streamVia(client: SpiderClient) {
    // Streamed form of planVia.
    client.routing.planStream(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        via = listOf(
            ViaLocation.Visit(
                location = Location.Coordinate(49.2100, 16.5900),
                minimumWaitTime = 10.minutes,
            ),
        ),
        targetResults = 5,
        maxWindow = 2.hours,
    ).collect { event ->
        when (event) {
            is PlanStreamEvent.Result -> event.itineraries.forEach { println("${it.start} → ${it.end}") }
            is PlanStreamEvent.Done -> {}
            is PlanStreamEvent.Failure -> println("stream failed: ${event.error}")
        }
    }
}

suspend fun streamWheelchair(client: SpiderClient) {
    // Streamed form of wheelchairPlan.
    client.routing.planStream(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        wheelchairAccessible = true,
        targetResults = 5,
        maxWindow = 2.hours,
    ).collect { event ->
        when (event) {
            is PlanStreamEvent.Result -> event.itineraries.forEach { println("${it.start} → ${it.end}") }
            is PlanStreamEvent.Done -> {}
            is PlanStreamEvent.Failure -> println("stream failed: ${event.error}")
        }
    }
}

suspend fun streamWithLimits(client: SpiderClient) {
    // Streamed form of planWithLimits.
    client.routing.planStream(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        maxTransfers = 2,
        targetResults = 5,
        maxWindow = 2.hours,
    ).collect { event ->
        when (event) {
            is PlanStreamEvent.Result -> event.itineraries.forEach { println("${it.start} → ${it.end}") }
            is PlanStreamEvent.Done -> {}
            is PlanStreamEvent.Failure -> println("stream failed: ${event.error}")
        }
    }
}

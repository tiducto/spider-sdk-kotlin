package examples.routing

import eu.tiducto.spider.client.*
import kotlin.time.Instant

fun setup() {
    val client = SpiderClient(
        baseUrl = "https://your-env-slug.api.tiducto.eu",
        apiKey = "your-api-key",
    ) {
        install(Routing)
    }
}

suspend fun planTrip(client: SpiderClient) {
    val result = client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        first = 3,
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
    )
}

suspend fun laterItineraries(client: SpiderClient) {
    val firstPage = when (val result = client.routing.plan(
        origin = Location.Coordinate(49.1951, 16.6068),
        destination = Location.Coordinate(49.2246, 16.5747),
        first = 3,
    )) {
        is SpiderResult.Success -> result.data
        is SpiderResult.Error -> {
            println("Planning failed: ${result.error}")
            return
        }
    }

    when (val later = client.routing.planNext(firstPage, first = 3)) {
        null -> println("No later itineraries — that was the last page")
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
                println("${departure.routeShortName} → ${departure.headsign} at ${departure.scheduledTime}")
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

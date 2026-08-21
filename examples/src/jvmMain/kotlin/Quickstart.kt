package examples.quickstart

import eu.tiducto.spider.client.*

suspend fun firstCall() {
    val client = SpiderClient(
        baseUrl = "https://your-env-slug.api.tiducto.eu",
        apiKey = "your-api-key",
    )

    val result = client.routing.plan(
        origin = Location.Coordinate(49.1908, 16.6128),
        destination = Location.Coordinate(49.2270, 16.5273),
        first = 3,
    )
}

suspend fun handleResult(client: SpiderClient) {
    when (val result = client.routing.plan(
        origin = Location.Coordinate(49.1908, 16.6128),
        destination = Location.Coordinate(49.2270, 16.5273),
        first = 3,
    )) {
        is SpiderResult.Success -> result.data.edges.forEach { edge ->
            println(edge.itinerary)
        }
        is SpiderResult.Error -> System.err.println("Plan failed: ${result.error}")
    }
}

suspend fun otherSurfaces(client: SpiderClient, tripId: String) {
    client.stops.search { filter { name eq "central" } }
    client.realtime.vehicleForTrip(tripId)
}

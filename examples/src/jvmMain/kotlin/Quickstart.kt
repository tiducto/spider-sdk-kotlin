package examples.quickstart

import eu.tiducto.spider.client.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

suspend fun firstCall() {
    val client = SpiderClient(
        baseUrl = "https://your-env-slug.api.tiducto.eu",
        apiKey = "your-api-key",
    )

    val result = client.routing.plan(
        origin = Location.Coordinate(49.1908, 16.6128),
        destination = Location.Coordinate(49.2270, 16.5273),
        time = RouteTime.DepartAt(Clock.System.now()),
        searchWindow = 60.minutes,
    )
}

suspend fun handleResult(client: SpiderClient) {
    when (val result = client.routing.plan(
        origin = Location.Coordinate(49.1908, 16.6128),
        destination = Location.Coordinate(49.2270, 16.5273),
        time = RouteTime.DepartAt(Clock.System.now()),
        searchWindow = 60.minutes,
    )) {
        is SpiderResult.Success -> result.data.edges.forEach { edge ->
            println(edge.itinerary)
        }
        is SpiderResult.Error -> System.err.println("Plan failed: ${result.error}")
    }
}

suspend fun handleErrors(client: SpiderClient) {
    when (val result = client.routing.plan(
        origin = Location.Coordinate(49.1908, 16.6128),
        destination = Location.Coordinate(49.2270, 16.5273),
        time = RouteTime.DepartAt(Clock.System.now()),
        searchWindow = 60.minutes,
    )) {
        is SpiderResult.Success -> result.data.edges.forEach { println(it.itinerary) }
        // SpiderError is a sealed interface — match the case you care about.
        is SpiderResult.Error -> when (val error = result.error) {
            is SpiderError.Unauthorized -> println("Check the apikey header — HTTP ${error.httpStatus}")
            is SpiderError.RateLimited -> println("Rate limited — back off and retry later")
            is SpiderError.Timeout -> println("Timed out — safe to retry")
            is SpiderError.NotFound -> println("No data for that request")
            is SpiderError.Network -> println("Connectivity problem: ${error.message}")
            is SpiderError.Server -> println("Upstream error — HTTP ${error.httpStatus}")
            is SpiderError.Decoding -> println("Unexpected response shape: ${error.message}")
            is SpiderError.Unknown -> println("Unclassified failure: ${error.message}")
        }
    }
}

// The stable wire code (SpiderErrorCode) drives retry policy and structured logging.
fun isRetryable(error: SpiderError): Boolean = when (error.code) {
    SpiderErrorCode.NETWORK,
    SpiderErrorCode.TIMEOUT,
    SpiderErrorCode.RATE_LIMITED,
    SpiderErrorCode.SERVER -> true
    SpiderErrorCode.UNAUTHORIZED,
    SpiderErrorCode.NOT_FOUND,
    SpiderErrorCode.DECODING,
    SpiderErrorCode.UNKNOWN -> false
}

suspend fun otherSurfaces(client: SpiderClient, tripId: String) {
    client.stops.search { filter { name eq "central" } }
    client.realtime.vehicleForTrip(tripId)
}

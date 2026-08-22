package examples.stops

import eu.tiducto.spider.client.*

private fun placeMarker(lat: Double, lon: Double, label: String) {}

suspend fun search() {
    val client = SpiderClient(
        baseUrl = "https://your-env-slug.api.tiducto.eu",
        apiKey = "your-api-key",
    )

    when (val result = client.stops.search { filter { name eq "Hlavní nádraží" } }) {
        is SpiderResult.Success -> {
            result.data.forEach { stop ->
                println("${stop.name} (${stop.gtfsId}) — ${stop.admin[AdminLevel.CITY]}")
            }
        }
        is SpiderResult.Error -> {
            println("Search failed: ${result.error}")
        }
    }
}

suspend fun reuseHit(client: SpiderClient) {
    val hit = (client.stops.search { filter { name eq "Náměstí" } } as? SpiderResult.Success)
        ?.data?.firstOrNull()

    if (hit != null) {
        client.routing.departures(hit.gtfsId, numberOfDepartures = 10)
        val lat = hit.lat
        val lon = hit.lon
        if (lat != null && lon != null) {
            placeMarker(lat, lon, label = hit.name)
        }
    }
}

suspend fun stopsNearby(client: SpiderClient) {
    // Nearest-first stops within 500 m of a point in central Brno.
    when (val result = client.stops.near(lat = 49.1951, lng = 16.6068, radiusMeters = 500)) {
        is SpiderResult.Success ->
            result.data.forEach { stop ->
                println("${stop.name}  ·  ${stop.lat}, ${stop.lon}")
            }
        is SpiderResult.Error -> println("Nearby search failed: ${result.error}")
    }
}

suspend fun stopById(client: SpiderClient) {
    when (val result = client.stops.byId("U123Z1")) {
        is SpiderResult.Success -> {
            val stop = result.data
            if (stop != null) {
                println("${stop.name} (${stop.gtfsId}) — ${stop.admin[AdminLevel.CITY]}")
            } else {
                println("No stop with id U123Z1")
            }
        }
        is SpiderResult.Error -> println("Lookup failed: ${result.error}")
    }
}

suspend fun stopsByCity(client: SpiderClient) {
    // Free-text name match, constrained to a single administrative city.
    val result = client.stops.search {
        filter {
            name eq "Náměstí"
            AdminLevel.CITY eq "Brno"
        }
    }

    when (result) {
        is SpiderResult.Success ->
            result.data.forEach { stop ->
                println("${stop.name} (${stop.gtfsId})")
            }
        is SpiderResult.Error -> println("Search failed: ${result.error}")
    }
}

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

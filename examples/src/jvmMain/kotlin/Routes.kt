package examples.routes

import eu.tiducto.spider.client.*

suspend fun routesSearch(client: SpiderClient) {
    // Results come back busiest-first — ranked by trip count, so the most-used routes lead.
    val result = client.routes.search {
        query = "4"
        mode = RouteMode.TRAM
        agency = "DPMB"
        limit = 20
    }

    when (result) {
        is SpiderResult.Success ->
            result.data.forEach { route ->
                println("${route.shortName ?: "?"} ${route.longName ?: ""} · ${route.mode} · ${route.tripCount} trips")
            }
        is SpiderResult.Error -> println("Route search failed: ${result.error}")
    }
}

suspend fun routesById(client: SpiderClient) {
    when (val result = client.routes.byId("1:L4")) {
        is SpiderResult.Success -> {
            val route = result.data
            if (route != null) {
                println("${route.shortName ?: route.routeId} — ${route.longName ?: ""} (${route.mode}, ${route.tripCount} trips)")
            } else {
                println("No route with id 1:L4")
            }
        }
        is SpiderResult.Error -> println("Lookup failed: ${result.error}")
    }
}

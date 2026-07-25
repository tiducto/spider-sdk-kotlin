package examples.authentication

import eu.tiducto.spider.client.*

fun authenticated() {
    val apiKey = System.getenv("SPIDER_API_KEY")
        ?: error("SPIDER_API_KEY is not set")

    val client = SpiderClient(
        baseUrl = "https://brno.api.tiducto.eu",
        apiKey = apiKey,
    ) {
        install(Routing)
        install(Stops)
        install(Realtime)
    }
}

fun targeting() {
    val brno = SpiderClient(
        baseUrl = "https://brno.api.tiducto.eu",
        apiKey = System.getenv("BRNO_API_KEY") ?: error("BRNO_API_KEY is not set"),
    ) {
        install(Routing)
        install(Stops)
    }

    val praha = SpiderClient(
        baseUrl = "https://praha.api.tiducto.eu",
        apiKey = System.getenv("PRAHA_API_KEY") ?: error("PRAHA_API_KEY is not set"),
    ) {
        install(Routing)
        install(Stops)
    }
}

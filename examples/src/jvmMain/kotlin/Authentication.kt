package examples.authentication

import eu.tiducto.spider.client.*

fun authenticated() {
    val apiKey = System.getenv("SPIDER_API_KEY")
        ?: error("SPIDER_API_KEY is not set")

    val client = SpiderClient(
        baseUrl = "https://your-env-slug.api.tiducto.eu",
        apiKey = apiKey,
    ) {
        install(Routing)
        install(Stops)
        install(Realtime)
    }
}

fun targeting() {
    val primary = SpiderClient(
        baseUrl = "https://your-env-slug.api.tiducto.eu",
        apiKey = System.getenv("SPIDER_API_KEY") ?: error("SPIDER_API_KEY is not set"),
    ) {
        install(Routing)
        install(Stops)
    }

    val secondary = SpiderClient(
        baseUrl = "https://another-env-slug.api.tiducto.eu",
        apiKey = System.getenv("SPIDER_OTHER_API_KEY") ?: error("SPIDER_OTHER_API_KEY is not set"),
    ) {
        install(Routing)
        install(Stops)
    }
}

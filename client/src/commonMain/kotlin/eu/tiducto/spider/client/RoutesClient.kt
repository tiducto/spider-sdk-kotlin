package eu.tiducto.spider.client

import eu.tiducto.spider.contract.routes.RouteHit
import eu.tiducto.spider.contract.routes.RouteSearchError
import eu.tiducto.spider.contract.routes.RouteSearchRequest
import eu.tiducto.spider.contract.routes.RouteSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json

internal class RoutesClient(
    private val baseUrl: String,
    private val apiKey: String,
    retry: RetryConfig? = null,
    log: SpiderLog,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient = HttpClient {
        installAutoRetry(retry)
        install(ContentNegotiation) {
            json(json)
        }
        installSpiderLogging(log, "SpiderRoutes")
    }

    suspend fun searchRoutes(
        query: String,
        mode: RouteMode? = null,
        agency: String? = null,
        idFilter: String? = null,
        limit: Int? = null,
    ): ImmutableList<TransitRoute> {
        val url = "$baseUrl/routes/search"
        val filterExpr = composeRouteFilter(idFilter, mode, agency)
        val httpResponse = http.post {
            url(url)
            contentType(ContentType.Application.Json)
            spiderHeaders(apiKey)
            setBody(RouteSearchRequest(q = query, filter = filterExpr, limit = limit))
        }
        ContractGuard.check(httpResponse.headers[SpiderContract.HEADER])

        if (!httpResponse.status.isSuccess()) {
            val body = httpResponse.bodyAsText()
            val parsed = runCatching { json.decodeFromString<RouteSearchError>(body) }.getOrNull()
            val detail = parsed?.message ?: body.take(300)
            throw SpiderTransportException.Http(httpResponse.status.value, "POST $url → ${httpResponse.status.value}: $detail", parsed?.code)
        }

        val response: RouteSearchResponse<RouteHit> = httpResponse.body()
        return response.hits.map { it.toTransitRoute() }.toImmutableList()
    }

    private fun RouteHit.toTransitRoute(): TransitRoute = TransitRoute(
        routeId = routeId,
        shortName = shortName,
        longName = longName,
        mode = RouteMode.fromWire(mode),
        routeType = routeType,
        agencyName = agencyName,
        tripCount = tripCount,
    )
}

// Composes the Meilisearch filter expression: `routeId = "…" AND mode = "TRAM" AND agencyName = "…"`.
// Attribute names are bare identifiers (Meili doesn't quote them); only string values are quoted, with
// embedded `"`/`\` escaped so a value can't break out of its clause. The mode value comes from the
// closed RouteMode vocabulary, so its name is safe to interpolate directly. Top-level + internal so the
// wire-contract test can assert the exact strings without a live HTTP round-trip.
internal fun composeRouteFilter(
    idFilter: String?,
    mode: RouteMode?,
    agency: String?,
): String? {
    val clauses = buildList {
        idFilter?.let { add("routeId = \"${it.escapeFilterString()}\"") }
        mode?.let { add("mode = \"${it.name}\"") }
        agency?.let { add("agencyName = \"${it.escapeFilterString()}\"") }
    }
    return clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
}

private fun String.escapeFilterString(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

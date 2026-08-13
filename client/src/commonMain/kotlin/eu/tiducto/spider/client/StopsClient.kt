package eu.tiducto.spider.client

import eu.tiducto.spider.contract.stops.StopSearchError
import eu.tiducto.spider.contract.stops.StopHit
import eu.tiducto.spider.contract.stops.StopSearchRequest
import eu.tiducto.spider.contract.stops.StopSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
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

internal class StopsClient(
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
        installSpiderLogging(log, "SpiderStops")
    }

    suspend fun searchStops(
        query: String,
        filters: Collection<Filter> = emptyList(),
    ): ImmutableList<Stop> {
        val url = "$baseUrl/stops/search"
        val filterExpr = filters.takeIf { it.isNotEmpty() }?.toFilterExpression()
        val httpResponse = http.post {
            url(url)
            contentType(ContentType.Application.Json)
            headers {
                append("apikey", apiKey)
                append(SpiderContract.HEADER, SpiderContract.VERSION)
            }
            setBody(StopSearchRequest(q = query, filter = filterExpr))
        }
        ContractGuard.check(httpResponse.headers[SpiderContract.HEADER])

        if (!httpResponse.status.isSuccess()) {
            val body = httpResponse.bodyAsText()
            val parsed = runCatching { json.decodeFromString<StopSearchError>(body) }.getOrNull()
            val detail = parsed?.message ?: body.take(300)
            throw SpiderTransportException.Http(httpResponse.status.value, "POST $url → ${httpResponse.status.value}: $detail", parsed?.code)
        }

        val response: StopSearchResponse<StopHit> = httpResponse.body()
        return response.hits.map { it.toStop() }.toImmutableList()
    }

    // stop-search filter syntax: `field = "value" AND other = "x"`. Embedded `"` and `\`
    // are escaped per the filter grammar so values containing quotes don't break the
    // expression. Operators other than `eq` are not supported yet — when one is
    // requested we surface a clear error rather than emit a string the backend would
    // reject anyway.
    private fun Collection<Filter>.toFilterExpression(): String =
        joinToString(" AND ") { it.toFilterClause() }

    private fun Filter.toFilterClause(): String = when (operator) {
        "eq" -> "\"${key.escapeFilterString()}\" = \"${value.toString().escapeFilterString()}\""
        else -> throw IllegalArgumentException("Unsupported filter operator: $operator")
    }

    private fun String.escapeFilterString(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    private fun StopHit.toStop(): Stop {
        // Iterate the enum so admin entries come back in canonical order
        // (COUNTRY → SUBURB) regardless of how the backend serialized the doc.
        val adminPairs = buildList {
            AdminLevel.entries.forEach { level ->
                val value = when (level) {
                    AdminLevel.COUNTRY -> country
                    AdminLevel.REGION -> region
                    AdminLevel.DISTRICT -> district
                    AdminLevel.CITY -> city
                    AdminLevel.SUBURB -> suburb
                }
                if (!value.isNullOrBlank()) add(level to value)
            }
        }
        return Stop(
            gtfsId = gtfsId,
            name = name,
            lat = lat,
            lon = lon,
            admin = adminPairs.toMap(),
        )
    }
}

package cz.davidkurzica.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class MeiliClient(
    private val baseUrl: String,
    private val apiKey: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    co.touchlab.kermit.Logger.d(tag = "MeiliClient") { message }
                }
            }
        }
    }

    suspend fun searchStops(
        query: String,
        filters: Collection<Filter> = emptyList(),
    ): ImmutableList<Stop> {
        val url = "$baseUrl/stops/search"
        val filterExpr = filters.takeIf { it.isNotEmpty() }?.toMeiliExpression()
        val httpResponse = http.post {
            url(url)
            contentType(ContentType.Application.Json)
            // Kong key-auth expects the raw key in an `apikey` header (not Authorization: Bearer).
            headers { append("apikey", apiKey) }
            setBody(SearchRequest(q = query, filter = filterExpr))
        }

        if (!httpResponse.status.isSuccess()) {
            val body = httpResponse.bodyAsText()
            val message = runCatching { json.decodeFromString<MeiliError>(body).message }.getOrNull()
                ?: body.take(300)
            throw SpiderTransportException.Http(httpResponse.status.value, "POST $url → ${httpResponse.status.value}: $message")
        }

        val response: SearchResponse<MeiliStop> = httpResponse.body()
        return response.hits.map { it.toStop() }.toImmutableList()
    }

    // Meili filter syntax: `field = "value" AND other = "x"`. Embedded `"` and `\`
    // are escaped per Meili's grammar so values containing quotes don't break the
    // expression. Operators other than `eq` are not supported yet — when one is
    // requested we surface a clear error rather than emit a string Meili would
    // reject anyway.
    private fun Collection<Filter>.toMeiliExpression(): String =
        joinToString(" AND ") { it.toMeiliClause() }

    private fun Filter.toMeiliClause(): String = when (operator) {
        "eq" -> "\"${key.escapeMeiliString()}\" = \"${value.toString().escapeMeiliString()}\""
        else -> throw IllegalArgumentException("Unsupported filter operator: $operator")
    }

    private fun String.escapeMeiliString(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    private fun MeiliStop.toStop(): Stop {
        // Iterate the enum so admin entries come back in canonical order
        // (COUNTRY → SUBURB) regardless of how Meili serialized the doc.
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

@Serializable
internal data class SearchRequest(
    val q: String,
    val filter: String? = null,
)

@Serializable
internal data class SearchResponse<T>(val hits: List<T>, val query: String)

@Serializable
internal data class MeiliStop(
    val gtfsId: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val country: String? = null,
    val region: String? = null,
    val district: String? = null,
    val city: String? = null,
    val suburb: String? = null,
)

@Serializable
internal data class MeiliError(
    val message: String,
    val code: String? = null,
    val type: String? = null,
    val link: String? = null,
)

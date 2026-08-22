package eu.tiducto.spider.client

import eu.tiducto.spider.contract.stops.StopSearchError
import eu.tiducto.spider.contract.stops.StopHit
import eu.tiducto.spider.contract.stops.StopSearchRequest
import eu.tiducto.spider.contract.stops.StopSearchResponse
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
        idFilter: String? = null,
        near: GeoPoint? = null,
        radiusMeters: Int? = null,
        bbox: BoundingBox? = null,
        sortByDistance: Boolean = false,
        limit: Int? = null,
    ): ImmutableList<Stop> {
        val url = "$baseUrl/stops/search"
        val filterExpr = composeStopFilter(filters, idFilter, near, radiusMeters, bbox)
        val sort = composeStopSort(near, sortByDistance)
        val httpResponse = http.post {
            url(url)
            contentType(ContentType.Application.Json)
            spiderHeaders(apiKey)
            setBody(StopSearchRequest(q = query, filter = filterExpr, sort = sort, limit = limit))
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

// Composes the Meilisearch filter expression: `field = "value" AND gtfsId = "…" AND _geoRadius(…) AND …`.
// Attribute names are bare identifiers (Meili doesn't quote them); only string values are quoted, with
// embedded `"`/`\` escaped so a value can't break out of its clause. Coordinates are formatted via string
// interpolation, which is Locale-invariant ('.' decimal) — never String.format, which can emit a comma
// under some default Locales and corrupt the geo call. Top-level + internal so the wire-contract test can
// assert the exact strings without a live HTTP round-trip.
internal fun composeStopFilter(
    filters: Collection<Filter>,
    idFilter: String?,
    near: GeoPoint?,
    radiusMeters: Int?,
    bbox: BoundingBox?,
): String? {
    val clauses = buildList {
        filters.forEach { add(it.toFilterClause()) }
        idFilter?.let { add("gtfsId = \"${it.escapeFilterString()}\"") }
        if (near != null && radiusMeters != null) {
            add("_geoRadius(${near.lat}, ${near.lng}, $radiusMeters)")
        }
        bbox?.let { add("_geoBoundingBox([${it.maxLat}, ${it.maxLng}], [${it.minLat}, ${it.minLng}])") }
    }
    return clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
}

// Nearest-first ordering. Requires an anchor point; SpiderStops rejects sortByDistance without a `near`
// before we get here, so a null anchor simply means "no sort".
internal fun composeStopSort(near: GeoPoint?, sortByDistance: Boolean): List<String>? =
    if (sortByDistance && near != null) listOf("_geoPoint(${near.lat}, ${near.lng}):asc") else null

// Operators other than `eq` are not supported yet — surface a clear error rather than emit a string the
// backend would reject anyway.
private fun Filter.toFilterClause(): String = when (operator) {
    "eq" -> "$key = \"${value.toString().escapeFilterString()}\""
    else -> throw IllegalArgumentException("Unsupported filter operator: $operator")
}

private fun String.escapeFilterString(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

package eu.tiducto.spider.client

import eu.tiducto.spider.contract.places.PlaceHitDto
import eu.tiducto.spider.contract.places.PlaceSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json

internal class PlacesClient(
    private val baseUrl: String,
    private val apiKey: String,
    retry: RetryConfig? = null,
    log: SpiderLog,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient = HttpClient {
        installApiKey(apiKey)
        installAutoRetry(retry)
        install(ContentNegotiation) {
            json(json)
        }
        installSpiderLogging(log, "SpiderPlaces")
    }

    suspend fun search(query: String, near: LatLon?, limit: Int): ImmutableList<PlaceHit> {
        requireInRange("limit", limit in 1..MAX_PLACE_LIMIT)
        val response = http.get {
            url { takeFrom(baseUrl); appendPathSegments("places", "v1", "search") }
            parameter("q", query)
            parameter("limit", limit)
            near?.let {
                parameter("latitude", decimalDegrees(it.lat))
                parameter("longitude", decimalDegrees(it.lon))
            }
            spiderHeaders()
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            val envelope = parseErrorEnvelope(body)
            envelope.planLimitCode?.let {
                throw planLimitFailure(WHERE, response.status.value, it, envelope.message)
            }
            val detail = envelope.message ?: body.take(300).trim()
            throw SpiderTransportException.Http(
                response.status.value,
                "$WHERE → ${response.status.value}: $detail",
                envelope.code,
                detail,
                envelope.field,
            )
        }

        val dto: PlaceSearchResponse = response.body()
        return dto.hits.map { it.toPlaceHit() }.toImmutableList()
    }
}

private const val MAX_PLACE_LIMIT = 20
private const val WHERE = "GET places/v1/search"

private fun PlaceHitDto.toPlaceHit(): PlaceHit = PlaceHit(
    id = id,
    type = placeTypeFromWire(type),
    name = name,
    lat = lat,
    lon = lon,
    category = category,
    admin = adminLabels(country, region, district, city, suburb),
    distance = distance,
)

internal fun placeTypeFromWire(raw: String): PlaceType = when (raw) {
    "stop" -> PlaceType.STOP
    "address" -> PlaceType.ADDRESS
    "place" -> PlaceType.PLACE
    "poi" -> PlaceType.POI
    else -> PlaceType.UNKNOWN
}

// A plain decimal rounded to 7 places (about 1 cm), built by hand: Double.toString switches to exponent
// notation near zero ("1.0E-5"), which the API refuses, and String.format follows the default Locale.
internal fun decimalDegrees(value: Double): String {
    val scaled = abs(value * 10_000_000).roundToLong()
    val whole = scaled / 10_000_000
    val fraction = (scaled % 10_000_000).toString().padStart(7, '0').trimEnd('0')
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

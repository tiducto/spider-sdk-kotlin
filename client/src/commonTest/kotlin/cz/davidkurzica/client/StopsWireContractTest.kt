package cz.davidkurzica.client

import cz.davidkurzica.contract.stops.StopHit
import cz.davidkurzica.contract.stops.StopSearchRequest
import cz.davidkurzica.contract.stops.StopSearchResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

/**
 * Guards the `/stops/search` wire format. Mirrors RoutingWireContractTest, but the Json config mirrors
 * StopsClient's own — `ignoreUnknownKeys = true`, and NOT `explicitNulls = false` (StopsClient doesn't
 * set it; the only optional request field, `filter`, is dropped by its `= null` default anyway).
 *
 * Native-safe test names (no `()` etc. in backticks) — commonTest runs on all targets.
 */
class StopsWireContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `search request without a filter omits the filter key`() {
        val expected = json.parseToJsonElement("""{"q":"Hlavní"}""")
        val actual = json.encodeToJsonElement(StopSearchRequest.serializer(), StopSearchRequest(q = "Hlavní"))
        assertEquals(expected, actual)
    }

    @Test
    fun `search request with a filter carries the raw filter expression`() {
        val expected = json.parseToJsonElement(
            """{"q":"Hlavní","filter":"\"city\" = \"Brno\""}""",
        )
        val actual = json.encodeToJsonElement(
            StopSearchRequest.serializer(),
            StopSearchRequest(q = "Hlavní", filter = "\"city\" = \"Brno\""),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `search response parses hits and tolerates extra envelope and doc fields`() {
        // processingTimeMs/estimatedTotalHits (envelope) and _geo (doc) are extras the SDK doesn't model.
        val body =
            """
            {
              "hits": [
                {"gtfsId":"1:U123","name":"Hlavní nádraží","lat":49.19,"lon":16.61,
                 "country":"Česko","region":"Jihomoravský kraj","district":"Brno-město",
                 "city":"Brno","suburb":"Trnitá","_geo":{"lat":49.19,"lng":16.61}}
              ],
              "query": "Hlavní",
              "processingTimeMs": 3,
              "estimatedTotalHits": 1
            }
            """.trimIndent()

        val response = json.decodeFromString(StopSearchResponse.serializer(StopHit.serializer()), body)
        val hit = response.hits.single()
        assertEquals("Hlavní", response.query)
        assertEquals("1:U123", hit.gtfsId)
        assertEquals("Brno", hit.city)
        assertEquals("Trnitá", hit.suburb)
    }

    @Test
    fun `absent coordinates and admin levels decode to null`() {
        val body = """{"hits":[{"gtfsId":"1:U999","name":"Zastávka bez metadat"}],"query":"x"}"""
        val hit = json.decodeFromString(StopSearchResponse.serializer(StopHit.serializer()), body).hits.single()
        assertNull(hit.lat)
        assertNull(hit.lon)
        assertNull(hit.country)
        assertNull(hit.city)
    }
}

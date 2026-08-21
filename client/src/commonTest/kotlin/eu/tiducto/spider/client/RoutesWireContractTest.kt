package eu.tiducto.spider.client

import eu.tiducto.spider.contract.routes.RouteHit
import eu.tiducto.spider.contract.routes.RouteSearchRequest
import eu.tiducto.spider.contract.routes.RouteSearchResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

/**
 * Guards the `/routes/search` wire format. Mirrors StopsWireContractTest — the Json config mirrors
 * RoutesClient's own (`ignoreUnknownKeys = true`, and NOT `explicitNulls = false`; the only optional
 * request field exercised here, `filter`, is dropped by its `= null` default anyway).
 *
 * Native-safe test names (no `()` etc. in backticks) — commonTest runs on all targets.
 */
class RoutesWireContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `search request without a filter omits the filter key`() {
        val expected = json.parseToJsonElement("""{"q":"4"}""")
        val actual = json.encodeToJsonElement(RouteSearchRequest.serializer(), RouteSearchRequest(q = "4"))
        assertEquals(expected, actual)
    }

    @Test
    fun `search request with a filter carries the raw filter expression`() {
        val expected = json.parseToJsonElement(
            """{"q":"4","filter":"mode = \"TRAM\""}""",
        )
        val actual = json.encodeToJsonElement(
            RouteSearchRequest.serializer(),
            RouteSearchRequest(q = "4", filter = "mode = \"TRAM\""),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `search response parses hits and tolerates extra envelope and doc fields`() {
        // processingTimeMs/estimatedTotalHits (envelope) and color (doc) are extras the SDK doesn't model.
        val body =
            """
            {
              "hits": [
                {"routeId":"1:L4","shortName":"4","longName":"Náměstí Míru – Štefánikova čtvrť",
                 "routeType":0,"mode":"TRAM","agencyName":"DPMB","tripCount":842,"color":"D4021D"}
              ],
              "query": "4",
              "processingTimeMs": 2,
              "estimatedTotalHits": 1
            }
            """.trimIndent()

        val response = json.decodeFromString(RouteSearchResponse.serializer(RouteHit.serializer()), body)
        val hit = response.hits.single()
        assertEquals("4", response.query)
        assertEquals("1:L4", hit.routeId)
        assertEquals("TRAM", hit.mode)
        assertEquals("DPMB", hit.agencyName)
        assertEquals(842, hit.tripCount)
    }

    @Test
    fun `absent optional names and agency decode to null`() {
        val body = """{"hits":[{"routeId":"1:L99","routeType":3,"mode":"BUS","tripCount":12}],"query":"x"}"""
        val hit = json.decodeFromString(RouteSearchResponse.serializer(RouteHit.serializer()), body).hits.single()
        assertNull(hit.shortName)
        assertNull(hit.longName)
        assertNull(hit.agencyName)
    }

    // The exact Meili filter strings the by-id + mode/agency surface composes. Pinned here so a change to
    // the clause grammar (attribute quoting, clause order, AND joining) fails loudly.

    @Test
    fun `byId composes a bare-attribute routeId equality clause with a quoted value`() {
        val filter = composeRouteFilter(
            idFilter = "1:L4",
            mode = null,
            agency = null,
        )
        assertEquals("routeId = \"1:L4\"", filter)
    }

    @Test
    fun `search composes mode and agency clauses joined with AND`() {
        val filter = composeRouteFilter(
            idFilter = null,
            mode = RouteMode.TRAM,
            agency = "DPMB",
        )
        assertEquals("mode = \"TRAM\" AND agencyName = \"DPMB\"", filter)
    }
}

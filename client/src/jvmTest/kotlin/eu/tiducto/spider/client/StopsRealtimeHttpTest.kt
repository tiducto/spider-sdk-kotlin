package eu.tiducto.spider.client

import eu.tiducto.spider.client.FakeGateway.Companion.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Stop search and realtime end to end against [FakeGateway]: request defaults, limit checks, mapping. */
class StopsRealtimeHttpTest {

    private val gateway = FakeGateway()
    private val stops = SpiderStops(gateway.baseUrl, "test-key")
    private val realtime = SpiderRealtime(gateway.baseUrl, "test-key")

    @AfterTest
    fun stop() = gateway.close()

    private fun assertOutOfRange(field: String, result: SpiderResult<*>) {
        val error = assertIs<SpiderError.BadRequest>(assertIs<SpiderResult.Error>(result).error)
        assertEquals(field, error.field)
        assertEquals("$field is out of range", error.message)
    }

    @Test
    fun `stop search sends limit 20 by default and filters by modes`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/stops/search" to json(
                """
                {"hits":[{"gtfsId":"1:U1","name":"Náměstí Svobody","code":"NS","locationType":1,"wheelchairBoarding":1,
                          "modes":["TRAM","FUNICULAR","HOVERCRAFT"]}],"query":"Náměstí"}
                """.trimIndent(),
            ),
        )

        val stop = assertIs<SpiderResult.Success<List<Stop>>>(
            stops.search {
                filter { name eq "Náměstí" }
                modes = setOf(TransitMode.TRAM, TransitMode.BUS)
            },
        ).data.single()

        val body = gateway.body()
        assertEquals(20, body.getValue("limit").jsonPrimitive.int)
        assertEquals("""modes IN ["TRAM", "BUS"]""", body.getValue("filter").jsonPrimitive.content)
        assertEquals(listOf(TransitMode.TRAM, TransitMode.FUNICULAR, TransitMode.UNKNOWN), stop.modes)
        assertEquals(WheelchairBoarding.POSSIBLE, stop.wheelchairBoarding)
    }

    @Test
    fun `a stop search limit outside 1 to 50 is a BadRequest without a request`() = runBlocking<Unit> {
        assertOutOfRange("limit", stops.search { filter { name eq "x" }; limit = 0 })
        assertOutOfRange("limit", stops.search { filter { name eq "x" }; limit = 51 })
        assertOutOfRange("limit", stops.near(49.19, 16.61, limit = 51))
        assertOutOfRange("limit", stops.within(49.18, 16.59, 49.21, 16.63, limit = 0))
        assertEquals(emptyList(), gateway.seen.toList())
    }

    @Test
    fun `realtime tripIds outside 1 to 50 is a BadRequest without a request`() = runBlocking<Unit> {
        val ids = (1..51).map { "1:T$it" }
        assertOutOfRange("tripIds", realtime.vehicles(emptyList()))
        assertOutOfRange("tripIds", realtime.vehicles(ids))
        assertOutOfRange("tripIds", realtime.delays(emptyMap()))
        assertOutOfRange("tripIds", realtime.delays(emptyList(), "2026-09-28"))
        // Counted across all service dates: 30 + 21 is over the limit even though each group is under it.
        assertOutOfRange("tripIds", realtime.delays(mapOf("2026-09-27" to ids.take(30), "2026-09-28" to ids.drop(30))))
        assertEquals(emptyList(), gateway.seen.toList())

        gateway.replies = mapOf("/realtime/delays" to json("""{"results":[]}"""))
        assertIs<SpiderResult.Success<TripDelays>>(
            realtime.delays(mapOf("2026-09-27" to ids.take(30), "2026-09-28" to ids.drop(30).take(20))),
        )
    }
}

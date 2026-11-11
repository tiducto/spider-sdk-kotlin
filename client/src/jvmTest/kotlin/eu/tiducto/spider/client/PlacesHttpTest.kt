package eu.tiducto.spider.client

import eu.tiducto.spider.client.FakeGateway.Companion.json
import eu.tiducto.spider.client.FakeGateway.Reply
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

/** Place search end to end against [FakeGateway]: the query string, limit checks, hit mapping and errors. */
class PlacesHttpTest {

    private val gateway = FakeGateway()
    private val places = SpiderPlaces(gateway.baseUrl, "test-key")

    @AfterTest
    fun stop() = gateway.close()

    private val empty = mapOf("/places/v1/search" to json("""{"hits":[]}"""))

    @Test
    fun `search is one GET with q and limit 10 by default and no location`() = runBlocking<Unit> {
        gateway.replies = empty

        assertIs<SpiderResult.Success<List<PlaceHit>>>(places.search("Mendlovo náměstí"))

        val request = gateway.seen.single()
        assertEquals("GET", request.method)
        assertEquals("/places/v1/search", request.path)
        assertEquals("q=Mendlovo+n%C3%A1m%C4%9Bst%C3%AD&limit=10", request.query)
    }

    @Test
    fun `a location sends latitude and longitude as plain decimals whatever the default locale`() = runBlocking<Unit> {
        gateway.replies = empty
        val default = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            places.search("Údolní 53", near = LatLon(49.1951, 16.6068), limit = 5)
        } finally {
            Locale.setDefault(default)
        }

        assertEquals(
            "q=%C3%9Adoln%C3%AD+53&limit=5&latitude=49.1951&longitude=16.6068",
            gateway.seen.single().query,
        )
    }

    @Test
    fun `coordinates near zero are not sent in exponent notation`() {
        assertEquals("-0.00001", decimalDegrees(-0.00001))
        assertEquals("0.0000001", decimalDegrees(1.0E-7))
        assertEquals("0", decimalDegrees(-0.00000001))
        assertEquals("49.1951", decimalDegrees(49.1951))
        assertEquals("-180", decimalDegrees(-180.0))
        assertEquals("16.6068123", decimalDegrees(16.60681234))
    }

    @Test
    fun `hits map type, labels in canonical order, category and distance`() = runBlocking<Unit> {
        gateway.replies = mapOf(
            "/places/v1/search" to json(
                """
                {"hits":[
                  {"id":"1:U123","type":"stop","name":"Údolní","lat":49.197,"lon":16.6,
                   "suburb":"Veveří","city":"Brno","country":"Česko","distance":120,"score":0.9},
                  {"id":"a:8123","type":"address","name":"Údolní 53","lat":49.198,"lon":16.599,
                   "category":"building","region":"Jihomoravský kraj","district":" ","city":"Brno","distance":45},
                  {"id":"s:1","type":"street","name":"Údolní","lat":49.19,"lon":16.6,"category":"street"},
                  {"id":"p:1","type":"place","name":"Brno","lat":49.19,"lon":16.6,"category":"municipality"},
                  {"id":"o:1","type":"poi","name":"Masarykova univerzita","lat":49.2,"lon":16.6,"category":"university"},
                  {"id":"x:1","type":"bridge","name":"Most","lat":49.2,"lon":16.6}
                ],"query":"Údolní"}
                """.trimIndent(),
            ),
        )

        val hits = assertIs<SpiderResult.Success<List<PlaceHit>>>(
            places.search("Údolní", near = LatLon(49.19, 16.61)),
        ).data

        assertEquals(
            listOf(PlaceType.STOP, PlaceType.ADDRESS, PlaceType.STREET, PlaceType.PLACE, PlaceType.POI, PlaceType.UNKNOWN),
            hits.map { it.type },
        )
        val (stop, address) = hits
        assertEquals("1:U123", stop.id)
        assertEquals(49.197, stop.lat)
        assertEquals(16.6, stop.lon)
        assertEquals(null, stop.category)
        assertEquals(120, stop.distance)
        assertEquals(listOf(AdminLevel.COUNTRY, AdminLevel.CITY, AdminLevel.SUBURB), stop.admin.keys.toList())
        assertEquals("Veveří", stop.admin[AdminLevel.SUBURB])
        assertEquals("building", address.category)
        assertEquals(45, address.distance)
        assertEquals(
            mapOf(AdminLevel.REGION to "Jihomoravský kraj", AdminLevel.CITY to "Brno"),
            address.admin,
        )
        assertEquals("university", hits[4].category)
        assertEquals(null, hits[4].distance)
        assertEquals(emptyMap(), hits[5].admin)
    }

    @Test
    fun `a limit outside 1 to 20 is a BadRequest without a request`() = runBlocking<Unit> {
        for (limit in listOf(0, 21)) {
            val error = assertIs<SpiderError.BadRequest>(assertIs<SpiderResult.Error>(places.search("Brno", limit = limit)).error)
            assertEquals("limit", error.field)
            assertEquals("limit is out of range", error.message)
        }
        assertEquals(emptyList(), gateway.seen.toList())

        gateway.replies = empty
        assertIs<SpiderResult.Success<List<PlaceHit>>>(places.search("Brno", limit = 1))
        assertIs<SpiderResult.Success<List<PlaceHit>>>(places.search("Brno", limit = 20))
    }

    @Test
    fun `the error envelope maps to the typed error`() = runBlocking<Unit> {
        val cases = listOf(
            Reply(400, "application/json", """{"code":"bad_request","message":"q is out of range","field":"q"}"""),
            Reply(401, "application/json", """{"code":"unauthorized","message":"api key is invalid"}"""),
            Reply(403, "application/json", """{"code":"agreement_inactive","message":"agreement is not active"}"""),
            Reply(403, "application/json", """{"error":"planning_limit_reached","message":"trip planning limit reached"}"""),
            Reply(429, "application/json", """{"code":"rate_limited","message":"rate limit exceeded"}"""),
        )
        val errors = cases.map { reply ->
            gateway.replies = mapOf("/places/v1/search" to reply)
            assertIs<SpiderResult.Error>(places.search("x1")).error
        }

        val badRequest = assertIs<SpiderError.BadRequest>(errors[0])
        assertEquals("q", badRequest.field)
        assertEquals("q is out of range", badRequest.message)
        assertEquals(400, badRequest.httpStatus)
        assertIs<SpiderError.Unauthorized>(errors[1])
        assertIs<SpiderError.AgreementInactive>(errors[2])
        assertIs<SpiderError.PlanningLimitReached>(errors[3])
        assertEquals(403, errors[3].httpStatus)
        assertIs<SpiderError.RateLimited>(errors[4])
        assertEquals(429, errors[4].httpStatus)
    }
}

package eu.tiducto.spider.client

import kotlinx.collections.immutable.ImmutableList

/**
 * Place search: one query over everything a rider might type as a destination — stops, addresses,
 * municipalities and points of interest within the environment's service area. Reachable as `client.places`
 * on any [SpiderClient].
 *
 * ```kotlin
 * // Best matches first, at most 10 hits.
 * client.places.search("Mendlovo náměstí")
 *
 * // With a location, the nearest of equally good matches comes first and each hit carries its distance.
 * client.places.search("Údolní 53", near = LatLon(49.1951, 16.6068), limit = 5)
 * ```
 *
 * Hits come best first: stops, then municipalities and landmarks, then other points of interest and addresses.
 * A query with a digit (a house number) lists individual addresses; without one, it lists stops, municipalities
 * and points of interest, and no addresses. Abbreviated and spelled-out forms find each other, and text typed
 * without diacritics matches text with them.
 *
 * A [PlaceType.STOP] hit's [PlaceHit.id] is the stop's feed-prefixed id, the value routing takes as a stop
 * location, and its [PlaceHit.stop] is the stop as stop search returns it; every other hit routes by its
 * coordinates.
 */
class SpiderPlaces(
    private val baseUrl: String,
    apiKey: String,
    retry: RetryConfig? = null,
    logging: LoggingConfig = LoggingConfig(),
) {
    private val log = logging.buildLog()
    private val places = PlacesClient(baseUrl, apiKey, retry, log)

    /**
     * Places matching [query], best first. [query] is 2 to 100 characters after trimming whitespace. With
     * [near], the nearest of equally good matches ranks first and each hit carries [PlaceHit.distance]; without
     * it, distance plays no part. Returns at most [limit] hits, 1 to 20; a limit outside that range returns
     * [SpiderError.BadRequest] without a request.
     */
    suspend fun search(
        query: String,
        near: LatLon? = null,
        limit: Int = 10,
    ): SpiderResult<ImmutableList<PlaceHit>> = context(log) {
        spiderCall(
            tag = "SpiderPlaces",
            message = { "search failed against $baseUrl (q=$query, near=${near != null}, limit=$limit)" },
        ) {
            places.search(query, near, limit)
        }
    }
}

/** Optional configuration for the places surface. Apply it via `SpiderClient(...) { places { … } }`. */
class PlacesConfig : SurfaceConfig()

/** What a [PlaceHit] is. */
enum class PlaceType {
    /** A stop or station; its [PlaceHit.id] is the feed-prefixed stop id routing takes. */
    STOP,

    /** A single address: a building with its house number. */
    ADDRESS,

    /** A municipality. */
    PLACE,

    /** A point of interest, such as a university, hospital or shopping centre. */
    POI,

    /** A type this SDK version doesn't know. */
    UNKNOWN,
}

/**
 * One place search hit.
 *
 * [id] is, for a [PlaceType.STOP], the stop's feed-prefixed id (`<feedId>:<id>`); for any other type an opaque
 * id, stable across searches. [category] says what kind of place it is — for a [PlaceType.POI] e.g.
 * `university`, `hospital`, `mall`; for a [PlaceType.PLACE] `municipality`; for an [PlaceType.ADDRESS]
 * `building` or `recreational`; null for a stop. New categories may appear.
 * [admin] holds the administrative areas the place lies in, labelled the same way as [Stop.admin]. [distance]
 * is the distance in metres from the location the search was given, null when it was given none. [stop] is a
 * [PlaceType.STOP] hit's stop, with the same members stop search returns for it; null for every other type.
 */
data class PlaceHit(
    val id: String,
    val type: PlaceType,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String? = null,
    val admin: Map<AdminLevel, String> = emptyMap(),
    val distance: Int? = null,
    val stop: Stop? = null,
)

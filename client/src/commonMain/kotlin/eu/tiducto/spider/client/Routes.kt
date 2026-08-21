package eu.tiducto.spider.client

import kotlinx.collections.immutable.ImmutableList

/**
 * Route search and lookup. Reachable as `client.routes` on any [SpiderClient].
 *
 * [search] is the general entry point — a free-text query (matched against the
 * route's short and long names), an optional [RouteMode] and/or agency filter,
 * expressed via the [RouteRequest] DSL:
 *
 * ```kotlin
 * // Free-text only — fuzzy match against short/long name.
 * client.routes.search { query = "4" }
 *
 * // Filter by mode (server-side).
 * client.routes.search { mode = RouteMode.TRAM }
 *
 * // Combine — trams whose name matches, run by one agency, capped to 20.
 * client.routes.search {
 *     query = "Hlavní"
 *     mode = RouteMode.TRAM
 *     agency = "DPMB"
 *     limit = 20
 * }
 * ```
 *
 * Results come back busier-first (ranked by trip count), so the most-used routes
 * lead without an explicit sort. [byId] is the exact-lookup shortcut by `routeId`.
 */
class SpiderRoutes(
    private val baseUrl: String,
    apiKey: String,
    retry: RetryConfig? = null,
    logging: LoggingConfig = LoggingConfig(),
) {
    private val log = logging.buildLog()
    private val routes = RoutesClient(baseUrl, apiKey, retry, log)

    suspend fun search(block: RouteRequest.() -> Unit): SpiderResult<ImmutableList<TransitRoute>> {
        val request = RouteRequest().apply(block)
        val query = request.query.orEmpty()
        return context(log) {
            spiderCatch(
                tag = "SpiderRoutes",
                message = { "search failed against $baseUrl (q=$query, mode=${request.mode}, agency=${request.agency})" },
            ) {
                routes.searchRoutes(
                    query = query,
                    mode = request.mode,
                    agency = request.agency,
                    limit = request.limit,
                )
            }
        }
    }

    /**
     * Look up a single route by its opaque, feed-prefixed [routeId] (e.g. `"1:L4"`). Returns the hit,
     * or `null` when no route carries that id. Transport failures surface as [SpiderResult.Error].
     */
    suspend fun byId(routeId: String): SpiderResult<TransitRoute?> = context(log) {
        spiderCatch(
            tag = "SpiderRoutes",
            message = { "byId failed against $baseUrl (routeId=$routeId)" },
        ) {
            routes.searchRoutes(query = "", idFilter = routeId, limit = 1).firstOrNull()
        }
    }
}

/** Optional configuration for the routes surface. Apply it via `SpiderClient(...) { routes { … } }`. */
class RoutesConfig : SurfaceConfig()

/**
 * Coarse transit-mode bucket for a [TransitRoute], derived from its GTFS `route_type`.
 *
 * A closed vocabulary the `routes_env_{envId}` index stamps on each document so
 * callers can filter and badge routes without decoding raw `route_type` numbers.
 * [OTHER] is the catch-all for any bucket this SDK version doesn't recognise (or
 * a route whose type didn't map to a named bucket).
 */
enum class RouteMode {
    BUS,
    TRAM,
    RAIL,
    SUBWAY,
    FERRY,
    TROLLEYBUS,
    AERIAL,
    FUNICULAR,
    CABLE_TRAM,
    MONORAIL,
    OTHER,
    ;

    internal companion object {
        // Unknown/absent bucket strings collapse to OTHER so a new backend value never breaks decoding.
        fun fromWire(raw: String?): RouteMode = entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

/**
 * A transit route (a GTFS "route" — a named line such as tram 4 or bus 25), as indexed for search.
 *
 * Named [TransitRoute] rather than `Route` because the routing surface already owns a public `Route`
 * (the result of a trip-plan / `planConnection`); the two are unrelated and must not collide.
 */
data class TransitRoute(
    val routeId: String,
    val shortName: String? = null,
    val longName: String? = null,
    val mode: RouteMode,
    val routeType: Int,
    val agencyName: String? = null,
    val tripCount: Int,
)

/**
 * Request builder for [SpiderRoutes.search]. See [SpiderRoutes] for end-to-end
 * usage examples; this class exists so the `search { … }` DSL works.
 */
class RouteRequest {
    /** Free-text query, matched against the route's short and long names. */
    var query: String? = null

    /** Restrict results to a single [RouteMode]. */
    var mode: RouteMode? = null

    /** Restrict results to routes operated by this agency (exact match on the indexed agency name). */
    var agency: String? = null

    /** Cap on the number of hits returned. */
    var limit: Int? = null
}

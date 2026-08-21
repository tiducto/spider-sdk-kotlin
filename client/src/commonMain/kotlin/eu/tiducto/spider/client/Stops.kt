package eu.tiducto.spider.client

import kotlinx.collections.immutable.ImmutableList

/**
 * Stop search and lookup. Reachable as `client.stops` on any [SpiderClient].
 *
 * [search] is the general entry point — a free-text query, filters, geographic
 * constraints, or any combination, expressed via the [StopRequest] DSL:
 *
 * ```kotlin
 * // Free-text only — fuzzy match against stop names.
 * client.stops.search { filter { name eq "Hlavní" } }
 *
 * // Filter by administrative geography (server-side, scales to millions of stops).
 * client.stops.search { filter { AdminLevel.CITY eq "Brno" } }
 *
 * // Combine — match the name fragment, restrict to a specific district.
 * client.stops.search {
 *     filter {
 *         name eq "Hlavní"
 *         AdminLevel.CITY eq "Brno"
 *         AdminLevel.DISTRICT eq "Brno-město"
 *     }
 * }
 *
 * // Nearest stops within 500 m, closest first.
 * client.stops.search { near(49.19, 16.61); radiusMeters = 500; sortByDistance = true }
 *
 * // Stops inside a bounding box (SW corner, then NE corner).
 * client.stops.search { bbox(49.18, 16.59, 49.21, 16.63) }
 * ```
 *
 * Three convenience shortcuts cover the common cases: [byId] (exact lookup by
 * `gtfsId`), [near] (nearest-first radius search), and [within] (bounding box).
 *
 * Filtering by an [AdminLevel] only works if the deployment was enriched with
 * boundary polygons for that level — see [AdminLevel] for what each level
 * represents across jurisdictions. Filtering by a level the tenant did not
 * enrich produces a [SpiderResult.Error] surfacing the backend's rejection.
 */
class SpiderStops(
    private val baseUrl: String,
    apiKey: String,
    retry: RetryConfig? = null,
    logging: LoggingConfig = LoggingConfig(),
) {
    private val log = logging.buildLog()
    private val stops = StopsClient(baseUrl, apiKey, retry, log)

    suspend fun search(block: StopRequest.() -> Unit): SpiderResult<ImmutableList<Stop>> {
        // Build + validate outside spiderCatch so misuse (radius/sort without `near`) throws
        // IllegalArgumentException eagerly rather than being folded into a SpiderResult.Error.
        val request = StopRequest().apply(block)
        request.validate()
        val name = request.nameQuery.orEmpty()
        val filters = request.nonNameFilters
        return context(log) {
            spiderCatch(
                tag = "SpiderStops",
                message = { "search failed against $baseUrl (q=$name, filters=${filters.size})" },
            ) {
                stops.searchStops(
                    query = name,
                    filters = filters,
                    near = request.anchor,
                    radiusMeters = request.radiusMeters,
                    bbox = request.boundingBox,
                    sortByDistance = request.sortByDistance,
                    limit = request.limit,
                )
            }
        }
    }

    /**
     * Look up a single stop by its opaque, feed-prefixed [gtfsId] (e.g. `"1:39822"`). Returns the hit,
     * or `null` when no stop carries that id. Transport failures surface as [SpiderResult.Error].
     */
    suspend fun byId(gtfsId: String): SpiderResult<Stop?> = context(log) {
        spiderCatch(
            tag = "SpiderStops",
            message = { "byId failed against $baseUrl (gtfsId=$gtfsId)" },
        ) {
            stops.searchStops(query = "", idFilter = gtfsId, limit = 1).firstOrNull()
        }
    }

    /**
     * Stops nearest (lat, lng), closest first. With [radiusMeters] the search is capped to that radius;
     * without it, the nearest [limit] stops overall are returned. Convenience over
     * `search { near(lat, lng); radiusMeters = …; sortByDistance = true }`.
     */
    suspend fun near(
        lat: Double,
        lng: Double,
        radiusMeters: Int? = null,
        limit: Int? = null,
    ): SpiderResult<ImmutableList<Stop>> = search {
        near(lat, lng)
        this.radiusMeters = radiusMeters
        sortByDistance = true
        this.limit = limit
    }

    /**
     * Stops inside the axis-aligned bounding box defined by its south-west (min) and north-east (max)
     * corners. Convenience over `search { bbox(minLat, minLng, maxLat, maxLng) }`.
     */
    suspend fun within(
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
        limit: Int? = null,
    ): SpiderResult<ImmutableList<Stop>> = search {
        bbox(minLat, minLng, maxLat, maxLng)
        this.limit = limit
    }
}

/** Anchor point for radius filtering and distance sorting. Internal — never crosses the public surface. */
internal data class GeoPoint(val lat: Double, val lng: Double)

/** Axis-aligned bounding box, south-west (min) and north-east (max) corners. Internal wire helper. */
internal data class BoundingBox(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
)

/** Optional configuration for the stops surface. Apply it via `SpiderClient(...) { stops { … } }`. */
class StopsConfig : SurfaceConfig()

/**
 * Administrative geography levels that may be associated with a [Stop].
 *
 * Each entry corresponds to an OSM `admin_level` value; the semantic name
 * abstracts over the local label. A CZ `obec`, a DE `Gemeinde` and a UK
 * London borough all live at [CITY] even though they're called different
 * things locally. A tenant chooses which levels to populate per dataset;
 * any level not populated for a given stop is simply absent from
 * [Stop.admin].
 */
enum class AdminLevel(val osmLevel: Int) {
    /** Nation state. OSM `admin_level=2`. Examples: Czechia, Germany, USA. */
    COUNTRY(2),

    /** Top-level subdivision below the country. OSM `admin_level=4`.
     *  Examples: kraj (CZ), Bundesland (DE), state (US), nation within UK. */
    REGION(4),

    /** Second-level subdivision. OSM `admin_level=6`.
     *  Examples: okres (CZ), Landkreis (DE), county (US / UK). */
    DISTRICT(6),

    /** Municipality. OSM `admin_level=8`.
     *  Examples: obec (CZ), Gemeinde (DE), city (US), London borough (UK). */
    CITY(8),

    /** City quarter or neighborhood. OSM `admin_level=10`.
     *  Examples: městská část (CZ), Stadtteil (DE), neighborhood (US). */
    SUBURB(10),
}

data class Stop(
    val gtfsId: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val admin: Map<AdminLevel, String> = emptyMap(),
    val wheelchairBoarding: WheelchairBoarding? = null,
)

/**
 * Filter clause used inside a [StopRequest.filter] block.
 *
 * Constructed by the infix operators on [StopFilters] (`name eq "..."`,
 * `AdminLevel.CITY eq "Brno"`); end users don't instantiate this directly.
 */
class Filter(
    val key: String,
    val operator: String,
    val value: Any,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is Filter &&
                key == other.key &&
                operator == other.operator &&
                value == other.value)

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + operator.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

/**
 * Filter DSL surface inside [StopRequest.filter].
 *
 * Two ways to write a clause:
 *  - `name eq "Hlavní"` — fuzzy free-text match against the stop name.
 *  - `AdminLevel.CITY eq "Brno"` (typed) — exact match against an admin level
 *    the deployment was enriched with. Same shape for every level in
 *    [AdminLevel] (`COUNTRY`, `REGION`, `DISTRICT`, `CITY`, `SUBURB`).
 *
 * Multiple clauses combine with AND. Filtering an unrelated level (one this
 * deployment didn't enrich) surfaces as a [SpiderResult.Error].
 */
class StopFilters {
    /** Free-text name match. Use as `name eq "Hlavní nádraží"`. */
    val name: String = "name"

    private val filters: MutableSet<Filter> = mutableSetOf()

    /** Free-text or string-keyed clause: `name eq "Hlavní"`. */
    infix fun String.eq(value: Any) {
        filters.add(Filter(this, "eq", value))
    }

    /** Typed admin-level clause: `AdminLevel.CITY eq "Brno"`. */
    infix fun AdminLevel.eq(value: Any) {
        filters.add(Filter(name.lowercase(), "eq", value))
    }

    fun toSet(): Set<Filter> = filters
}

/**
 * Request builder for [SpiderStops.search]. See [SpiderStops] for end-to-end
 * usage examples; this class exists so the `search { filter { … } }` DSL works.
 */
class StopRequest {
    private val filters = mutableSetOf<Filter>()

    /** Anchor for [radiusMeters] / [sortByDistance]. Set via [near]; consumed internally. */
    internal var anchor: GeoPoint? = null
        private set

    /** Bounding box set via [bbox]; consumed internally. */
    internal var boundingBox: BoundingBox? = null
        private set

    /** Radius in meters around the [near] anchor. Requires [near]. */
    var radiusMeters: Int? = null

    /** Order hits by ascending distance from the [near] anchor (closest first). Requires [near]. */
    var sortByDistance: Boolean = false

    /** Cap on the number of hits returned. */
    var limit: Int? = null

    fun filter(block: StopFilters.() -> Unit) {
        filters.addAll(StopFilters().apply(block).toSet())
    }

    /** Anchor radius filtering and distance sorting to (lat, lng). */
    fun near(lat: Double, lng: Double) {
        anchor = GeoPoint(lat, lng)
    }

    /** Restrict results to a bounding box: south-west (min) and north-east (max) corners. */
    fun bbox(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double) {
        boundingBox = BoundingBox(minLat, minLng, maxLat, maxLng)
    }

    /** Rejects geo options that need an anchor but weren't given one. */
    internal fun validate() {
        require(radiusMeters == null || anchor != null) {
            "radiusMeters requires near(lat, lng) to be set"
        }
        require(!sortByDistance || anchor != null) {
            "sortByDistance requires near(lat, lng) to be set"
        }
    }

    /** The free-text `name eq "…"` clause, if present. Becomes the search query. */
    val nameQuery: String?
        get() = filters.firstOrNull { it.key == "name" && it.operator == "eq" }?.value?.toString()

    /** Every clause except [nameQuery]. Becomes server-side filter predicates. */
    val nonNameFilters: Set<Filter>
        get() = filters.filterNotTo(mutableSetOf()) { it.key == "name" && it.operator == "eq" }
}

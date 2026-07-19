package cz.davidkurzica.client

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList

/**
 * Stop search. Reachable as `client.stops` once the [Stops] feature is installed.
 *
 * The single entry point is [search] — a free-text query, an optional set of
 * filters, or both. Both are expressed via the [StopRequest] DSL:
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
 * ```
 *
 * Filtering by an [AdminLevel] only works if the deployment was enriched with
 * boundary polygons for that level — see [AdminLevel] for what each level
 * represents across jurisdictions. Filtering by a level the tenant did not
 * enrich produces a [SpiderResult.Error] surfacing the backend's rejection.
 */
class SpiderStops(
    private val baseUrl: String,
    apiKey: String,
) {
    private val stops = StopsClient(baseUrl, apiKey)

    suspend fun search(block: StopRequest.() -> Unit): SpiderResult<ImmutableList<Stop>> {
        var name = ""
        var filters: Set<Filter> = emptySet()
        return try {
            val request = StopRequest().apply(block)
            name = request.nameQuery.orEmpty()
            filters = request.nonNameFilters
            SpiderResult.Success(stops.searchStops(query = name, filters = filters))
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(throwable = e, tag = "SpiderStops") {
                "search failed against $baseUrl (q=$name, filters=${filters.size})"
            }
            SpiderResult.Error(e.toSpiderError())
        }
    }
}

class StopsConfig

object Stops : SpiderFeature<StopsConfig, SpiderStops> {
    override fun newConfig() = StopsConfig()
    override fun build(baseUrl: String, apiKey: String, config: StopsConfig): SpiderStops =
        SpiderStops(baseUrl = baseUrl, apiKey = apiKey)
}

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

    fun filter(block: StopFilters.() -> Unit) {
        filters.addAll(StopFilters().apply(block).toSet())
    }

    /** The free-text `name eq "…"` clause, if present. Becomes the search query. */
    val nameQuery: String?
        get() = filters.firstOrNull { it.key == "name" && it.operator == "eq" }?.value?.toString()

    /** Every clause except [nameQuery]. Becomes server-side filter predicates. */
    val nonNameFilters: Set<Filter>
        get() = filters.filterNotTo(mutableSetOf()) { it.key == "name" && it.operator == "eq" }
}

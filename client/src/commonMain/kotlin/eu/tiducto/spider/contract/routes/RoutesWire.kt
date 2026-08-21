package eu.tiducto.spider.contract.routes

import kotlinx.serialization.Serializable

/**
 * The route-search wire contract.
 *
 * **Hand-written, not generated.** Unlike the OTP models under `eu.tiducto.spider.contract.routing`
 * (produced by openapi-generator and wiped/rewritten by `scripts/generate-contract.sh`), these live in
 * their own package so the generator's `rm -rf` on `routing/` never touches them. The real upstream
 * owner of the [RouteHit] document shape is the `routes_env_{envId}` index builder (part of the
 * graph-build pipeline, another repo) — this file is the SDK-side mirror of that cross-repo contract,
 * pinned by `RoutesWireContractTest`.
 *
 * `:client` consumes these internally and maps them to the public domain type `Route`; they must not
 * appear in `:client`'s public API (`:contract` is an `implementation` dependency; `PublicApiLeakTest`
 * enforces it).
 */

/**
 * POST body for `/routes/search`. [filter] is a route-search filter expression the client composes;
 * [sort] carries index sort directives (unused today — the index already default-ranks busier routes
 * first); [limit] caps the hit count. All three are omitted from the wire when null (the encoder drops
 * defaults).
 */
@Serializable
internal data class RouteSearchRequest(
    val q: String,
    val filter: String? = null,
    val sort: List<String>? = null,
    val limit: Int? = null,
)

/**
 * The route-search envelope. Generic over the hit type (the SDK only ever searches the routes index →
 * `RouteSearchResponse<RouteHit>`, but the shape is the index-agnostic search response). [query] is the
 * backend's echo of the search term. Extra envelope fields the backend adds (`processingTimeMs`,
 * `estimatedTotalHits`, …) are tolerated by the client's `ignoreUnknownKeys` decoder and intentionally
 * not modeled.
 */
@Serializable
internal data class RouteSearchResponse<T>(val hits: List<T>, val query: String)

/**
 * One route document as indexed. [routeId], [routeType], [mode] and [tripCount] are always present;
 * [shortName]/[longName]/[agencyName] are populated only where the feed supplied them, so they are
 * nullable. [mode] is a coarse bucket string (BUS/TRAM/RAIL/…) derived from [routeType]. These field
 * names must match the index builder (`routes_env_{envId}`).
 */
@Serializable
internal data class RouteHit(
    val routeId: String,
    val shortName: String? = null,
    val longName: String? = null,
    val routeType: Int,
    val mode: String,
    val agencyName: String? = null,
    val tripCount: Int,
)

/** Route-search error envelope, parsed on a non-2xx to surface a useful message. */
@Serializable
internal data class RouteSearchError(
    val message: String,
    val code: String? = null,
    val type: String? = null,
    val link: String? = null,
)

package eu.tiducto.spider.contract.stops

import kotlinx.serialization.Serializable

/**
 * The stop-search wire contract.
 *
 * **Hand-written, not generated.** Unlike the OTP models under `eu.tiducto.spider.contract.routing`
 * (produced by openapi-generator and wiped/rewritten by `scripts/generate-contract.sh`), these live in
 * their own package so the generator's `rm -rf` on `routing/` never touches them. The real upstream
 * owner of the [StopHit] document shape is the `stops_env_{envId}` index builder (`seed-stops.sh`,
 * another repo) — this file is the SDK-side mirror of that cross-repo contract, pinned by
 * `StopsWireContractTest`.
 *
 * `:client` consumes these internally and maps them to the public domain type `Stop`; they must not
 * appear in `:client`'s public API (`:contract` is an `implementation` dependency; `PublicApiLeakTest`
 * enforces it).
 */

/** POST body for `/stops/search`. [filter] is a stop-search filter expression the client composes. */
@Serializable
internal data class StopSearchRequest(
    val q: String,
    val filter: String? = null,
)

/**
 * The stop-search envelope. Generic over the hit type (the SDK only ever searches the stops index →
 * `StopSearchResponse<StopHit>`, but the shape is the index-agnostic search response). [query] is the backend's
 * echo of the search term. Extra envelope fields the backend adds (`processingTimeMs`, `estimatedTotalHits`,
 * …) are tolerated by the client's `ignoreUnknownKeys` decoder and intentionally not modeled.
 */
@Serializable
internal data class StopSearchResponse<T>(val hits: List<T>, val query: String)

/**
 * One stop document as indexed. [gtfsId]/[name] are always present; coordinates and the admin-geography
 * levels ([country]…[suburb]) are populated only where the deployment enriched them, so all are
 * nullable. These field names must match the index builder (`seed-stops.sh`).
 */
@Serializable
internal data class StopHit(
    val gtfsId: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val country: String? = null,
    val region: String? = null,
    val district: String? = null,
    val city: String? = null,
    val suburb: String? = null,
)

/** Stop-search error envelope, parsed on a non-2xx to surface a useful message. */
@Serializable
internal data class StopSearchError(
    val message: String,
    val code: String? = null,
    val type: String? = null,
    val link: String? = null,
)

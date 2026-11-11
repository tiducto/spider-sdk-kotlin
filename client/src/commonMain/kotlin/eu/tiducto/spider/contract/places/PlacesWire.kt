package eu.tiducto.spider.contract.places

import kotlinx.serialization.Serializable

/**
 * The place-search wire contract (`GET /places/v1/search`), hand-written like `contract.stops` and pinned
 * against `places-openapi.json` by `OpenApiPinTest`. Internal to `:client`, mapped to the public `PlaceHit`.
 */

@Serializable
internal data class PlaceSearchResponse(val hits: List<PlaceHitDto>)

/** [type] stays a raw string so a type added to the contract later decodes instead of failing the response. */
@Serializable
internal data class PlaceHitDto(
    val id: String,
    val type: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String? = null,
    val country: String? = null,
    val region: String? = null,
    val district: String? = null,
    val city: String? = null,
    val suburb: String? = null,
    val distance: Int? = null,
)

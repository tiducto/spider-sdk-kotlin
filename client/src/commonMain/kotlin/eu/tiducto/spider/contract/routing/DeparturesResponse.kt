package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * `stop` is the board, or null for an id that resolves to no stop or station.
 */
@Serializable
internal data class DeparturesResponse(
    val stop: DepartureBoard?
)

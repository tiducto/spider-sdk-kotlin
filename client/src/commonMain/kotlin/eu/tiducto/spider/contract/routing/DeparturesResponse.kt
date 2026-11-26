package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * `stop` holds the departures, or is null for an id that resolves to no stop or station.
 */
@Serializable
internal data class DeparturesResponse(
    val stop: DepartureBoard?
)

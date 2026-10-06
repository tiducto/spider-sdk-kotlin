package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * A filter on the trips the search may ride.
 */
@Serializable
internal data class TransitFilterInput(
    /** Leave out every trip of a route or agency that any of these selectors names. */
    val exclude: List<TransitFilterSelectInput>? = null
)

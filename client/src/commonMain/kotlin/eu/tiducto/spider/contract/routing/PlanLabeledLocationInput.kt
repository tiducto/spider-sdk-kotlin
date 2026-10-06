package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * An origin or destination.
 */
@Serializable
internal data class PlanLabeledLocationInput(
    val location: PlanLocationInput
)

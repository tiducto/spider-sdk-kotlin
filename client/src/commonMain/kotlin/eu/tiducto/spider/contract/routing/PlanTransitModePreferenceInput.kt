package eu.tiducto.spider.contract.routing

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

/**
 * A transit mode the search may use.
 */
@Serializable
internal data class PlanTransitModePreferenceInput(
    val mode: TransitMode,
    val cost: kotlinx.serialization.json.JsonElement? = null
)

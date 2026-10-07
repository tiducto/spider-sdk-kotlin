package eu.tiducto.spider.contract.routing

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

/**
 * Street preferences, for walking to, from and between stops.
 */
@Serializable
internal data class PlanStreetPreferencesInput(
    val walk: kotlinx.serialization.json.JsonElement? = null
)

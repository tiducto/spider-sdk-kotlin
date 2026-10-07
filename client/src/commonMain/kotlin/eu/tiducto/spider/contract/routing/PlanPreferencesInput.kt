package eu.tiducto.spider.contract.routing

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

/**
 * Routing preferences. An absent member keeps the environment's default.
 */
@Serializable
internal data class PlanPreferencesInput(
    val street: kotlinx.serialization.json.JsonElement? = null,
    val transit: kotlinx.serialization.json.JsonElement? = null,
    val accessibility: kotlinx.serialization.json.JsonElement? = null
)

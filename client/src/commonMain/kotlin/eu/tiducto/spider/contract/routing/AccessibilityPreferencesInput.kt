package eu.tiducto.spider.contract.routing

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

/**
 * Accessibility preferences.
 */
@Serializable
internal data class AccessibilityPreferencesInput(
    val wheelchair: kotlinx.serialization.json.JsonElement? = null
)

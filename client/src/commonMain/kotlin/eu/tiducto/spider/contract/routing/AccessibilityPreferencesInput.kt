package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Accessibility preferences.
 */
@Serializable
internal data class AccessibilityPreferencesInput(
    val wheelchair: WheelchairPreferencesInput? = null
)

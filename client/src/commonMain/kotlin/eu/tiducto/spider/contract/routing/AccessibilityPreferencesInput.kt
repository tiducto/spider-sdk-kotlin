package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class AccessibilityPreferencesInput(
    val wheelchair: WheelchairPreferencesInput? = null
)

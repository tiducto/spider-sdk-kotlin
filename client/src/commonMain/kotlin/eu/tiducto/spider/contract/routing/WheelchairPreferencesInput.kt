package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Wheelchair preferences.
 */
@Serializable
internal data class WheelchairPreferencesInput(
    /** Consider wheelchair accessibility in routing. The feed's accessibility data limits what this guarantees. */
    val enabled: Boolean? = null
)

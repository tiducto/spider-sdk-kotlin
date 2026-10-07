package eu.tiducto.spider.contract.routing

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

/**
 * Transit preferences.
 */
@Serializable
internal data class TransitPreferencesInput(
    val transfer: kotlinx.serialization.json.JsonElement? = null,
    val board: kotlinx.serialization.json.JsonElement? = null,
    val alight: kotlinx.serialization.json.JsonElement? = null,
    /** Routes or agencies to leave out of the search. */
    val filters: List<TransitFilterInput>? = null
)

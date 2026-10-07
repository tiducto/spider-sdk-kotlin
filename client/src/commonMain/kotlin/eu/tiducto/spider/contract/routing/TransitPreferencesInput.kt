package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Transit preferences.
 */
@Serializable
internal data class TransitPreferencesInput(
    val transfer: TransferPreferencesInput? = null,
    val board: BoardPreferencesInput? = null,
    val alight: AlightPreferencesInput? = null,
    /** Routes or agencies to leave out of the search. */
    val filters: List<TransitFilterInput>? = null
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TransferPreferencesInput(
    val cost: Int? = null,
    val maximumAdditionalTransfers: Int? = null,
    val maximumTransfers: Int? = null,
    val slack: String? = null
)

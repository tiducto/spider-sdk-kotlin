package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class BoardPreferencesInput(
    val slack: String? = null,
    val waitReluctance: Double? = null
)

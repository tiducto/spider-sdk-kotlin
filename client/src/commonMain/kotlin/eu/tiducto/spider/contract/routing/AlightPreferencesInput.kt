package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class AlightPreferencesInput(
    val slack: String? = null
)

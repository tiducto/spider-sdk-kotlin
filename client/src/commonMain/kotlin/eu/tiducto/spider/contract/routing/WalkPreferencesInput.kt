package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class WalkPreferencesInput(
    val boardCost: Int? = null,
    val reluctance: Double? = null,
    val safetyFactor: Double? = null,
    val speed: Double? = null
)

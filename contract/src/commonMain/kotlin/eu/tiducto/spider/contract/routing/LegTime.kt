package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class LegTime(
    val scheduledTime: String
)

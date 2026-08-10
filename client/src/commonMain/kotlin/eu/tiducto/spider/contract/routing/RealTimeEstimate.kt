package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class RealTimeEstimate(
    val time: String,
    val delay: String
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class LegTime(
    val scheduledTime: String,
    /** Null without realtime. */
    val estimated: RealTimeEstimate? = null
)

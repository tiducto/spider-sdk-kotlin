package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class RealTimeEstimate(
    val time: String,
    /** The difference from the schedule, as an ISO-8601 duration such as `PT2M`; negative when early, such as `-PT1M`. */
    val delay: String
)

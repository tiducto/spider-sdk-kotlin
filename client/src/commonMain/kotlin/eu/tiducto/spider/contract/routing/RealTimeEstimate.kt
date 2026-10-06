package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class RealTimeEstimate(
    val time: String,
    /** The difference from the schedule, as an ISO-8601 duration; negative when early. */
    val delay: String
)

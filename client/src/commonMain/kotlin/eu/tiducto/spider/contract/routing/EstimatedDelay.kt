package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class EstimatedDelay(
    val reliability: Reliability,
    /** Seconds: the level's percentile of the trip's recorded delay at this stop on the service date's day type, from the environment's realtime history; never below 0. */
    val delay: Int
)

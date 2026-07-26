package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Stop(
    val gtfsId: String,
    val name: String,
    val wheelchairBoarding: WheelchairBoarding? = null,
    val stoptimesWithoutPatterns: List<Stoptime>? = null
)

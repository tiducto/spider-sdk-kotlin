package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class Stop(
    val gtfsId: String,
    val wheelchairBoarding: WheelchairBoarding? = null
)

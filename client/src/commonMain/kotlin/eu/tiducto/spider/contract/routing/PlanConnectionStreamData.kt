package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanConnectionStreamData(
    val planConnection: PlanConnection? = null
)

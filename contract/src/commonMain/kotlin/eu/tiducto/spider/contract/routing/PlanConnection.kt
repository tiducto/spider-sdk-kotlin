package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
data class PlanConnection(
    val pageInfo: PlanPageInfo,
    val routingErrors: List<RoutingError>,
    val edges: List<PlanEdge>? = null,
    val searchDateTime: String? = null
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanConnection(
    val pageInfo: PlanPageInfo,
    val routingErrors: List<RoutingError>,
    val edges: List<PlanEdge>? = null,
    val searchDateTime: String? = null
)

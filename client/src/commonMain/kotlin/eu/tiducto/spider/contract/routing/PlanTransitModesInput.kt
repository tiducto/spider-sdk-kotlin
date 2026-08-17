package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanTransitModesInput(
    val access: List<PlanAccessMode>? = null,
    val egress: List<PlanEgressMode>? = null,
    val transfer: List<PlanTransferMode>? = null,
    val transit: List<PlanTransitModePreferenceInput>? = null
)

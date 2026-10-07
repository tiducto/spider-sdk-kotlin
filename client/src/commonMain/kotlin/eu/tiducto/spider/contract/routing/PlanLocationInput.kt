package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Exactly one of `coordinate`, `stopLocation`; neither or both is a 400 naming `origin.location` or `destination.location`.
 */
@Serializable
internal data class PlanLocationInput(
    /** A point; the journey walks between it and the stops. */
    val coordinate: PlanCoordinateInput? = null,
    /** A stop or a station. */
    val stopLocation: PlanStopLocationInput? = null
)

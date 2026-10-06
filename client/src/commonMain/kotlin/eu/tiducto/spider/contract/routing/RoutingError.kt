package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Why the router declined the plan: `LOCATION_NOT_FOUND` for an unknown stop id (`inputField` `FROM`, `TO` or `VIA`), `WALKING_BETTER_THAN_TRANSIT` when the origin and destination are close enough that walking beats transit (`inputField` null), or `OUTSIDE_SERVICE_PERIOD` for a date the feed does not cover (`DATE_TIME`). New codes may be added.
 */
@Serializable
internal data class RoutingError(
    val code: RoutingErrorCode,
    val description: String,
    /** The request member at fault; null when it is none in particular. */
    val inputField: InputField? = null
)

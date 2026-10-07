package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Exactly one of `earliestDeparture`, `latestArrival`; neither or both is a 400 `dateTime is invalid`. Both are RFC 3339 date-times with seconds and an offset, e.g. `2026-10-07T08:00:00+02:00`.
 */
@Serializable
internal data class PlanDateTimeInput(
    /** Depart at or after this time. */
    val earliestDeparture: String? = null,
    /** Arrive at or before this time. */
    val latestArrival: String? = null
)

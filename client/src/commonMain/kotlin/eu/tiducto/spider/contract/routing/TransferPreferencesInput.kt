package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

/**
 * Transfer preferences.
 */
@Serializable
internal data class TransferPreferencesInput(
    /** Generalized cost added for each transfer, an integer from 0 to 1000000; rejected, never clamped. */
    val cost: Int? = null,
    /** Least time between alighting one vehicle and boarding the next, on top of the walk between stops, as an ISO-8601 duration: `PT0S` to `PT1H`; rejected, never clamped. */
    val slack: String? = null,
    /** Most rides an itinerary may take: `N` allows `N - 1` transfers, and `0` allows walking only. From 0 up to the environment's transfer limit plus 1; rejected, never clamped. Absent means the environment's limit. */
    val maximumTransfers: Int? = null
)

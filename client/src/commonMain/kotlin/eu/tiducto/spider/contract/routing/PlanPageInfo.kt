package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanPageInfo(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    /** Send as `before` for the previous page; null when there is none. */
    val startCursor: String? = null,
    /** Send as `after` for the next page; null when there is none. */
    val endCursor: String? = null,
    /** The window the search covered, as an ISO-8601 duration; null for a declined plan. */
    val searchWindowUsed: String? = null
)

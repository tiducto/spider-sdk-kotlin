package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class PlanPageInfo(
    /** Send as `before` for the previous page; null when there is none, as for a declined, direct-only or unroutable plan. */
    val startCursor: String?,
    /** Send as `after` for the next page; null when there is none, as for a declined, direct-only or unroutable plan. */
    val endCursor: String?,
    /** True exactly when `endCursor` is present. */
    val hasNextPage: Boolean,
    /** True exactly when `startCursor` is present. */
    val hasPreviousPage: Boolean,
    /** The window the search covered, as an ISO-8601 duration; null when no transit search ran, as for a declined, direct-only or unroutable plan. */
    val searchWindowUsed: String?
)

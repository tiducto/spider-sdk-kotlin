package cz.davidkurzica.contract.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanPageInfo(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String? = null,
    val endCursor: String? = null,
    val searchWindowUsed: String? = null
)

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.Serializable

@Serializable
internal data class TimetablePreferencesInput(
    val excludeRealTimeUpdates: Boolean? = null,
    val includePlannedCancellations: Boolean? = null,
    val includeRealTimeCancellations: Boolean? = null
)

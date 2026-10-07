package eu.tiducto.spider.contract.routing

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable

@Serializable
internal data class LegTime(
    val scheduledTime: String,
    /** Null without realtime. */
    val estimated: kotlinx.serialization.json.JsonElement
)

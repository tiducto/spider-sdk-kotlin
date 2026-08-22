package eu.tiducto.spider.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RealtimeStateSerializer::class)
internal enum class RealtimeState(val value: String) {
    @SerialName("ADDED") ADDED("ADDED"),
    @SerialName("CANCELED") CANCELED("CANCELED"),
    @SerialName("MODIFIED") MODIFIED("MODIFIED"),
    @SerialName("SCHEDULED") SCHEDULED("SCHEDULED"),
    @SerialName("UPDATED") UPDATED("UPDATED"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object RealtimeStateSerializer : KSerializer<RealtimeState> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): RealtimeState {
        val raw = decoder.decodeString()
        return RealtimeState.entries.firstOrNull { it.value == raw } ?: RealtimeState.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: RealtimeState) {
        encoder.encodeString(value.value)
    }
}

package eu.tiducto.spider.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Delay level: `STANDARD`, `SAFE` and `VERY_SAFE` are the p50, p70 and p90 of the trip's recorded delay at a stop, on the service date's day type, from the environment's realtime history. Omitted means the timetable, on every operation.
 */
@Serializable(with = ReliabilitySerializer::class)
internal enum class Reliability(val value: String) {
    @SerialName("STANDARD") STANDARD("STANDARD"),
    @SerialName("SAFE") SAFE("SAFE"),
    @SerialName("VERY_SAFE") VERY_SAFE("VERY_SAFE"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object ReliabilitySerializer : KSerializer<Reliability> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): Reliability {
        val raw = decoder.decodeString()
        return Reliability.entries.firstOrNull { it.value == raw } ?: Reliability.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: Reliability) {
        encoder.encodeString(value.value)
    }
}

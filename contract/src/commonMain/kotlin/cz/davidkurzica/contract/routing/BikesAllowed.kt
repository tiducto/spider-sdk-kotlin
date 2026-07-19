package cz.davidkurzica.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = BikesAllowedSerializer::class)
enum class BikesAllowed(val value: String) {
    @SerialName("ALLOWED") ALLOWED("ALLOWED"),
    @SerialName("NOT_ALLOWED") NOT_ALLOWED("NOT_ALLOWED"),
    @SerialName("NO_INFORMATION") NO_INFORMATION("NO_INFORMATION"),
    @SerialName("unknown_default_open_api") UNKNOWN_DEFAULT_OPEN_API("unknown_default_open_api");
}

internal object BikesAllowedSerializer : KSerializer<BikesAllowed> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): BikesAllowed {
        val raw = decoder.decodeString()
        return BikesAllowed.entries.firstOrNull { it.value == raw } ?: BikesAllowed.UNKNOWN_DEFAULT_OPEN_API
    }
    override fun serialize(encoder: Encoder, value: BikesAllowed) {
        encoder.encodeString(value.value)
    }
}

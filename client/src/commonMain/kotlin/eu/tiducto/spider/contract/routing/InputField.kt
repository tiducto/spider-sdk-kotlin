package eu.tiducto.spider.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = InputFieldSerializer::class)
internal enum class InputField(val value: String) {
    @SerialName("DATE_TIME") DATE_TIME("DATE_TIME"),
    @SerialName("FROM") FROM("FROM"),
    @SerialName("TO") TO("TO"),
    @SerialName("VIA") VIA("VIA"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object InputFieldSerializer : KSerializer<InputField> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): InputField {
        val raw = decoder.decodeString()
        return InputField.entries.firstOrNull { it.value == raw } ?: InputField.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: InputField) {
        encoder.encodeString(value.value)
    }
}

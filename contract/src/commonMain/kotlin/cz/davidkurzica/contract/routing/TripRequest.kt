package cz.davidkurzica.contract.routing

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class TripRequest(
    /** Persisted-query id (lowercase hex SHA-256 of the canonical query). */
    val id: Id,
    val variables: TripVariables
) {

    @Serializable(with = IdSerializer::class)
    enum class Id(val value: String) {
        @SerialName("e8959a8d47a8e8437ee3ec740cd9c3e28bd401efdd236dde0502559daea53920") E8959A8D47A8E8437EE3EC740CD9C3E28BD401EFDD236DDE0502559DAEA53920("e8959a8d47a8e8437ee3ec740cd9c3e28bd401efdd236dde0502559daea53920"),
        @SerialName("unknown_default_open_api") UNKNOWN_DEFAULT_OPEN_API("unknown_default_open_api");
    }

    internal object IdSerializer : KSerializer<Id> {
        override val descriptor = String.serializer().descriptor
        override fun deserialize(decoder: Decoder): Id {
            val raw = decoder.decodeString()
            return Id.entries.firstOrNull { it.value == raw } ?: Id.UNKNOWN_DEFAULT_OPEN_API
        }
        override fun serialize(encoder: Encoder, value: Id) {
            encoder.encodeString(value.value)
        }
    }
}

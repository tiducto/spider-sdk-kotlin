package eu.tiducto.spider.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Whether and how a rider boards (`pickupType`) or alights (`dropoffType`) at a stop, from the GTFS `pickup_type` / `drop_off_type`: `SCHEDULED` regularly (0, or the field left empty), `NONE` not at all (1), `CALL_AGENCY` only after phoning the agency (2), `COORDINATE_WITH_DRIVER` only by arranging it with the driver, a stop on request (3).
 */
@Serializable(with = PickupDropoffTypeSerializer::class)
internal enum class PickupDropoffType(val value: String) {
    @SerialName("CALL_AGENCY") CALL_AGENCY("CALL_AGENCY"),
    @SerialName("COORDINATE_WITH_DRIVER") COORDINATE_WITH_DRIVER("COORDINATE_WITH_DRIVER"),
    @SerialName("NONE") NONE("NONE"),
    @SerialName("SCHEDULED") SCHEDULED("SCHEDULED"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object PickupDropoffTypeSerializer : KSerializer<PickupDropoffType> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): PickupDropoffType {
        val raw = decoder.decodeString()
        return PickupDropoffType.entries.firstOrNull { it.value == raw } ?: PickupDropoffType.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: PickupDropoffType) {
        encoder.encodeString(value.value)
    }
}

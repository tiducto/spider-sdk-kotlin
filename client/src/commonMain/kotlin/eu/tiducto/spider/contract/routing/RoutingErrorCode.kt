package eu.tiducto.spider.contract.routing

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RoutingErrorCodeSerializer::class)
internal enum class RoutingErrorCode(val value: String) {
    @SerialName("LOCATION_NOT_FOUND") LOCATION_NOT_FOUND("LOCATION_NOT_FOUND"),
    @SerialName("NO_STOPS_IN_RANGE") NO_STOPS_IN_RANGE("NO_STOPS_IN_RANGE"),
    @SerialName("NO_TRANSIT_CONNECTION") NO_TRANSIT_CONNECTION("NO_TRANSIT_CONNECTION"),
    @SerialName("NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW") NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW("NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW"),
    @SerialName("OUTSIDE_BOUNDS") OUTSIDE_BOUNDS("OUTSIDE_BOUNDS"),
    @SerialName("OUTSIDE_SERVICE_PERIOD") OUTSIDE_SERVICE_PERIOD("OUTSIDE_SERVICE_PERIOD"),
    @SerialName("WALKING_BETTER_THAN_TRANSIT") WALKING_BETTER_THAN_TRANSIT("WALKING_BETTER_THAN_TRANSIT"),
    @SerialName("UNKNOWN") UNKNOWN("UNKNOWN");
}

internal object RoutingErrorCodeSerializer : KSerializer<RoutingErrorCode> {
    override val descriptor = String.serializer().descriptor
    override fun deserialize(decoder: Decoder): RoutingErrorCode {
        val raw = decoder.decodeString()
        return RoutingErrorCode.entries.firstOrNull { it.value == raw } ?: RoutingErrorCode.UNKNOWN
    }
    override fun serialize(encoder: Encoder, value: RoutingErrorCode) {
        encoder.encodeString(value.value)
    }
}

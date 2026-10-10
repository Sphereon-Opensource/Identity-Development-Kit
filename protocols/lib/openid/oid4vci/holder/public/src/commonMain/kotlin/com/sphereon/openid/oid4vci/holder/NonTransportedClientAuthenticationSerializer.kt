package com.sphereon.openid.oid4vci.holder

import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Lets a holder command argument that names token-endpoint client authentication travel over a
 * command transport only when no authentication material is set.
 *
 * [ClientAuthenticationConfig] carries client secrets and signing references. They never leave the
 * process: an absent value encodes as `null`, and any present value is refused in both directions.
 */
object NonTransportedClientAuthenticationSerializer : KSerializer<ClientAuthenticationConfig> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.sphereon.openid.oid4vci.holder.NonTransportedClientAuthentication", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ClientAuthenticationConfig): Nothing =
        throw SerializationException("Token endpoint client authentication is never serialized")

    override fun deserialize(decoder: Decoder): ClientAuthenticationConfig =
        throw SerializationException("Token endpoint client authentication is never deserialized")
}

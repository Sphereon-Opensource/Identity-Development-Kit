package com.sphereon.oauth2.server.authorization.command.discovery

import com.sphereon.core.api.json.jsonSerializer
import com.sphereon.oauth2.common.config.TokenFormat
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.ObserveHostedServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.ObservedAsSigningDescriptor
import com.sphereon.oauth2.server.authorization.command.ObservedHostedServerMetadata
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Public remote payload shape only; this does not prove authentication or source currentness. */
class HostedServerMetadataSerializationTest {
    @Test
    fun exactHostedSelectionArgsRoundTripThroughSharedSerializer() {
        val args = ObserveHostedServerMetadataArgs(
            serverKey = "hosted-slug",
            effectiveIssuer = "https://as.example:8443/Tenant/%2F",
        )

        val wire = jsonSerializer.encodeToString(ObserveHostedServerMetadataArgs.serializer(), args)
        val decoded = jsonSerializer.decodeFromString(ObserveHostedServerMetadataArgs.serializer(), wire)
        val fields = jsonSerializer.parseToJsonElement(wire).jsonObject

        assertEquals(args, decoded)
        assertEquals("hosted-slug", fields["serverKey"]?.jsonPrimitive?.content)
        assertEquals("https://as.example:8443/Tenant/%2F", fields["effectiveIssuer"]?.jsonPrimitive?.content)
    }

    @Test
    fun jwtObservationPreservesTypedMetadataPublicDescriptorAndFingerprints() {
        val observed = ObservedHostedServerMetadata(
            serverKey = "hosted-slug",
            effectiveIssuer = "https://as.example:8443/Tenant/%2F",
            tokenFormat = TokenFormat.JWT,
            oidcEnabled = true,
            metadata = metadata(),
            signingDescriptor = ObservedAsSigningDescriptor(kid = "public-kid-7", algorithm = "ES256"),
            metadataFingerprint = "metadata-fingerprint-1",
            contextFingerprint = "context-fingerprint-2",
            signingDescriptorFingerprint = "descriptor-fingerprint-3",
        )

        val wire = jsonSerializer.encodeToString(ObservedHostedServerMetadata.serializer(), observed)
        val decoded = jsonSerializer.decodeFromString(ObservedHostedServerMetadata.serializer(), wire)
        val fields = jsonSerializer.parseToJsonElement(wire).jsonObject
        val metadata = fields.getValue("metadata").jsonObject
        val descriptor = fields.getValue("signingDescriptor").jsonObject

        assertEquals(observed, decoded)
        assertEquals("hosted-slug", decoded.serverKey)
        assertEquals("https://as.example:8443/Tenant/%2F", decoded.effectiveIssuer)
        assertEquals(TokenFormat.JWT, decoded.tokenFormat)
        assertEquals("https://as.example:8443/Tenant/%2F", metadata["issuer"]?.jsonPrimitive?.content)
        assertEquals("https://as.example:8443/Tenant/%2F/token", metadata["token_endpoint"]?.jsonPrimitive?.content)
        assertEquals(listOf("code"), metadata["response_types_supported"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals(listOf("openid", "profile"), metadata["scopes_supported"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals("public-kid-7", descriptor["kid"]?.jsonPrimitive?.content)
        assertEquals("ES256", descriptor["algorithm"]?.jsonPrimitive?.content)
        assertEquals(setOf("kid", "algorithm"), descriptor.keys)
        assertEquals("metadata-fingerprint-1", fields["metadataFingerprint"]?.jsonPrimitive?.content)
        assertEquals("context-fingerprint-2", fields["contextFingerprint"]?.jsonPrimitive?.content)
        assertEquals("descriptor-fingerprint-3", fields["signingDescriptorFingerprint"]?.jsonPrimitive?.content)
        assertEquals(JsonPrimitive("https://as.example/terms"), decoded.metadata.additionalMetadata["terms_of_service"])
    }

    @Test
    fun opaqueObservationPreservesUnsignedMetadataAndNullableDescriptor() {
        val observed = ObservedHostedServerMetadata(
            serverKey = "opaque-hosted",
            effectiveIssuer = "https://opaque.example/AS",
            tokenFormat = TokenFormat.OPAQUE,
            oidcEnabled = false,
            metadata = AuthorizationServerMetadata(
                issuer = "https://opaque.example/AS",
                tokenEndpoint = "https://opaque.example/AS/token",
                authorizationEndpoint = "https://opaque.example/AS/authorize",
                grantTypesSupported = listOf("authorization_code"),
                responseTypesSupported = listOf("code"),
            ),
            signingDescriptor = null,
            metadataFingerprint = "metadata-fingerprint-opaque",
            contextFingerprint = "context-fingerprint-opaque",
            signingDescriptorFingerprint = null,
        )

        val wire = jsonSerializer.encodeToString(ObservedHostedServerMetadata.serializer(), observed)
        val decoded = jsonSerializer.decodeFromString(ObservedHostedServerMetadata.serializer(), wire)
        val fields = jsonSerializer.parseToJsonElement(wire).jsonObject

        assertEquals(observed, decoded)
        assertEquals(TokenFormat.OPAQUE, decoded.tokenFormat)
        assertFalse(decoded.oidcEnabled)
        assertNull(decoded.signingDescriptor)
        assertNull(decoded.signingDescriptorFingerprint)
        assertNull(decoded.metadata.signedMetadata)
        assertEquals("https://opaque.example/AS", fields["effectiveIssuer"]?.jsonPrimitive?.content)
        assertEquals("metadata-fingerprint-opaque", fields["metadataFingerprint"]?.jsonPrimitive?.content)
        assertEquals("context-fingerprint-opaque", fields["contextFingerprint"]?.jsonPrimitive?.content)
        assertEquals(listOf("authorization_code"), fields.getValue("metadata").jsonObject["grant_types_supported"]?.jsonArray?.map { it.jsonPrimitive.content })
    }

    private fun metadata() = AuthorizationServerMetadata(
        issuer = "https://as.example:8443/Tenant/%2F",
        tokenEndpoint = "https://as.example:8443/Tenant/%2F/token",
        authorizationEndpoint = "https://as.example:8443/Tenant/%2F/authorize",
        jwksUri = "https://as.example:8443/Tenant/%2F/.well-known/jwks.json",
        grantTypesSupported = listOf("authorization_code", "refresh_token"),
        responseTypesSupported = listOf("code"),
        scopesSupported = listOf("openid", "profile"),
        idTokenSigningAlgValuesSupported = listOf("ES256"),
        additionalMetadata = mapOf("terms_of_service" to JsonPrimitive("https://as.example/terms")),
    )
}

package com.sphereon.crypto.kms.rest.api.command

import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.certificate.persistence.CertificateReferenceKind
import com.sphereon.crypto.certificate.persistence.CertificateReferenceSource
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.kms.rest.api.generated.infrastructure.Base64ByteArray
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmsCertificateReferenceCommandsTest {
    @Test
    fun certificateReferenceWireMaterialUsesBase64StringWrappers() {
        val request = RegisterCertificateReferenceInput(
            providerId = "provider-1",
            alias = "certificate-1",
            kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
            source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
            linkedKeyAlias = "key-1",
            certificateChain = listOf(Base64ByteArray(byteArrayOf(1, 2))),
        )

        val encoded = Json.encodeToString(request)

        assertTrue(encoded.contains("\"certificateChain\":[\"AQI=\"]"))
        assertFalse(encoded.contains("\"certificateChain\":[1,2]"))
    }

    @Test
    fun registrationCommandIsBoundToLiteralBeforeAliasRoute() {
        assertEquals("kms.certificates.register", RegisterCertificateReferenceServiceCommand.COMMAND_ID)
        assertEquals("/certificates/register", RegisterCertificateReferenceServiceCommand.ENDPOINT.pathPattern)
    }

    @Test
    fun certificateReferenceResponseRequiresExplicitOrigin() {
        val encoded = Json.encodeToString(
            CertificateReferenceResponse(
                id = "id-1",
                alias = "certificate-1",
                providerId = "provider-1",
                kind = CertificateReferenceKind.TRUSTED_CERTIFICATE,
                source = CertificateReferenceSource.PROVIDER_NATIVE,
                controlMode = ResourceControlMode.PLATFORM_MANAGED,
                origin = Origin.MANAGED,
                certificateFingerprint = Base64ByteArray(ByteArray(32)),
                publicKeyFingerprint = Base64ByteArray(ByteArray(32)),
            ),
        )
        val withoutOrigin = encoded.replace(Regex(",?\\\"origin\\\":\\\"[^\\\"]+\\\""), "")

        assertFailsWith<SerializationException> {
            Json.decodeFromString<CertificateReferenceResponse>(withoutOrigin)
        }
    }

    @Test
    fun certificateReferenceMetadataResponseExposesOnlyThePublicProjectionFields() {
        val encoded = Json.parseToJsonElement(
            Json.encodeToString(
                CertificateReferenceMetadataResponse(
                    id = "id-1",
                    alias = "certificate-1",
                    providerId = "provider-1",
                    providerCertificateId = "provider-certificate-1",
                    kind = CertificateReferenceKind.KEY_CERTIFICATE_CHAIN,
                    source = CertificateReferenceSource.STORED_PUBLIC_MATERIAL,
                    controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                    origin = Origin.EXTERNAL,
                    linkedKeyReferenceId = "key-reference-1",
                    linkedKeyAlias = "key-alias-1",
                    linkedKeyKid = "key-kid-1",
                    certificateFingerprint = Base64ByteArray(ByteArray(32) { 1 }),
                    publicKeyFingerprint = Base64ByteArray(ByteArray(32) { 2 }),
                ),
            ),
        ).jsonObject

        assertEquals(
            setOf(
                "id",
                "alias",
                "providerId",
                "providerCertificateId",
                "kind",
                "source",
                "controlMode",
                "origin",
                "linkedKeyReferenceId",
                "linkedKeyAlias",
                "linkedKeyKid",
                "certificateFingerprint",
                "publicKeyFingerprint",
            ),
            encoded.keys,
        )
        assertFalse(encoded.containsKey("certificateChain"))
        assertFalse(encoded.containsKey("tenantId"))
        assertFalse(encoded.containsKey("deletedAt"))
    }

    @Test
    fun certificateReferenceProjectionCommandsUseDedicatedListAndDetailRoutes() {
        assertEquals("kms.certificate-references.list", ListCertificateReferencesServiceCommand.COMMAND_ID)
        assertEquals("/certificate-references", ListCertificateReferencesServiceCommand.ENDPOINT.pathPattern)
        assertEquals("kms.certificate-references.get", GetCertificateReferenceServiceCommand.COMMAND_ID)
        assertEquals("/certificate-references/{id}", GetCertificateReferenceServiceCommand.ENDPOINT.pathPattern)
    }
}

/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vc.common.vcdm

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Structural vectors copied from the W3C VCDM 2.0 test suite.
 *
 * The upstream issuer tests submit intentionally sparse input documents and let an issuer add
 * issuer/validity data.  Consequently, positive credential inputs are exercised through
 * [VcdmClassifier] (context, type, and shape), while the profile assertions use the upstream
 * negative inputs that are complete enough to reach the corresponding data-model rule.
 */
class W3cVcdm2DataModelVectorTest {
    @Test
    fun `vendored official vectors match their pinned upstream hashes`() {
        val manifest = resourceBytes("SHA256SUMS").decodeToString()
        manifest.lineSequence().filter(String::isNotBlank).forEach { line ->
            val (expected, name) = line.trim().split(Regex("\\s+"), limit = 2)
            val actual = MessageDigest.getInstance("SHA-256").digest(resourceBytes(name)).toHex()
            assertEquals(expected, actual, "pinned W3C vector hash mismatch: $name")
        }
    }

    @Test
    fun `official positive credential inputs classify as VCDM 2 credentials`() {
        positiveCredentialInputs.forEach { name ->
            val result = VcdmClassifier.classifyDocument(fixture(name))

            assertTrue(result.isOk, "official positive credential input $name must classify")
            assertEquals(VcdmVersion.V2_0, result.value.version, name)
            assertEquals(VcdmDocumentKind.CREDENTIAL, result.value.kind, name)
        }
    }

    @Test
    fun `official negative credential vectors are rejected by the VCDM 2 profile`() {
        negativeCredentialInputs.forEach { vector ->
            val result = VcdmProfiles.v2_0.validateCredential(fixture(vector.name))

            assertFalse(result.valid, "official negative credential input ${vector.name} must be invalid")
            assertExpectedFailure(result.errors, vector)
        }
    }

    @Test
    fun `official positive presentation vectors validate including multiple credentials and an envelope`() {
        positivePresentationInputs.forEach { name ->
            val document = fixture(name)
            val classification = VcdmClassifier.classifyDocument(document)

            assertTrue(classification.isOk, "official positive presentation input $name must classify")
            assertEquals(VcdmVersion.V2_0, classification.value.version, name)
            assertEquals(VcdmDocumentKind.PRESENTATION, classification.value.kind, name)
            assertTrue(
                VcdmProfiles.v2_0.validatePresentation(document).valid,
                "official positive presentation input $name must validate",
            )
        }
    }

    @Test
    fun `official negative presentation vectors are rejected by the VCDM 2 profile`() {
        negativePresentationInputs.forEach { vector ->
            val result = VcdmProfiles.v2_0.validatePresentation(fixture(vector.name))

            assertFalse(result.valid, "official negative presentation input ${vector.name} must be invalid")
            assertExpectedFailure(result.errors, vector)
        }
    }

    @Test
    fun `derived nested presentation envelope validates with an explicit provenance boundary`() {
        // The pinned suite has an official EnvelopedVerifiableCredential input but no nested
        // EnvelopedVerifiablePresentation input. This object is therefore a derived shape-only
        // mutation; the child id is intentionally not presented as an official signed vector.
        val nested = buildJsonObject {
            put("@context", VcdmProfiles.V2_0_CONTEXT)
            put("type", "VerifiablePresentation")
            put(
                "verifiableCredential",
                buildJsonObject {
                    put("@context", VcdmProfiles.V2_0_CONTEXT)
                    put("type", "EnvelopedVerifiablePresentation")
                    put("id", "data:application/vp+jwt,derived-from-presentation-enveloped-vc-ok")
                },
            )
        }

        assertTrue(VcdmProfiles.v2_0.validatePresentation(nested).valid)
        val classification = VcdmClassifier.classifyDocument(nested)
        assertTrue(classification.isOk)
        assertEquals(VcdmDocumentKind.PRESENTATION, classification.value.kind)
    }

    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(resourceBytes(name).decodeToString()).jsonObject

    private fun resourceBytes(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/w3c-vcdm2-data-model/$name")) {
            "missing vendored W3C vector: $name"
        }.use { it.readBytes() }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun assertExpectedFailure(errors: List<VcdmError>, vector: NegativeVector) {
        if (vector.property != null) {
            assertTrue(
                errors.any { it is VcdmError.InvalidProperty && it.property == vector.property },
                "official negative input ${vector.name} must report ${vector.property}; got $errors",
            )
        }
        if (vector.errorCode != null) {
            assertTrue(
                errors.any { it.code == vector.errorCode },
                "official negative input ${vector.name} must report ${vector.errorCode}; got $errors",
            )
        }
    }

    private companion object {
        data class NegativeVector(
            val name: String,
            val property: String? = null,
            val errorCode: String? = null,
        )

        val positiveCredentialInputs = listOf(
            "credential-ok.json",
            "credential-context-combo2-ok.json",
            "credential-type-url-ok.json",
            "credential-type-mapped-url-ok.json",
            "credential-validuntil-ok.json",
            "credential-status-ok.json",
            "credential-schema-ok.json",
            "credential-multi-language-name-ok.json",
        )

        val negativeCredentialInputs = listOf(
            NegativeVector("credential-no-context-fail-or-inject.json", errorCode = "VCDM_MISSING_BASE_CONTEXT"),
            NegativeVector("credential-context-combo3-fail.json", property = "@context"),
            NegativeVector("credential-no-type-fail.json", errorCode = "VCDM_UNSUPPORTED_DOCUMENT_KIND"),
            NegativeVector("credential-issuer-no-url-fail.json", property = "issuer"),
            NegativeVector("credential-subject-no-claims-fail.json", property = "credentialSubject"),
            NegativeVector("credential-validuntil-invalid-fail.json", property = "validUntil"),
            NegativeVector("credential-status-missing-type-fail.json", property = "credentialStatus"),
            NegativeVector("credential-schema-no-id-fail.json", property = "credentialSchema"),
            NegativeVector("credential-name-extra-prop-en-fail.json", property = "name"),
        )

        val positivePresentationInputs = listOf(
            "presentation-ok.json",
            "presentation-holder-object-ok.json",
            "presentation-multiple-vc-ok.json",
            "presentation-context-combo2-ok.json",
            "presentation-enveloped-vc-ok.json",
        )

        val negativePresentationInputs = listOf(
            NegativeVector("presentation-no-type-fail.json", errorCode = "VCDM_UNSUPPORTED_DOCUMENT_KIND"),
            NegativeVector("presentation-context-order-fail.json", property = "@context"),
            NegativeVector("presentation-vc-as-string-fail.json", property = "verifiableCredential"),
            NegativeVector("presentation-enveloped-vc-missing-type-fail.json", property = "verifiableCredential"),
        )
    }
}

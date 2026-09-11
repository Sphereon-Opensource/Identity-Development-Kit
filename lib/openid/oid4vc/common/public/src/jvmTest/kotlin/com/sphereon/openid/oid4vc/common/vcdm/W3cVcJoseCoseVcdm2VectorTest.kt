/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vc.common.vcdm

import com.sphereon.openid.oid4vc.common.CredentialFormat
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * W3C VC JOSE/COSE interoperability vectors pinned in the adjacent resource directory.
 *
 * These tests intentionally stop at the common classifier boundary.  The classifier parses
 * protected headers and VCDM shapes, while issuer-key trust and signature verification belong to
 * the verifier integration tests (which must provide real identifier-service resolution).
 */
class W3cVcJoseCoseVcdm2VectorTest {
    @Test
    fun `vendored official jose vectors match pinned logical-content hashes`() {
        val manifest = resourceText("SHA256SUMS")
        manifest.lineSequence().filter(String::isNotBlank).forEach { line ->
            val (expected, name) = line.trim().split(Regex("\\s+"), limit = 2)
            val logicalBytes = resourceText(name).trim().encodeToByteArray()
            val actual = MessageDigest.getInstance("SHA-256").digest(logicalBytes).toHex()
            assertEquals(expected, actual, "pinned W3C JOSE vector hash mismatch: $name")
        }
    }

    @Test
    fun `official vcdm 2 credential is a direct root jose payload`() {
        val classification = classify("credential-jose-minimal.txt")

        assertEquals(VcdmVersion.V2_0, classification.document.version)
        assertEquals(VcdmDocumentKind.CREDENTIAL, classification.document.kind)
        assertEquals(CredentialFormat.JWT_VC_JSON_LD, classification.credentialFormat)
        assertEquals(null, classification.presentationFormat)
        assertEquals("vc+jwt", classification.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("vc", classification.protectedHeader["cty"]?.jsonPrimitive?.content)
        assertFalse(classification.document.json.containsKey("vc"))
        assertFalse(classification.document.json.containsKey("vp"))
        assertEquals("https://www.w3.org/ns/credentials/v2", baseContext(classification.document.json))
    }

    /**
     * Test 15 in the pinned W3C mapping is the normative forbidden-claim vector.  A VCDM 2 JOSE
     * payload is a direct root document; accepting a JWT `vc` or `vp` claim would cross the
     * protocol/document boundary and must fail closed at classification.
     */
    @Test
    fun `official vcdm 2 credential rejects forbidden vc and vp claims`() {
        val result = VcdmClassifier.classifyCompactJws(fixture("credential-jose-vc-vp-claims.txt"))

        assertTrue(result.isErr, "VCDM 2 JOSE must reject JWT vc/vp claims")
    }

    @Test
    fun `official mapping case 8 accepts a credential whose issuer and iss match`() {
        val classification = classify("credential-issuer-match-signed.txt")

        assertEquals(VcdmDocumentKind.CREDENTIAL, classification.document.kind)
        assertEquals(
            "https://example.issuer/vc-jose-cose",
            classification.document.json["issuer"]?.jsonPrimitive?.content,
        )
        assertEquals(
            classification.document.json["issuer"]?.jsonPrimitive?.content,
            classification.rawPayload["iss"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `official mapping case 13 rejects an invalid credential media type`() {
        val result = VcdmClassifier.classifyCompactJws(fixture("credential-jose-bad-media-type.txt"))

        assertTrue(result.isErr, "invalid VCDM 2 JOSE typ and cty values must be rejected")
    }

    @Test
    fun `official mapping case 16 rejects a presentation with an invalid credential child`() {
        val classification = classify("presentation-jose-bad-credential.txt")
        val validation = VcdmProfiles.v2_0.validatePresentation(classification.document.json)

        assertFalse(validation.valid, "VCDM 2 presentations must reject invalid credential children")
    }

    @Test
    fun `official mapping cases 10 and 11 reject unsecured JSON as compact JWS`() {
        assertTrue(
            VcdmClassifier.classifyCompactJws(resourceText("credential-minimal.json").trim()).isErr,
            "an unsecured credential must not be accepted as a compact JWS",
        )
        assertTrue(
            VcdmClassifier.classifyCompactJws(resourceText("presentation-single.json").trim()).isErr,
            "an unsecured presentation must not be accepted as a compact JWS",
        )
    }

    /**
     * Mapping case 9 is expected to fail upstream, but its signed fixture has an ES512/P-521
     * header and no unknown extension member. It is therefore retained for provenance only and
     * is not asserted as evidence of the specification's unknown-extension ignore rule.
     */
    @Test
    fun `official mapping case 9 does not claim unknown extension conformance`() {
        val classification = classify("credential-jose-unknown-extensions.txt")

        assertEquals("ES512", classification.protectedHeader["alg"]?.jsonPrimitive?.content)
        assertFalse(classification.rawPayload.containsKey("badExtension"))
        assertFalse(classification.rawPayload.containsKey("anotherBadOne"))
    }

    @Test
    fun `official vcdm 2 presentation uses jose headers and enveloped children`() {
        val classification = classify("presentation-jose-multiple.txt")

        assertEquals(VcdmVersion.V2_0, classification.document.version)
        assertEquals(VcdmDocumentKind.PRESENTATION, classification.document.kind)
        assertEquals("vp+jwt", classification.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("vp", classification.protectedHeader["cty"]?.jsonPrimitive?.content)
        assertFalse(classification.document.json.containsKey("vc"))
        assertFalse(classification.document.json.containsKey("vp"))

        val children = assertNotNull(classification.document.json["verifiableCredential"]?.jsonArray)
        assertEquals(3, children.size, "the official complex VP contains three secured children")
        children.forEach { child ->
            val envelope = assertNotNull(child as? JsonObject, "VCDM 2 VP children must be JSON envelope objects")
            assertEquals(
                "EnvelopedVerifiableCredential",
                envelope["type"]?.jsonPrimitive?.content,
            )
            assertEquals("https://www.w3.org/ns/credentials/v2", baseContext(envelope))
            assertTrue(envelope["id"]?.jsonPrimitive?.content?.startsWith("data:application/vc+") == true)
        }

        // The JOSE child is the first fixture entry.  It is independently classifiable as a VCDM
        // 2 credential; SD-JWT and COSE entries are retained as envelope inputs but are outside
        // this JWT-specific vector test.
        val joseChild = children.first().jsonObject["id"]!!.jsonPrimitive.content.removePrefix("data:application/vc+jwt,")
        val childClassification = VcdmClassifier.classifyCompactJws(joseChild)
        assertTrue(childClassification.isOk)
        assertEquals(VcdmVersion.V2_0, childClassification.value.document.version)
        assertEquals(VcdmDocumentKind.CREDENTIAL, childClassification.value.document.kind)

        // A bounded nested-presentation envelope has the same VCDM 2 envelope contract.  Reusing
        // the signed official VP artifact keeps the child cryptographic input real; the verifier
        // integration layer owns recursive depth/holder-binding enforcement.
        val nestedVpEnvelope = buildJsonEnvelope(
            type = "EnvelopedVerifiablePresentation",
            mediaType = "vp",
            compact = fixture("presentation-jose-multiple.txt"),
        )
        assertEquals("EnvelopedVerifiablePresentation", nestedVpEnvelope["type"]!!.jsonPrimitive.content)
        assertTrue(nestedVpEnvelope["id"]!!.jsonPrimitive.content.startsWith("data:application/vp+jwt,"))
        assertEquals(VcdmDocumentKind.PRESENTATION, VcdmClassifier.classifyCompactJws(fixture("presentation-jose-multiple.txt")).value.document.kind)
    }

    private fun classify(name: String): VcdmClassification {
        val result = VcdmClassifier.classifyCompactJws(fixture(name))
        assertTrue(result.isOk, "official vector $name must parse as compact JWS")
        return result.value
    }

    private fun fixture(name: String): String =
        resourceText(name).trim()

    private fun resourceText(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/w3c-vc-jose-cose-vcdm2/$name")) {
            "missing vendored W3C vector: $name"
        }.bufferedReader().use { it.readText() }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun baseContext(document: JsonObject): String =
        when (val context = document["@context"]) {
            is kotlinx.serialization.json.JsonPrimitive -> context.content
            is JsonArray -> context.first().jsonPrimitive.content
            else -> error("missing VCDM base context")
        }

    private fun buildJsonEnvelope(type: String, mediaType: String, compact: String): JsonObject =
        kotlinx.serialization.json.buildJsonObject {
            put("@context", "https://www.w3.org/ns/credentials/v2")
            put("type", type)
            put("id", "data:application/$mediaType+jwt,$compact")
        }
}

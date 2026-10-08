/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.dataintegrity.eddsajcs2022

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.dataintegrity.model.ProofOptions
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Direct tests for the `eddsa-jcs-2022` cryptosuite primitives — exercises
 * the [EddsaJcs2022Cryptosuite] shape without needing the full DI graph.
 *
 * Real sign+verify with eddsa-jcs-2022 is exercised end-to-end in
 * `WebvhCreationE2ETest` (via the `did:webvh` create command + Ed25519 KMS).
 * This suite focuses on the deterministic and structural contract of
 * `hashData` / `encodeProofValue` / `decodeProofValue` per W3C VC-DI 1.0
 * §3.3.1 (eddsa-jcs-2022).
 */
class EddsaJcs2022CryptosuiteTest {
    private val sampleProof =
        DataIntegrityProof(
            cryptosuite = EddsaJcs2022Cryptosuite.ID,
            proofPurpose = ProofPurpose.ASSERTION_METHOD,
            verificationMethod = "did:webvh:Qm:example.com#key-1",
            proofValue = "zSampleSig",
            created = "2026-04-30T12:00:00Z",
        )

    private val sampleDoc =
        buildJsonObject {
            put("id", JsonPrimitive("did:webvh:Qm:example.com"))
            put("@context", JsonPrimitive("https://www.w3.org/ns/did/v1"))
        }

    @Test
    fun cryptosuiteIdMatchesSpec() {
        assertEquals("eddsa-jcs-2022", EddsaJcs2022Cryptosuite.ID, "Spec mandates literal id 'eddsa-jcs-2022'")
    }

    @Test
    fun hashDataLengthIsSixtyFourBytes() {
        // hashData = SHA-256(canonicalProofConfig) || SHA-256(canonicalDocument) → 32 + 32 = 64.
        val hash = EddsaJcs2022Cryptosuite.hashData(sampleDoc, sampleProof)
        assertEquals(64, hash.size, "hashData per spec is 2 × SHA-256 digests concatenated → 64 bytes")
    }

    @Test
    fun hashDataIsDeterministicForSameInput() {
        val a = EddsaJcs2022Cryptosuite.hashData(sampleDoc, sampleProof)
        val b = EddsaJcs2022Cryptosuite.hashData(sampleDoc, sampleProof)
        assertContentEquals(a, b, "hashData must be deterministic — JCS canonicalisation + SHA-256 are both deterministic")
    }

    @Test
    fun hashDataDiffersWhenDocumentChanges() {
        val docA = sampleDoc
        val docB = buildJsonObject { put("id", JsonPrimitive("did:webvh:Qm:other.example")) }
        val a = EddsaJcs2022Cryptosuite.hashData(docA, sampleProof)
        val b = EddsaJcs2022Cryptosuite.hashData(docB, sampleProof)
        assertNotEquals(a.toList(), b.toList(), "hashData must differ when the document changes")
    }

    @Test
    fun hashDataDiffersWhenProofConfigChanges() {
        val proofA = sampleProof
        val proofB = sampleProof.copy(verificationMethod = "did:webvh:Qm:example.com#key-2")
        val a = EddsaJcs2022Cryptosuite.hashData(sampleDoc, proofA)
        val b = EddsaJcs2022Cryptosuite.hashData(sampleDoc, proofB)
        assertNotEquals(a.toList(), b.toList(), "hashData must differ when the proof config changes")
    }

    @Test
    fun hashDataDiffersWhenProofExtensionChanges() {
        val proofA = sampleProof
        val proofB =
            sampleProof.copy(
                additionalProofProperties = buildJsonObject { put("suiteParameter", JsonPrimitive("one")) },
            )

        val a = EddsaJcs2022Cryptosuite.hashData(sampleDoc, proofA)
        val b = EddsaJcs2022Cryptosuite.hashData(sampleDoc, proofB)

        assertNotEquals(a.toList(), b.toList(), "proof extensions are part of the canonical proof configuration")
    }

    @Test
    fun creatorProofConfigCopiesOptionsExtensionsBeforeHashing() {
        val proofWithExtension =
            proofOptions(
                additionalProofProperties =
                    buildJsonObject {
                        put("suiteParameter", JsonPrimitive("one"))
                    },
            ).toEddsaJcs2022ProofConfig()
        val proofWithoutExtension =
            proofWithExtension.copy(additionalProofProperties = kotlinx.serialization.json.JsonObject(emptyMap()))

        assertEquals(JsonPrimitive("one"), proofWithExtension.additionalProofProperties["suiteParameter"])
        assertNotEquals(
            EddsaJcs2022Cryptosuite.hashData(sampleDoc, proofWithoutExtension).toList(),
            EddsaJcs2022Cryptosuite.hashData(sampleDoc, proofWithExtension).toList(),
            "creator extensions must be present in the proof config before hashing",
        )
    }

    @Test
    fun creatorProofConfigRejectsExtensionsThatShadowTypedProperties() {
        assertFailsWith<IllegalArgumentException> {
            proofOptions(
                additionalProofProperties =
                    buildJsonObject {
                        put("proofValue", JsonPrimitive("attacker-controlled"))
                    },
            ).toEddsaJcs2022ProofConfig()
        }
    }

    @Test
    fun proofValueIsExcludedFromHashInput() {
        // Per spec the proof input to hashing is the proof config WITHOUT proofValue —
        // changing proofValue must NOT change hashData (otherwise verification would
        // be impossible: the verifier sees the proofValue that signing produced, but
        // the signer didn't have it yet).
        val proofWithoutValue = sampleProof.copy(proofValue = "")
        val proofWithDifferentValue = sampleProof.copy(proofValue = "zDifferentSignaturePlaceholder")
        val a = EddsaJcs2022Cryptosuite.hashData(sampleDoc, proofWithoutValue)
        val b = EddsaJcs2022Cryptosuite.hashData(sampleDoc, proofWithDifferentValue)
        assertContentEquals(a, b, "hashData must NOT depend on the proofValue field")
    }

    @Test
    fun encodeAndDecodeProofValueRoundTrip() {
        val rawSig = ByteArray(64) { it.toByte() } // 64-byte Ed25519 signature shape
        val encoded = EddsaJcs2022Cryptosuite.encodeProofValue(rawSig)
        assertTrue(
            encoded.startsWith(EddsaJcs2022Cryptosuite.PROOF_VALUE_PREFIX_BASE58BTC),
            "Encoded proofValue must start with multibase base58btc prefix '${EddsaJcs2022Cryptosuite.PROOF_VALUE_PREFIX_BASE58BTC}'",
        )
        val decoded = EddsaJcs2022Cryptosuite.decodeProofValue(encoded)
        assertContentEquals(rawSig, decoded, "encode → decode must round-trip the raw signature bytes")
    }

    @Test
    fun decodeProofValueRejectsNonBase58btcPrefix() {
        // Multibase 'm' = base64; the cryptosuite mandates base58btc only.
        assertFailsWith<IllegalArgumentException>("Non-'z'-prefixed multibase must be rejected") {
            EddsaJcs2022Cryptosuite.decodeProofValue("mAQID")
        }
    }

    @Test
    fun decodeProofValueRejectsEmptyString() {
        assertFailsWith<IllegalArgumentException>("Empty proofValue must be rejected") {
            EddsaJcs2022Cryptosuite.decodeProofValue("")
        }
    }

    @Test
    fun proofValueAndVerificationKeyMustHaveEd25519SuiteShape() {
        val valid = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, alg = JwaAlgorithm.EdDSA, x = "AQ")
        EddsaJcs2022Cryptosuite.requireEd25519Key(valid)

        assertFailsWith<IllegalArgumentException> {
            EddsaJcs2022Cryptosuite.encodeProofValue(ByteArray(63))
        }
        assertFailsWith<IllegalArgumentException> {
            EddsaJcs2022Cryptosuite.decodeProofValue(EddsaJcs2022Cryptosuite.encodeProofValue(ByteArray(64)) + "1")
        }
        assertFailsWith<IllegalArgumentException> {
            EddsaJcs2022Cryptosuite.requireEd25519Key(
                Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256, alg = JwaAlgorithm.ES256, x = "AQ", y = "AQ"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EddsaJcs2022Cryptosuite.requireEd25519Key(
                Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.X25519, x = "AQ"),
            )
        }
    }

    private fun proofOptions(additionalProofProperties: kotlinx.serialization.json.JsonObject) =
        ProofOptions(
            cryptosuite = EddsaJcs2022Cryptosuite.ID,
            verificationMethod = sampleProof.verificationMethod,
            proofPurpose = sampleProof.proofPurpose,
            signingKeyRef = "test-key",
            created = sampleProof.created,
            additionalProofProperties = additionalProofProperties,
        )
}

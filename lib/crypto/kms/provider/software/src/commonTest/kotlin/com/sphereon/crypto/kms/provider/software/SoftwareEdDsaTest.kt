/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.kms.provider.software

import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.okpRawToJwk
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.kms.provider.software.testutil.SoftwareKmsTestContext
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 8032 EdDSA tests for the software KMS provider.
 *
 * Ed25519 vector: RFC 8032 §7.1 "Test 1" — empty message, well-known seed.
 * Ed448 vector:   RFC 8032 §7.4 "Blank" — empty message, well-known seed.
 *
 * EdDSA is deterministic, so a key + message yields exactly one valid
 * signature (64 bytes for Ed25519, 114 bytes for Ed448). We assert
 * byte-identical output against the RFC vectors.
 */
class SoftwareEdDsaTest {
    private lateinit var provider: SoftwareKmsProvider
    val ctx = SoftwareKmsTestContext("test-eddsa", this)

    @BeforeTest
    fun setUp() {
        val config = SoftwareKmsProviderConfig(id = "test-eddsa", cryptographyProvider = CryptographyProvider.Default.name)
        provider = ctx.softwareKmsProviderFactory.create(config, ctx.session.sessionExecution)
    }

    @Test
    fun ed25519CurveAndAlgorithmAreSupported() {
        assertTrue(provider.isSupportedCurve(Curve.Ed25519), "Ed25519 must be a supported curve")
        assertTrue(
            provider.supportedSignatureAlgorithms().contains(SignatureAlgorithm.ED25519),
            "ED25519 must be in supportedSignatureAlgorithms",
        )
    }

    @Test
    fun ed448CurveAndAlgorithmAreSupported() {
        assertTrue(provider.isSupportedCurve(Curve.Ed448), "Ed448 must be a supported curve")
        assertTrue(
            provider.supportedSignatureAlgorithms().contains(SignatureAlgorithm.ED448),
            "ED448 must be in supportedSignatureAlgorithms",
        )
    }

    @Test
    fun generatedEd25519KeySignsAndVerifies() =
        runTest {
            val keyPair = provider.generateKeyAsync(alg = SignatureAlgorithm.ED25519)
            val keyInfo = keyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
            assertNotNull(keyInfo)

            val msg = "hello, ed25519".encodeToByteArray()
            val sig = provider.createRawSignature(keyInfo = keyInfo, input = msg, requireX5Chain = false)
            assertEquals(ED25519_SIGNATURE_BYTES, sig.size, "Ed25519 signatures must be 64 bytes")

            assertTrue(
                provider.isValidRawSignature(keyInfo = keyInfo, input = msg, signature = sig),
                "Ed25519 signature must verify",
            )
            assertFalse(
                provider.isValidRawSignature(keyInfo = keyInfo, input = "tampered".encodeToByteArray(), signature = sig),
                "Ed25519 signature must NOT verify against a different message",
            )
        }

    @Test
    fun generatedEd25519KeySkipsAutomaticX509Certificate() =
        runTest {
            val certificateEnabledProvider = ctx.softwareKmsProviderFactory.create(
                SoftwareKmsProviderConfig(
                    id = "test-eddsa-auto-cert",
                    cryptographyProvider = CryptographyProvider.Default.name,
                    autoCreateCertificate = true,
                ),
                ctx.session.sessionExecution,
            )

            val keyPair = certificateEnabledProvider.generateKeyAsync(
                alias = "ed25519-without-x509",
                alg = SignatureAlgorithm.ED25519,
            )

            assertEquals(JwaKeyType.OKP, keyPair.jose.publicJwk.kty)
            assertTrue(keyPair.jose.publicJwk.x5c.isNullOrEmpty())
        }

    @Test
    fun generatedEd448KeySignsAndVerifies() =
        runTest {
            val keyPair = provider.generateKeyAsync(alg = SignatureAlgorithm.ED448)
            val keyInfo = keyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
            assertNotNull(keyInfo)

            val msg = "hello, ed448".encodeToByteArray()
            val sig = provider.createRawSignature(keyInfo = keyInfo, input = msg, requireX5Chain = false)
            assertEquals(ED448_SIGNATURE_BYTES, sig.size, "Ed448 signatures must be 114 bytes")

            assertTrue(
                provider.isValidRawSignature(keyInfo = keyInfo, input = msg, signature = sig),
                "Ed448 signature must verify",
            )
            assertFalse(
                provider.isValidRawSignature(keyInfo = keyInfo, input = "tampered".encodeToByteArray(), signature = sig),
                "Ed448 signature must NOT verify against a different message",
            )
        }

    /**
     * RFC 8032 §7.1 Test 1 (Ed25519) — message is empty, deterministic signature
     * over a known seed produces an exact 64-byte output.
     */
    @Test
    fun ed25519MatchesRfc8032Test1Vector() =
        runTest {
            val seed = ED25519_RFC8032_TEST1_SEED.hexToByteArray()
            val pub = ED25519_RFC8032_TEST1_PUBKEY.hexToByteArray()
            val expectedSig = ED25519_RFC8032_TEST1_SIGNATURE.hexToByteArray()

            val jwk = okpRawToJwk(
                rawPublic = pub,
                rawPrivate = seed,
                curve = Curve.Ed25519,
                keyOps = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
            )
            val keyInfo =
                ResolvedKeyInfo(
                    key = jwk,
                    keyVisibility = KeyVisibility.PRIVATE,
                    keyType = KeyTypeMapping.OKP,
                    alias = "ed25519-rfc8032-test1",
                    providerId = provider.id,
                    kid = jwk.kid,
                    signatureAlgorithm = SignatureAlgorithm.ED25519,
                )

            val sig = provider.createRawSignature(keyInfo = keyInfo, input = ByteArray(0), requireX5Chain = false)
            assertEquals(expectedSig.toHexString(), sig.toHexString(), "Ed25519 RFC 8032 §7.1 Test 1 signature must match exactly")
            assertTrue(
                provider.isValidRawSignature(keyInfo = keyInfo, input = ByteArray(0), signature = sig),
                "Ed25519 RFC 8032 vector must verify",
            )
        }

    /**
     * RFC 8032 §7.4 "Blank" (Ed448) — message is empty, deterministic signature
     * produces an exact 114-byte output.
     */
    @Test
    fun ed448MatchesRfc8032BlankVector() =
        runTest {
            val seed = ED448_RFC8032_BLANK_SEED.hexToByteArray()
            val pub = ED448_RFC8032_BLANK_PUBKEY.hexToByteArray()
            val expectedSig = ED448_RFC8032_BLANK_SIGNATURE.hexToByteArray()

            val jwk = okpRawToJwk(
                rawPublic = pub,
                rawPrivate = seed,
                curve = Curve.Ed448,
                keyOps = arrayOf(KeyOperations.SIGN, KeyOperations.VERIFY),
            )
            val keyInfo =
                ResolvedKeyInfo(
                    key = jwk,
                    keyVisibility = KeyVisibility.PRIVATE,
                    keyType = KeyTypeMapping.OKP,
                    alias = "ed448-rfc8032-blank",
                    providerId = provider.id,
                    kid = jwk.kid,
                    signatureAlgorithm = SignatureAlgorithm.ED448,
                )

            val sig = provider.createRawSignature(keyInfo = keyInfo, input = ByteArray(0), requireX5Chain = false)
            assertEquals(expectedSig.toHexString(), sig.toHexString(), "Ed448 RFC 8032 §7.4 Blank signature must match exactly")
            assertTrue(
                provider.isValidRawSignature(keyInfo = keyInfo, input = ByteArray(0), signature = sig),
                "Ed448 RFC 8032 vector must verify",
            )
        }

    private companion object {
        const val ED25519_SIGNATURE_BYTES: Int = 64
        const val ED448_SIGNATURE_BYTES: Int = 114

        // RFC 8032 §7.1 Test 1
        const val ED25519_RFC8032_TEST1_SEED: String =
            "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
        const val ED25519_RFC8032_TEST1_PUBKEY: String =
            "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        const val ED25519_RFC8032_TEST1_SIGNATURE: String =
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"

        // RFC 8032 §7.4 "Blank" — empty message
        const val ED448_RFC8032_BLANK_SEED: String =
            "6c82a562cb808d10d632be89c8513ebf6c929f34ddfa8c9f63c9960ef6e348a3528c8a3fcc2f044e39a3fc5b94492f8f032e7549a20098f95b"
        const val ED448_RFC8032_BLANK_PUBKEY: String =
            "5fd7449b59b461fd2ce787ec616ad46a1da1342485a70e1f8a0ea75d80e96778edf124769b46c7061bd6783df1e50f6cd1fa1abeafe8256180"
        const val ED448_RFC8032_BLANK_SIGNATURE: String =
            "533a37f6bbe457251f023c0d88f976ae2dfb504a843e34d2074fd823d41a591f2b233f034f628281f2fd7a22ddd47d7828c59bd0a21bfd3980ff0d2028d4b18a9df63e006c5d1c2d345b925d8dc00b4104852db99ac5c7cdda8530a113a0f4dbb61149f05a7363268c71d95808ff2e652600"
    }
}

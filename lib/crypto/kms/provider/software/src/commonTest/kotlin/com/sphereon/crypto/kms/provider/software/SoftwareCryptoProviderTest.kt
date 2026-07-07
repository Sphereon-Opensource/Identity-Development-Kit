/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.crypto.kms.provider.software

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KmsProviderOperation
import com.sphereon.crypto.core.kms.command.EcPointMultiplyOutput
import com.sphereon.crypto.core.kms.command.EcdhDeriveMode
import com.sphereon.crypto.core.kms.command.SignatureEncoding
import com.sphereon.crypto.kms.provider.software.testutil.SoftwareKmsTestContext
import com.sphereon.crypto.secdsa.impl.DefaultSecdsaPrimitives
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SoftwareCryptoProviderTest {
    private lateinit var softwareKMSProvider: SoftwareKmsProvider

    val ctx = SoftwareKmsTestContext("test", this)

    @BeforeTest
    fun setUp() {
        val config = SoftwareKmsProviderConfig(id = "test-ecdsa", cryptographyProvider = CryptographyProvider.Default.name)
        softwareKMSProvider = ctx.softwareKmsProviderFactory.create(config, ctx.session.sessionExecution)
    }

    @Test
    fun testSupportedCurves() {
        val curves = softwareKMSProvider.supportedCurves()
        assertContentEquals(
            arrayOf(
                Curve.P_256,
                Curve.P_384,
                Curve.P_521,
                Curve.Ed25519,
                Curve.Ed448,
                Curve.X25519,
                Curve.X448,
            ),
            curves,
        )
    }

    @Test
    fun testIsSupportedCurve() {
        assertTrue(softwareKMSProvider.isSupportedCurve(Curve.P_256))
        assertTrue(softwareKMSProvider.isSupportedCurve(Curve.P_384))
        assertTrue(softwareKMSProvider.isSupportedCurve(Curve.P_521))
        assertTrue(softwareKMSProvider.isSupportedCurve(Curve.Ed25519))
        assertTrue(softwareKMSProvider.isSupportedCurve(Curve.Ed448))
        assertTrue(softwareKMSProvider.isSupportedCurve(Curve.X25519))
        assertTrue(softwareKMSProvider.isSupportedCurve(Curve.X448))
        // Secp256k1 remains unsupported by the OSS software provider.
        assertFalse(softwareKMSProvider.isSupportedCurve(Curve.Secp256k1))
    }

    @Test
    fun testSupportedDigests() {
        val digests = softwareKMSProvider.supportedDigests()
        assertContentEquals(
            arrayOf(DigestAlg.SHA256, DigestAlg.SHA384, DigestAlg.SHA512),
            digests,
        )
    }

    @Test
    fun testGenerateKeyAsync() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            assertNotNull(managedKeyPair)
            assertNotNull(managedKeyPair.cborToManagedKeyInfo().key.kid)
        }

    @Test
    fun testValidEcdsaRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
            assertNotNull(keyInfo)
            val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
            assertTrue(verification)
        }

    @Test
    fun testInvalidEcdsaRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
            assertNotNull(keyInfo)
            val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test2".encodeToByteArray())
            assertFalse(verification)
        }

    @Test
    fun testDigestSignatureSignsDigestWithoutHashingAgain() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
            val digest = hash("secdsa message".encodeToByteArray(), DigestAlg.SHA256)

            val signature =
                softwareKMSProvider.signDigest(
                    keyInfo = keyInfo,
                    digest = digest,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    signatureEncoding = SignatureEncoding.RAW,
                )

            assertTrue(
                softwareKMSProvider.verifyDigest(
                    keyInfo = keyInfo,
                    digest = digest,
                    signature = signature,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    signatureEncoding = SignatureEncoding.RAW,
                ),
            )
            assertFalse(
                softwareKMSProvider.isValidRawSignature(
                    keyInfo = keyInfo,
                    input = digest,
                    signature = signature,
                ),
                "A digest signature must not verify as a normal ECDSA signature over SHA-256(digest)",
            )
        }

    @Test
    fun testSplitSecdsaSignatureVerifiesAgainstPinDerivedPublicKey() =
        runTest {
            val primitives = DefaultSecdsaPrimitives()
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alias = "software-nch-secdsa", alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
            val pinScalar = primitives.scalar(byteArrayOf(0x07))
            val nchPublicKey = primitives.pointFromJwk(managedKeyPair.jose.publicJwk)
            val pinDerivedPublicKey = primitives.multiply(pinScalar, nchPublicKey)
            val digest = hash("secdsa split signing".encodeToByteArray(), DigestAlg.SHA256)

            val signature =
                primitives.splitSign(
                    digest = digest,
                    pinScalar = pinScalar,
                    nchKeyInfo = keyInfo,
                    kmsProvider = softwareKMSProvider,
                )

            assertTrue(primitives.verifyDigestSignature(digest, pinDerivedPublicKey, signature))
            assertFalse(
                softwareKMSProvider.verifyDigest(
                    keyInfo = keyInfo,
                    digest = digest,
                    signature = signature.toRawBytes(),
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    signatureEncoding = SignatureEncoding.RAW,
                ),
                "A SECDSA split signature must verify under the PIN-derived public key, not the base NCH key",
            )
        }

    @Test
    fun testValidPSSRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
            assertNotNull(keyInfo)
            val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
            assertTrue(verification)
        }

    @Test
    fun testInvalidPSSRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
            assertNotNull(keyInfo)
            val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test#".encodeToByteArray())
            assertFalse(verification)
        }

    @Test
    fun testValidRsaPkcs1RawSignatureAndVerification() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
            assertNotNull(keyInfo)
            val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
            assertTrue(verification)
        }

    @Test
    fun testInvalidRsaPkcs1RawSignatureAndVerification() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
            assertNotNull(keyInfo)
            val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test#".encodeToByteArray())
            assertFalse(verification)
        }

    @Test
    fun testAllAlgSignaturesAndVerification() =
        runTest {
            softwareKMSProvider
                .supportedSignatureAlgorithms()
                .filter { it.cryptoAlgorithm != com.sphereon.crypto.core.generic.CryptoAlg.HMAC }
                .forEach { alg ->
                    val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = alg)
                    val keyInfo = managedKeyPair.joseToManagedKeyInfo(visibility = KeyVisibility.PRIVATE)
                    assertNotNull(keyInfo)
                    val signature = softwareKMSProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
                    assertNotNull(signature)
                    val verification = softwareKMSProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
                    assertTrue(verification)
                }
        }

    @Test
    fun testGenerateKeyThrowsExceptionForUnsupportedCurve() =
        runTest {
            // ES256K (secp256k1) remains unsupported in the OSS software provider.
            val unsupportedAlg = SignatureAlgorithm.ES256K
            val exception =
                assertFailsWith<IllegalArgumentException> {
                    softwareKMSProvider.generateKeyAsync(alg = unsupportedAlg)
                }
            assertEquals("Curve ${unsupportedAlg.curve!!.jose.name} not supported for EcDSA", exception.message)
        }

    @Test
    fun testSupportedKeyTypes() {
        val keyTypes = softwareKMSProvider.supportedKeyTypes()
        assertContentEquals(
            arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA, KeyTypeMapping.Symmetric, KeyTypeMapping.OKP),
            keyTypes,
        )
    }

    @Test
    fun testSupportedAlg() {
        val algorithms = softwareKMSProvider.supportedSignatureAlgorithms()
        assertContentEquals(
            arrayOf(
                SignatureAlgorithm.ECDSA_SHA256,
                SignatureAlgorithm.ECDSA_SHA384,
                SignatureAlgorithm.ECDSA_SHA512,
                SignatureAlgorithm.RSA_RAW,
                SignatureAlgorithm.RSA_SHA256,
                SignatureAlgorithm.RSA_SHA384,
                SignatureAlgorithm.RSA_SHA512,
                SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
                SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1,
                SignatureAlgorithm.HMAC_SHA256,
                SignatureAlgorithm.HMAC_SHA384,
                SignatureAlgorithm.HMAC_SHA512,
                SignatureAlgorithm.ED25519,
                SignatureAlgorithm.ED448,
            ),
            algorithms,
        )
    }

    @Test
    fun testGenerateHmacSha256Key() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.HMAC_SHA256)
            assertNotNull(managedKeyPair)
            assertNotNull(managedKeyPair.kid)
            assertNotNull(managedKeyPair.jose)
            // Public JWK should not contain secret material
            val publicJwk = managedKeyPair.jose.publicJwk
            assertNotNull(publicJwk)
            assertEquals(com.sphereon.crypto.core.jose.JwaKeyType.oct, publicJwk.kty)
            assertEquals(null, publicJwk.k, "Public JWK must not expose symmetric key material")
        }

    @Test
    fun testGenerateHmacSha384Key() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.HMAC_SHA384)
            assertNotNull(managedKeyPair)
            assertNotNull(managedKeyPair.kid)
        }

    @Test
    fun testGenerateHmacSha512Key() =
        runTest {
            val managedKeyPair = softwareKMSProvider.generateKeyAsync(alg = SignatureAlgorithm.HMAC_SHA512)
            assertNotNull(managedKeyPair)
            assertNotNull(managedKeyPair.kid)
        }

    @Test
    fun testHmacGenerateMacAndVerify() =
        runTest {
            // Generate an HMAC key with persistence so generateMac/verifyMac can look it up
            val persistConfig =
                SoftwareKmsProviderConfig(
                    id = "test-hmac",
                    cryptographyProvider = CryptographyProvider.Default.name,
                    persistKeysDuringGeneration = true,
                    exposePrivateKeysDuringGeneration = true,
                )
            val hmacProvider = ctx.softwareKmsProviderFactory.create(persistConfig, ctx.session.sessionExecution)

            val managedKeyPair = hmacProvider.generateKeyAsync(alg = SignatureAlgorithm.HMAC_SHA256)
            val kid = managedKeyPair.kid
            assertNotNull(kid)

            val message = "Hello HMAC".encodeToByteArray()
            val mac = hmacProvider.generateMac(keyId = kid, message = message, digestAlgorithm = DigestAlg.SHA256)
            assertNotNull(mac)
            assertTrue(mac.isNotEmpty())

            // Verify the MAC is valid
            val isValid = hmacProvider.verifyMac(keyId = kid, message = message, mac = mac, digestAlgorithm = DigestAlg.SHA256)
            assertTrue(isValid, "MAC verification should succeed for correct message")

            // Verify the MAC fails for tampered message
            val isInvalid = hmacProvider.verifyMac(keyId = kid, message = "Tampered".encodeToByteArray(), mac = mac, digestAlgorithm = DigestAlg.SHA256)
            assertFalse(isInvalid, "MAC verification should fail for tampered message")
        }

    @Test
    fun testHmacGenerateMacResolvesAliasBeforeKid() =
        runTest {
            val persistConfig =
                SoftwareKmsProviderConfig(
                    id = "test-hmac-alias",
                    cryptographyProvider = CryptographyProvider.Default.name,
                    persistKeysDuringGeneration = true,
                    exposePrivateKeysDuringGeneration = true,
                )
            val hmacProvider = ctx.softwareKmsProviderFactory.create(persistConfig, ctx.session.sessionExecution)
            val alias = "idfr:bi:tenant-a"

            hmacProvider.generateKeyAsync(
                alias = alias,
                alg = SignatureAlgorithm.HMAC_SHA256,
            )

            val message = "alias-first MAC lookup".encodeToByteArray()
            val macByAlias = hmacProvider.generateMac(keyId = alias, message = message, digestAlgorithm = DigestAlg.SHA256)
            val verifiedByAlias = hmacProvider.verifyMac(keyId = alias, message = message, mac = macByAlias, digestAlgorithm = DigestAlg.SHA256)

            assertTrue(verifiedByAlias, "MAC operations must resolve the configured key alias directly")
        }

    @Test
    fun testHmacKeyRoundTripThroughKeyStore() =
        runTest {
            val persistConfig =
                SoftwareKmsProviderConfig(
                    id = "test-hmac-store",
                    cryptographyProvider = CryptographyProvider.Default.name,
                    persistKeysDuringGeneration = true,
                    exposePrivateKeysDuringGeneration = true,
                )
            val hmacProvider = ctx.softwareKmsProviderFactory.create(persistConfig, ctx.session.sessionExecution)

            val managedKeyPair =
                hmacProvider.generateKeyAsync(
                    alias = "my-hmac-key",
                    alg = SignatureAlgorithm.HMAC_SHA256,
                )
            assertEquals("my-hmac-key", managedKeyPair.alias)

            // Retrieve the key from the store
            val retrieved =
                hmacProvider.getKey(
                    com.sphereon.crypto.core
                        .KeyInfo<com.sphereon.crypto.core.jose.Jwk>(alias = "my-hmac-key"),
                )
            assertNotNull(retrieved)
            assertEquals("my-hmac-key", retrieved.alias)
            assertEquals(KeyTypeMapping.Symmetric, retrieved.keyType)
        }

    @Test
    fun testCapabilitiesIncludeSymmetric() {
        val capabilities = softwareKMSProvider.getCapabilities()
        assertTrue(capabilities.supportedKeyTypes.contains(KeyTypeMapping.Symmetric))
        assertTrue(capabilities.supportedCryptoAlgorithms.contains(com.sphereon.crypto.core.generic.CryptoAlg.HMAC))

        val macGenOp = capabilities.operations.find { it.operation == com.sphereon.crypto.core.kms.KmsProviderOperation.GENERATE_MAC }
        assertNotNull(macGenOp)
        assertTrue(macGenOp.supported)
        assertNotNull(macGenOp.signatureAlgorithms)
        assertTrue(macGenOp.signatureAlgorithms!!.contains(SignatureAlgorithm.HMAC_SHA256))
    }

    @Test
    fun testCapabilitiesAdvertiseScdsaPhase1Primitives() {
        val capabilities = softwareKMSProvider.getCapabilities()
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.SIGN_DIGEST))
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.VERIFY_DIGEST))
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.ECDH_DERIVE_RAW_X))
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.ECDH_DERIVE_KDF))
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.EC_POINT_MULTIPLY))
        assertFalse(capabilities.supportsOperation(KmsProviderOperation.KEY_ATTESTATION))
    }

    @Test
    fun testProviderBackedEcdhDeriveResolvesAliasOnlyPrivateKeys() =
        runTest {
            val alice = softwareKMSProvider.generateKeyAsync(alias = "alice-ecdh", alg = SignatureAlgorithm.ECDSA_SHA256)
            val bob = softwareKMSProvider.generateKeyAsync(alias = "bob-ecdh", alg = SignatureAlgorithm.ECDSA_SHA256)

            val aliceReference = KeyInfo<com.sphereon.crypto.core.jose.Jwk>(providerId = softwareKMSProvider.id, alias = alice.alias)
            val bobReference = KeyInfo<com.sphereon.crypto.core.jose.Jwk>(providerId = softwareKMSProvider.id, alias = bob.alias)

            val aliceSecret =
                softwareKMSProvider.ecdhDerive(
                    privateKeyInfo = aliceReference,
                    publicKeyInfo = bob.joseToManagedKeyInfo(visibility = KeyVisibility.PUBLIC),
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    mode = EcdhDeriveMode.RAW_X,
                )
            val bobSecret =
                softwareKMSProvider.ecdhDerive(
                    privateKeyInfo = bobReference,
                    publicKeyInfo = alice.joseToManagedKeyInfo(visibility = KeyVisibility.PUBLIC),
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    mode = EcdhDeriveMode.RAW_X,
                )

            assertEquals(32, aliceSecret.derivedSecret.size)
            assertContentEquals(aliceSecret.derivedSecret, bobSecret.derivedSecret)
        }

    @Test
    fun testProviderBackedEcdhDeriveConcatKdf() =
        runTest {
            val alice = softwareKMSProvider.generateKeyAsync(alias = "alice-ecdh-kdf", alg = SignatureAlgorithm.ECDSA_SHA256)
            val bob = softwareKMSProvider.generateKeyAsync(alias = "bob-ecdh-kdf", alg = SignatureAlgorithm.ECDSA_SHA256)
            val aliceReference = KeyInfo<com.sphereon.crypto.core.jose.Jwk>(providerId = softwareKMSProvider.id, alias = alice.alias)

            val result =
                softwareKMSProvider.ecdhDerive(
                    privateKeyInfo = aliceReference,
                    publicKeyInfo = bob.joseToManagedKeyInfo(visibility = KeyVisibility.PUBLIC),
                    algorithm = KeyAgreementAlgorithm.ECDH_ES_A256KW,
                    mode = EcdhDeriveMode.CONCAT_KDF,
                    keyDataLen = 256,
                    algorithmId = "A256KW",
                    partyUInfo = "alice".encodeToByteArray(),
                    partyVInfo = "bob".encodeToByteArray(),
                )

            assertEquals(32, result.derivedSecret.size)
            assertEquals(32, assertNotNull(result.rawSharedSecret).size)
        }

    @Test
    fun testEcPointMultiplyReturnsSameRawXAsEcdhDerive() =
        runTest {
            val alice = softwareKMSProvider.generateKeyAsync(alias = "alice-point-multiply", alg = SignatureAlgorithm.ECDSA_SHA256)
            val bob = softwareKMSProvider.generateKeyAsync(alias = "bob-point-multiply", alg = SignatureAlgorithm.ECDSA_SHA256)
            val aliceReference = KeyInfo<com.sphereon.crypto.core.jose.Jwk>(providerId = softwareKMSProvider.id, alias = alice.alias)
            val bobPublic = bob.joseToManagedKeyInfo(visibility = KeyVisibility.PUBLIC)

            val rawEcdh =
                softwareKMSProvider.ecdhDerive(
                    privateKeyInfo = aliceReference,
                    publicKeyInfo = bobPublic,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    mode = EcdhDeriveMode.RAW_X,
                )
            val pointMultiply =
                softwareKMSProvider.ecPointMultiply(
                    privateKeyInfo = aliceReference,
                    publicKeyInfo = bobPublic,
                    output = EcPointMultiplyOutput.RAW_X,
                )

            assertContentEquals(rawEcdh.derivedSecret, pointMultiply.rawX)
        }

    @Test
    fun testKeyTypeMappingSymmetricFromJose() {
        val mapping = KeyTypeMapping.fromJose(com.sphereon.crypto.core.jose.JwaKeyType.oct)
        assertEquals(KeyTypeMapping.Symmetric, mapping)
    }
}

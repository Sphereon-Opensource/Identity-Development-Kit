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
 *
 */

package com.sphereon.crypto.kms.provider.mobile

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.Uuid
import com.sphereon.core.defaults.app.staticMinimalTestAppGraph
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
import com.sphereon.crypto.secdsa.impl.DefaultSecdsaPrimitives
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MobileCryptoProviderTest {
    private lateinit var mobileCryptoProvider: MobileKmsProviderImpl

    @BeforeTest
    fun setUp() {
        val app = staticMinimalTestAppGraph(application = this, appId = "test-app", profile = "test-profile", version = "test-version")
        val user = app.userContextManager.getAnonymous()
        val session = user.sessionContextManager.createOrGetFromId("test-session", principalType = com.sphereon.di.context.PrincipalType.USER)

        val providerId = "test-mobile-${Uuid.v4String()}"
        mobileCryptoProvider =
            MobileKmsProviderImpl(
                MobileKmsProviderConfig(
                    id = providerId,
                    defaultConfigValues = mapOf("jks.path" to "build/mobile-kms-test/$providerId.p12"),
                ),
                execution = session.asCoreApiServiceGraph().serviceExecution,
            )
    }

    @Test
    fun testSupportedCurves() {
        val curves = mobileCryptoProvider.supportedCurves()
        assertContentEquals(
            arrayOf(Curve.P_256, Curve.P_384, Curve.P_521),
            curves,
        )
    }

    @Test
    fun testIsSupportedCurve() {
        assertTrue(mobileCryptoProvider.isSupportedCurve(Curve.P_256))
        assertTrue(mobileCryptoProvider.isSupportedCurve(Curve.P_384))
        assertTrue(mobileCryptoProvider.isSupportedCurve(Curve.P_521))
        assertFalse(mobileCryptoProvider.isSupportedCurve(Curve.X25519))
    }

    @Test
    fun testSupportedDigests() {
        val digests = mobileCryptoProvider.supportedDigests()
        assertContentEquals(
            arrayOf(DigestAlg.SHA256, DigestAlg.SHA384, DigestAlg.SHA512),
            digests,
        )
    }

    @Test
    fun testGenerateKeyAsync() =
        runTest {
            val managedKeyPair = mobileCryptoProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            assertNotNull(managedKeyPair)
            assertNotNull(managedKeyPair.cborToManagedKeyInfo().key.kid)
        }

    @Test
    fun testValidEcdsaRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = mobileCryptoProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo()
            assertNotNull(keyInfo)
            val signature = mobileCryptoProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = mobileCryptoProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
            assertTrue(verification)
        }

    @Test
    fun testInvalidEcdsaRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = mobileCryptoProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo()
            assertNotNull(keyInfo)
            val signature = mobileCryptoProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = mobileCryptoProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test2".encodeToByteArray())
            assertFalse(verification)
        }

    @Test
    fun testDigestSignatureSignsDigestWithoutHashingAgain() =
        runTest {
            val managedKeyPair = mobileCryptoProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo()
            val digest = hash("secdsa message".encodeToByteArray(), DigestAlg.SHA256)

            val signature =
                mobileCryptoProvider.signDigest(
                    keyInfo = keyInfo,
                    digest = digest,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    signatureEncoding = SignatureEncoding.RAW,
                )

            assertTrue(
                mobileCryptoProvider.verifyDigest(
                    keyInfo = keyInfo,
                    digest = digest,
                    signature = signature,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    signatureEncoding = SignatureEncoding.RAW,
                ),
            )
            assertFalse(
                mobileCryptoProvider.isValidRawSignature(
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
            val managedKeyPair = mobileCryptoProvider.generateKeyAsync(alias = "mobile-nch-secdsa", alg = SignatureAlgorithm.ECDSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo()
            val pinScalar = primitives.scalar(byteArrayOf(0x07))
            val nchPublicKey = primitives.pointFromJwk(managedKeyPair.jose.publicJwk)
            val pinDerivedPublicKey = primitives.multiply(pinScalar, nchPublicKey)
            val digest = hash("secdsa mobile split signing".encodeToByteArray(), DigestAlg.SHA256)

            val signature =
                primitives.splitSign(
                    digest = digest,
                    pinScalar = pinScalar,
                    nchKeyInfo = keyInfo,
                    kmsProvider = mobileCryptoProvider,
                )

            assertTrue(primitives.verifyDigestSignature(digest, pinDerivedPublicKey, signature))
            assertFalse(
                mobileCryptoProvider.verifyDigest(
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
            val managedKeyPair = mobileCryptoProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo()
            assertNotNull(keyInfo)
            val signature = mobileCryptoProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = mobileCryptoProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
            assertTrue(verification)
        }

    @Test
    fun testInvalidPSSRawSignatureAndVerification() =
        runTest {
            val managedKeyPair = mobileCryptoProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo()
            assertNotNull(keyInfo)
            val signature = mobileCryptoProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = mobileCryptoProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test#".encodeToByteArray())
            assertFalse(verification)
        }

    @Test
    fun testValidRsaPkcs1RawSignatureAndVerification() =
        runTest {
            val managedKeyPair = mobileCryptoProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo()
            assertNotNull(keyInfo)
            val signature = mobileCryptoProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = mobileCryptoProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
            assertTrue(verification)
        }

    @Test
    fun testInvalidRsaPkcs1RawSignatureAndVerification() =
        runTest {
            val managedKeyPair = mobileCryptoProvider.generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
            val keyInfo = managedKeyPair.joseToManagedKeyInfo()
            assertNotNull(keyInfo)
            val signature = mobileCryptoProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
            assertNotNull(signature)
            val verification = mobileCryptoProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test#".encodeToByteArray())
            assertFalse(verification)
        }

    @Test
    fun testAllAlgSignaturesAndVerification() =
        runTest {
            mobileCryptoProvider.supportedSignatureAlgorithms().forEach { alg ->
                val managedKeyPair = mobileCryptoProvider.generateKeyAsync(alg = alg)
                val keyInfo = managedKeyPair.joseToManagedKeyInfo()
                assertNotNull(keyInfo)
                val signature = mobileCryptoProvider.createRawSignature(keyInfo = keyInfo, input = "test".encodeToByteArray(), false)
                assertNotNull(signature)
                val verification = mobileCryptoProvider.isValidRawSignature(keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray())
                assertTrue(verification)
            }
        }

    @Test
    fun testGenerateKeyThrowsExceptionForUnsupportedCurve() =
        runTest {
            val unsupportedAlg = SignatureAlgorithm.ED25519
            val exception =
                assertFailsWith<IllegalArgumentException> {
                    mobileCryptoProvider.generateKeyAsync(alg = unsupportedAlg)
                }
            assertEquals("Curve ${unsupportedAlg.curve!!.jose.name} not supported for EcDSA", exception.message)
        }

    @Test
    fun testSupportedKeyTypes() {
        val keyTypes = mobileCryptoProvider.supportedKeyTypes()
        assertContentEquals(arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA), keyTypes)
    }

    @Test
    fun testCapabilitiesAdvertiseScdsaPhase1Primitives() {
        val capabilities = mobileCryptoProvider.getCapabilities()
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.SIGN_DIGEST))
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.VERIFY_DIGEST))
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.KEY_AGREEMENT))
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.ECDH_DERIVE_RAW_X))
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.ECDH_DERIVE_KDF))
        assertTrue(capabilities.supportsOperation(KmsProviderOperation.EC_POINT_MULTIPLY))
        assertFalse(capabilities.supportsOperation(KmsProviderOperation.KEY_ATTESTATION))
    }

    @Test
    fun testProviderBackedEcdhDeriveRawX() =
        runTest {
            val alice = mobileCryptoProvider.generateKeyAsync(alias = "alice-mobile-ecdh", alg = SignatureAlgorithm.ECDSA_SHA256)
            val bob = mobileCryptoProvider.generateKeyAsync(alias = "bob-mobile-ecdh", alg = SignatureAlgorithm.ECDSA_SHA256)
            val aliceKeyInfo = alice.joseToManagedKeyInfo()
            val bobKeyInfo = bob.joseToManagedKeyInfo()

            val aliceSecret =
                mobileCryptoProvider.ecdhDerive(
                    privateKeyInfo = aliceKeyInfo,
                    publicKeyInfo = bobKeyInfo,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    mode = EcdhDeriveMode.RAW_X,
                )
            val bobSecret =
                mobileCryptoProvider.ecdhDerive(
                    privateKeyInfo = bobKeyInfo,
                    publicKeyInfo = aliceKeyInfo,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    mode = EcdhDeriveMode.RAW_X,
                )

            assertEquals(32, aliceSecret.derivedSecret.size)
            assertContentEquals(aliceSecret.derivedSecret, bobSecret.derivedSecret)
        }

    @Test
    fun testProviderBackedEcdhDeriveConcatKdf() =
        runTest {
            val alice = mobileCryptoProvider.generateKeyAsync(alias = "alice-mobile-ecdh-kdf", alg = SignatureAlgorithm.ECDSA_SHA256)
            val bob = mobileCryptoProvider.generateKeyAsync(alias = "bob-mobile-ecdh-kdf", alg = SignatureAlgorithm.ECDSA_SHA256)

            val result =
                mobileCryptoProvider.ecdhDerive(
                    privateKeyInfo = alice.joseToManagedKeyInfo(),
                    publicKeyInfo = bob.joseToManagedKeyInfo(),
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
            val alice = mobileCryptoProvider.generateKeyAsync(alias = "alice-mobile-point-multiply", alg = SignatureAlgorithm.ECDSA_SHA256)
            val bob = mobileCryptoProvider.generateKeyAsync(alias = "bob-mobile-point-multiply", alg = SignatureAlgorithm.ECDSA_SHA256)
            val aliceKeyInfo = alice.joseToManagedKeyInfo()
            val bobKeyInfo = bob.joseToManagedKeyInfo()

            val rawEcdh =
                mobileCryptoProvider.ecdhDerive(
                    privateKeyInfo = aliceKeyInfo,
                    publicKeyInfo = bobKeyInfo,
                    algorithm = KeyAgreementAlgorithm.ECDH_ES,
                    mode = EcdhDeriveMode.RAW_X,
                )
            val pointMultiply =
                mobileCryptoProvider.ecPointMultiply(
                    privateKeyInfo = aliceKeyInfo,
                    publicKeyInfo = bobKeyInfo,
                    output = EcPointMultiplyOutput.RAW_X,
                )

            assertContentEquals(rawEcdh.derivedSecret, pointMultiply.rawX)
        }

    @Test
    fun testSupportedAlg() {
        val algorithms = mobileCryptoProvider.supportedSignatureAlgorithms()
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
                SignatureAlgorithm.RSA_SSA_PSS_RAW_MGF1,
            ),
            algorithms,
        )
    }
}

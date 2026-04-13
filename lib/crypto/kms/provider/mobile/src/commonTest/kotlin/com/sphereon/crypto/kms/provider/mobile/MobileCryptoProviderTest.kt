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
import com.sphereon.core.defaults.app.staticMinimalTestAppGraph
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
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
        val session = user.sessionContextManager.createOrGetFromId("test-session")

        mobileCryptoProvider = MobileKmsProviderImpl(MobileKmsProviderConfig(id = "test-mobile"), execution = session.asCoreApiServiceGraph().serviceExecution)
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

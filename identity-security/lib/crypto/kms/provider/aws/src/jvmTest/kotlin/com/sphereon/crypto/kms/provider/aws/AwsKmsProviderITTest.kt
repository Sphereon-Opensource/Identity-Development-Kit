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

package com.sphereon.crypto.kms.provider.aws

import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AwsKmsProviderITTest {
    private lateinit var awsKmsCryptoProvider: AwsKmsCryptoProvider

    var managedKeyPair: ManagedKeyPair? = null

    private val config = AwsKmsTestConfig(this)

    @BeforeTest
    fun setUp() {
        // Region comes from runtime configuration (`kms.providers.aws-kms-it.region`, or the
        // KMS_PROVIDERS_AWS_KMS_IT_REGION environment variable). Credentials come from the AWS SDK
        // default chain at runtime, so nothing about the target account is compiled into the build.
        assumeTrue(config.isConfigured("$PROVIDER_PREFIX.region")) {
            "AWS KMS live test skipped: '$PROVIDER_PREFIX.region' (environment: KMS_PROVIDERS_AWS_KMS_IT_REGION) is not configured"
        }
        config.putIfAbsent(
            "$PROVIDER_PREFIX.id" to PROVIDER_ID,
            "$PROVIDER_PREFIX.type" to "aws_kms",
            "$PROVIDER_PREFIX.application-id" to "aws-kms-test",
            "$PROVIDER_PREFIX.credential-opts.credential-mode" to "DEFAULT_CHAIN",
        )
        awsKmsCryptoProvider = config.createProvider(PROVIDER_ID) as AwsKmsCryptoProvider
        runBlocking {
            managedKeyPair = awsKmsCryptoProvider.generateKeyAsync(
                alias = "aws-kms-test-${System.currentTimeMillis()}",
                alg = SignatureAlgorithm.ECDSA_SHA256, keyOperations = arrayOf(
                    KeyOperations.SIGN, KeyOperations.VERIFY
                )
            )
        }
    }

    @Test
    fun testSupportedCurves() {
        val curves = awsKmsCryptoProvider.supportedCurves()
        assertContentEquals(
            arrayOf(Curve.P_256, Curve.P_384, Curve.P_521), curves
        )
    }

    @Test
    fun testSupportedKeyTypes() {
        val keyTypes = awsKmsCryptoProvider.supportedKeyTypes()
        assertContentEquals(
            arrayOf(KeyTypeMapping.EC), keyTypes
        )
    }

    @Test
    fun testSupportedDigests() {
        val digests = awsKmsCryptoProvider.supportedDigests()
        assertContentEquals(
            arrayOf(DigestAlg.SHA256, DigestAlg.SHA384, DigestAlg.SHA512), digests
        )
    }

    @Test
    fun testGenerateKeyAsyncECDSA_SHA256() = runTest {
        val kp = managedKeyPair ?: throw AssertionError("Managed key pair is null")
        assertNotNull(kp)
        assertNotNull(kp.cborToManagedKeyInfo().key.kid)
        assertEquals(JwaKeyType.EC, kp.jose.publicJwk.kty)
        assertEquals(JwaAlgorithm.ES256, kp.jose.publicJwk.alg)
        println(kp.jose.publicJwk.toString())

        awsKmsCryptoProvider.deleteKey(kp.toManagedKeyInfo<Jwk>(visibility = KeyVisibility.PUBLIC, keyEncoding = KeyEncoding.JOSE))

    }

    @Test
    fun testGenerateKeyAsyncECDSA_SHA384() = runTest {
        val managedKeyPair = awsKmsCryptoProvider.generateKeyAsync(
            alg = SignatureAlgorithm.ECDSA_SHA384, keyOperations = arrayOf(
                KeyOperations.SIGN, KeyOperations.VERIFY
            )
        )
        assertNotNull(managedKeyPair)
        assertNotNull(managedKeyPair.joseToManagedKeyInfo().key.kid)
        assertEquals(JwaKeyType.EC, managedKeyPair.jose.publicJwk.kty)
        assertEquals(JwaAlgorithm.ES384, managedKeyPair.jose.publicJwk.alg)
        awsKmsCryptoProvider.deleteKey(managedKeyPair.toManagedKeyInfo<Jwk>(visibility = KeyVisibility.PUBLIC, keyEncoding = KeyEncoding.JOSE))
    }

    @Test
    fun testGenerateKeyAsyncECDSA_SHA512() = runTest {
        val managedKeyPair = awsKmsCryptoProvider.generateKeyAsync(
            alg = SignatureAlgorithm.ECDSA_SHA512, keyOperations = arrayOf(
                KeyOperations.SIGN, KeyOperations.VERIFY
            )
        )
        assertNotNull(managedKeyPair)
        assertNotNull(managedKeyPair.cborToManagedKeyInfo().key.kid)
        assertEquals(JwaKeyType.EC, managedKeyPair.jose.publicJwk.kty)
        assertEquals(JwaAlgorithm.ES512, managedKeyPair.jose.publicJwk.alg)
        awsKmsCryptoProvider.deleteKey(managedKeyPair.toManagedKeyInfo<Jwk>(visibility = KeyVisibility.PUBLIC, keyEncoding = KeyEncoding.JOSE))
    }

    @Test
    fun testValidRawSignatureAndVerification() = runTest {
//        val managedKeyPair = awsKmsCryptoProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair!!.joseToManagedKeyInfo()
        assertNotNull(keyInfo)
        val signature = awsKmsCryptoProvider.createRawSignature(
            keyInfo = keyInfo, input = "test".encodeToByteArray(), false
        )
        assertNotNull(signature)
        val verification = awsKmsCryptoProvider.isValidRawSignature(
            keyInfo = keyInfo, signature = signature, input = "test".encodeToByteArray()
        )
        assertTrue(verification)
//        awsKmsCryptoProvider.deleteKey(managedKeyPair.toManagedKeyInfo<Jwk>(visibility = KeyVisibility.PUBLIC, keyEncoding = KeyEncoding.JOSE))
    }

    @Test
    fun testInvalidRawSignatureAndVerification() = runTest {
//        val managedKeyPair = awsKmsCryptoProvider.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo = managedKeyPair!!.joseToManagedKeyInfo()
        assertNotNull(keyInfo)
        val signature = awsKmsCryptoProvider.createRawSignature(
            keyInfo = keyInfo, input = "test".encodeToByteArray(), false
        )
        assertNotNull(signature)
        val verification = awsKmsCryptoProvider.isValidRawSignature(
            keyInfo = keyInfo, signature = signature, input = "test2".encodeToByteArray()
        )
        assertFalse(verification)
//        awsKmsCryptoProvider.deleteKey(managedKeyPair.toManagedKeyInfo<Jwk>(visibility = KeyVisibility.PUBLIC, keyEncoding = KeyEncoding.JOSE))
    }

    @Test
    fun testGenerateKeyThrowsExceptionForUnsupportedAlgorithm() = runTest {
        val unsupportedAlg = SignatureAlgorithm.ED25519
        val exception = assertFailsWith<IllegalArgumentException> {
            awsKmsCryptoProvider.generateKeyAsync(alg = unsupportedAlg)
        }
        assertEquals("Signature algorithm Ed25519 is not supported by AWS KMS", exception.message)
    }

    @Test
    fun testSupportedAlg() {
        val algorithms = awsKmsCryptoProvider.supportedSignatureAlgorithms()
        assertContentEquals(
            arrayOf(
                SignatureAlgorithm.ECDSA_SHA256,
                SignatureAlgorithm.ECDSA_SHA384,
                SignatureAlgorithm.ECDSA_SHA512
            ), algorithms
        )
    }

    @AfterTest
    fun tearDown() {
        if (::awsKmsCryptoProvider.isInitialized) awsKmsCryptoProvider.close()
        config.reset()
    }

    private companion object {
        const val PROVIDER_ID = "aws-kms-it"
        const val PROVIDER_PREFIX = "kms.providers.$PROVIDER_ID"
    }
}

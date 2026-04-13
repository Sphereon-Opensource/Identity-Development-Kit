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
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.model.AccessKeyCredentialOpts
import com.sphereon.crypto.core.kms.model.AwsKmsClientConfig
import com.sphereon.crypto.core.kms.model.CredentialMode
import com.sphereon.crypto.core.kms.model.CredentialOpts
import com.sphereon.crypto.core.kms.model.ExponentialBackoffRetryOpts
import com.sphereon.crypto.core.kms.model.KeyProviderConfig
import com.sphereon.crypto.core.kms.model.KeyProviderSettings
import com.sphereon.crypto.core.kms.model.KeyProviderType
import com.sphereon.crypto.kms.aws.BuildKonfig
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

    @BeforeTest
    fun setUp() {
        val awsConfig = AwsKmsClientConfig(
            applicationId = "aws-kms-test",
            region = BuildKonfig.AWS_REGION ?: throw IllegalArgumentException("Missing AWS region env var AWS_REGION"),
            credentialOpts = CredentialOpts(
                credentialMode = CredentialMode.ACCESS_KEY,
                accessKeyCredentialOpts = AccessKeyCredentialOpts(
                    accessKeyId = BuildKonfig.AWS_ACCESS_KEY_ID ?: throw IllegalArgumentException("Missing AWS access key id env var AWS_ACCESS_KEY_ID"),
                    secretAccessKey = BuildKonfig.AWS_SECRET_ACCESS_KEY ?: throw IllegalArgumentException("Missing AWS secret access key env var AWS_SECRET_ACCESS_KEY")
                )
            ),
            exponentialBackoffRetryOpts = ExponentialBackoffRetryOpts(
                maxRetries = 10, // let's try max 10 times
                baseDelayInMS = 500, // Wait 0.5 seconds the first time
                maxDelayInMS = 15000 // Wait for max 15 seconds eventually
            )
        )
        val settings = KeyProviderSettings(id = "aws-kms-test", config = KeyProviderConfig(type = KeyProviderType.AWS_KMS, aws = awsConfig))
        awsKmsCryptoProvider = AwsKmsCryptoProvider(settings)
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
}

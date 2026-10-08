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

package com.sphereon.crypto.core.x509

import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.CryptoConst
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class X509ValidationContextTest {
    private val testCertificate =
        Certificate(
            der = byteArrayOf(0x30.toByte(), 0x82.toByte(), 0x01.toByte(), 0x00.toByte()),
            fingerPrint = "ABC123",
            serialNumber = "123456",
            issuerDN = "CN=Test Issuer,O=Test Org",
            subjectDN = "CN=Test Subject,O=Test Org",
            notBefore = Instant.parse("2024-01-01T00:00:00Z"),
            notAfter = Instant.parse("2025-12-31T23:59:59Z"),
        )

    private val testJwk =
        Jwk(
            generateKid = false,
            kty = JwaKeyType.EC,
            crv = com.sphereon.crypto.core.jose.JwaCurve.P_256,
            x = "testX",
            y = "testY",
        )

    private val testVerificationTime = LocalDateTimeKMP.now()

    private val testRequest =
        X509VerificationRequest(
            enabled = true,
            chainPEM = arrayOf("test-pem"),
            trustedCerts = arrayOf("trusted-cert"),
            verificationProfile = X509VerificationProfile.RFC_5280,
            verificationTime = testVerificationTime,
        )

    private val testContext =
        X509ValidationContext(
            request = testRequest,
            verificationAt = testVerificationTime,
            certificateChain = arrayOf(testCertificate),
            leafCertificate = testCertificate,
            publicKey = testJwk,
        )

    @Test
    fun successResultShouldCreateValidSuccessResult(): TestResult =
        runTest {
            val result = testContext.successResult()

            assertEquals(CryptoConst.X509_LITERAL, result.name)
            assertEquals("Certificate chain validated", result.message)
            assertFalse(result.error)
            assertFalse(result.critical)
            assertEquals(1, result.certificateChain.size)
            assertEquals(testCertificate, result.certificateChain[0])
            assertNotNull(result.publicKey)
            assertEquals(testVerificationTime, result.verificationTime)
        }

    @Test
    fun successResultShouldAcceptCustomMessage(): TestResult =
        runTest {
            val customMessage = "Custom success message"
            val result = testContext.successResult(message = customMessage)

            assertEquals(customMessage, result.message)
            assertFalse(result.error)
            assertFalse(result.critical)
        }

    @Test
    fun errorResultWithMessageShouldCreateValidErrorResult(): TestResult =
        runTest {
            val errorMessage = "Certificate validation failed"
            val result = testContext.errorResult(message = errorMessage)

            assertEquals(CryptoConst.X509_LITERAL, result.name)
            assertEquals(errorMessage, result.message)
            assertTrue(result.error)
            assertFalse(result.critical)
            assertNull(result.detailMessage)
            assertEquals(testVerificationTime, result.verificationTime)
        }

    @Test
    fun errorResultWithCriticalFlagShouldBeCritical(): TestResult =
        runTest {
            val result =
                testContext.errorResult(
                    message = "Critical error",
                    critical = true,
                )

            assertTrue(result.error)
            assertTrue(result.critical)
        }

    @Test
    fun errorResultWithDetailMessageShouldIncludeDetail(): TestResult =
        runTest {
            val errorMessage = "Certificate expired"
            val detailMessage = "Certificate expired on 2024-01-01"
            val result =
                testContext.errorResult(
                    message = errorMessage,
                    critical = false,
                    detailMessage = detailMessage,
                )

            assertEquals(errorMessage, result.message)
            assertEquals(detailMessage, result.detailMessage)
        }

    @Test
    fun errorResultFromExceptionShouldExtractExceptionDetails(): TestResult =
        runTest {
            val exception = IllegalArgumentException("Invalid certificate format")
            val result = testContext.errorResult(ex = exception)

            assertEquals(CryptoConst.X509_LITERAL, result.name)
            assertEquals("Invalid certificate format", result.message)
            assertTrue(result.error)
            assertFalse(result.critical)
            assertNotNull(result.detailMessage)
            assertTrue(result.detailMessage!!.contains("IllegalArgumentException"))
        }

    @Test
    fun errorResultFromExceptionWithCriticalFlagShouldBeCritical(): TestResult =
        runTest {
            val exception = RuntimeException("Critical failure")
            val result = testContext.errorResult(ex = exception, critical = true)

            assertTrue(result.error)
            assertTrue(result.critical)
            assertEquals("Critical failure", result.message)
        }

    @Test
    fun errorResultFromExceptionWithNullMessageShouldUseToString(): TestResult =
        runTest {
            // Create an exception with null message
            val exception =
                object : Exception() {
                    override val message: String? = null

                    override fun toString(): String = "CustomException[]"
                }
            val result = testContext.errorResult(ex = exception)

            assertEquals("CustomException[]", result.message)
        }

    @Test
    fun disabledResultShouldCreateValidDisabledResult(): TestResult =
        runTest {
            val result = testContext.disabledResult()

            assertEquals(CryptoConst.X509_LITERAL, result.name)
            assertEquals("X509 verification has been disabled", result.message)
            assertFalse(result.error)
            assertFalse(result.critical)
            assertEquals(testVerificationTime, result.verificationTime)
        }

    @Test
    fun allResultsShouldPreserveCertificateChain(): TestResult =
        runTest {
            val successResult = testContext.successResult()
            val errorResult = testContext.errorResult("Error")
            val disabledResult = testContext.disabledResult()

            assertEquals(1, successResult.certificateChain.size)
            assertEquals(1, errorResult.certificateChain.size)
            assertEquals(1, disabledResult.certificateChain.size)

            assertEquals(testCertificate, successResult.certificateChain[0])
            assertEquals(testCertificate, errorResult.certificateChain[0])
            assertEquals(testCertificate, disabledResult.certificateChain[0])
        }

    @Test
    fun allResultsShouldPreservePublicKey(): TestResult =
        runTest {
            val successResult = testContext.successResult()
            val errorResult = testContext.errorResult("Error")
            val disabledResult = testContext.disabledResult()

            assertEquals(testJwk, successResult.publicKey)
            assertEquals(testJwk, errorResult.publicKey)
            assertEquals(testJwk, disabledResult.publicKey)
        }

    @Test
    fun contextWithNullPublicKeyShouldWorkCorrectly(): TestResult =
        runTest {
            val contextWithNullKey =
                X509ValidationContext(
                    request = testRequest,
                    verificationAt = testVerificationTime,
                    certificateChain = arrayOf(testCertificate),
                    leafCertificate = testCertificate,
                    publicKey = null,
                )

            val successResult = contextWithNullKey.successResult()
            val errorResult = contextWithNullKey.errorResult("Error")
            val disabledResult = contextWithNullKey.disabledResult()

            assertNull(successResult.publicKey)
            assertNull(errorResult.publicKey)
            assertNull(disabledResult.publicKey)
        }
}

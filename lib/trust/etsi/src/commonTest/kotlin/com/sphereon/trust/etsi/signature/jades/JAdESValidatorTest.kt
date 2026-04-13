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

package com.sphereon.trust.etsi.signature.jades

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.trust.etsi.testutil.EtsiTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for JAdES validator — focused on structural/parsing validation.
 *
 * Since no real JAdES-signed trust lists (ETSI TS 119 602 LoTE) exist yet,
 * these tests validate the JWS parsing, ETSI header extraction, and error
 * reporting paths. Cryptographic signature verification is tested implicitly
 * through the validation pipeline.
 */
class JAdESValidatorTest {
    private val testContext = EtsiTestContext("jades-test", this)
    private val validator: JAdESValidator = testContext.jadesValidator

    // Dummy certificate (Base64 "AAAA" decodes to 3 zero bytes)
    private val certBase64 = "AAAA"

    private fun base64url(s: String): String = s.encodeToByteArray().encodeTo(Encoding.BASE64URL)

    private fun buildCompactJws(
        headerJson: String = """{"alg":"RS256","x5c":["$certBase64"]}""",
        payloadJson: String = """{"data":"test"}""",
        signatureB64url: String = "AAAA",
    ): String {
        val header = base64url(headerJson)
        val payload = base64url(payloadJson)
        return "$header.$payload.$signatureB64url"
    }

    @Test
    fun shouldParseJAdESSigningTime() =
        runTest {
            val headerJson = """{"alg":"RS256","x5c":["$certBase64"],"sigT":"2024-01-15T10:30:00Z"}"""
            val jws = buildCompactJws(headerJson = headerJson)
            val result = validator.validate(jws.encodeToByteArray())

            // Crypto will fail with dummy cert, but ETSI headers should still be extracted
            assertNotNull(result.etsiHeaders)
            assertEquals("2024-01-15T10:30:00Z", result.etsiHeaders!!.sigT)
            assertNotNull(result.signingTime)
        }

    @Test
    fun shouldReportMissingX5c() =
        runTest {
            val headerJson = """{"alg":"RS256"}"""
            val jws = buildCompactJws(headerJson = headerJson)
            val result = validator.validate(jws.encodeToByteArray())

            assertFalse(result.valid)
            assertTrue(result.errors.any { "No x5c certificate chain" in it })
            assertNull(result.signingCertificate)
        }

    @Test
    fun shouldParseFlattenedJsonSerialization() =
        runTest {
            val headerB64 = base64url("""{"alg":"RS256","x5c":["$certBase64"]}""")
            val payloadB64 = base64url("""{"data":"test"}""")
            val flattenedJson = """{"protected":"$headerB64","payload":"$payloadB64","signature":"AAAA"}"""

            val result = validator.validate(flattenedJson.encodeToByteArray())

            // Should successfully parse flattened JWS (crypto may fail with dummy cert)
            assertNotNull(result.signingCertificate)
            assertNotNull(result.certificateChain)
        }

    @Test
    fun shouldParseGeneralJsonSerialization() =
        runTest {
            val headerB64 = base64url("""{"alg":"RS256","x5c":["$certBase64"]}""")
            val payloadB64 = base64url("""{"data":"test"}""")
            val generalJson = """{"payload":"$payloadB64","signatures":[{"protected":"$headerB64","signature":"AAAA"}]}"""

            val result = validator.validate(generalJson.encodeToByteArray())

            // Should successfully parse general JWS serialization
            assertNotNull(result.signingCertificate)
        }

    @Test
    fun shouldHandleDetachedContent() =
        runTest {
            val headerB64 = base64url("""{"alg":"RS256","x5c":["$certBase64"]}""")
            val detachedContent = "detached payload content".encodeToByteArray()

            // Compact JWS with empty payload (detached)
            val jws = "$headerB64..AAAA"

            val result = validator.validate(jws.encodeToByteArray(), detachedContent = detachedContent)

            // Should parse and process detached content (crypto may fail with dummy cert)
            assertNotNull(result.signingCertificate)
        }

    @Test
    fun shouldWarnAboutUnsupportedCriticalHeaders() =
        runTest {
            val headerJson = """{"alg":"RS256","x5c":["$certBase64"],"crit":["sigT","unknownHeader"],"sigT":"2024-01-15T10:30:00Z"}"""
            val jws = buildCompactJws(headerJson = headerJson)
            val result = validator.validate(jws.encodeToByteArray())

            assertNotNull(result.etsiHeaders)
            assertNotNull(result.etsiHeaders!!.crit)
            assertTrue(result.warnings.any { "Unsupported critical headers" in it })
        }

    @Test
    fun shouldRequireEtsiHeadersWhenOptionSet() =
        runTest {
            // JWS without sigT, but requireEtsiHeaders = true
            val jws = buildCompactJws()
            val result =
                validator.validate(
                    jws.encodeToByteArray(),
                    options = JAdESValidationOptions(requireEtsiHeaders = true),
                )

            assertFalse(result.valid)
            assertTrue(result.errors.any { "sigT" in it && "required" in it })
        }

    @Test
    fun shouldNotRequireEtsiHeadersByDefault() =
        runTest {
            // JWS without sigT, default options (requireEtsiHeaders = false)
            val jws = buildCompactJws()
            val result = validator.validate(jws.encodeToByteArray())

            // sigT should not be required by default
            assertNull(result.signingTime)
            assertFalse(result.errors.any { "sigT" in it && "required" in it })
        }

    @Test
    fun shouldReportInvalidCertificateThumbprint() =
        runTest {
            // Use a wrong thumbprint
            val wrongThumbprint = "AQIDBAUG"
            val headerJson = """{"alg":"RS256","x5c":["$certBase64"],"x5t#S256":"$wrongThumbprint"}"""
            val jws = buildCompactJws(headerJson = headerJson)
            val result = validator.validate(jws.encodeToByteArray())

            assertFalse(result.valid)
            assertTrue(result.errors.any { "thumbprint" in it.lowercase() })
        }

    @Test
    fun shouldParseSigDHeader() =
        runTest {
            val headerJson =
                """{"alg":"RS256","x5c":["$certBase64"],""" +
                    """"sigD":{"mId":"http://uri.etsi.org/19182/HttpHeaders",""" +
                    """"pars":["https://example.com/doc"],"hashM":"S256",""" +
                    """"hashV":["abc123"],"ctys":["application/json"]}}"""
            val jws = buildCompactJws(headerJson = headerJson)
            val result = validator.validate(jws.encodeToByteArray())

            assertNotNull(result.etsiHeaders?.sigD)
            val sigD = result.etsiHeaders!!.sigD!!
            assertEquals("http://uri.etsi.org/19182/HttpHeaders", sigD.mId)
            assertEquals(listOf("https://example.com/doc"), sigD.pars)
            assertEquals("S256", sigD.hashM)
            assertEquals(listOf("abc123"), sigD.hashV)
            assertEquals(listOf("application/json"), sigD.ctys)
        }

    @Test
    fun shouldReportInvalidJwsFormat() =
        runTest {
            val result = validator.validate("not-a-valid-jws".encodeToByteArray())

            assertFalse(result.valid)
            assertTrue(result.errors.isNotEmpty())
        }

    @Test
    fun shouldReturnCertificateChain() =
        runTest {
            val jws = buildCompactJws()
            val result = validator.validate(jws.encodeToByteArray())

            assertNotNull(result.signingCertificate)
            assertNotNull(result.certificateChain)
            assertTrue(result.certificateChain!!.isNotEmpty())
            assertTrue(result.signingCertificate!!.contentEquals(result.certificateChain!!.first()))
        }

    @Test
    fun shouldExtractMultipleCertificatesFromX5c() =
        runTest {
            val cert2 = "BBBB"
            val headerJson = """{"alg":"RS256","x5c":["$certBase64","$cert2"]}"""
            val jws = buildCompactJws(headerJson = headerJson)
            val result = validator.validate(jws.encodeToByteArray())

            assertNotNull(result.certificateChain)
            assertEquals(2, result.certificateChain!!.size)
        }
}

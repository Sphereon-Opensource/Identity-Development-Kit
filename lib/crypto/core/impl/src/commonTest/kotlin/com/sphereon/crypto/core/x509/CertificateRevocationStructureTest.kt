/*
 * © 2025 Sphereon International B.V.
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

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for certificate revocation related structures and constants.
 *
 * Note: Actual CRL/OCSP revocation checking is not yet fully implemented.
 * When implemented, additional integration tests should be added for:
 * - CRL distribution point retrieval and parsing
 * - OCSP responder interaction
 * - Revocation status checking
 */
class CertificateRevocationStructureTest {

    // ==========================================
    // CRL/OCSP Extension OID Tests
    // ==========================================

    @Test
    fun testCrlDistributionPointsOid(): TestResult = runTest {
        // RFC 5280 Section 4.2.1.13
        assertEquals("2.5.29.31", X509ExtensionOids.CRL_DISTRIBUTION_POINTS)
    }

    @Test
    fun testFreshestCrlOid(): TestResult = runTest {
        // RFC 5280 Section 4.2.1.15 (Delta CRL Distribution Points)
        assertEquals("2.5.29.46", X509ExtensionOids.FRESHEST_CRL)
    }

    @Test
    fun testCrlNumberOid(): TestResult = runTest {
        // RFC 5280 Section 5.2.3
        assertEquals("2.5.29.20", X509ExtensionOids.CRL_NUMBER)
    }

    @Test
    fun testDeltaCrlIndicatorOid(): TestResult = runTest {
        // RFC 5280 Section 5.2.4
        assertEquals("2.5.29.27", X509ExtensionOids.DELTA_CRL_INDICATOR)
    }

    @Test
    fun testIssuingDistributionPointOid(): TestResult = runTest {
        // RFC 5280 Section 5.2.5
        assertEquals("2.5.29.28", X509ExtensionOids.ISSUING_DISTRIBUTION_POINT)
    }

    @Test
    fun testReasonCodeOid(): TestResult = runTest {
        // RFC 5280 Section 5.3.1 - CRL entry extension
        assertEquals("2.5.29.21", X509ExtensionOids.REASON_CODE)
    }

    @Test
    fun testHoldInstructionCodeOid(): TestResult = runTest {
        // RFC 5280 Section 5.3.2 - CRL entry extension (obsolete)
        assertEquals("2.5.29.23", X509ExtensionOids.HOLD_INSTRUCTION_CODE)
    }

    @Test
    fun testInvalidityDateOid(): TestResult = runTest {
        // RFC 5280 Section 5.3.3 - CRL entry extension
        assertEquals("2.5.29.24", X509ExtensionOids.INVALIDITY_DATE)
    }

    @Test
    fun testCertificateIssuerOid(): TestResult = runTest {
        // RFC 5280 Section 5.3.4 - CRL entry extension
        assertEquals("2.5.29.29", X509ExtensionOids.CERTIFICATE_ISSUER)
    }

    @Test
    fun testOcspNoCheckOid(): TestResult = runTest {
        // RFC 6960 - OCSP No Check extension for OCSP responder certificates
        assertEquals("1.3.6.1.5.5.7.48.1.5", X509ExtensionOids.OCSP_NO_CHECK)
    }

    // ==========================================
    // X500 Attribute OID Tests
    // ==========================================

    @Test
    fun testDeltaRevocationListAttributeOid(): TestResult = runTest {
        assertEquals("2.5.4.53", X500AttributeTypeOids.DELTA_REVOCATION_LIST)
    }

    // ==========================================
    // Key Usage Flag Tests
    // ==========================================

    @Test
    fun testCrlSignKeyUsageFlag(): TestResult = runTest {
        val crlSign = KeyUsageFlag.CRL_SIGN

        assertEquals(6, crlSign.position)
        assertEquals("cRLSign", crlSign.value)
    }

    @Test
    fun testCrlSignInKeyUsageSet(): TestResult = runTest {
        // Create KeyUsage with CRL_SIGN flag set
        val keyUsage = KeyUsage(mapOf(
            KeyUsageFlag.CRL_SIGN to true,
            KeyUsageFlag.KEY_CERT_SIGN to false,
            KeyUsageFlag.DIGITAL_SIGNATURE to false
        ))

        assertTrue(keyUsage.has(KeyUsageFlag.CRL_SIGN))
        assertFalse(keyUsage.has(KeyUsageFlag.KEY_CERT_SIGN))
        assertFalse(keyUsage.has(KeyUsageFlag.DIGITAL_SIGNATURE))
    }

    @Test
    fun testCaKeyUsageWithCrlSign(): TestResult = runTest {
        // CA certificates typically have keyCertSign and cRLSign
        val keyUsage = KeyUsage(mapOf(
            KeyUsageFlag.KEY_CERT_SIGN to true,
            KeyUsageFlag.CRL_SIGN to true,
            KeyUsageFlag.DIGITAL_SIGNATURE to false
        ))

        assertTrue(keyUsage.has(KeyUsageFlag.KEY_CERT_SIGN))
        assertTrue(keyUsage.has(KeyUsageFlag.CRL_SIGN))
        assertFalse(keyUsage.has(KeyUsageFlag.DIGITAL_SIGNATURE))
    }

    @Test
    fun testEndEntityKeyUsageWithoutCrlSign(): TestResult = runTest {
        // End-entity certificates should NOT have cRLSign
        val keyUsage = KeyUsage(mapOf(
            KeyUsageFlag.DIGITAL_SIGNATURE to true,
            KeyUsageFlag.KEY_ENCIPHERMENT to true,
            KeyUsageFlag.CRL_SIGN to false
        ))

        assertFalse(keyUsage.has(KeyUsageFlag.CRL_SIGN))
        assertTrue(keyUsage.has(KeyUsageFlag.DIGITAL_SIGNATURE))
        assertTrue(keyUsage.has(KeyUsageFlag.KEY_ENCIPHERMENT))
    }

    // ==========================================
    // Verification Profile Tests
    // ==========================================

    @Test
    fun testVerificationProfileEnumValues(): TestResult = runTest {
        // Verify enum values exist
        val profiles = X509VerificationProfile.entries.toTypedArray()

        assertTrue(profiles.isNotEmpty())
        assertTrue(profiles.contains(X509VerificationProfile.ISO_18013_5))
        assertTrue(profiles.contains(X509VerificationProfile.RFC_5280))
    }

    @Test
    fun testIso18013VerificationProfile(): TestResult = runTest {
        val profile = X509VerificationProfile.ISO_18013_5
        assertNotNull(profile)
        assertEquals("ISO_18013_5", profile.name)
    }

    @Test
    fun testRfc5280VerificationProfile(): TestResult = runTest {
        val profile = X509VerificationProfile.RFC_5280
        assertNotNull(profile)
        assertEquals("RFC_5280", profile.name)
    }

    // ==========================================
    // X509 Verification Request Tests
    // ==========================================

    @Test
    fun testVerificationRequestWithProfile(): TestResult = runTest {
        val request = X509VerificationRequest(
            chainPEM = arrayOf("test-cert-pem"),
            trustedCerts = arrayOf("trusted-cert"),
            verificationProfile = X509VerificationProfile.RFC_5280
        )

        assertEquals(X509VerificationProfile.RFC_5280, request.verificationProfile)
        assertTrue(request.enabled)
    }

    @Test
    fun testVerificationRequestDisabled(): TestResult = runTest {
        // Verify the disabled flag is properly set
        val request = X509VerificationRequest(
            enabled = false,
            chainPEM = null,
            trustedCerts = null
        )

        assertFalse(request.enabled)
        // Note: validateToContext() requires valid certificate data to process,
        // but when disabled, it returns early with a non-error result.
        // Since we don't have a valid certificate chain, we just verify the flag is set correctly.
    }

    // ==========================================
    // Key Usage Flag Complete Set Test
    // ==========================================

    @Test
    fun testAllKeyUsageFlags(): TestResult = runTest {
        // Verify all standard key usage flags exist with correct positions
        val expectedFlags = listOf(
            KeyUsageFlag.DIGITAL_SIGNATURE to 0,
            KeyUsageFlag.NON_REPUDIATION to 1,
            KeyUsageFlag.KEY_ENCIPHERMENT to 2,
            KeyUsageFlag.DATA_ENCIPHERMENT to 3,
            KeyUsageFlag.KEY_AGREEMENT to 4,
            KeyUsageFlag.KEY_CERT_SIGN to 5,
            KeyUsageFlag.CRL_SIGN to 6,
            KeyUsageFlag.ENCIPHER_ONLY to 7,
            KeyUsageFlag.DECIPHER_ONLY to 8
        )

        expectedFlags.forEach { (flag, expectedPosition) ->
            assertEquals(expectedPosition, flag.position, "Position mismatch for ${flag.name}")
        }
    }
}

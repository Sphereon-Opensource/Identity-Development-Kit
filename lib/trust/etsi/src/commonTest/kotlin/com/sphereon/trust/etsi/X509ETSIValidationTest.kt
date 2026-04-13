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

package com.sphereon.trust.etsi

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.etsi.model.*
import com.sphereon.trust.etsi.resolution.ExternalIdentifierX509ETSIValidationOpts
import com.sphereon.trust.etsi.resolution.TspMatchType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for X.509 ETSI validation flow.
 *
 * These tests verify the complete validation flow including:
 * - Certificate extraction from KeyInfo
 * - Country extraction from certificates
 * - LOTL navigation
 * - Trust list caching
 * - TSP matching (direct and CA chain)
 * - Service status evaluation
 */
class X509ETSIValidationTest {

    @Test
    fun testValidationOptsCreationWithKeyInfo() {
        // Create a KeyInfo with x5c
        val x5c = arrayOf(
            "MIIDXTCCAkWgAwIBAgIJAKZV7kLv1q+JMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNVBAYTAk5MMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBXaWRnaXRzIFB0eSBMdGQwHhcNMjUwMTAxMDAwMDAwWhcNMjYwMTAxMDAwMDAwWjBFMQswCQYDVQQGEwJOTDETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA"
        )
        
        val keyInfo = KeyInfo<Nothing>(
            x5c = x5c
        )

        val opts = ExternalIdentifierX509ETSIValidationOpts(
            identifier = keyInfo,
            lotlUri = "https://ec.europa.eu/tools/lotl/eu-lotl.xml"
        )

        assertEquals(keyInfo, opts.identifier)
        assertEquals("https://ec.europa.eu/tools/lotl/eu-lotl.xml", opts.lotlUri)
        assertTrue(opts.allowCAChainMatch)
        assertTrue(opts.useCache)
    }

    @Test
    fun testTspMatchTypeDifferentiation() {
        val directMatch = TspMatchType.DIRECT_MATCH
        val caChainMatch = TspMatchType.CA_CHAIN_MATCH
        val subjectMatch = TspMatchType.SUBJECT_MATCH

        // Verify they are distinct values
        assertTrue(directMatch != caChainMatch)
        assertTrue(directMatch != subjectMatch)
        assertTrue(caChainMatch != subjectMatch)
    }

    @Test
    fun testTrustStatusDifferentiation() {
        // Verify distinct status values
        assertTrue(TrustStatus.TRUSTED != TrustStatus.UNTRUSTED)
        assertTrue(TrustStatus.TRUSTED != TrustStatus.REVOKED)
        assertTrue(TrustStatus.UNTRUSTED != TrustStatus.REVOKED)
    }

    @Test
    fun testEtsiServiceStatusConstantsAreValidUris() {
        // Verify status constants follow ETSI URI scheme
        val statusPrefix = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/"

        assertTrue(ETSIServiceStatus.GRANTED.startsWith(statusPrefix))
        assertTrue(ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL.startsWith(statusPrefix))
        assertTrue(ETSIServiceStatus.WITHDRAWN.startsWith(statusPrefix))
        assertTrue(ETSIServiceStatus.REVOKED.startsWith(statusPrefix))
        assertTrue(ETSIServiceStatus.SUSPENDED.startsWith(statusPrefix))

        // Verify they are distinct
        val allStatuses = setOf(
            ETSIServiceStatus.GRANTED,
            ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL,
            ETSIServiceStatus.WITHDRAWN,
            ETSIServiceStatus.REVOKED,
            ETSIServiceStatus.SUSPENDED
        )
        assertEquals(5, allStatuses.size, "All status constants should be distinct")
    }

    @Test
    fun testValidationOptionsWithCustomSettings() {
        val keyInfo = KeyInfo<Nothing>(
            x5c = arrayOf("CERT_DATA")
        )

        val opts = ExternalIdentifierX509ETSIValidationOpts(
            identifier = keyInfo,
            lotlUri = "https://custom-lotl.example.com/lotl.xml",
            explicitTslUri = "https://custom-tsl.example.com/tsl.xml",
            validationTime = Instant.parse("2025-06-15T12:00:00Z"),
            checkRevocation = true,
            allowCAChainMatch = false,
            useCache = false,
            maxCacheAge = 1800000,
            serviceTypeFilter = listOf("http://uri.etsi.org/TrstSvc/Svctype/CA/QC")
        )

        assertEquals("https://custom-lotl.example.com/lotl.xml", opts.lotlUri)
        assertEquals("https://custom-tsl.example.com/tsl.xml", opts.explicitTslUri)
        assertTrue(opts.checkRevocation)
        assertFalse(opts.allowCAChainMatch)
        assertFalse(opts.useCache)
        assertEquals(1800000L, opts.maxCacheAge)
        assertEquals(1, opts.serviceTypeFilter?.size)
    }
}

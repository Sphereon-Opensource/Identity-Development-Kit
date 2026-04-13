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

package com.sphereon.trust.etsi.matcher

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.trust.etsi.lote.model.MultiLangString
import com.sphereon.trust.etsi.model.*
import com.sphereon.trust.etsi.resolution.TspMatchType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CertificateTrustListMatcherTest {

    // Simulated certificate DER bytes
    private val certBytes = "test-certificate-data".encodeToByteArray()
    private val certBase64 = certBytes.encodeTo(Encoding.BASE64)
    private val otherCertBytes = "other-certificate-data".encodeToByteArray()
    private val otherCertBase64 = otherCertBytes.encodeTo(Encoding.BASE64)
    private val caCertBytes = "ca-certificate-data".encodeToByteArray()
    private val caCertBase64 = caCertBytes.encodeTo(Encoding.BASE64)

    private fun buildTrustList(
        serviceTypeIdentifier: String = "http://uri.etsi.org/19602/SvcType/PID/Issuance",
        serviceCertBase64: String = certBase64,
        serviceStatus: String = ETSIServiceStatus.NOTIFIED,
        statusStartingTime: Instant = Instant.parse("2024-01-01T00:00:00Z")
    ): ETSILoTE {
        return ETSILoTE(
            sequenceNumber = 1,
            type = "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric",
            schemeOperatorName = listOf(MultiLangString("en", "Test Operator")),
            statusDeterminationApproach = "http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate",
            schemeTerritory = "NL",
            listIssueDateTime = Instant.parse("2024-01-01T00:00:00Z"),
            nextUpdate = Instant.parse("2025-01-01T00:00:00Z"),
            trustedEntities = listOf(
                ETSITrustedEntity(
                    trustedEntityInformation = ETSITrustedEntityInformation(
                        name = listOf(MultiLangString("en", "Test Entity")),
                        address = ETSIOperatorAddress(postalAddresses = emptyList()),
                        identifier = "TSP-001"
                    ),
                    trustedEntityServices = listOf(
                        ETSITrustedEntityService(
                            serviceInformation = ETSIServiceInformation(
                                serviceTypeIdentifier = serviceTypeIdentifier,
                                serviceName = listOf(MultiLangString("en", "PID Issuance Service")),
                                serviceDigitalIdentity = ETSIServiceDigitalIdentity(
                                    x509Certificates = listOf(serviceCertBase64)
                                ),
                                serviceStatus = serviceStatus,
                                statusStartingTime = statusStartingTime
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun directMatchFindsCertInTrustList() {
        val trustList = buildTrustList()
        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = null,
            serviceTypeFilter = null,
            allowCAChainMatch = false
        )

        assertNotNull(result)
        assertEquals(TspMatchType.DIRECT_MATCH, result.matchType)
        assertEquals("Test Entity", result.entity.trustedEntityInformation.name.first().value)
        assertEquals("PID Issuance Service", result.serviceInfo.serviceName.first().value)
    }

    @Test
    fun noMatchReturnsNull() {
        val trustList = buildTrustList(serviceCertBase64 = otherCertBase64)
        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = null,
            serviceTypeFilter = null,
            allowCAChainMatch = false
        )

        assertNull(result)
    }

    @Test
    fun caChainMatchFindsIssuerCert() {
        // Trust list contains the CA cert
        val trustList = buildTrustList(serviceCertBase64 = caCertBase64)

        // Certificate chain: cert -> CA cert
        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = listOf(caCertBase64),
            serviceTypeFilter = null,
            allowCAChainMatch = true
        )

        assertNotNull(result)
        assertEquals(TspMatchType.CA_CHAIN_MATCH, result.matchType)
        assertNotNull(result.caChain)
    }

    @Test
    fun caChainMatchDisabledReturnsNull() {
        val trustList = buildTrustList(serviceCertBase64 = caCertBase64)

        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = listOf(caCertBase64),
            serviceTypeFilter = null,
            allowCAChainMatch = false
        )

        assertNull(result)
    }

    @Test
    fun serviceTypeFilterExcludesNonMatchingServices() {
        val trustList = buildTrustList(serviceTypeIdentifier = "http://uri.etsi.org/19602/SvcType/PID/Issuance")

        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = null,
            serviceTypeFilter = listOf("http://uri.etsi.org/19602/SvcType/WalletSolution/Issuance"),
            allowCAChainMatch = false
        )

        assertNull(result)
    }

    @Test
    fun serviceTypeFilterIncludesMatchingServices() {
        val trustList = buildTrustList(serviceTypeIdentifier = "http://uri.etsi.org/19602/SvcType/PID/Issuance")

        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = null,
            serviceTypeFilter = listOf("http://uri.etsi.org/19602/SvcType/PID/Issuance"),
            allowCAChainMatch = false
        )

        assertNotNull(result)
    }

    @Test
    fun nullServiceTypeFilterMatchesAll() {
        val trustList = buildTrustList()

        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = null,
            serviceTypeFilter = null,
            allowCAChainMatch = false
        )

        assertNotNull(result)
    }

    @Test
    fun validationTimeSkipsFutureServices() {
        val trustList = buildTrustList(
            statusStartingTime = Instant.parse("2030-01-01T00:00:00Z")
        )

        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = null,
            serviceTypeFilter = null,
            allowCAChainMatch = false,
            validationTime = Instant.parse("2025-06-01T00:00:00Z")
        )

        assertNull(result)
    }

    @Test
    fun nullValidationTimeSkipsTimeCheck() {
        val trustList = buildTrustList(
            statusStartingTime = Instant.parse("2030-01-01T00:00:00Z")
        )

        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = null,
            serviceTypeFilter = null,
            allowCAChainMatch = false,
            validationTime = null
        )

        assertNotNull(result)
    }

    @Test
    fun emptyTrustListReturnsNull() {
        val trustList = ETSILoTE(
            sequenceNumber = 1,
            type = "test",
            schemeOperatorName = emptyList(),
            statusDeterminationApproach = "test",
            schemeTerritory = "NL",
            listIssueDateTime = Instant.parse("2024-01-01T00:00:00Z"),
            nextUpdate = Instant.parse("2025-01-01T00:00:00Z"),
            trustedEntities = emptyList()
        )

        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = null,
            serviceTypeFilter = null,
            allowCAChainMatch = false
        )

        assertNull(result)
    }

    @Test
    fun matchResultContainsEntityInfo() {
        val trustList = buildTrustList()
        val result = CertificateTrustListMatcher.findMatchingEntity(
            trustList = trustList,
            certDER = certBytes,
            chain = null,
            serviceTypeFilter = null,
            allowCAChainMatch = false
        )

        assertNotNull(result)
        assertEquals("TSP-001", result.entity.trustedEntityInformation.identifier)
        assertEquals(ETSIServiceStatus.NOTIFIED, result.serviceInfo.serviceStatus)
    }
}

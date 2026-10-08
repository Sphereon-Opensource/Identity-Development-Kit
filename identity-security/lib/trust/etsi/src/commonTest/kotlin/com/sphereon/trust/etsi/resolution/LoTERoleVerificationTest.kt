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

package com.sphereon.trust.etsi.resolution

import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.trust.etsi.lote.model.EidasRole
import com.sphereon.trust.etsi.lote.model.LoTLServiceType
import com.sphereon.trust.etsi.lote.model.LoTEServiceType
import com.sphereon.trust.etsi.lote.model.LoTEType
import com.sphereon.trust.etsi.lote.model.MultiLangString
import com.sphereon.trust.etsi.model.ETSIAdditionalInformation
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.ETSIOtherLoTEPointer
import com.sphereon.trust.etsi.model.ETSIServiceStatus
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests for the role verification types and the pure helper functions
 * used by LoTERoleVerificationServiceImpl.
 *
 * Uses [RoleVerificationTestHelpers] to test internal logic without DI.
 */
class LoTERoleVerificationTest {

    @Test
    fun roleVerificationRequestCarriesConfiguredSignerRoots() {
        val roots = listOf(byteArrayOf(1, 2, 3))
        val request =
            RoleVerificationRequest(
                certificate = KeyInfo<Nothing>(x5c = arrayOf("AAAA")),
                role = EidasRole.PID_PROVIDER,
                trustedSignerRoots = roots,
            )

        assertContentEquals(roots.single(), request.trustedSignerRoots!!.single())
    }

    @Test
    fun missingPointerAndCertificateNotFoundHaveDistinctReasonCodes() {
        assertEquals("TRUST_LIST_POINTER_MISSING", TrustDiagnosticReasonCodes.TRUST_LIST_POINTER_MISSING)
        assertEquals("CERTIFICATE_NOT_FOUND", TrustDiagnosticReasonCodes.CERTIFICATE_NOT_FOUND)
    }

    @Test
    fun freshnessPolicyRejectsExpiredAndRolledBackTrustLists() {
        val now = Instant.parse("2026-08-20T10:00:00Z")

        assertEquals(
            TrustDiagnosticReasonCodes.TRUST_LIST_NEXT_UPDATE_EXPIRED,
            TrustListFreshnessPolicy.failureReason(
                sequenceNumber = 5,
                nextUpdate = Instant.parse("2026-08-20T09:59:59Z"),
                previousSequenceNumber = 4,
                now = now,
            ),
        )
        assertEquals(
            TrustDiagnosticReasonCodes.TRUST_LIST_SEQUENCE_ROLLBACK,
            TrustListFreshnessPolicy.failureReason(
                sequenceNumber = 3,
                nextUpdate = Instant.parse("2026-08-20T11:00:00Z"),
                previousSequenceNumber = 4,
                now = now,
            ),
        )
        assertNull(
            TrustListFreshnessPolicy.failureReason(
                sequenceNumber = 5,
                nextUpdate = Instant.parse("2026-08-20T11:00:00Z"),
                previousSequenceNumber = 4,
                now = now,
            ),
        )
    }
    @Test
    fun productionRoutingDoesNotUseLoTEPointersForQeaa() {
        val lotl = buildLotlWith602Pointers()

        val result = LoTERoleTrustListRouting.findPointersForRole(lotl, EidasRole.QEAA_PROVIDER, null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun productionRoutingDoesNotFallbackToOtherTerritories() {
        val lotl = buildLotlWith602Pointers()

        val result = LoTERoleTrustListRouting.findPointersForRole(lotl, EidasRole.PID_ISSUER, "NL")

        assertTrue(result.isEmpty())
    }

    @Test
    fun productionServiceFilterExcludesRevocationOnlyServices() {
        assertEquals(
            listOf(LoTEServiceType.PID_ISSUANCE),
            LoTERoleTrustListRouting.buildServiceTypeFilter(EidasRole.PID_PROVIDER),
        )
        assertEquals(
            listOf(LoTLServiceType.QEAA_ISSUANCE),
            LoTERoleTrustListRouting.buildServiceTypeFilter(EidasRole.QEAA_PROVIDER),
        )
    }

    @Test
    fun productionStatusPolicyFailsClosedForUnknownAndRevocation() {
        assertEquals(
            false to TrustStatus.UNKNOWN,
            LoTERoleTrustListStatusPolicy.evaluate("http://unknown/status"),
        )
        assertEquals(
            false to TrustStatus.REVOKED,
            LoTERoleTrustListStatusPolicy.evaluate(ETSIServiceStatus.REVOKED),
        )
    }

    @Test
    fun productionStatusPolicyAcceptsActiveOnlyAndRejectsEveryInactiveCategory() {
        listOf(
            ETSIServiceStatus.GRANTED to true,
            ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL to true,
            ETSIServiceStatus.WITHDRAWN to false,
            ETSIServiceStatus.SUSPENDED to false,
            ETSIServiceStatus.REVOKED to false,
            "" to false,
            "malformed-status" to false,
        ).forEach { (status, accepted) ->
            assertEquals(accepted, LoTERoleTrustListStatusPolicy.evaluate(status).first, status)
        }
    }

    // -- findPointersForRole tests (602 format) --

    @Test
    fun findPointers602FormatFiltersByLoTEType() {
        val lotl = buildLotlWith602Pointers()

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_PROVIDER, null)

        assertEquals(1, result.size)
        assertEquals("https://example.com/pid-providers.xml", result[0].location)
    }

    @Test
    fun findPointers602FormatWalletProvider() {
        val lotl = buildLotlWith602Pointers()

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.WALLET_PROVIDER, null)

        assertEquals(1, result.size)
        assertEquals("https://example.com/wallet-providers.xml", result[0].location)
    }

    @Test
    fun findPointers602FormatWithTerritoryFilter() {
        val lotl =
            buildLotlWith602Pointers(
                pointers =
                    listOf(
                        build602Pointer(LoTEType.EU_PID_PROVIDERS, "NL", "https://nl.example.com/pid.xml"),
                        build602Pointer(LoTEType.EU_PID_PROVIDERS, "BE", "https://be.example.com/pid.xml"),
                    ),
            )

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_PROVIDER, "NL")

        assertEquals(1, result.size)
        assertEquals("https://nl.example.com/pid.xml", result[0].location)
    }

    @Test
    fun findPointers602FormatTerritoryFilterDoesNotFallback() {
        val lotl = buildLotlWith602Pointers()

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_PROVIDER, "NL")

        assertTrue(result.isEmpty())
    }

    // -- findPointersForRole tests (612 format) --

    @Test
    fun findPointers612FormatFiltersByTerritory() {
        val lotl = buildLotlWith612Pointers()

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.QEAA_PROVIDER, "NL")

        assertEquals(1, result.size)
        assertEquals("https://nl.example.com/tl.xml", result[0].location)
    }

    @Test
    fun findPointers612FormatReturnsAllWithoutTerritory() {
        val lotl = buildLotlWith612Pointers()

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.QEAA_PROVIDER, null)

        assertEquals(2, result.size)
    }

    @Test
    fun findPointers602QualifierIsRecognizedAfterOrdinary612Metadata() {
        val pointer =
            ETSIOtherLoTEPointer(
                schemeOperatorName = listOf(MultiLangString("en", "PID Authority")),
                schemeTerritory = "EU",
                location = "https://example.com/pid-providers.xml",
                additionalInformation =
                    ETSIAdditionalInformation(
                        otherInformation =
                            listOf(
                                "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/TrustedList",
                                LoTEType.EU_PID_PROVIDERS,
                            ),
                    ),
            )
        val lotl = buildLotlWith602Pointers(pointers = listOf(pointer))

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_PROVIDER, null)

        assertEquals(listOf(pointer), result)
    }

    @Test
    fun ambiguous602QualifiersAreRejected() {
        val pointer =
            build602Pointer(LoTEType.EU_PID_PROVIDERS, "EU", "https://example.com/ambiguous.xml").copy(
                additionalInformation =
                    ETSIAdditionalInformation(
                        otherInformation = listOf(LoTEType.EU_PID_PROVIDERS, LoTEType.EU_WALLET_PROVIDERS),
                    ),
            )
        val lotl = buildLotlWith602Pointers(pointers = listOf(pointer))

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_PROVIDER, null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun malformedCrossProfileQualifierIsRejected() {
        val pointer =
            build602Pointer(LoTEType.EU_PID_PROVIDERS, "EU", "https://example.com/malformed.xml").copy(
                additionalInformation =
                    ETSIAdditionalInformation(
                        otherInformation =
                            listOf(
                                LoTEType.EU_PID_PROVIDERS,
                                "http://uri.etsi.org/19602/LoTEType/Unknown",
                            ),
                    ),
            )
        val lotl = buildLotlWith602Pointers(pointers = listOf(pointer))

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_PROVIDER, null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun findPointers602DoesNotFallbackWhenNoRoleMatch() {
        val lotl = buildLotlWith602Pointers()

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.REGISTRATION_CERTIFICATE_PROVIDER, null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun findPointersReturnsEmptyWithTerritoryFilterNoMatch() {
        val lotl = buildLotlWith612Pointers()

        // Territory "XX" doesn't match any pointer
        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_PROVIDER, "XX")

        assertTrue(result.isEmpty())
    }

    // -- buildServiceTypeFilter tests --

    @Test
    fun serviceTypeFilterUsesIssuanceOnly() {
        val filter = RoleVerificationTestHelpers.buildServiceTypeFilter(EidasRole.PID_PROVIDER)

        assertTrue(filter.contains(LoTEServiceType.PID_ISSUANCE))
        assertEquals(listOf(LoTEServiceType.PID_ISSUANCE), filter)
    }

    @Test
    fun serviceTypeFilterWalletProvider() {
        val filter = RoleVerificationTestHelpers.buildServiceTypeFilter(EidasRole.WALLET_PROVIDER)

        assertTrue(filter.contains(LoTEServiceType.WALLET_ISSUANCE))
        assertEquals(listOf(LoTEServiceType.WALLET_ISSUANCE), filter)
    }

    @Test
    fun qeaaFilterUsesOnlyMemberStateIssuanceType() {
        val filter = RoleVerificationTestHelpers.buildServiceTypeFilter(EidasRole.QEAA_PROVIDER)

        assertEquals(listOf(LoTLServiceType.QEAA_ISSUANCE), filter)
    }

    @Test
    fun registrationCertificateProviderFilterHasNoRevocation() {
        val filter = RoleVerificationTestHelpers.buildServiceTypeFilter(EidasRole.REGISTRATION_CERTIFICATE_PROVIDER)

        assertEquals(1, filter.size)
        assertTrue(filter.contains(LoTEServiceType.WRPRC_ISSUANCE))
    }

    // -- evaluateServiceStatus tests --

    @Test
    fun notified602StatusIsTrusted() {
        val (trusted, status) = RoleVerificationTestHelpers.evaluateServiceStatus(ETSIServiceStatus.NOTIFIED)
        assertTrue(trusted)
        assertEquals(TrustStatus.TRUSTED, status)
    }

    @Test
    fun withdrawn602StatusIsUntrusted() {
        val (trusted, status) = RoleVerificationTestHelpers.evaluateServiceStatus(ETSIServiceStatus.WITHDRAWN_602)
        assertFalse(trusted)
        assertEquals(TrustStatus.UNTRUSTED, status)
    }

    @Test
    fun granted612StatusIsTrusted() {
        val (trusted, status) = RoleVerificationTestHelpers.evaluateServiceStatus(ETSIServiceStatus.GRANTED)
        assertTrue(trusted)
        assertEquals(TrustStatus.TRUSTED, status)
    }

    @Test
    fun recognisedNationalLevel612IsTrusted() {
        val (trusted, status) = RoleVerificationTestHelpers.evaluateServiceStatus(ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL)
        assertTrue(trusted)
        assertEquals(TrustStatus.TRUSTED, status)
    }

    @Test
    fun revoked612StatusIsRevoked() {
        val (trusted, status) = RoleVerificationTestHelpers.evaluateServiceStatus(ETSIServiceStatus.REVOKED)
        assertFalse(trusted)
        assertEquals(TrustStatus.REVOKED, status)
    }

    @Test
    fun withdrawn612StatusIsUntrusted() {
        val (trusted, status) = RoleVerificationTestHelpers.evaluateServiceStatus(ETSIServiceStatus.WITHDRAWN)
        assertFalse(trusted)
        assertEquals(TrustStatus.UNTRUSTED, status)
    }

    @Test
    fun suspended612StatusIsUntrusted() {
        val (trusted, status) = RoleVerificationTestHelpers.evaluateServiceStatus(ETSIServiceStatus.SUSPENDED)
        assertFalse(trusted)
        assertEquals(TrustStatus.UNTRUSTED, status)
    }

    @Test
    fun unknownStatusIsUnknown() {
        val (trusted, status) = RoleVerificationTestHelpers.evaluateServiceStatus("http://unknown/status")
        assertFalse(trusted)
        assertEquals(TrustStatus.UNKNOWN, status)
    }

    // -- MatchStrategy tests --

    @Test
    fun matchStrategyValuesAreDistinct() {
        val values = MatchStrategy.entries.toSet()
        assertEquals(3, values.size)
        assertTrue(values.contains(MatchStrategy.DIRECT_ONLY))
        assertTrue(values.contains(MatchStrategy.CA_CHAIN_ONLY))
        assertTrue(values.contains(MatchStrategy.DIRECT_THEN_CA_CHAIN))
    }

    // -- RoleVerificationResult tests --

    @Test
    fun roleVerificationResultNotVerifiedHasNoMatchedEntity() {
        val result =
            RoleVerificationResult(
                verified = false,
                role = EidasRole.PID_PROVIDER,
                trustStatus = TrustStatus.UNTRUSTED,
                matchedEntity = null,
                trustListInfo = null,
                details = "Not found",
                verifiedAt = Clock.System.now(),
            )

        assertFalse(result.verified)
        assertEquals(null, result.matchedEntity)
        assertEquals(EidasRole.PID_PROVIDER, result.role)
    }

    @Test
    fun roleDiscoveryResultContainsAllCheckedRoles() {
        val results =
            EidasRole.entries.map { role ->
                RoleVerificationResult(
                    verified = false,
                    role = role,
                    trustStatus = TrustStatus.UNTRUSTED,
                    matchedEntity = null,
                    trustListInfo = null,
                    details = "Not found",
                    verifiedAt = Clock.System.now(),
                )
            }

        val discovery =
            RoleDiscoveryResult(
                verifiedRoles = results,
                discoveredAt = Clock.System.now(),
            )

        assertEquals(EidasRole.entries.size, discovery.verifiedRoles.size)
    }

    @Test
    fun matchedEntityInfoPreservesAllFields() {
        val entity =
            MatchedEntityInfo(
                entityName = "Test TSP",
                entityIdentifier = "TSP-001",
                serviceName = "PID Service",
                serviceType = LoTEServiceType.PID_ISSUANCE,
                serviceStatus = ETSIServiceStatus.NOTIFIED,
                statusStartingTime = Instant.parse("2024-06-01T00:00:00Z"),
                matchType = TspMatchType.DIRECT_MATCH,
                territory = "NL",
            )

        assertEquals("Test TSP", entity.entityName)
        assertEquals("TSP-001", entity.entityIdentifier)
        assertEquals("PID Service", entity.serviceName)
        assertEquals(LoTEServiceType.PID_ISSUANCE, entity.serviceType)
        assertEquals(ETSIServiceStatus.NOTIFIED, entity.serviceStatus)
        assertEquals(TspMatchType.DIRECT_MATCH, entity.matchType)
        assertEquals("NL", entity.territory)
    }

    // -- Helpers --

    private fun build602Pointer(
        loTEType: String,
        territory: String,
        location: String,
    ): ETSIOtherLoTEPointer =
        ETSIOtherLoTEPointer(
            schemeOperatorName = listOf(MultiLangString("en", "Test Operator")),
            schemeTerritory = territory,
            location = location,
            additionalInformation =
                ETSIAdditionalInformation(
                    otherInformation = listOf(loTEType),
                ),
        )

    private fun buildLotlWith602Pointers(
        pointers: List<ETSIOtherLoTEPointer> =
            listOf(
                build602Pointer(LoTEType.EU_PID_PROVIDERS, "EU", "https://example.com/pid-providers.xml"),
                build602Pointer(LoTEType.EU_WALLET_PROVIDERS, "EU", "https://example.com/wallet-providers.xml"),
            ),
    ): ETSILoTE =
        ETSILoTE(
            sequenceNumber = 1,
            type = "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists",
            schemeOperatorName = listOf(MultiLangString("en", "EU Commission")),
            statusDeterminationApproach = "http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate",
            schemeTerritory = "EU",
            listIssueDateTime = Instant.parse("2024-01-01T00:00:00Z"),
            nextUpdate = Instant.parse("2025-01-01T00:00:00Z"),
            trustedEntities = emptyList(),
            pointersToOtherLoTE = pointers,
        )

    private fun buildLotlWith612Pointers(): ETSILoTE =
        ETSILoTE(
            sequenceNumber = 1,
            type = "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists",
            schemeOperatorName = listOf(MultiLangString("en", "EU Commission")),
            statusDeterminationApproach = "http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate",
            schemeTerritory = "EU",
            listIssueDateTime = Instant.parse("2024-01-01T00:00:00Z"),
            nextUpdate = Instant.parse("2025-01-01T00:00:00Z"),
            trustedEntities = emptyList(),
            pointersToOtherLoTE =
                listOf(
                    ETSIOtherLoTEPointer(
                        schemeOperatorName = listOf(MultiLangString("en", "NL Authority")),
                        schemeTerritory = "NL",
                        location = "https://nl.example.com/tl.xml",
                        additionalInformation =
                            ETSIAdditionalInformation(
                                otherInformation =
                                    listOf(
                                        "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/TrustedList",
                                        "NL",
                                    ),
                            ),
                    ),
                    ETSIOtherLoTEPointer(
                        schemeOperatorName = listOf(MultiLangString("en", "BE Authority")),
                        schemeTerritory = "BE",
                        location = "https://be.example.com/tl.xml",
                        additionalInformation =
                            ETSIAdditionalInformation(
                                otherInformation =
                                    listOf(
                                        "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/TrustedList",
                                        "BE",
                                    ),
                            ),
                    ),
                ),
        )
}

/**
 * Test helpers that mirror the pure-function logic from [LoTERoleVerificationServiceImpl]
 * without requiring DI injection.
 */
internal object RoleVerificationTestHelpers {
    fun findPointersForRole(
        lotl: ETSILoTE,
        role: EidasRole,
        territory: String?,
    ): List<ETSIOtherLoTEPointer> = LoTERoleTrustListRouting.findPointersForRole(lotl, role, territory)

    fun buildServiceTypeFilter(role: EidasRole): List<String> =
        LoTERoleTrustListRouting.buildServiceTypeFilter(role)

    fun evaluateServiceStatus(serviceStatus: String): Pair<Boolean, TrustStatus> =
        LoTERoleTrustListStatusPolicy.evaluate(serviceStatus)
}

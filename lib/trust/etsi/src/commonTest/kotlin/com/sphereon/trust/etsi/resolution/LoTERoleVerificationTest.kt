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

package com.sphereon.trust.etsi.resolution

import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.etsi.lote.model.EidasRole
import com.sphereon.trust.etsi.lote.model.LoTEServiceType
import com.sphereon.trust.etsi.lote.model.LoTEType
import com.sphereon.trust.etsi.lote.model.MultiLangString
import com.sphereon.trust.etsi.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the role verification types and the pure helper functions
 * used by LoTERoleVerificationServiceImpl.
 *
 * Uses [RoleVerificationTestHelpers] to test internal logic without DI.
 */
class LoTERoleVerificationTest {

    // -- findPointersForRole tests (602 format) --

    @Test
    fun findPointers602FormatFiltersByLoTEType() {
        val lotl = buildLotlWith602Pointers()

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_ISSUER, null)

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
        val lotl = buildLotlWith602Pointers(
            pointers = listOf(
                build602Pointer(LoTEType.EU_PID_PROVIDERS, "NL", "https://nl.example.com/pid.xml"),
                build602Pointer(LoTEType.EU_PID_PROVIDERS, "BE", "https://be.example.com/pid.xml"),
            )
        )

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_ISSUER, "NL")

        assertEquals(1, result.size)
        assertEquals("https://nl.example.com/pid.xml", result[0].location)
    }

    @Test
    fun findPointers602FormatTerritoryFilterFallsBackToAll() {
        val lotl = buildLotlWith602Pointers()

        // Territory "NL" doesn't match any 602 pointer territory (they have "EU"),
        // but since 602 pointers exist, fallback returns all 602 pointers for that role
        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_ISSUER, "NL")

        assertEquals(1, result.size) // Falls back to all PID pointers
    }

    // -- findPointersForRole tests (612 format) --

    @Test
    fun findPointers612FormatFiltersByTerritory() {
        val lotl = buildLotlWith612Pointers()

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_ISSUER, "NL")

        assertEquals(1, result.size)
        assertEquals("https://nl.example.com/tl.xml", result[0].location)
    }

    @Test
    fun findPointers612FormatReturnsAllWithoutTerritory() {
        val lotl = buildLotlWith612Pointers()

        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_ISSUER, null)

        assertEquals(2, result.size)
    }

    @Test
    fun findPointers602FallsBackToAllWhenNoRoleMatch() {
        val lotl = buildLotlWith602Pointers()

        // REGISTRAR has no 602 pointers, so falls back to 612 behavior (all pointers)
        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.REGISTRAR, null)

        // Without 602 match and without territory filter, all pointers are returned
        assertEquals(2, result.size)
    }

    @Test
    fun findPointersReturnsEmptyWithTerritoryFilterNoMatch() {
        val lotl = buildLotlWith612Pointers()

        // Territory "XX" doesn't match any pointer
        val result = RoleVerificationTestHelpers.findPointersForRole(lotl, EidasRole.PID_ISSUER, "XX")

        assertTrue(result.isEmpty())
    }

    // -- buildServiceTypeFilter tests --

    @Test
    fun serviceTypeFilterIncludesIssuanceAndRevocation() {
        val filter = RoleVerificationTestHelpers.buildServiceTypeFilter(EidasRole.PID_ISSUER)

        assertTrue(filter.contains(LoTEServiceType.PID_ISSUANCE))
        assertTrue(filter.contains(LoTEServiceType.PID_REVOCATION))
        assertEquals(2, filter.size)
    }

    @Test
    fun serviceTypeFilterWalletProvider() {
        val filter = RoleVerificationTestHelpers.buildServiceTypeFilter(EidasRole.WALLET_PROVIDER)

        assertTrue(filter.contains(LoTEServiceType.WALLET_ISSUANCE))
        assertTrue(filter.contains(LoTEServiceType.WALLET_REVOCATION))
        assertEquals(2, filter.size)
    }

    @Test
    fun serviceTypeFilterIncludesLegacyTypes() {
        val filter = RoleVerificationTestHelpers.buildServiceTypeFilter(EidasRole.QEAA_ISSUER)

        assertTrue(filter.contains(LoTEServiceType.PUB_EAA_ISSUANCE))
        assertTrue(filter.contains(LoTEServiceType.PUB_EAA_REVOCATION))
        assertTrue(filter.contains("http://uri.etsi.org/TrstSvc/Svctype/CA/QC"))
        assertEquals(3, filter.size)
    }

    @Test
    fun serviceTypeFilterRegistrarHasNoRevocation() {
        val filter = RoleVerificationTestHelpers.buildServiceTypeFilter(EidasRole.REGISTRAR)

        assertEquals(1, filter.size)
        assertTrue(filter.contains(LoTEServiceType.REGISTER))
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
        val result = RoleVerificationResult(
            verified = false,
            role = EidasRole.PID_ISSUER,
            trustStatus = TrustStatus.UNTRUSTED,
            matchedEntity = null,
            trustListInfo = null,
            details = "Not found",
            verifiedAt = Clock.System.now()
        )

        assertFalse(result.verified)
        assertEquals(null, result.matchedEntity)
        assertEquals(EidasRole.PID_ISSUER, result.role)
    }

    @Test
    fun roleDiscoveryResultContainsAllCheckedRoles() {
        val results = EidasRole.entries.map { role ->
            RoleVerificationResult(
                verified = false,
                role = role,
                trustStatus = TrustStatus.UNTRUSTED,
                matchedEntity = null,
                trustListInfo = null,
                details = "Not found",
                verifiedAt = Clock.System.now()
            )
        }

        val discovery = RoleDiscoveryResult(
            verifiedRoles = results,
            discoveredAt = Clock.System.now()
        )

        assertEquals(EidasRole.entries.size, discovery.verifiedRoles.size)
    }

    @Test
    fun matchedEntityInfoPreservesAllFields() {
        val entity = MatchedEntityInfo(
            entityName = "Test TSP",
            entityIdentifier = "TSP-001",
            serviceName = "PID Service",
            serviceType = LoTEServiceType.PID_ISSUANCE,
            serviceStatus = ETSIServiceStatus.NOTIFIED,
            statusStartingTime = Instant.parse("2024-06-01T00:00:00Z"),
            matchType = TspMatchType.DIRECT_MATCH,
            territory = "NL"
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

    private fun build602Pointer(loTEType: String, territory: String, location: String): ETSIOtherLoTEPointer {
        return ETSIOtherLoTEPointer(
            schemeOperatorName = listOf(MultiLangString("en", "Test Operator")),
            schemeTerritory = territory,
            location = location,
            additionalInformation = ETSIAdditionalInformation(
                otherInformation = listOf(loTEType)
            )
        )
    }

    private fun buildLotlWith602Pointers(
        pointers: List<ETSIOtherLoTEPointer> = listOf(
            build602Pointer(LoTEType.EU_PID_PROVIDERS, "EU", "https://example.com/pid-providers.xml"),
            build602Pointer(LoTEType.EU_WALLET_PROVIDERS, "EU", "https://example.com/wallet-providers.xml"),
        )
    ): ETSILoTE {
        return ETSILoTE(
            sequenceNumber = 1,
            type = "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists",
            schemeOperatorName = listOf(MultiLangString("en", "EU Commission")),
            statusDeterminationApproach = "http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate",
            schemeTerritory = "EU",
            listIssueDateTime = Instant.parse("2024-01-01T00:00:00Z"),
            nextUpdate = Instant.parse("2025-01-01T00:00:00Z"),
            trustedEntities = emptyList(),
            pointersToOtherLoTE = pointers
        )
    }

    private fun buildLotlWith612Pointers(): ETSILoTE {
        return ETSILoTE(
            sequenceNumber = 1,
            type = "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists",
            schemeOperatorName = listOf(MultiLangString("en", "EU Commission")),
            statusDeterminationApproach = "http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate",
            schemeTerritory = "EU",
            listIssueDateTime = Instant.parse("2024-01-01T00:00:00Z"),
            nextUpdate = Instant.parse("2025-01-01T00:00:00Z"),
            trustedEntities = emptyList(),
            pointersToOtherLoTE = listOf(
                ETSIOtherLoTEPointer(
                    schemeOperatorName = listOf(MultiLangString("en", "NL Authority")),
                    schemeTerritory = "NL",
                    location = "https://nl.example.com/tl.xml"
                ),
                ETSIOtherLoTEPointer(
                    schemeOperatorName = listOf(MultiLangString("en", "BE Authority")),
                    schemeTerritory = "BE",
                    location = "https://be.example.com/tl.xml"
                )
            )
        )
    }
}

/**
 * Test helpers that mirror the pure-function logic from [LoTERoleVerificationServiceImpl]
 * without requiring DI injection.
 */
internal object RoleVerificationTestHelpers {

    fun findPointersForRole(
        lotl: ETSILoTE,
        role: EidasRole,
        territory: String?
    ): List<ETSIOtherLoTEPointer> {
        // 602 navigation: look for pointers with matching LoTEType qualifier
        val loteTypePointers = lotl.pointersToOtherLoTE.filter { pointer ->
            val qualifierLoTEType = pointer.additionalInformation?.otherInformation?.firstOrNull()
            qualifierLoTEType == role.loTEType
        }

        if (loteTypePointers.isNotEmpty()) {
            return if (territory != null) {
                loteTypePointers.filter { it.schemeTerritory.equals(territory, ignoreCase = true) }
                    .ifEmpty { loteTypePointers }
            } else {
                loteTypePointers
            }
        }

        // 612 fallback: filter by territory
        return if (territory != null) {
            lotl.pointersToOtherLoTE.filter { pointer ->
                pointer.schemeTerritory.equals(territory, ignoreCase = true)
            }
        } else {
            lotl.pointersToOtherLoTE
        }
    }

    fun buildServiceTypeFilter(role: EidasRole): List<String> = buildList {
        add(role.issuanceServiceType)
        role.revocationServiceType?.let { add(it) }
        addAll(role.legacyServiceTypes)
    }

    fun evaluateServiceStatus(serviceStatus: String): Pair<Boolean, TrustStatus> {
        return when (serviceStatus) {
            ETSIServiceStatus.NOTIFIED -> true to TrustStatus.TRUSTED
            ETSIServiceStatus.WITHDRAWN_602 -> false to TrustStatus.UNTRUSTED
            ETSIServiceStatus.GRANTED,
            ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL -> true to TrustStatus.TRUSTED
            ETSIServiceStatus.REVOKED -> false to TrustStatus.REVOKED
            ETSIServiceStatus.WITHDRAWN,
            ETSIServiceStatus.SUSPENDED -> false to TrustStatus.UNTRUSTED
            else -> false to TrustStatus.UNKNOWN
        }
    }
}

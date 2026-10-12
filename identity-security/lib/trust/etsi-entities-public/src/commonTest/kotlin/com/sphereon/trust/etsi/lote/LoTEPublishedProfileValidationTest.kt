package com.sphereon.trust.etsi.lote

import com.sphereon.trust.etsi.lote.model.*
import com.sphereon.trust.etsi.lote.serialization.LoTEJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LoTEPublishedProfileValidationTest {
    private data class Profile(val type: String, val status: String, val rules: String, val service: String)
    private val profiles = listOf(
        Profile("EUPIDProvidersList", "PIDProvidersList", "PIDProviders", "PID/Issuance"),
        Profile("EUWalletProvidersList", "WalletProvidersList", "WalletProvidersList", "WalletSolution/Issuance"),
        Profile("EUWRPACProvidersList", "WRPACProvidersList", "WRPACProvidersList", "WRPAC/Issuance"),
        Profile("EUWRPRCProvidersList", "WRPRCrovidersList", "WRPRCProvidersList", "WRPRC/Issuance"),
        Profile("EUPubEAAProvidersList", "PubEAAProvidersList", "PubEAAProvidersList", "PubEAA/Issuance"),
        Profile("EURegistrarsAndRegistersList", "RegistrarsAndRegistersList", "RegistrarsAndRegistersList", "Register"),
    )
    private val baseUri = "http://uri.etsi.org/19602/"
    private fun validate(list: LoTE) = LoTEProfileValidation.validate(list, requireNotNull(list.listAndSchemeInformation.type))
    private fun fixture(profile: Profile): LoTE {
        val base = LoTEJson.parse(LoTEPublishedBindingTest.pidFixture)
        val pub = profile.type == "EUPubEAAProvidersList"
        val info = base.listAndSchemeInformation.copy(
            type = baseUri + "LoTEType/" + profile.type, schemeTerritory = "EU",
            schemeName = listOf(MultiLangString("en", "Synthetic profile test")),
            schemeInformationURI = listOf(MultiLangURI("en", "https://example.com/information"), MultiLangURI("en", "https://example.com/history")),
            statusDeterminationApproach = baseUri + profile.status + "/StatusDetn/EU",
            schemeTypeCommunityRules = listOf(MultiLangURI("en", baseUri + profile.rules + "/schemerules/EU")),
            policyOrLegalNotice = listOf(PolicyOrLegalNoticeEntry(lang = "en", notice = "Synthetic test; not an official EU list")),
            historicalInformationPeriod = if (pub) 65535 else null,
        )
        val identity = LoTEServiceDigitalIdentity(x509Certificates = listOf(PkiObject("Y2VydGlmaWNhdGU=")))
        val pointer = OtherLoTEPointer(listOf(identity), "https://example.com/list", listOf(LoTEQualifier(
            loTEType = info.type, schemeOperatorName = info.schemeOperatorName,
            schemeTypeCommunityRules = info.schemeTypeCommunityRules, schemeTerritory = "EU", mimeType = "application/json")))
        val entity = base.trustedEntitiesList.single()
        val service = entity.trustedEntityServices.single().serviceInformation.copy(
            serviceTypeIdentifier = baseUri + "SvcType/" + profile.service, serviceDigitalIdentity = identity,
            serviceStatus = if (pub) LoTEServiceStatus.NOTIFIED else null,
            statusStartingTime = if (pub) Instant.parse("2026-09-01T00:00:00Z") else null,
            serviceInformationExtensions = if (profile.type == "EUWalletProvidersList") listOf(buildJsonObject { put("ServiceUniqueIdentifier", "urn:example:wallet:1") }) else emptyList(),
        )
        return base.copy(listAndSchemeInformation = info.copy(pointersToOtherLoTE = if (pub) emptyList() else listOf(pointer)),
            trustedEntitiesList = listOf(entity.copy(trustedEntityInformation = entity.trustedEntityInformation.copy(tradeName = if (pub) listOf(MultiLangString("en", "OJ:EU:synthetic-test-law")) else emptyList()), trustedEntityServices = listOf(entity.trustedEntityServices.single().copy(serviceInformation = service)))))
    }

    @Test fun allSixRequireExplicitSchemeAndExactPublishedUris() {
        profiles.forEach { profile ->
            val valid = fixture(profile)
            validate(valid)
            val info = valid.listAndSchemeInformation
            val invalid = listOf(
                info.copy(schemeTerritory = "NL"), info.copy(schemeOperatorAddress = null),
                info.copy(schemeName = emptyList()), info.copy(schemeInformationURI = emptyList()),
                info.copy(policyOrLegalNotice = emptyList()), info.copy(statusDeterminationApproach = "urn:wrong"),
                info.copy(schemeTypeCommunityRules = listOf(MultiLangURI("en", "urn:wrong"))),
            )
            invalid.forEach { altered -> assertFailsWith<IllegalArgumentException>(profile.type) { validate(valid.copy(listAndSchemeInformation = altered)) } }
        }
    }

    @Test fun pubEaaRequiresStructuralOfficialJournalReference() {
        val valid = fixture(profiles.single { it.type == "EUPubEAAProvidersList" })
        val entity = valid.trustedEntitiesList.single()
        listOf(emptyList(), listOf(MultiLangString("en", "https://example.com/law")), listOf(MultiLangString("en", "OJ:EU")), listOf(MultiLangString("en", "OJ:EU law"))).forEach { names ->
            assertFailsWith<IllegalArgumentException> { validate(valid.copy(trustedEntitiesList = listOf(entity.copy(trustedEntityInformation = entity.trustedEntityInformation.copy(tradeName = names))))) }
        }
        validate(valid)
    }

    @Test fun calendarMonthsAreNotAnApproximationInDays() {
        profiles.forEach { profile ->
            val base = fixture(profile)
            val info = base.listAndSchemeInformation.copy(listIssueDateTime = Instant.parse("2026-08-31T12:34:56Z"), nextUpdate = Instant.parse("2027-02-28T12:34:56Z"))
            validate(base.copy(listAndSchemeInformation = info))
            assertFailsWith<IllegalArgumentException> { validate(base.copy(listAndSchemeInformation = info.copy(nextUpdate = Instant.parse("2027-02-28T12:34:57Z")))) }
        }
    }

    @Test fun membershipNeedsItsOwnPointerAndProhibitsHistoryEvenZero() {
        profiles.filterNot { it.type == "EUPubEAAProvidersList" }.forEach { profile ->
            val base = fixture(profile); val info = base.listAndSchemeInformation
            val pointer = info.pointersToOtherLoTE.single()
            val qualifier = pointer.qualifiers.single()
            validate(base.copy(listAndSchemeInformation = info.copy(pointersToOtherLoTE = listOf(pointer.copy(
                qualifiers = listOf(qualifier.copy(schemeTerritory = null, schemeTypeCommunityRules = emptyList())))))))
            assertFailsWith<IllegalArgumentException> {
                validate(base.copy(listAndSchemeInformation = info.copy(pointersToOtherLoTE = listOf(pointer.copy(qualifiers = emptyList())))))
            }
            assertFailsWith<IllegalArgumentException> { validate(base.copy(listAndSchemeInformation = info.copy(pointersToOtherLoTE = emptyList()))) }
            assertFailsWith<IllegalArgumentException> { validate(base.copy(listAndSchemeInformation = info.copy(historicalInformationPeriod = 0))) }
            val foreign = pointer.copy(qualifiers = listOf(qualifier.copy(loTEType = "urn:other")))
            assertFailsWith<IllegalArgumentException> { validate(base.copy(listAndSchemeInformation = info.copy(pointersToOtherLoTE = listOf(foreign)))) }
        }
    }

    @Test fun pubEaaRequiresSkiOnlyHistoryInDescendingOrder() {
        val base = fixture(profiles[4]); val entity = base.trustedEntitiesList.single(); val original = entity.trustedEntityServices.single()
        val current = original.serviceInformation.copy(statusStartingTime = Instant.parse("2026-10-01T00:00:00Z"))
        val history = current.copy(serviceDigitalIdentity = LoTEServiceDigitalIdentity(x509SKIs = listOf("AQID")), statusStartingTime = Instant.parse("2026-09-01T00:00:00Z")).toHistoryInstance()
        fun check(items: List<ServiceHistoryInstance>) = validate(base.copy(trustedEntitiesList = listOf(entity.copy(trustedEntityServices = listOf(original.copy(serviceInformation = current, serviceHistory = items))))))
        val older = history.copy(statusStartingTime = Instant.parse("2026-08-01T00:00:00Z"))
        check(listOf(history, older))
        assertFailsWith<IllegalArgumentException> { check(listOf(older, history)) }
        assertFailsWith<IllegalArgumentException> { check(listOf(history, history)) }
        assertFailsWith<IllegalArgumentException> { check(listOf(history.copy(serviceDigitalIdentity = current.serviceDigitalIdentity))) }
        assertFailsWith<IllegalArgumentException> { check(listOf(history.copy(serviceDigitalIdentity = current.serviceDigitalIdentity.copy(x509SKIs = listOf("AQID"))))) }
        assertFailsWith<IllegalArgumentException> { validate(base.copy(listAndSchemeInformation = base.listAndSchemeInformation.copy(historicalInformationPeriod = 1))) }
        assertFailsWith<IllegalArgumentException> { validate(base.copy(listAndSchemeInformation = base.listAndSchemeInformation.copy(pointersToOtherLoTE = fixture(profiles[0]).listAndSchemeInformation.pointersToOtherLoTE))) }
    }

    @Test fun transitionTimingDoesNotRejectContinuingStatusOrAuthorizeFutureStatus() {
        val base = fixture(profiles[4]); val current = base.trustedEntitiesList.single().trustedEntityServices.single().serviceInformation
        val issue = base.listAndSchemeInformation.listIssueDateTime
        LoTEProfileValidation.validateStatusTransition(current, current, issue)
        assertFailsWith<IllegalArgumentException> { LoTEProfileValidation.validateStatusTransition(current, current.copy(serviceStatus = LoTEServiceStatus.WITHDRAWN), issue) }
        LoTEProfileValidation.validateStatusTransition(current, current.copy(serviceStatus = LoTEServiceStatus.WITHDRAWN, statusStartingTime = issue), issue)
        assertTrue(LoTEProfileValidation.isStatusEffective(current, issue))
        assertFalse(LoTEProfileValidation.isStatusEffective(current.copy(statusStartingTime = base.listAndSchemeInformation.nextUpdate), issue))
    }

    @Test fun onlyExactRegisteredWalletExtensionIsSupported() {
        val known = buildJsonObject { put("ServiceUniqueIdentifier", "urn:example:wallet") }
        LoTEProfileValidation.validateServiceExtensions(listOf(known), true)
        listOf(buildJsonObject { put("ServiceUniqueIdentifier", "relative") }, buildJsonObject { put("ServiceUniqueIdentifier", "urn:example:x"); put("critical", false) }, buildJsonObject { put("critical", false) }, buildJsonObject { put("critical", true) }).forEach {
            assertFailsWith<IllegalArgumentException> { LoTEProfileValidation.validateServiceExtensions(listOf(it), true) }
            assertFailsWith<IllegalArgumentException> { LoTEProfileValidation.validateServiceExtensions(listOf(it), false) }
        }
        assertFailsWith<IllegalArgumentException> { LoTEProfileValidation.validateServiceExtensions(emptyList(), true) }
        assertFailsWith<IllegalArgumentException> { LoTEProfileValidation.validateServiceExtensions(listOf(known, known), true) }
        val wallet = fixture(profiles[1]); val entity = wallet.trustedEntitiesList.single()
        assertFailsWith<IllegalArgumentException> { validate(wallet.copy(trustedEntitiesList = listOf(entity.copy(trustedEntityServices = entity.trustedEntityServices + entity.trustedEntityServices)))) }
    }
}

/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.trust.etsi.lote

import com.sphereon.trust.etsi.lote.model.ListAndSchemeInformation
import com.sphereon.trust.etsi.lote.model.LoTE
import com.sphereon.trust.etsi.lote.model.LoTEPostalAddress
import com.sphereon.trust.etsi.lote.model.LoTEQualifier
import com.sphereon.trust.etsi.lote.model.LoTEServiceDigitalIdentity
import com.sphereon.trust.etsi.lote.model.LoTEServiceInformation
import com.sphereon.trust.etsi.lote.model.LoTEServiceStatus
import com.sphereon.trust.etsi.lote.model.LoTEServiceType
import com.sphereon.trust.etsi.lote.model.LoTEType
import com.sphereon.trust.etsi.lote.model.MultiLangString
import com.sphereon.trust.etsi.lote.model.MultiLangURI
import com.sphereon.trust.etsi.lote.model.OperatorAddress
import com.sphereon.trust.etsi.lote.model.OtherLoTEPointer
import com.sphereon.trust.etsi.lote.model.PkiObject
import com.sphereon.trust.etsi.lote.model.PolicyOrLegalNoticeEntry
import com.sphereon.trust.etsi.lote.model.TrustedEntity
import com.sphereon.trust.etsi.lote.model.TrustedEntityInformation
import com.sphereon.trust.etsi.lote.model.TrustedEntityService
import com.sphereon.trust.etsi.lote.model.forLang
import com.sphereon.trust.etsi.lote.serialization.LoTEXml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LoTEXmlSerializationTest {
    @Test
    fun testXmlRoundTrip() {
        val lote = createSampleLoTE()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        assertEquals(lote.listAndSchemeInformation.versionIdentifier, parsed.listAndSchemeInformation.versionIdentifier)
        assertEquals(lote.listAndSchemeInformation.sequenceNumber, parsed.listAndSchemeInformation.sequenceNumber)
        assertEquals(lote.listAndSchemeInformation.type, parsed.listAndSchemeInformation.type)
        assertEquals(lote.listAndSchemeInformation.schemeTerritory, parsed.listAndSchemeInformation.schemeTerritory)
        assertEquals(lote.listAndSchemeInformation.listIssueDateTime, parsed.listAndSchemeInformation.listIssueDateTime)
        assertEquals(lote.listAndSchemeInformation.nextUpdate, parsed.listAndSchemeInformation.nextUpdate)
        assertEquals(lote.listAndSchemeInformation.statusDeterminationApproach, parsed.listAndSchemeInformation.statusDeterminationApproach)
        assertEquals(lote.trustedEntitiesList.size, parsed.trustedEntitiesList.size)

        val entity = parsed.trustedEntitiesList[0]
        assertEquals("Test Entity", entity.trustedEntityInformation.name.forLang("en"))

        val service = entity.trustedEntityServices[0]
        assertEquals(LoTEServiceType.PID_ISSUANCE, service.serviceInformation.serviceTypeIdentifier)
        assertEquals("Test PID Service", service.serviceInformation.serviceName.forLang("en"))
        assertEquals(1, service.serviceInformation.serviceDigitalIdentity.x509Certificates.size)
        assertEquals(
            "BASE64CERTDATA",
            service.serviceInformation.serviceDigitalIdentity.x509Certificates[0]
                .value,
        )
    }

    @Test
    fun testXmlMultiLangRoundTrip() {
        val lote = createSampleLoTE()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        val operatorNames = parsed.listAndSchemeInformation.schemeOperatorName
        assertEquals(2, operatorNames.size)
        assertEquals("en", operatorNames[0].lang)
        assertEquals("Test Operator", operatorNames[0].value)
        assertEquals("nl", operatorNames[1].lang)
        assertEquals("Test Operator NL", operatorNames[1].value)

        val schemeNames = parsed.listAndSchemeInformation.schemeName
        assertEquals(1, schemeNames.size)
        assertEquals("en", schemeNames[0].lang)
        assertEquals("Test Scheme", schemeNames[0].value)

        val uris = parsed.listAndSchemeInformation.schemeInformationURI
        assertEquals(1, uris.size)
        assertEquals("en", uris[0].lang)
        assertEquals("https://example.com/scheme", uris[0].uriValue)
    }

    @Test
    fun testXmlAddressRoundTrip() {
        val lote = createLoTEWithAddress()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        val address = parsed.listAndSchemeInformation.schemeOperatorAddress
        assertNotNull(address)
        assertEquals(1, address.postalAddresses.size)
        assertEquals("Test Street 1", address.postalAddresses[0].streetAddress)
        assertEquals("Amsterdam", address.postalAddresses[0].locality)
        assertEquals("NH", address.postalAddresses[0].stateOrProvince)
        assertEquals("1000AA", address.postalAddresses[0].postalCode)
        assertEquals("NL", address.postalAddresses[0].countryName)
        assertEquals("en", address.postalAddresses[0].lang)
        assertEquals(1, address.electronicAddress.size)
        assertEquals("https://example.com", address.electronicAddress[0].uriValue)
    }

    @Test
    fun testXmlPointersRoundTrip() {
        val lote = createLoTEWithPointers()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        assertEquals(1, parsed.listAndSchemeInformation.pointersToOtherLoTE.size)
        val pointer = parsed.listAndSchemeInformation.pointersToOtherLoTE[0]
        assertEquals("https://example.com/member-state-lote.xml", pointer.location)

        assertEquals(1, pointer.serviceDigitalIdentities.size)
        assertEquals(1, pointer.serviceDigitalIdentities[0].x509Certificates.size)
        assertEquals("POINTERCERTDATA", pointer.serviceDigitalIdentities[0].x509Certificates[0].value)

        assertEquals(1, pointer.qualifiers.size)
        val qualifier = pointer.qualifiers[0]
        assertEquals(LoTEType.EU_PID_PROVIDERS, qualifier.loTEType)
        assertEquals("NL", qualifier.schemeTerritory)
        assertEquals("application/xml", qualifier.mimeType)
        assertEquals(1, qualifier.schemeOperatorName.size)
        assertEquals("NL Authority", qualifier.schemeOperatorName.forLang("en"))
        assertEquals(1, qualifier.schemeTypeCommunityRules.size)
        assertEquals("http://uri.etsi.org/19602/SchemeRules/CommonRules", qualifier.schemeTypeCommunityRules[0].uriValue)
    }

    @Test
    fun testXmlServiceHistoryRoundTrip() {
        val lote = createLoTEWithServiceHistory()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        assertEquals(1, parsed.trustedEntitiesList.size)
        val service = parsed.trustedEntitiesList[0].trustedEntityServices[0]

        assertEquals(LoTEServiceType.PID_ISSUANCE, service.serviceInformation.serviceTypeIdentifier)
        assertEquals(LoTEServiceStatus.NOTIFIED, service.serviceInformation.serviceStatus)
        assertEquals(Instant.parse("2025-06-01T00:00:00Z"), service.serviceInformation.statusStartingTime)

        assertEquals(1, service.serviceHistory.size)
        val history = service.serviceHistory[0]
        assertEquals(LoTEServiceType.PID_ISSUANCE, history.serviceTypeIdentifier)
        assertEquals(LoTEServiceStatus.WITHDRAWN, history.serviceStatus)
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), history.statusStartingTime)
    }

    @Test
    fun testXmlMinimalRoundTrip() {
        val lote = createMinimalLoTE()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        assertEquals(1, parsed.listAndSchemeInformation.versionIdentifier)
        assertEquals(1, parsed.listAndSchemeInformation.sequenceNumber)
        assertEquals("Operator", parsed.listAndSchemeInformation.schemeOperatorName.forLang("en"))
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), parsed.listAndSchemeInformation.listIssueDateTime)
        assertEquals(Instant.parse("2025-02-01T00:00:00Z"), parsed.listAndSchemeInformation.nextUpdate)
        assertTrue(parsed.trustedEntitiesList.isEmpty())
        assertTrue(parsed.listAndSchemeInformation.pointersToOtherLoTE.isEmpty())
        assertTrue(parsed.listAndSchemeInformation.distributionPoints.isEmpty())
    }

    @Test
    fun testXmlPolicyOrLegalNoticeRoundTrip() {
        val lote = createLoTEWithPolicy()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        assertEquals(2, parsed.listAndSchemeInformation.policyOrLegalNotice.size)
        assertEquals("en", parsed.listAndSchemeInformation.policyOrLegalNotice[0].lang)
        assertEquals("Legal notice text", parsed.listAndSchemeInformation.policyOrLegalNotice[0].notice)
        assertEquals("nl", parsed.listAndSchemeInformation.policyOrLegalNotice[1].lang)
        assertEquals("Juridische mededeling", parsed.listAndSchemeInformation.policyOrLegalNotice[1].notice)
    }

    @Test
    fun testXmlDistributionPointsRoundTrip() {
        val lote = createLoTEWithDistributionPoints()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        assertEquals(2, parsed.listAndSchemeInformation.distributionPoints.size)
        assertEquals("https://example.com/lote.xml", parsed.listAndSchemeInformation.distributionPoints[0])
        assertEquals("https://mirror.example.com/lote.xml", parsed.listAndSchemeInformation.distributionPoints[1])
    }

    @Test
    fun testXmlServiceSupplyPointsRoundTrip() {
        val lote = createLoTEWithServiceSupplyPoints()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        val service = parsed.trustedEntitiesList[0].trustedEntityServices[0]
        assertEquals(2, service.serviceInformation.serviceSupplyPoints.size)
        assertEquals("https://issuer.example.com/pid", service.serviceInformation.serviceSupplyPoints[0])
        assertEquals("https://issuer.example.com/pid/alt", service.serviceInformation.serviceSupplyPoints[1])
    }

    @Test
    fun testXmlServiceDefinitionURIsRoundTrip() {
        val lote = createLoTEWithServiceDefinitionURIs()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        val service = parsed.trustedEntitiesList[0].trustedEntityServices[0]
        assertEquals(1, service.serviceInformation.schemeServiceDefinitionURI.size)
        assertEquals("https://example.com/scheme-def", service.serviceInformation.schemeServiceDefinitionURI[0].uriValue)
        assertEquals(1, service.serviceInformation.teServiceDefinitionURI.size)
        assertEquals("https://example.com/te-def", service.serviceInformation.teServiceDefinitionURI[0].uriValue)
    }

    @Test
    fun testXmlDigitalIdentityMultipleFieldsRoundTrip() {
        val lote = createLoTEWithFullDigitalIdentity()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        val identity =
            parsed.trustedEntitiesList[0]
                .trustedEntityServices[0]
                .serviceInformation.serviceDigitalIdentity
        assertEquals(2, identity.x509Certificates.size)
        assertEquals("CERT1DATA", identity.x509Certificates[0].value)
        assertEquals("CERT2DATA", identity.x509Certificates[1].value)
        assertEquals(1, identity.x509SubjectNames.size)
        assertEquals("CN=Test, O=Test Org, C=NL", identity.x509SubjectNames[0])
        assertEquals(1, identity.x509SKIs.size)
        assertEquals("SKIDATA123", identity.x509SKIs[0])
    }

    @Test
    fun testXmlTrustedEntityInformationRoundTrip() {
        val lote = createLoTEWithFullEntityInfo()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        val entityInfo = parsed.trustedEntitiesList[0].trustedEntityInformation
        assertEquals("Test Entity", entityInfo.name.forLang("en"))
        assertEquals("Test Entiteit", entityInfo.name.forLang("nl"))
        assertEquals("Trading Co", entityInfo.tradeName.forLang("en"))
        assertNotNull(entityInfo.address)
        assertEquals(1, entityInfo.address!!.postalAddresses.size)
        assertEquals("Entity Street 10", entityInfo.address!!.postalAddresses[0].streetAddress)
        assertEquals(1, entityInfo.informationURI.size)
        assertEquals("https://entity.example.com", entityInfo.informationURI.forLang("en"))
    }

    @Test
    fun testXmlHistoricalInformationPeriodRoundTrip() {
        val lote = createLoTEWithHistoricalPeriod()
        val xml = LoTEXml.encode(lote)
        val parsed = LoTEXml.parse(xml)

        assertEquals(65535, parsed.listAndSchemeInformation.historicalInformationPeriod)
    }

    @Test
    fun testXmlEncodeProducesValidStructure() {
        val lote = createSampleLoTE()
        val xml = LoTEXml.encode(lote)

        assertTrue(xml.contains("ListOfTrustedEntities"))
        assertTrue(xml.contains("ListAndSchemeInformation"))
        assertTrue(xml.contains("LoTEVersionIdentifier"))
        assertTrue(xml.contains("TrustedEntitiesList"))
        assertTrue(xml.contains("TrustedEntity"))
        assertTrue(xml.contains("ServiceDigitalIdentity"))
        assertTrue(xml.contains("X509Certificate"))
    }

    // --- Helper factories ---

    private fun createMinimalLoTE(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "Operator")),
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                ),
        )

    private fun createSampleLoTE(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 42,
                    type = LoTEType.EU_PID_PROVIDERS,
                    schemeOperatorName =
                        listOf(
                            MultiLangString("en", "Test Operator"),
                            MultiLangString("nl", "Test Operator NL"),
                        ),
                    schemeName = listOf(MultiLangString("en", "Test Scheme")),
                    schemeInformationURI = listOf(MultiLangURI("en", "https://example.com/scheme")),
                    statusDeterminationApproach = "http://uri.etsi.org/19602/StatusDetn/appropriate",
                    schemeTerritory = "NL",
                    listIssueDateTime = Instant.parse("2025-06-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-07-01T00:00:00Z"),
                ),
            trustedEntitiesList =
                listOf(
                    TrustedEntity(
                        trustedEntityInformation =
                            TrustedEntityInformation(
                                name = listOf(MultiLangString("en", "Test Entity")),
                                informationURI = listOf(MultiLangURI("en", "https://example.com/entity")),
                            ),
                        trustedEntityServices =
                            listOf(
                                TrustedEntityService(
                                    serviceInformation =
                                        LoTEServiceInformation(
                                            serviceTypeIdentifier = LoTEServiceType.PID_ISSUANCE,
                                            serviceName = listOf(MultiLangString("en", "Test PID Service")),
                                            serviceDigitalIdentity =
                                                LoTEServiceDigitalIdentity(
                                                    x509Certificates = listOf(PkiObject("BASE64CERTDATA")),
                                                    x509SubjectNames = listOf("CN=Test, O=Test Org, C=NL"),
                                                ),
                                            serviceStatus = LoTEServiceStatus.NOTIFIED,
                                            statusStartingTime = Instant.parse("2025-01-01T00:00:00Z"),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    private fun createLoTEWithAddress(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "Test")),
                    schemeOperatorAddress =
                        OperatorAddress(
                            postalAddresses =
                                listOf(
                                    LoTEPostalAddress(
                                        lang = "en",
                                        streetAddress = "Test Street 1",
                                        locality = "Amsterdam",
                                        stateOrProvince = "NH",
                                        postalCode = "1000AA",
                                        countryName = "NL",
                                    ),
                                ),
                            electronicAddress = listOf(MultiLangURI("en", "https://example.com")),
                        ),
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                ),
        )

    private fun createLoTEWithPointers(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "EU Commission")),
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                    pointersToOtherLoTE =
                        listOf(
                            OtherLoTEPointer(
                                location = "https://example.com/member-state-lote.xml",
                                serviceDigitalIdentities =
                                    listOf(
                                        LoTEServiceDigitalIdentity(
                                            x509Certificates = listOf(PkiObject("POINTERCERTDATA")),
                                        ),
                                    ),
                                qualifiers =
                                    listOf(
                                        LoTEQualifier(
                                            loTEType = LoTEType.EU_PID_PROVIDERS,
                                            schemeTerritory = "NL",
                                            schemeOperatorName = listOf(MultiLangString("en", "NL Authority")),
                                            schemeTypeCommunityRules = listOf(MultiLangURI("en", "http://uri.etsi.org/19602/SchemeRules/CommonRules")),
                                            mimeType = "application/xml",
                                        ),
                                    ),
                            ),
                        ),
                ),
        )

    private fun createLoTEWithPolicy(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "Test")),
                    policyOrLegalNotice =
                        listOf(
                            PolicyOrLegalNoticeEntry(lang = "en", notice = "Legal notice text"),
                            PolicyOrLegalNoticeEntry(lang = "nl", notice = "Juridische mededeling"),
                        ),
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                ),
        )

    private fun createLoTEWithServiceHistory(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "Test")),
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                ),
            trustedEntitiesList =
                listOf(
                    TrustedEntity(
                        trustedEntityInformation =
                            TrustedEntityInformation(
                                name = listOf(MultiLangString("en", "Entity")),
                            ),
                        trustedEntityServices =
                            listOf(
                                TrustedEntityService(
                                    serviceInformation =
                                        LoTEServiceInformation(
                                            serviceTypeIdentifier = LoTEServiceType.PID_ISSUANCE,
                                            serviceName = listOf(MultiLangString("en", "Service")),
                                            serviceDigitalIdentity = LoTEServiceDigitalIdentity(),
                                            serviceStatus = LoTEServiceStatus.NOTIFIED,
                                            statusStartingTime = Instant.parse("2025-06-01T00:00:00Z"),
                                        ),
                                    serviceHistory =
                                        listOf(
                                            LoTEServiceInformation(
                                                serviceTypeIdentifier = LoTEServiceType.PID_ISSUANCE,
                                                serviceName = listOf(MultiLangString("en", "Service")),
                                                serviceDigitalIdentity = LoTEServiceDigitalIdentity(),
                                                serviceStatus = LoTEServiceStatus.WITHDRAWN,
                                                statusStartingTime = Instant.parse("2025-01-01T00:00:00Z"),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    private fun createLoTEWithDistributionPoints(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "Test")),
                    distributionPoints =
                        listOf(
                            "https://example.com/lote.xml",
                            "https://mirror.example.com/lote.xml",
                        ),
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                ),
        )

    private fun createLoTEWithServiceSupplyPoints(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "Test")),
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                ),
            trustedEntitiesList =
                listOf(
                    TrustedEntity(
                        trustedEntityInformation =
                            TrustedEntityInformation(
                                name = listOf(MultiLangString("en", "Entity")),
                            ),
                        trustedEntityServices =
                            listOf(
                                TrustedEntityService(
                                    serviceInformation =
                                        LoTEServiceInformation(
                                            serviceTypeIdentifier = LoTEServiceType.PID_ISSUANCE,
                                            serviceName = listOf(MultiLangString("en", "Service")),
                                            serviceDigitalIdentity = LoTEServiceDigitalIdentity(),
                                            serviceSupplyPoints =
                                                listOf(
                                                    "https://issuer.example.com/pid",
                                                    "https://issuer.example.com/pid/alt",
                                                ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    private fun createLoTEWithServiceDefinitionURIs(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "Test")),
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                ),
            trustedEntitiesList =
                listOf(
                    TrustedEntity(
                        trustedEntityInformation =
                            TrustedEntityInformation(
                                name = listOf(MultiLangString("en", "Entity")),
                            ),
                        trustedEntityServices =
                            listOf(
                                TrustedEntityService(
                                    serviceInformation =
                                        LoTEServiceInformation(
                                            serviceTypeIdentifier = LoTEServiceType.PID_ISSUANCE,
                                            serviceName = listOf(MultiLangString("en", "Service")),
                                            serviceDigitalIdentity = LoTEServiceDigitalIdentity(),
                                            schemeServiceDefinitionURI = listOf(MultiLangURI("en", "https://example.com/scheme-def")),
                                            teServiceDefinitionURI = listOf(MultiLangURI("en", "https://example.com/te-def")),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    private fun createLoTEWithFullDigitalIdentity(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "Test")),
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                ),
            trustedEntitiesList =
                listOf(
                    TrustedEntity(
                        trustedEntityInformation =
                            TrustedEntityInformation(
                                name = listOf(MultiLangString("en", "Entity")),
                            ),
                        trustedEntityServices =
                            listOf(
                                TrustedEntityService(
                                    serviceInformation =
                                        LoTEServiceInformation(
                                            serviceTypeIdentifier = LoTEServiceType.PID_ISSUANCE,
                                            serviceName = listOf(MultiLangString("en", "Service")),
                                            serviceDigitalIdentity =
                                                LoTEServiceDigitalIdentity(
                                                    x509Certificates = listOf(PkiObject("CERT1DATA"), PkiObject("CERT2DATA")),
                                                    x509SubjectNames = listOf("CN=Test, O=Test Org, C=NL"),
                                                    x509SKIs = listOf("SKIDATA123"),
                                                ),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    private fun createLoTEWithFullEntityInfo(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "Test")),
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                ),
            trustedEntitiesList =
                listOf(
                    TrustedEntity(
                        trustedEntityInformation =
                            TrustedEntityInformation(
                                name =
                                    listOf(
                                        MultiLangString("en", "Test Entity"),
                                        MultiLangString("nl", "Test Entiteit"),
                                    ),
                                tradeName = listOf(MultiLangString("en", "Trading Co")),
                                address =
                                    OperatorAddress(
                                        postalAddresses =
                                            listOf(
                                                LoTEPostalAddress(
                                                    lang = "en",
                                                    streetAddress = "Entity Street 10",
                                                    locality = "Rotterdam",
                                                    postalCode = "3000AB",
                                                    countryName = "NL",
                                                ),
                                            ),
                                    ),
                                informationURI = listOf(MultiLangURI("en", "https://entity.example.com")),
                            ),
                        trustedEntityServices =
                            listOf(
                                TrustedEntityService(
                                    serviceInformation =
                                        LoTEServiceInformation(
                                            serviceTypeIdentifier = LoTEServiceType.PID_ISSUANCE,
                                            serviceName = listOf(MultiLangString("en", "Service")),
                                            serviceDigitalIdentity = LoTEServiceDigitalIdentity(),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    private fun createLoTEWithHistoricalPeriod(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = 1,
                    sequenceNumber = 1,
                    schemeOperatorName = listOf(MultiLangString("en", "Test")),
                    historicalInformationPeriod = 65535,
                    listIssueDateTime = Instant.parse("2025-01-01T00:00:00Z"),
                    nextUpdate = Instant.parse("2025-02-01T00:00:00Z"),
                ),
        )
}

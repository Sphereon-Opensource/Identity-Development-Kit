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
import com.sphereon.trust.etsi.lote.model.toLangMap
import com.sphereon.trust.etsi.lote.model.toURIMap
import com.sphereon.trust.etsi.lote.parser.DefaultLoTEParser
import com.sphereon.trust.etsi.lote.parser.LoTESerializationFormat
import com.sphereon.trust.etsi.lote.serialization.LoTEJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LoTEJsonSerializationTest {
    private val parser = DefaultLoTEParser()

    @Test
    fun testJsonRoundTrip() {
        val lote = createSampleLoTE()
        val json = LoTEJson.encode(lote)
        val parsed = LoTEJson.parse(json)

        assertEquals(lote.listAndSchemeInformation.versionIdentifier, parsed.listAndSchemeInformation.versionIdentifier)
        assertEquals(lote.listAndSchemeInformation.sequenceNumber, parsed.listAndSchemeInformation.sequenceNumber)
        assertEquals(lote.listAndSchemeInformation.type, parsed.listAndSchemeInformation.type)
        assertEquals(lote.listAndSchemeInformation.schemeTerritory, parsed.listAndSchemeInformation.schemeTerritory)
        assertEquals(lote.listAndSchemeInformation.listIssueDateTime, parsed.listAndSchemeInformation.listIssueDateTime)
        assertEquals(lote.listAndSchemeInformation.nextUpdate, parsed.listAndSchemeInformation.nextUpdate)
        assertEquals(lote.trustedEntitiesList.size, parsed.trustedEntitiesList.size)
    }

    @Test
    fun testMultiLangStringRoundTrip() {
        val lote = createSampleLoTE()
        val json = LoTEJson.encode(lote)
        val parsed = LoTEJson.parse(json)

        val operatorNames = parsed.listAndSchemeInformation.schemeOperatorName
        assertEquals(2, operatorNames.size)
        assertEquals("en", operatorNames[0].lang)
        assertEquals("Test Operator", operatorNames[0].value)
        assertEquals("nl", operatorNames[1].lang)
        assertEquals("Test Operator NL", operatorNames[1].value)
    }

    @Test
    fun testMultiLangURIRoundTrip() {
        val lote = createSampleLoTE()
        val json = LoTEJson.encode(lote)
        val parsed = LoTEJson.parse(json)

        val uris = parsed.listAndSchemeInformation.schemeInformationURI
        assertEquals(1, uris.size)
        assertEquals("en", uris[0].lang)
        assertEquals("https://example.com/scheme", uris[0].uriValue)
    }

    @Test
    fun testTrustedEntityRoundTrip() {
        val lote = createSampleLoTE()
        val json = LoTEJson.encode(lote)
        val parsed = LoTEJson.parse(json)

        assertEquals(1, parsed.trustedEntitiesList.size)
        val entity = parsed.trustedEntitiesList[0]
        assertEquals("Test Entity", entity.trustedEntityInformation.name.forLang("en"))
        assertEquals(1, entity.trustedEntityServices.size)

        val service = entity.trustedEntityServices[0]
        assertEquals(LoTEServiceType.PID_ISSUANCE, service.serviceInformation.serviceTypeIdentifier)
        assertEquals("Test PID Service", service.serviceInformation.serviceName.forLang("en"))
    }

    @Test
    fun testServiceDigitalIdentityRoundTrip() {
        val lote = createSampleLoTE()
        val json = LoTEJson.encode(lote)
        val parsed = LoTEJson.parse(json)

        val identity =
            parsed.trustedEntitiesList[0]
                .trustedEntityServices[0]
                .serviceInformation.serviceDigitalIdentity
        assertEquals(1, identity.x509Certificates.size)
        assertEquals("BASE64CERTDATA", identity.x509Certificates[0].value)
        assertEquals(1, identity.x509SubjectNames.size)
        assertEquals("CN=Test, O=Test Org, C=NL", identity.x509SubjectNames[0])
    }

    @Test
    fun testOtherLoTEPointerRoundTrip() {
        val lote = createLoTEWithPointers()
        val json = LoTEJson.encode(lote)
        val parsed = LoTEJson.parse(json)

        assertEquals(1, parsed.listAndSchemeInformation.pointersToOtherLoTE.size)
        val pointer = parsed.listAndSchemeInformation.pointersToOtherLoTE[0]
        assertEquals("https://example.com/member-state-lote.json", pointer.location)
        assertEquals(1, pointer.qualifiers.size)
        assertEquals("NL", pointer.qualifiers[0].schemeTerritory)
        assertEquals(LoTEType.EU_PID_PROVIDERS, pointer.qualifiers[0].loTEType)
    }

    @Test
    fun testAddressRoundTrip() {
        val lote = createLoTEWithAddress()
        val json = LoTEJson.encode(lote)
        val parsed = LoTEJson.parse(json)

        val address = parsed.listAndSchemeInformation.schemeOperatorAddress
        assertNotNull(address)
        assertEquals(1, address.postalAddresses.size)
        assertEquals("Test Street 1", address.postalAddresses[0].streetAddress)
        assertEquals("Amsterdam", address.postalAddresses[0].locality)
        assertEquals("NL", address.postalAddresses[0].countryName)
        assertEquals(1, address.electronicAddress.size)
        assertEquals("https://example.com", address.electronicAddress[0].uriValue)
    }

    @Test
    fun testFormatDetection() {
        assertEquals(LoTESerializationFormat.JSON, parser.detectFormat("""{"ListAndSchemeInformation":{}}"""))
        assertEquals(LoTESerializationFormat.XML, parser.detectFormat("""<?xml version="1.0"?>"""))
        assertEquals(LoTESerializationFormat.XML, parser.detectFormat("""<ListOfTrustedEntities>"""))
        assertEquals(LoTESerializationFormat.JSON, parser.detectFormat("""  {"ListAndSchemeInformation":{}}"""))
    }

    @Test
    fun testMultiLangExtensions() {
        val strings =
            listOf(
                MultiLangString("en", "English"),
                MultiLangString("nl", "Nederlands"),
                MultiLangString("de", "Deutsch"),
            )

        assertEquals("English", strings.forLang("en"))
        assertEquals("Nederlands", strings.forLang("nl"))
        assertEquals(null, strings.forLang("fr"))

        val map = strings.toLangMap()
        assertEquals(3, map.size)
        assertEquals("English", map["en"])
    }

    @Test
    fun testMultiLangURIExtensions() {
        val uris =
            listOf(
                MultiLangURI("en", "https://example.com/en"),
                MultiLangURI("nl", "https://example.com/nl"),
            )

        assertEquals("https://example.com/en", uris.forLang("en"))
        assertEquals(null, uris.forLang("fr"))

        val map = uris.toURIMap()
        assertEquals(2, map.size)
    }

    @Test
    fun testPolicyOrLegalNoticeRoundTrip() {
        val lote = createLoTEWithPolicy()
        val json = LoTEJson.encode(lote)
        val parsed = LoTEJson.parse(json)

        assertEquals(2, parsed.listAndSchemeInformation.policyOrLegalNotice.size)
        assertEquals("en", parsed.listAndSchemeInformation.policyOrLegalNotice[0].lang)
        assertEquals("Legal notice text", parsed.listAndSchemeInformation.policyOrLegalNotice[0].notice)
    }

    @Test
    fun testIgnoreUnknownKeys() {
        val jsonWithUnknownKeys =
            """
            {
                "ListAndSchemeInformation": {
                    "LoTEVersionIdentifier": 1,
                    "LoTESequenceNumber": 1,
                    "SchemeOperatorName": [{"lang": "en", "value": "Test"}],
                    "ListIssueDateTime": "2025-01-01T00:00:00Z",
                    "NextUpdate": "2025-02-01T00:00:00Z",
                    "UnknownField": "should be ignored"
                },
                "AnotherUnknownField": true
            }
            """.trimIndent()

        val parsed = LoTEJson.parse(jsonWithUnknownKeys)
        assertEquals(1, parsed.listAndSchemeInformation.versionIdentifier)
        assertEquals(1, parsed.listAndSchemeInformation.sequenceNumber)
    }

    @Test
    fun testEmptyTrustedEntitiesList() {
        val lote = createMinimalLoTE()
        val json = LoTEJson.encode(lote)
        val parsed = LoTEJson.parse(json)

        assertTrue(parsed.trustedEntitiesList.isEmpty())
    }

    @Test
    fun testServiceHistoryRoundTrip() {
        val lote = createLoTEWithServiceHistory()
        val json = LoTEJson.encode(lote)
        val parsed = LoTEJson.parse(json)

        val service = parsed.trustedEntitiesList[0].trustedEntityServices[0]
        assertEquals(1, service.serviceHistory.size)
        assertEquals(LoTEServiceStatus.WITHDRAWN, service.serviceHistory[0].serviceStatus)
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
                                location = "https://example.com/member-state-lote.json",
                                qualifiers =
                                    listOf(
                                        LoTEQualifier(
                                            loTEType = LoTEType.EU_PID_PROVIDERS,
                                            schemeTerritory = "NL",
                                            schemeOperatorName = listOf(MultiLangString("en", "NL Authority")),
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
}

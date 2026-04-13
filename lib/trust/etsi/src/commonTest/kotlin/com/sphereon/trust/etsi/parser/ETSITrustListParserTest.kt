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

package com.sphereon.trust.etsi.parser

import com.sphereon.trust.etsi.lote.model.forLang
import nl.adaptivity.xmlutil.core.impl.multiplatform.assert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull



/**
 * Tests for ETSI Trust List Parser.
 *
 * The parser implementation supports ETSI TS 119 612 v2.3.1 (v6) format.
 *
 * Current implementation includes:
 * - SchemeInformation with v6 fields (SchemeTypeCommunityRules, PolicyOrLegalNotice, HistoricalInformationPeriod, DistributionPoints)
 * - PointersToOtherTSL with AdditionalInformation/OtherInformation structure
 * - TSPInformation with TSPAddress (PostalAddresses/ElectronicAddress), TSPTradeName
 * - ServiceDigitalIdentity with multiple DigitalId elements
 * - Multilingual fields (Name/URI elements with xml:lang attributes)
 *
 * Integration tests based on real v6 XML examples from DSS project:
 * https://github.com/esig/dss/tree/master/specs-trusted-list/src/test/resources/tlv6.xml
 */
class ETSITrustListParserTest {

    private val parser = StreamingETSITrustListParser()

    @Test
    fun testParserRejectsInvalidXml() {
        val invalidXml = "<invalid>xml</wrong>"

        assertFailsWith<ETSIParseException> {
            parser.parseFromString(invalidXml)
        }
    }

    @Test
    fun testParserRejectsEmptyString() {
        assertFailsWith<ETSIParseException> {
            parser.parseFromString("")
        }
    }

    @Test
    fun testParserRejectsNonXmlContent() {
        val nonXml = "This is not XML at all"

        assertFailsWith<ETSIParseException> {
            parser.parseFromString(nonXml)
        }
    }

    @Test
    fun testParseFromBytesDelegatesToParseFromString() {
        val invalidXml = "<invalid>xml</wrong>"
        val bytes = invalidXml.encodeToByteArray()

        // Should fail the same way as parseFromString
        assertFailsWith<ETSIParseException> {
            parser.parseFromBytes(bytes)
        }
    }

    @Test
    fun testEtsiParseExceptionConstructionWithMessageOnly() {
        val exception = ETSIParseException("Test error")

        assertEquals("Test error", exception.message)
        assertEquals(null, exception.cause)
    }

    @Test
    fun testEtsiParseExceptionConstructionWithCause() {
        val cause = IllegalArgumentException("Root cause")
        val exception = ETSIParseException("Test error", cause)

        assertEquals("Test error", exception.message)
        assertNotNull(exception.cause)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun testParserValidatesInterfaceContract() {
        // Verify parser implements the interface
        val parserInterface: ETSITrustListParser = parser
        assertNotNull(parserInterface)
    }

    /**
     * Integration test based on real v6 XML structure from DSS project.
     * Tests SchemeInformation v6 fields.
     */
    @Test
    fun testParserSupportsV6SchemeInformationFields() {
        val v6Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#">
                <SchemeInformation>
                    <TSLVersionIdentifier>6</TSLVersionIdentifier>
                    <TSLSequenceNumber>49</TSLSequenceNumber>
                    <TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric</TSLType>
                    <SchemeOperatorName>
                        <Name xml:lang="en">Finnish Transport and Communications Agency Traficom</Name>
                        <Name xml:lang="fi">Liikenne- ja viestintävirasto Traficom</Name>
                        <Name xml:lang="sv">Transport- och kommunikationsverket Traficom</Name>
                    </SchemeOperatorName>
                    <SchemeOperatorAddress>
                        <PostalAddresses>
                            <PostalAddress xml:lang="en">
                                <StreetAddress>P.O. Box 313</StreetAddress>
                                <Locality>Helsinki</Locality>
                                <PostalCode>00561</PostalCode>
                                <CountryName>FI</CountryName>
                            </PostalAddress>
                        </PostalAddresses>
                        <ElectronicAddress>
                            <URI xml:lang="en">mailto:kirjaamo@traficom.fi</URI>
                            <URI xml:lang="en">https://www.traficom.fi/en</URI>
                        </ElectronicAddress>
                    </SchemeOperatorAddress>
                    <SchemeName>
                        <Name xml:lang="en">FI Trusted list</Name>
                    </SchemeName>
                    <SchemeInformationURI>
                        <URI xml:lang="en">https://www.traficom.fi/en</URI>
                    </SchemeInformationURI>
                    <StatusDeterminationApproach>http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate</StatusDeterminationApproach>
                    <SchemeTypeCommunityRules>
                        <URI xml:lang="en">http://uri.etsi.org/TrstSvc/TrustedList/schemerules/EUcommon</URI>
                        <URI xml:lang="en">http://uri.etsi.org/TrstSvc/TrustedList/schemerules/FI</URI>
                    </SchemeTypeCommunityRules>
                    <SchemeTerritory>FI</SchemeTerritory>
                    <PolicyOrLegalNotice>
                        <TSLLegalNotice xml:lang="en">The applicable legal framework for the present trusted list is Regulation (EU) No 910/2014</TSLLegalNotice>
                        <TSLLegalNotice xml:lang="fi">Tähän luotettuun luetteloon sovelletaan Euroopan parlamentin ja neuvoston asetusta (EU) N:o 910/2014</TSLLegalNotice>
                    </PolicyOrLegalNotice>
                    <HistoricalInformationPeriod>65535</HistoricalInformationPeriod>
                    <ListIssueDateTime>2024-01-01T00:00:00Z</ListIssueDateTime>
                    <NextUpdate>
                        <dateTime>2024-02-01T00:00:00Z</dateTime>
                    </NextUpdate>
                </SchemeInformation>
                <TrustServiceProviderList/>
            </TrustServiceStatusList>
        """.trimIndent()

        val result = parser.parseFromString(v6Xml)

        // Verify v6-specific fields
        assertEquals(6, result.versionIdentifier)
        assertEquals(49, result.sequenceNumber)
        assertEquals("FI", result.schemeTerritory)

        // SchemeTypeCommunityRules - multilingual URIs
        assertEquals(2, result.schemeTypeCommunityRules.size)
        assertEquals("http://uri.etsi.org/TrstSvc/TrustedList/schemerules/EUcommon", result.schemeTypeCommunityRules.forLang("en"))

        // PolicyOrLegalNotice - multilingual legal notices
        assertEquals(2, result.policyOrLegalNotice.size)
        assert(result.policyOrLegalNotice.forLang("en")!!.contains("Regulation (EU) No 910/2014"))
        assert(result.policyOrLegalNotice.forLang("fi")!!.contains("Euroopan parlamentin"))

        // HistoricalInformationPeriod
        assertEquals(65535, result.historicalInformationPeriod)

        // Multilingual scheme operator names
        assertEquals(3, result.schemeOperatorName.size)
        assertEquals("Finnish Transport and Communications Agency Traficom", result.schemeOperatorName.forLang("en"))
        assertEquals("Liikenne- ja viestintävirasto Traficom", result.schemeOperatorName.forLang("fi"))
        assertEquals("Transport- och kommunikationsverket Traficom", result.schemeOperatorName.forLang("sv"))
    }

    /**
     * Integration test based on real v6 XML structure from DSS project.
     * Tests TSPAddress with multiple postal addresses and electronic addresses.
     */
    @Test
    fun testParserSupportsV6TspAddressStructure() {
        val v6Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#">
                <SchemeInformation>
                    <TSLVersionIdentifier>6</TSLVersionIdentifier>
                    <TSLSequenceNumber>1</TSLSequenceNumber>
                    <TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric</TSLType>
                    <SchemeOperatorName><Name xml:lang="en">Test</Name></SchemeOperatorName>
                    <SchemeOperatorAddress>
                        <PostalAddresses>
                            <PostalAddress><StreetAddress>Test</StreetAddress><Locality>Test</Locality><CountryName>FI</CountryName></PostalAddress>
                        </PostalAddresses>
                        <ElectronicAddress><URI>test@example.com</URI></ElectronicAddress>
                    </SchemeOperatorAddress>
                    <SchemeName><Name xml:lang="en">Test</Name></SchemeName>
                    <SchemeInformationURI><URI xml:lang="en">https://test.com</URI></SchemeInformationURI>
                    <StatusDeterminationApproach>test</StatusDeterminationApproach>
                    <SchemeTerritory>FI</SchemeTerritory>
                    <ListIssueDateTime>2024-01-01T00:00:00Z</ListIssueDateTime>
                    <NextUpdate><dateTime>2024-02-01T00:00:00Z</dateTime></NextUpdate>
                </SchemeInformation>
                <TrustServiceProviderList>
                    <TrustServiceProvider>
                        <TSPInformation>
                            <TSPName>
                                <Name xml:lang="en">Test Trust Service Provider</Name>
                                <Name xml:lang="fi">Testi Luottamuspalveluntarjoaja</Name>
                            </TSPName>
                            <TSPTradeName>
                                <Name xml:lang="en">TestTSP Trading Ltd</Name>
                            </TSPTradeName>
                            <TSPAddress>
                                <PostalAddresses>
                                    <PostalAddress xml:lang="en">
                                        <StreetAddress>Test Street 123</StreetAddress>
                                        <Locality>Helsinki</Locality>
                                        <StateOrProvince>Uusimaa</StateOrProvince>
                                        <PostalCode>00100</PostalCode>
                                        <CountryName>FI</CountryName>
                                    </PostalAddress>
                                    <PostalAddress xml:lang="fi">
                                        <StreetAddress>Testikatu 123</StreetAddress>
                                        <Locality>Helsinki</Locality>
                                        <PostalCode>00100</PostalCode>
                                        <CountryName>FI</CountryName>
                                    </PostalAddress>
                                </PostalAddresses>
                                <ElectronicAddress>
                                    <URI xml:lang="en">mailto:info@testtsp.fi</URI>
                                    <URI xml:lang="en">https://www.testtsp.fi</URI>
                                </ElectronicAddress>
                            </TSPAddress>
                            <TSPInformationURI>
                                <URI xml:lang="en">https://www.testtsp.fi/info</URI>
                            </TSPInformationURI>
                        </TSPInformation>
                        <TSPServices/>
                    </TrustServiceProvider>
                </TrustServiceProviderList>
            </TrustServiceStatusList>
        """.trimIndent()

        val result = parser.parseFromString(v6Xml)

        // Verify TSP was parsed
        assertEquals(1, result.trustedEntities.size)
        val tsp = result.trustedEntities[0]

        // TSPName multilingual
        assertEquals(2, tsp.trustedEntityInformation.name.size)
        assertEquals("Test Trust Service Provider", tsp.trustedEntityInformation.name.forLang("en"))
        assertEquals("Testi Luottamuspalveluntarjoaja", tsp.trustedEntityInformation.name.forLang("fi"))

        // TSPTradeName
        assertEquals(1, tsp.trustedEntityInformation.tradeName.size)
        assertEquals("TestTSP Trading Ltd", tsp.trustedEntityInformation.tradeName.forLang("en"))

        // TSPAddress structure
        val tspAddress = tsp.trustedEntityInformation.address

        // Multiple postal addresses (v6 feature)
        assertEquals(2, tspAddress.postalAddresses.size)
        assertEquals("Test Street 123", tspAddress.postalAddresses[0].streetAddress)
        assertEquals("Helsinki", tspAddress.postalAddresses[0].locality)
        assertEquals("Uusimaa", tspAddress.postalAddresses[0].stateOrProvince)
        assertEquals("00100", tspAddress.postalAddresses[0].postalCode)
        assertEquals("Testikatu 123", tspAddress.postalAddresses[1].streetAddress)

        // Electronic addresses (v6 feature)
        assertEquals(2, tspAddress.electronicAddresses.size)
        assertEquals("mailto:info@testtsp.fi", tspAddress.electronicAddresses[0])
        assertEquals("https://www.testtsp.fi", tspAddress.electronicAddresses[1])
    }

    /**
     * Integration test based on real v6 XML structure from DSS project.
     * Tests OtherTSLPointer with AdditionalInformation containing TextualInformation and OtherInformation.
     */
    @Test
    fun testParserSupportsV6OtherTslPointerWithAdditionalInformation() {
        val v6Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#" xmlns:ns3="http://uri.etsi.org/02231/v2/additionaltypes#">
                <SchemeInformation>
                    <TSLVersionIdentifier>6</TSLVersionIdentifier>
                    <TSLSequenceNumber>1</TSLSequenceNumber>
                    <TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric</TSLType>
                    <SchemeOperatorName><Name xml:lang="en">Test</Name></SchemeOperatorName>
                    <SchemeOperatorAddress>
                        <PostalAddresses>
                            <PostalAddress><StreetAddress>Test</StreetAddress><Locality>Test</Locality><CountryName>FI</CountryName></PostalAddress>
                        </PostalAddresses>
                        <ElectronicAddress><URI>test@example.com</URI></ElectronicAddress>
                    </SchemeOperatorAddress>
                    <SchemeName><Name xml:lang="en">Test</Name></SchemeName>
                    <SchemeInformationURI><URI xml:lang="en">https://test.com</URI></SchemeInformationURI>
                    <StatusDeterminationApproach>test</StatusDeterminationApproach>
                    <SchemeTerritory>FI</SchemeTerritory>
                    <ListIssueDateTime>2024-01-01T00:00:00Z</ListIssueDateTime>
                    <NextUpdate><dateTime>2024-02-01T00:00:00Z</dateTime></NextUpdate>
                    <PointersToOtherTSL>
                        <OtherTSLPointer>
                            <ServiceDigitalIdentities>
                                <ServiceDigitalIdentity>
                                    <DigitalId>
                                        <X509SubjectName>CN=Test LOTL Signer,O=European Commission,C=EU</X509SubjectName>
                                    </DigitalId>
                                </ServiceDigitalIdentity>
                            </ServiceDigitalIdentities>
                            <TSLLocation>https://ec.europa.eu/tools/lotl/eu-lotl.xml</TSLLocation>
                            <AdditionalInformation>
                                <TextualInformation xml:lang="en">European Union LOTL</TextualInformation>
                                <TextualInformation xml:lang="fi">Euroopan unionin LOTL</TextualInformation>
                                <OtherInformation>
                                    <TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists</TSLType>
                                </OtherInformation>
                                <OtherInformation>
                                    <SchemeTerritory>EU</SchemeTerritory>
                                </OtherInformation>
                                <OtherInformation>
                                    <ns3:MimeType>application/vnd.etsi.tsl+xml</ns3:MimeType>
                                </OtherInformation>
                                <OtherInformation>
                                    <SchemeOperatorName>
                                        <Name xml:lang="en">European Commission</Name>
                                    </SchemeOperatorName>
                                </OtherInformation>
                            </AdditionalInformation>
                        </OtherTSLPointer>
                    </PointersToOtherTSL>
                </SchemeInformation>
                <TrustServiceProviderList/>
            </TrustServiceStatusList>
        """.trimIndent()

        val result = parser.parseFromString(v6Xml)

        // Verify OtherTSLPointer was parsed
        assertEquals(1, result.pointersToOtherLoTE.size)
        val pointer = result.pointersToOtherLoTE[0]

        assertEquals("https://ec.europa.eu/tools/lotl/eu-lotl.xml", pointer.location)

        // ServiceDigitalIdentities with multiple DigitalId elements
        assertEquals(1, pointer.serviceDigitalIdentities.size)
        assert(pointer.serviceDigitalIdentities[0].subjectName!!.contains("CN=Test LOTL Signer"))

        // AdditionalInformation (v6 feature)
        assertNotNull(pointer.additionalInformation)
        val additionalInfo = pointer.additionalInformation!!

        // TextualInformation (multilingual)
        assertEquals(2, additionalInfo.textualInformation.size)
        assertEquals("European Union LOTL", additionalInfo.textualInformation.forLang("en"))
        assertEquals("Euroopan unionin LOTL", additionalInfo.textualInformation.forLang("fi"))

        // OtherInformation - contains TSLType, SchemeTerritory, MimeType, SchemeOperatorName
        assertEquals(4, additionalInfo.otherInformation.size)
        // Verify one contains TSLType
        assert(additionalInfo.otherInformation.any { it.contains("EUlistofthelists") })
        // Verify one contains SchemeTerritory
        assert(additionalInfo.otherInformation.any { it.contains("EU") })
        // Verify one contains MimeType
        assert(additionalInfo.otherInformation.any { it.contains("application/vnd.etsi.tsl+xml") })
    }

}

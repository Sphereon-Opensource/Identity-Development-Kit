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

package com.sphereon.trust.etsi.parser

import com.sphereon.trust.etsi.lote.model.forLang
import com.sphereon.trust.etsi.lote.serialization.LoTEJson
import com.sphereon.trust.etsi.lote.serialization.LoTEXml
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.LoTE612Converter.toETSILoTE
import com.sphereon.trust.etsi.model.LoTE612Converter.toLoTE
import com.sphereon.trust.etsi.testutil.EtsiTestContext
import com.sphereon.trust.etsi.testutil.FIDES_LOTL_URL
import com.sphereon.trust.etsi.testutil.FIDES_TL_URL
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-format conversion integration tests.
 *
 * Tests the full pipeline: 612 XML -> ETSILoTE -> 602 LoTE -> JSON -> LoTE -> ETSILoTE -> verify equivalence.
 *
 * Uses real FIDES sandbox trust list files fetched from GitHub:
 * - FIDES-LOTL.xml: https://github.com/FIDEScommunity/fides-trust-list/blob/main/FIDES-LOTL.xml
 * - FIDES-TL.xml: https://github.com/FIDEScommunity/fides-trust-list/blob/main/FIDES-TL.xml
 */
class CrossFormatConversionTest {
    private val ctx = EtsiTestContext("cross-format-test", this)
    private val parser = StreamingETSITrustListParser()

    // ===== FIDES TL (Trust List with TSPs) =====

    @Test
    fun testFidesTL612XmlTo602JsonRoundTrip() =
        runTest {
            val xml = ctx.fetchUrl(FIDES_TL_URL)
            val etsiLoTE = parser.parseFromString(xml)
            val roundTripped = roundTripViaJson(etsiLoTE)

            assertEquals(etsiLoTE.versionIdentifier, roundTripped.versionIdentifier)
            assertEquals(etsiLoTE.sequenceNumber, roundTripped.sequenceNumber)
            assertEquals(etsiLoTE.type, roundTripped.type)
            assertEquals(etsiLoTE.schemeTerritory, roundTripped.schemeTerritory)
            assertEquals(etsiLoTE.statusDeterminationApproach, roundTripped.statusDeterminationApproach)
            assertEquals(etsiLoTE.listIssueDateTime, roundTripped.listIssueDateTime)
            assertEquals(etsiLoTE.nextUpdate, roundTripped.nextUpdate)
            assertEquals(etsiLoTE.historicalInformationPeriod, roundTripped.historicalInformationPeriod)
            assertEquals(etsiLoTE.trustedEntities.size, roundTripped.trustedEntities.size)
        }

    @Test
    fun testFidesTLSchemeInfoSurvivesRoundTrip() =
        runTest {
            val xml = ctx.fetchUrl(FIDES_TL_URL)
            val etsiLoTE = parser.parseFromString(xml)
            val roundTripped = roundTripViaJson(etsiLoTE)

            assertEquals(
                etsiLoTE.schemeOperatorName.forLang("en"),
                roundTripped.schemeOperatorName.forLang("en"),
            )
            assertEquals(
                etsiLoTE.schemeName.forLang("en"),
                roundTripped.schemeName.forLang("en"),
            )
            assertEquals(etsiLoTE.schemeInformationURI.size, roundTripped.schemeInformationURI.size)
            assertEquals(etsiLoTE.schemeTypeCommunityRules.size, roundTripped.schemeTypeCommunityRules.size)
            assertEquals(etsiLoTE.policyOrLegalNotice.size, roundTripped.policyOrLegalNotice.size)
            assertEquals(etsiLoTE.distributionPoints, roundTripped.distributionPoints)
        }

    @Test
    fun testFidesTLSchemeOperatorAddressSurvivesRoundTrip() =
        runTest {
            val xml = ctx.fetchUrl(FIDES_TL_URL)
            val etsiLoTE = parser.parseFromString(xml)
            val roundTripped = roundTripViaJson(etsiLoTE)

            assertNotNull(etsiLoTE.schemeOperatorAddress)
            assertNotNull(roundTripped.schemeOperatorAddress)

            val original = etsiLoTE.schemeOperatorAddress!!
            val result = roundTripped.schemeOperatorAddress!!

            assertEquals(original.postalAddresses.size, result.postalAddresses.size)
            if (original.postalAddresses.isNotEmpty()) {
                assertEquals(original.postalAddresses[0].countryName, result.postalAddresses[0].countryName)
                assertEquals(original.postalAddresses[0].locality, result.postalAddresses[0].locality)
                assertEquals(original.postalAddresses[0].streetAddress, result.postalAddresses[0].streetAddress)
            }
            assertEquals(original.electronicAddresses.size, result.electronicAddresses.size)
        }

    @Test
    fun testFidesTLAllTSPsSurviveRoundTrip() =
        runTest {
            val xml = ctx.fetchUrl(FIDES_TL_URL)
            val etsiLoTE = parser.parseFromString(xml)
            val roundTripped = roundTripViaJson(etsiLoTE)

            assertTrue(etsiLoTE.trustedEntities.isNotEmpty(), "Expected TSPs in FIDES TL")
            assertEquals(
                etsiLoTE.trustedEntities.size,
                roundTripped.trustedEntities.size,
                "Expected same number of TSPs after round-trip",
            )

            for (i in etsiLoTE.trustedEntities.indices) {
                val original = etsiLoTE.trustedEntities[i]
                val result = roundTripped.trustedEntities[i]

                assertEquals(
                    original.trustedEntityInformation.name.forLang("en"),
                    result.trustedEntityInformation.name.forLang("en"),
                    "TSP name mismatch at index $i",
                )
                assertEquals(
                    original.trustedEntityInformation.tradeName.forLang("en"),
                    result.trustedEntityInformation.tradeName.forLang("en"),
                    "TSP trade name mismatch at index $i",
                )
                assertEquals(
                    original.trustedEntityInformation.address.postalAddresses.size,
                    result.trustedEntityInformation.address.postalAddresses.size,
                    "TSP postal address count mismatch at index $i",
                )
                assertEquals(
                    original.trustedEntityInformation.informationURI.size,
                    result.trustedEntityInformation.informationURI.size,
                    "TSP information URI count mismatch at index $i",
                )
                assertEquals(
                    original.trustedEntityServices.size,
                    result.trustedEntityServices.size,
                    "Service count mismatch for TSP at index $i",
                )
            }
        }

    @Test
    fun testFidesTLServiceInfoSurvivesRoundTrip() =
        runTest {
            val xml = ctx.fetchUrl(FIDES_TL_URL)
            val etsiLoTE = parser.parseFromString(xml)
            val roundTripped = roundTripViaJson(etsiLoTE)

            for (i in etsiLoTE.trustedEntities.indices) {
                val originalServices = etsiLoTE.trustedEntities[i].trustedEntityServices
                val resultServices = roundTripped.trustedEntities[i].trustedEntityServices

                for (j in originalServices.indices) {
                    val original = originalServices[j].serviceInformation
                    val result = resultServices[j].serviceInformation

                    assertEquals(
                        original.serviceTypeIdentifier,
                        result.serviceTypeIdentifier,
                        "Service type mismatch at TSP $i, service $j",
                    )
                    assertEquals(
                        original.serviceName.forLang("en"),
                        result.serviceName.forLang("en"),
                        "Service name mismatch at TSP $i, service $j",
                    )
                    assertEquals(
                        original.serviceStatus,
                        result.serviceStatus,
                        "Service status mismatch at TSP $i, service $j",
                    )
                    assertEquals(
                        original.statusStartingTime,
                        result.statusStartingTime,
                        "Status starting time mismatch at TSP $i, service $j",
                    )
                }
            }
        }

    @Test
    fun testFidesTLDigitalIdentitySurvivesRoundTrip() =
        runTest {
            val xml = ctx.fetchUrl(FIDES_TL_URL)
            val etsiLoTE = parser.parseFromString(xml)
            val roundTripped = roundTripViaJson(etsiLoTE)

            for (i in etsiLoTE.trustedEntities.indices) {
                val originalId =
                    etsiLoTE.trustedEntities[i]
                        .trustedEntityServices[0]
                        .serviceInformation.serviceDigitalIdentity
                val resultId =
                    roundTripped.trustedEntities[i]
                        .trustedEntityServices[0]
                        .serviceInformation.serviceDigitalIdentity

                assertEquals(
                    originalId.x509Certificates.size,
                    resultId.x509Certificates.size,
                    "Certificate count mismatch at TSP $i",
                )
                if (originalId.x509Certificates.isNotEmpty()) {
                    assertEquals(
                        originalId.x509Certificates[0],
                        resultId.x509Certificates[0],
                        "Certificate mismatch at TSP $i",
                    )
                }
                assertEquals(
                    originalId.subjectName,
                    resultId.subjectName,
                    "Subject name mismatch at TSP $i",
                )
                assertEquals(
                    originalId.x509SKIs,
                    resultId.x509SKIs,
                    "X509 SKI mismatch at TSP $i",
                )
            }
        }

    @Test
    fun testFidesTLParseFromJsonProducesEquivalentResult() =
        runTest {
            val xml = ctx.fetchUrl(FIDES_TL_URL)
            val fromXml = parser.parseFromString(xml)

            val json = LoTEJson.encode(fromXml.toLoTE())
            val fromJson = parser.parseFromJson(json)

            assertEquals(fromXml.versionIdentifier, fromJson.versionIdentifier)
            assertEquals(fromXml.sequenceNumber, fromJson.sequenceNumber)
            assertEquals(fromXml.schemeTerritory, fromJson.schemeTerritory)
            assertEquals(fromXml.trustedEntities.size, fromJson.trustedEntities.size)
        }

    // ===== FIDES LOTL (List of Lists with pointer) =====

    @Test
    fun testFidesLOTL612XmlTo602JsonRoundTrip() =
        runTest {
            val xml = ctx.fetchUrl(FIDES_LOTL_URL)
            val etsiLoTE = parser.parseFromString(xml)
            val roundTripped = roundTripViaJson(etsiLoTE)

            assertEquals(etsiLoTE.versionIdentifier, roundTripped.versionIdentifier)
            assertEquals(etsiLoTE.sequenceNumber, roundTripped.sequenceNumber)
            assertEquals(etsiLoTE.type, roundTripped.type)
            assertEquals(etsiLoTE.schemeTerritory, roundTripped.schemeTerritory)
            assertEquals(etsiLoTE.listIssueDateTime, roundTripped.listIssueDateTime)
            assertEquals(etsiLoTE.nextUpdate, roundTripped.nextUpdate)

            assertTrue(etsiLoTE.trustedEntities.isEmpty())
            assertTrue(roundTripped.trustedEntities.isEmpty())
        }

    @Test
    fun testFidesLOTLPointersSurviveRoundTrip() =
        runTest {
            val xml = ctx.fetchUrl(FIDES_LOTL_URL)
            val etsiLoTE = parser.parseFromString(xml)
            val roundTripped = roundTripViaJson(etsiLoTE)

            assertTrue(etsiLoTE.pointersToOtherLoTE.isNotEmpty(), "Expected at least 1 pointer in FIDES LOTL")
            assertEquals(
                etsiLoTE.pointersToOtherLoTE.size,
                roundTripped.pointersToOtherLoTE.size,
                "Expected same number of pointers after round-trip",
            )

            val original = etsiLoTE.pointersToOtherLoTE[0]
            val result = roundTripped.pointersToOtherLoTE[0]

            assertEquals(original.location, result.location)
            assertEquals(original.schemeTerritory, result.schemeTerritory)
            assertEquals(
                original.schemeOperatorName.forLang("en"),
                result.schemeOperatorName.forLang("en"),
            )
            assertEquals(original.serviceDigitalIdentities.size, result.serviceDigitalIdentities.size)
            if (original.serviceDigitalIdentities.isNotEmpty()) {
                assertEquals(
                    original.serviceDigitalIdentities[0].x509Certificates.size,
                    result.serviceDigitalIdentities[0].x509Certificates.size,
                )
            }
        }

    @Test
    fun testFidesLOTLJsonOutputIsValidAndReparseable() =
        runTest {
            val xml = ctx.fetchUrl(FIDES_LOTL_URL)
            val etsiLoTE = parser.parseFromString(xml)
            val lote = etsiLoTE.toLoTE()
            val json = LoTEJson.encode(lote)

            assertTrue(json.contains("LoTEVersionIdentifier"))
            assertTrue(json.contains("LoTESequenceNumber"))
            assertTrue(json.contains("SchemeOperatorName"))
            assertTrue(json.contains("SchemeTerritory"))
            assertTrue(json.contains("ListIssueDateTime"))
            assertTrue(json.contains("NextUpdate"))
            assertTrue(json.contains("PointersToOtherLoTE"))

            val reparsed = LoTEJson.parse(json)
            assertEquals(lote.listAndSchemeInformation.sequenceNumber, reparsed.listAndSchemeInformation.sequenceNumber)
        }

    @Test
    fun testFidesTL602XmlRoundTripViaLoTE() =
        runTest {
            // Pipeline: 612 XML -> ETSILoTE -> 602 LoTE -> 602 XML -> LoTE -> ETSILoTE -> verify
            val xml612 = ctx.fetchUrl(FIDES_TL_URL)
            val etsiLoTE = parser.parseFromString(xml612)
            val lote = etsiLoTE.toLoTE()

            val xml602 = LoTEXml.encode(lote)
            assertTrue(xml602.contains("ListOfTrustedEntities"))
            assertTrue(xml602.contains("TrustedEntity"))

            val reparsedLoTE = LoTEXml.parse(xml602)
            val reparsedETSI = reparsedLoTE.toETSILoTE()

            assertEquals(etsiLoTE.versionIdentifier, reparsedETSI.versionIdentifier)
            assertEquals(etsiLoTE.sequenceNumber, reparsedETSI.sequenceNumber)
            assertEquals(etsiLoTE.schemeTerritory, reparsedETSI.schemeTerritory)
            assertEquals(etsiLoTE.trustedEntities.size, reparsedETSI.trustedEntities.size)

            for (i in etsiLoTE.trustedEntities.indices) {
                assertEquals(
                    etsiLoTE.trustedEntities[i]
                        .trustedEntityInformation.name
                        .forLang("en"),
                    reparsedETSI.trustedEntities[i]
                        .trustedEntityInformation.name
                        .forLang("en"),
                    "TSP name mismatch at index $i after 602 XML round-trip",
                )
            }
        }

    // ===== Helper =====

    private fun roundTripViaJson(etsiLoTE: ETSILoTE): ETSILoTE {
        val lote = etsiLoTE.toLoTE()
        val json = LoTEJson.encode(lote)
        val reparsed = LoTEJson.parse(json)
        return reparsed.toETSILoTE()
    }
}

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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-specific tests for ETSI Trust List Parser using DSS v6 resource files and EU LOTL.
 *
 * These tests use:
 * - Real v6 XML files from the DSS project: https://github.com/esig/dss/tree/master/specs-trusted-list/src/test/resources
 * - EU List of Trusted Lists (LOTL): https://ec.europa.eu/tools/lotl/eu-lotl.xml
 *
 * Test resources are located in:
 * - src/commonTest/resources/dss-v6/
 * - src/commonTest/resources/eu-lotl/
 */
class ETSITrustListParserDSSResourcesTest {

    private fun loadResourceFile(directory: String, filename: String): String {
        val resourcePath = "src/commonTest/resources/$directory/$filename"
        val file = File(resourcePath)
        assertTrue(file.exists(), "Resource file not found: $resourcePath")
        return file.readText()
    }

    private fun loadDSSResourceFile(filename: String) = loadResourceFile("dss-v6", filename)
    private fun loadEULOTLResourceFile(filename: String) = loadResourceFile("eu-lotl", filename)

    @Test
    fun `test parse DSS tlv6 xml resource file`() {
        // This test uses the complete v6 trust list from DSS project
        // File: https://github.com/esig/dss/blob/master/specs-trusted-list/src/test/resources/tlv6.xml
        val xml = loadDSSResourceFile("tlv6.xml")

        val parser = StreamingETSITrustListParser()
        val result = parser.parseFromString(xml)

        // Verify basic structure
        assertNotNull(result)
        assertEquals(6, result.versionIdentifier)
        assertEquals(49, result.sequenceNumber)
        assertEquals("FI", result.schemeTerritory)

        // Verify v6 fields are parsed
        assertEquals(2, result.schemeTypeCommunityRules.size)
        assertEquals(2, result.policyOrLegalNotice.size)
        assertEquals(65535, result.historicalInformationPeriod)

        // Verify trust service providers are parsed
        assertTrue(result.trustedEntities.isNotEmpty())

        // Verify distribution points are parsed
        assertTrue(result.distributionPoints.isNotEmpty())

        // Verify scheme operator name
        assertTrue(result.schemeOperatorName.isNotEmpty())
        assertEquals("Finnish Transport and Communications Agency Traficom", result.schemeOperatorName.forLang("en"))

        // Verify scheme type community rules contain both URIs
        assertTrue(result.schemeTypeCommunityRules.forLang("en")?.contains("EUcommon") == true)
    }

    @Test
    fun `test parse DSS tlv6 custom attribute xml resource file`() {
        // This test uses the v6 trust list with custom attributes from DSS project
        val xml = loadDSSResourceFile("tlv6_custom_attribute.xml")

        val parser = StreamingETSITrustListParser()
        val result = parser.parseFromString(xml)

        // Should parse successfully even with custom attributes
        assertNotNull(result)
        assertEquals(6, result.versionIdentifier)
        assertEquals("FI", result.schemeTerritory)

        // Verify other standard fields still parse correctly
        assertEquals(49, result.sequenceNumber)
        assertEquals(2, result.schemeTypeCommunityRules.size)
    }

    @Test
    fun `test parse DSS tlv6 empty xml resource file`() {
        // This test uses an empty v6 trust list structure from DSS project
        val xml = loadDSSResourceFile("tlv6_empty.xml")

        val parser = StreamingETSITrustListParser()

        // Should throw exception as SchemeInformation is missing
        assertFailsWith<ETSIParseException> {
            parser.parseFromString(xml)
        }
    }

    @Test
    fun `test parse DSS tlv6 wrong inside xml resource file`() {
        // This test uses a v6 trust list with invalid content from DSS project
        val xml = loadDSSResourceFile("tlv6_wrong_inside.xml")

        val parser = StreamingETSITrustListParser()

        // Should throw exception as SchemeInformation is missing (has invalid <hello><world/> instead)
        assertFailsWith<ETSIParseException> {
            parser.parseFromString(xml)
        }
    }

    // ===== EU LOTL Tests =====

    @Test
    fun `test parse EU LOTL xml resource file`() {
        // This test uses the real EU List of Trusted Lists from https://ec.europa.eu/tools/lotl/eu-lotl.xml
        val xml = loadEULOTLResourceFile("eu-lotl.xml")

        val parser = StreamingETSITrustListParser()
        val result = parser.parseFromString(xml)

        // Verify basic structure
        assertNotNull(result)
        assertEquals(5, result.versionIdentifier) // EU LOTL is v5
        assertEquals("EU", result.schemeTerritory)

        // Verify scheme operator is European Commission
        assertTrue(result.schemeOperatorName.isNotEmpty())
        assertEquals("European Commission", result.schemeOperatorName.forLang("en"))

        // Verify TSL type is list of lists
        assertTrue(result.type.contains("EUlistofthelists"))

        // Verify pointers to other trust lists (member states)
        assertTrue(result.pointersToOtherLoTE.isNotEmpty())

        // Verify distribution points
        assertTrue(result.distributionPoints.isNotEmpty())
        assertTrue(result.distributionPoints.any { it.contains("eu-lotl.xml") })
    }

}

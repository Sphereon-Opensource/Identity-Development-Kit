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
 */

package com.sphereon.catalog.eu

import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.model.CatalogueExtension
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.catalog.eu.serializer.CatalogueEntryPaths
import com.sphereon.catalog.eu.serializer.DefaultEuCatalogueXmlSerializer
import com.sphereon.catalog.eu.testutil.readTestResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EuCatalogueXmlSerializerTest {
    private val parser = DefaultEuCatalogueXmlParser()
    private val serializer = DefaultEuCatalogueXmlSerializer()

    private fun text(path: String) = readTestResource("eu-catalogues/$path")

    private val familyName = "synthetic/attributes/eu.europa.ec.eudi.pid.1/family_name.xml"
    private val birthDate = "synthetic/attributes/eu.europa.ec.eudi.pid.1/birth_date.xml"

    @Test
    fun attributeEntriesRoundTrip() {
        for (path in listOf(familyName, birthDate)) {
            val parsed = parser.parseAttributeEntry(text(path).encodeToByteArray()).value.value
            val written = serializer.serializeAttributeEntry(parsed)
            assertTrue(written.isOk, "serialize failed for $path")
            val reparsed = parser.parseAttributeEntry(written.value.encodeToByteArray())
            assertTrue(reparsed.isOk, "reparse failed for $path: ${if (reparsed.isErr) reparsed.error.reason else ""}")
            assertEquals(parsed, reparsed.value.value, path)
        }
    }

    @Test
    fun schemeEntryRoundTripsIncludingTheTrustModelExtension() {
        val parsed = parser.parseSchemeEntry(text("synthetic/schemes/eu-pid.xml").encodeToByteArray()).value.value
        val written = serializer.serializeSchemeEntry(parsed).value
        val reparsed = parser.parseSchemeEntry(written.encodeToByteArray()).value.value
        assertEquals(parsed, reparsed)
        assertEquals(2, reparsed.versions.single().eaaTypes.single().trustModelTypes.size)
    }

    @Test
    fun catalogueOfAttributesRoundTripsWithoutTheSignature() {
        val parsed = parser.parseCoa(text("synthetic/coa-populated.xml").encodeToByteArray()).value.value
        val written = serializer.serializeCoa(parsed).value
        val reparsed = parser.parseCoa(written.encodeToByteArray()).value.value
        assertEquals(parsed.copy(hasSignature = false), reparsed)
        assertEquals(false, reparsed.hasSignature)
        assertEquals(2, reparsed.namespaces.single().entries.size)
    }

    @Test
    fun catalogueOfSchemesRoundTripsWithoutTheSignature() {
        val parsed = parser.parseCos(text("synthetic/cos-populated.xml").encodeToByteArray()).value.value
        val reparsed = parser.parseCos(serializer.serializeCos(parsed).value.encodeToByteArray()).value.value
        assertEquals(parsed.copy(hasSignature = false), reparsed)
    }

    @Test
    fun liveCataloguesWithoutEntriesRoundTrip() {
        val coa = parser.parseCoa(text("live/coa.xml").encodeToByteArray()).value.value
        assertEquals(coa.copy(hasSignature = false), parser.parseCoa(serializer.serializeCoa(coa).value.encodeToByteArray()).value.value)
        val cos = parser.parseCos(text("live/cos.xml").encodeToByteArray()).value.value
        assertEquals(cos.copy(hasSignature = false), parser.parseCos(serializer.serializeCos(cos).value.encodeToByteArray()).value.value)
    }

    @Test
    fun extensionsSurviveVerbatimIncludingPrefixesMixedContentAndWhitespace() {
        val extension = "<x:foo xmlns:x=\"urn:x\" x:a=\"1\"> lead <x:bar/> mid <y xmlns=\"urn:y\">t </y> tail </x:foo>"
        val xml =
            text(birthDate).replace(
                "<vendorHint xmlns=\"urn:example:vendor\">ignorable</vendorHint>",
                extension,
            )
        val parsed = parser.parseAttributeEntry(xml.encodeToByteArray()).value.value
        val written = serializer.serializeAttributeEntry(parsed).value
        val reparsed = parser.parseAttributeEntry(written.encodeToByteArray()).value.value
        assertEquals(extension, reparsed.extensions[1].rawXml)
        assertEquals(parsed, reparsed)
        assertTrue(written.contains(extension))
    }

    @Test
    fun aTypedFieldWithoutItsExtensionIsWrittenAsTheSpecifiedExtension() {
        val parsed = parser.parseAttributeEntry(text(birthDate).encodeToByteArray()).value.value
        val authored = parsed.copy(eidasAnnexVIAttributeType = 5, extensions = emptyList())
        val reparsed = parser.parseAttributeEntry(serializer.serializeAttributeEntry(authored).value.encodeToByteArray()).value.value
        assertEquals(5, reparsed.eidasAnnexVIAttributeType)
        assertTrue(reparsed.extensions.single().critical)

        val scheme = parser.parseSchemeEntry(text("synthetic/schemes/eu-pid.xml").encodeToByteArray()).value.value
        val version = scheme.versions.single()
        val type = version.eaaTypes.single()
        val edited = scheme.copy(versions = listOf(version.copy(eaaTypes = listOf(type.copy(trustModelTypes = listOf(EuCatalogueConstants.TRUST_MODEL_QEAA))))))
        val reparsedScheme = parser.parseSchemeEntry(serializer.serializeSchemeEntry(edited).value.encodeToByteArray()).value.value
        assertEquals(listOf(EuCatalogueConstants.TRUST_MODEL_QEAA), reparsedScheme.versions.single().eaaTypes.single().trustModelTypes)
    }

    @Test
    fun noEntryWithoutTheTypedFieldGetsAnAnnexExtension() {
        val parsed = parser.parseAttributeEntry(text(familyName).encodeToByteArray()).value.value
        assertNull(parsed.eidasAnnexVIAttributeType)
        val reparsed = parser.parseAttributeEntry(serializer.serializeAttributeEntry(parsed).value.encodeToByteArray()).value.value
        assertEquals(emptyList<CatalogueExtension>(), reparsed.extensions)
    }

    @Test
    fun missingPostalAddressesCannotBeSerialized() {
        val coa = parser.parseCoa(text("synthetic/coa-populated.xml").encodeToByteArray()).value.value
        val noAddress = coa.copy(info = coa.info.copy(operator = coa.info.operator.copy(postalAddresses = emptyList())))
        val result = serializer.serializeCoa(noAddress)
        assertTrue(result.isErr)
        assertEquals(CatalogErrorCode.SCHEMA_VIOLATION.code, result.error.code)
    }

    @Test
    fun aMalformedExtensionIsRefused() {
        val parsed = parser.parseAttributeEntry(text(birthDate).encodeToByteArray()).value.value
        val broken = parsed.copy(extensions = listOf(CatalogueExtension(false, "{urn:x}foo", "<x:foo>")))
        assertTrue(serializer.serializeAttributeEntry(broken).isErr)
        val mismatched = parsed.copy(extensions = listOf(CatalogueExtension(false, "{urn:x}foo", "<bar xmlns=\"urn:x\"/>")))
        assertTrue(serializer.serializeAttributeEntry(mismatched).isErr)
    }

    @Test
    fun entryPathsArePercentEncodedPerSegmentAndDecodeBack() {
        assertEquals("attributes/eu.europa.ec.eudi.pid.1/family_name.xml", CatalogueEntryPaths.attributeEntry("eu.europa.ec.eudi.pid.1", "family_name"))
        assertEquals("attributes/a%2Fb/c%20d.xml", CatalogueEntryPaths.attributeEntry("a/b", "c d"))
        assertEquals("schemes/eu%C3%A9.xml", CatalogueEntryPaths.schemeEntry("eué"))
        assertEquals("a/b é", CatalogueEntryPaths.decodeSegment("a%2Fb%20%C3%A9"))
        assertNull(CatalogueEntryPaths.decodeSegment("bad%2"))
    }
}

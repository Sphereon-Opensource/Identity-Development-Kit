package com.sphereon.catalog.eu

import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.catalog.eu.testutil.readTestResource
import com.sphereon.catalog.eu.validation.CatalogueConformanceValidator
import com.sphereon.catalog.eu.validation.CatalogueFindingCodes
import com.sphereon.catalog.eu.validation.CatalogueProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewFixesTest {
    private val parser = DefaultEuCatalogueXmlParser()
    private val validator = CatalogueConformanceValidator()

    private fun text(path: String) = readTestResource("eu-catalogues/$path")

    private val birthPath = "synthetic/attributes/eu.europa.ec.eudi.pid.1/birth_date.xml"

    @Test
    fun schemeIndexCheckIsStrictInBothDirections() {
        val entry = parser.parseSchemeEntry(text("synthetic/schemes/eu-pid.xml").encodeToByteArray()).value.value
        val ref = parser.parseCos(text("synthetic/cos-populated.xml").encodeToByteArray()).value.value.schemes.single()
        assertEquals(emptyList(), validator.validateSchemeEntry(entry, ref))
        val lacking = validator.validateSchemeEntry(entry, ref.copy(identifier = null, registrationIdentifier = null))
        assertEquals(2, lacking.count { it.code == CatalogueFindingCodes.INDEX_ENTRY_MISMATCH })
        val differing = validator.validateSchemeEntry(entry, ref.copy(identifier = "other", registrationIdentifier = "other"))
        assertEquals(2, differing.count { it.code == CatalogueFindingCodes.INDEX_ENTRY_MISMATCH })
    }

    @Test
    fun missingOrEmptyPostalAddressesFailTheParse() {
        val coa = text("synthetic/coa-populated.xml")
        val stripped = coa.replace(Regex("<PostalAddresses>.*?</PostalAddresses>", RegexOption.DOT_MATCHES_ALL), "<PostalAddresses/>")
        val empty = parser.parseCoa(stripped.encodeToByteArray())
        assertTrue(empty.isErr)
        assertEquals(CatalogErrorCode.SCHEMA_VIOLATION.code, empty.error.code)
        val gone = parser.parseCoa(stripped.replace("<PostalAddresses/>", "").encodeToByteArray())
        assertTrue(gone.isErr)
        val scheme = text("synthetic/schemes/eu-pid.xml").replace(Regex("<PostalAddresses>.*?</PostalAddresses>", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue(parser.parseSchemeEntry(scheme.encodeToByteArray()).isErr)
    }

    @Test
    fun authoredModelsWithoutAddressesGetAnAddressFinding() {
        val coa = parser.parseCoa(text("synthetic/coa-populated.xml").encodeToByteArray()).value.value
        val noAddress = coa.copy(info = coa.info.copy(operator = coa.info.operator.copy(postalAddresses = emptyList())))
        assertEquals(listOf(CatalogueFindingCodes.ADDRESS_EMPTY), validator.validateCoa(noAddress).map { it.code })
        val scheme = parser.parseSchemeEntry(text("synthetic/schemes/eu-pid.xml").encodeToByteArray()).value.value
        val noOwnerAddress = scheme.copy(owner = scheme.owner.copy(postalAddresses = emptyList()))
        assertEquals(listOf(CatalogueFindingCodes.ADDRESS_EMPTY), validator.validateSchemeEntry(noOwnerAddress).map { it.code })
    }

    @Test
    fun extensionRawXmlRoundTripsPrefixesDeclarationsMixedContentAndWhitespace() {
        val extension =
            "<x:foo xmlns:x=\"urn:x\" x:a=\"1\"> lead <x:bar/> mid <y xmlns=\"urn:y\">t </y> tail </x:foo>"
        val xml =
            text(birthPath).replace(
                "<vendorHint xmlns=\"urn:example:vendor\">ignorable</vendorHint>",
                extension,
            )
        val entry = parser.parseAttributeEntry(xml.encodeToByteArray()).value.value
        val raw = entry.extensions[1]
        assertEquals("{urn:x}foo", raw.qName)
        assertEquals(extension, raw.rawXml)
    }

    @Test
    fun annexVIExtensionIsRecognisedOnlyInTheCoaNamespace() {
        val xml =
            text(birthPath)
                .replace("<eIDASAnnexVIAttribute>", "<eIDASAnnexVIAttribute xmlns=\"urn:other\">")
        val entry = parser.parseAttributeEntry(xml.encodeToByteArray()).value.value
        assertNull(entry.eidasAnnexVIAttributeType)
        val findings = validator.validateAttributeEntry(entry, profile = CatalogueProfile.custom())
        assertTrue(CatalogueConformanceValidator.hasUnknownCriticalExtension(findings))
    }

    @Test
    fun langMatchingIsNamespaceAware() {
        val xml = text(birthPath).replace("<Name xml:lang=\"en\">Date of birth</Name>", "<Name lang=\"en\">Date of birth</Name>")
        val entry = parser.parseAttributeEntry(xml.encodeToByteArray()).value.value
        assertEquals("", entry.versions.first().information.name.names.single().lang)
    }
}

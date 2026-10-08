package com.sphereon.catalog.eu

import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.model.CatalogueKind
import com.sphereon.catalog.eu.model.ParsedCatalogueDocument
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.catalog.eu.testutil.readTestResource
import com.sphereon.core.api.IdkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EuCatalogueXmlParserTest {
    private val parser = DefaultEuCatalogueXmlParser()

    private fun bytes(path: String): ByteArray = readTestResource("eu-catalogues/$path").encodeToByteArray()

    private fun <T> IdkResult<ParsedCatalogueDocument<T>, *>.parsed(): T {
        assertTrue(isOk, "expected a successful parse but got $this")
        return value.value
    }

    @Test
    fun liveCoaParsesWithoutEntries() {
        val coa = parser.parseCoa(bytes("live/coa.xml")).parsed()
        assertEquals(EuCatalogueConstants.COA_IDENTIFIER, coa.info.identifier)
        assertEquals(1, coa.info.version)
        assertEquals(1L, coa.info.sequenceNumber)
        assertEquals(EuCatalogueConstants.HISTORY_FOREVER_MONTHS, coa.info.historicalInformationPeriod)
        assertEquals("EU", coa.info.territory)
        assertTrue(coa.info.name.names.size >= 20)
        assertEquals("EU:Catalogue of attributes", coa.info.name.forLang("en"))
        assertEquals("NTRBE-0949.383.342", coa.info.operator.identifier)
        assertEquals(3, coa.info.operator.postalAddresses.size)
        assertTrue("mailto:EU-TRUST@ec.europa.eu" in coa.info.operator.electronicAddress.uris)
        assertEquals(
            "http://data.europa.eu/c9v/EUCatalogueOfAttributes/statusDeterminationApproach",
            coa.info.statusDeterminationApproach,
        )
        assertEquals("https://trust.tech.ec.europa.eu/catalogues/eu-catalogue-of-attributes.xml", coa.info.distributionPoint.downloadUrl)
        assertTrue(coa.namespaces.isEmpty())
        assertTrue(coa.hasSignature)
    }

    @Test
    fun liveCosParsesWithoutEntries() {
        val cos = parser.parseCos(bytes("live/cos.xml")).parsed()
        assertEquals(EuCatalogueConstants.COS_IDENTIFIER, cos.info.identifier)
        assertTrue(cos.schemes.isEmpty())
        assertTrue(cos.hasSignature)
    }

    @Test
    fun liveLocParsesThreePointersWithSignerCertificates() {
        val loc = parser.parseLoc(bytes("live/loc.xml")).parsed()
        assertEquals(EuCatalogueConstants.LOC_TAG, loc.loteTag)
        assertEquals(EuCatalogueConstants.LOC_TYPE, loc.lote.listAndSchemeInformation.type)
        assertTrue(loc.lote.trustedEntitiesList.isEmpty())
        assertEquals(3, loc.pointers.size)
        assertEquals(
            setOf(EuCatalogueConstants.LOC_TYPE, EuCatalogueConstants.COA_LOTE_TYPE, EuCatalogueConstants.COS_LOTE_TYPE),
            loc.pointers.mapNotNull { it.loteType }.toSet(),
        )
        loc.pointers.forEach {
            assertEquals(6, it.signerCertificates.size)
            assertTrue(it.signerCertificates.all { cert -> cert.startsWith("MII") && cert.none(Char::isWhitespace) })
            assertEquals("EU", it.schemeTerritory)
            assertEquals("application/xml", it.mimeType)
            assertTrue(it.schemeTypeCommunityRules.isNotEmpty())
        }
        assertEquals("https://trust.tech.ec.europa.eu/catalogues/eu-catalogue-of-attributes.xml", loc.pointers.first { it.loteType == EuCatalogueConstants.COA_LOTE_TYPE }.location)
        assertEquals("2027-02-10T00:00:00Z", loc.nextUpdate.toString())
        assertTrue(loc.hasSignature)
    }

    @Test
    fun parseKeepsTheExactSourceBytes() {
        val source = bytes("live/coa.xml")
        val result = parser.parseCoa(source)
        assertTrue(result.isOk)
        assertTrue(source.contentEquals(result.value.rawBytes))
    }

    @Test
    fun syntheticCoaIndexesEntriesWithBothDigestUris() {
        val coa = parser.parseCoa(bytes("synthetic/coa-populated.xml")).parsed()
        assertEquals(2L, coa.info.sequenceNumber)
        val ns = coa.namespaces.single()
        assertEquals("eu.europa.ec.eudi.pid.1", ns.identifier)
        assertEquals(listOf("family_name", "birth_date"), ns.entries.map { it.attributeIdentifier })
        assertEquals("REG-ATTR-0001", ns.entries[0].registrationIdentifier)
        assertEquals(EuCatalogueConstants.DIGEST_SHA512_SPEC, ns.entries[0].reference.digestMethod)
        assertEquals(EuCatalogueConstants.DIGEST_SHA512_XMLENC, ns.entries[1].reference.digestMethod)
        assertEquals(listOf(EuCatalogueConstants.EXC_C14N_TRANSFORM), ns.entries[0].reference.transforms)
        assertEquals(64, ns.entries[0].reference.digestValue.size)
        assertEquals("attributes/eu.europa.ec.eudi.pid.1/family_name.xml", ns.entries[0].reference.uri)
        assertEquals("https://catalogue.example.org/status", coa.info.statusDeterminationApproach)
        assertEquals("Example Province", coa.info.operator.postalAddresses.single().stateOrProvince)
    }

    @Test
    fun syntheticAttributeEntryKeepsVersionsBindingsAndSources() {
        val entry = parser.parseAttributeEntry(bytes("synthetic/attributes/eu.europa.ec.eudi.pid.1/family_name.xml")).parsed()
        assertEquals("family_name", entry.attributeIdentifier)
        assertEquals("REG-ATTR-0001", entry.registrationIdentifier)
        assertEquals("https://reference.example.org", entry.referenceBody.website)
        assertEquals(listOf("2", "1"), entry.versions.map { it.version })
        val current = entry.versions[0]
        assertEquals(EuCatalogueConstants.ATTRIBUTE_STATUS_INFORCE, current.status?.statusUri)
        assertEquals("2026-09-01T00:00:00Z", current.status?.startingAt.toString())
        assertEquals("Achternaam", current.information.name.forLang("nl"))
        assertEquals("https://concepts.example.org/family-name", current.information.semanticDescription.pointer)
        val type = current.information.dataType
        assertTrue(type.hasSpecification)
        assertEquals("https://specs.example.org/family-name", type.specificationPointer)
        assertEquals(listOf("application/dc+sd-jwt", "application/mdoc"), type.formatBindings.map { it.mediaType })
        assertEquals("eu.europa.ec.eudi.pid.1/family_name", type.formatBindings[1].formatSpecificIdentifier)
        val source = current.information.authenticSources.single()
        assertEquals("NL", source.territory)
        assertEquals("geslachtsnaam", source.sourceAttributeIdentifier)
        val endpoint = source.verificationEndpoints.single()
        assertEquals(listOf("application/json"), endpoint.responseFormats)
        val deprecated = entry.versions[1]
        assertEquals(EuCatalogueConstants.ATTRIBUTE_STATUS_DEPRECATED, deprecated.status?.statusUri)
        assertEquals("2", deprecated.status?.linked?.version)
        assertFalse(deprecated.information.dataType.hasSpecification)
        assertNull(entry.eidasAnnexVIAttributeType)
    }

    @Test
    fun syntheticAttributeEntryReadsAnnexVIExtensionAndKeepsUnknownNonCriticalOne() {
        val entry = parser.parseAttributeEntry(bytes("synthetic/attributes/eu.europa.ec.eudi.pid.1/birth_date.xml")).parsed()
        assertEquals(3, entry.eidasAnnexVIAttributeType)
        assertEquals(2, entry.extensions.size)
        val annex = entry.extensions[0]
        assertTrue(annex.critical)
        assertEquals("{${EuCatalogueConstants.COA_NAMESPACE}}eIDASAnnexVIAttribute", annex.qName)
        assertTrue(annex.rawXml.contains("<eIDASAnnexVIAttributeType>3</eIDASAnnexVIAttributeType>"))
        val vendor = entry.extensions[1]
        assertFalse(vendor.critical)
        assertEquals("{urn:example:vendor}vendorHint", vendor.qName)
    }

    @Test
    fun syntheticCosListsSchemeReferences() {
        val cos = parser.parseCos(bytes("synthetic/cos-populated.xml")).parsed()
        val ref = cos.schemes.single()
        assertEquals("eu-pid", ref.name)
        assertEquals("urn:example:scheme:pid", ref.identifier)
        assertEquals("REG-SCHEME-0001", ref.registrationIdentifier)
        assertEquals("schemes/eu-pid.xml", ref.reference.uri)
    }

    @Test
    fun syntheticSchemeEntryKeepsEaaTypesReferencesAndTrustModels() {
        val entry = parser.parseSchemeEntry(bytes("synthetic/schemes/eu-pid.xml")).parsed()
        assertEquals("eu-pid", entry.name)
        assertEquals("REG-SCHEME-0001", entry.registrationIdentifier)
        assertEquals("NTRNL-12345678", entry.owner.identifier)
        assertEquals("nl", entry.owner.postalAddresses.single().lang)
        val version = entry.versions.single()
        assertEquals(EuCatalogueConstants.SCHEME_STATUS_INFORCE, version.status.statusUri)
        assertEquals("https://schemes.example.org/pid/rulebook.pdf", version.documentUri)
        val type = version.eaaTypes.single()
        assertEquals("pid", type.identifier)
        assertEquals(2, type.attributeReferences.size)
        assertEquals("REG-ATTR-0001", type.attributeReferences[0].uniqueIdentifier)
        assertNotNull(type.attributeReferences[0].definitionPointer)
        assertNull(type.attributeReferences[0].cataloguePointer)
        assertEquals(EuCatalogueConstants.COA_IDENTIFIER, type.attributeReferences[1].cataloguePointer?.catalogueIdentifier)
        assertEquals(listOf("urn:eudi:pid:1", "eu.europa.ec.eudi.pid.1"), type.formatBindings.map { it.eaaTypeIdentifier })
        assertEquals(
            listOf(EuCatalogueConstants.TRUST_MODEL_PUB_EAA, EuCatalogueConstants.TRUST_MODEL_QEAA),
            type.trustModelTypes,
        )
    }

    @Test
    fun malformedXmlIsReportedAsAnError() {
        val result = parser.parseCoa("<CatalogueOfAttributes><unclosed>".encodeToByteArray())
        assertTrue(result.isErr)
        assertEquals(CatalogErrorCode.MALFORMED_XML.code, result.error.code)
    }

    @Test
    fun wrongRootElementIsReportedAsAnError() {
        val result = parser.parseCoa(bytes("live/cos.xml"))
        assertTrue(result.isErr)
        assertEquals(CatalogErrorCode.UNEXPECTED_ROOT.code, result.error.code)
        assertTrue(parser.parseLoc(bytes("live/coa.xml")).isErr)
    }

    @Test
    fun missingRequiredElementNamesThePath() {
        val xml = bytes("synthetic/coa-populated.xml").decodeToString().replace("<CatalogueSequenceNumber>2</CatalogueSequenceNumber>", "")
        val result = parser.parseCoa(xml.encodeToByteArray())
        assertTrue(result.isErr)
        assertEquals(CatalogErrorCode.SCHEMA_VIOLATION.code, result.error.code)
        assertEquals("CatalogueInformation/CatalogueSequenceNumber", result.error.path)
    }

    @Test
    fun invalidDigestEncodingIsRejected() {
        val xml = bytes("synthetic/coa-populated.xml").decodeToString().replaceFirst("<ds:DigestValue>", "<ds:DigestValue>!!!")
        val result = parser.parseCoa(xml.encodeToByteArray())
        assertTrue(result.isErr)
        assertEquals(CatalogErrorCode.SCHEMA_VIOLATION.code, result.error.code)
    }

    @Test
    fun catalogueKindCoversTheThreeDocuments() {
        assertEquals(setOf("LOC", "COA", "COS"), CatalogueKind.entries.map { it.name }.toSet())
    }
}

package com.sphereon.catalog.eu

import com.sphereon.catalog.eu.model.CatalogueExtension
import com.sphereon.catalog.eu.model.ElectronicAddress
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.catalog.eu.serializer.DefaultEuCatalogueXmlSerializer
import com.sphereon.catalog.eu.testutil.CatalogueXsd
import com.sphereon.catalog.eu.testutil.TestResourceConfig
import com.sphereon.catalog.eu.testutil.readTestResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The fixtures and everything the serializer writes must be valid against the official CoA and CoS 1.0.1 schemas. */
class CatalogueXsdConformanceTest {
    private val xsd = CatalogueXsd.forTestResources(TestResourceConfig.RESOURCE_PATH)
    private val parser = DefaultEuCatalogueXmlParser()
    private val serializer = DefaultEuCatalogueXmlSerializer()

    private fun text(path: String) = readTestResource("eu-catalogues/$path")

    private val familyName = "synthetic/attributes/eu.europa.ec.eudi.pid.1/family_name.xml"
    private val birthDate = "synthetic/attributes/eu.europa.ec.eudi.pid.1/birth_date.xml"

    @Test
    fun theFixturesAreValidAgainstTheOfficialSchemas() {
        assertEquals(emptyList(), xsd.errors(xsd.coa, text("synthetic/coa-populated.xml").encodeToByteArray()))
        assertEquals(emptyList(), xsd.errors(xsd.cos, text("synthetic/cos-populated.xml").encodeToByteArray()))
        assertEquals(emptyList(), xsd.errors(xsd.attribute, text(familyName).encodeToByteArray()))
        assertEquals(emptyList(), xsd.errors(xsd.attribute, text(birthDate).encodeToByteArray()))
        assertEquals(emptyList(), xsd.errors(xsd.scheme, text("synthetic/schemes/eu-pid.xml").encodeToByteArray()))
        assertEquals(emptyList(), xsd.errors(xsd.coa, text("live/coa.xml").encodeToByteArray()))
        assertEquals(emptyList(), xsd.errors(xsd.cos, text("live/cos.xml").encodeToByteArray()))
    }

    @Test
    fun theSchemaCheckIsNotVacuous() {
        val broken = text(birthDate).replace("<ReferenceBody>", "<Bogus/><ReferenceBody>")
        assertTrue(xsd.errors(xsd.attribute, broken.encodeToByteArray()).isNotEmpty())
        val noLang = text("synthetic/coa-populated.xml").replace("<Name xml:lang=\"en\">Synthetic Operator</Name>", "<Name>Synthetic Operator</Name>")
        assertTrue(xsd.errors(xsd.coa, noLang.encodeToByteArray()).isNotEmpty())
    }

    @Test
    fun serializedFixturesAreValid() {
        val coa = parser.parseCoa(text("synthetic/coa-populated.xml").encodeToByteArray()).value.value
        assertEquals(emptyList(), xsd.errors(xsd.coa, serializer.serializeCoa(coa).value.encodeToByteArray()))
        val cos = parser.parseCos(text("synthetic/cos-populated.xml").encodeToByteArray()).value.value
        assertEquals(emptyList(), xsd.errors(xsd.cos, serializer.serializeCos(cos).value.encodeToByteArray()))
        for (path in listOf(familyName, birthDate)) {
            val entry = parser.parseAttributeEntry(text(path).encodeToByteArray()).value.value
            assertEquals(emptyList(), xsd.errors(xsd.attribute, serializer.serializeAttributeEntry(entry).value.encodeToByteArray()), path)
        }
        val scheme = parser.parseSchemeEntry(text("synthetic/schemes/eu-pid.xml").encodeToByteArray()).value.value
        assertEquals(emptyList(), xsd.errors(xsd.scheme, serializer.serializeSchemeEntry(scheme).value.encodeToByteArray()))
        val liveCoa = parser.parseCoa(text("live/coa.xml").encodeToByteArray()).value.value
        assertEquals(emptyList(), xsd.errors(xsd.coa, serializer.serializeCoa(liveCoa).value.encodeToByteArray()))
        val liveCos = parser.parseCos(text("live/cos.xml").encodeToByteArray()).value.value
        assertEquals(emptyList(), xsd.errors(xsd.cos, serializer.serializeCos(liveCos).value.encodeToByteArray()))
    }

    @Test
    fun everyExtensionContainerIsWrittenInSchemaOrder() {
        val extension = { label: String -> CatalogueExtension(false, "{urn:x}foo", "<foo xmlns=\"urn:x\">$label</foo>") }
        val coa = parser.parseCoa(text("synthetic/coa-populated.xml").encodeToByteArray()).value.value
        val withInfoExtension = coa.copy(info = coa.info.copy(extensions = listOf(extension("info"))))
        assertEquals(emptyList(), xsd.errors(xsd.coa, serializer.serializeCoa(withInfoExtension).value.encodeToByteArray()))
        val cos = parser.parseCos(text("synthetic/cos-populated.xml").encodeToByteArray()).value.value
        val cosWithExtension = cos.copy(info = cos.info.copy(extensions = listOf(extension("info"))))
        assertEquals(emptyList(), xsd.errors(xsd.cos, serializer.serializeCos(cosWithExtension).value.encodeToByteArray()))

        val attribute = parser.parseAttributeEntry(text(familyName).encodeToByteArray()).value.value
        val version = attribute.versions.first()
        val withExtensions =
            attribute.copy(
                extensions = listOf(extension("entry")),
                versions = listOf(version.copy(information = version.information.copy(extensions = listOf(extension("information"))))) + attribute.versions.drop(1),
            )
        assertEquals(emptyList(), xsd.errors(xsd.attribute, serializer.serializeAttributeEntry(withExtensions).value.encodeToByteArray()))

        val scheme = parser.parseSchemeEntry(text("synthetic/schemes/eu-pid.xml").encodeToByteArray()).value.value
        val schemeVersion = scheme.versions.single()
        val type = schemeVersion.eaaTypes.single()
        val withSchemeExtensions =
            scheme.copy(versions = listOf(schemeVersion.copy(extensions = listOf(extension("version")), eaaTypes = listOf(type.copy(extensions = type.extensions + extension("type"))))))
        assertEquals(emptyList(), xsd.errors(xsd.scheme, serializer.serializeSchemeEntry(withSchemeExtensions).value.encodeToByteArray()))
    }

    @Test
    fun anAddressNeedsAnElectronicAddressBecauseTheSchemaRequiresIt() {
        val coa = parser.parseCoa(text("synthetic/coa-populated.xml").encodeToByteArray()).value.value
        val without = coa.copy(info = coa.info.copy(operator = coa.info.operator.copy(electronicAddress = ElectronicAddress())))
        assertTrue(serializer.serializeCoa(without).isErr)
        val scheme = parser.parseSchemeEntry(text("synthetic/schemes/eu-pid.xml").encodeToByteArray()).value.value
        assertTrue(serializer.serializeSchemeEntry(scheme.copy(owner = scheme.owner.copy(electronicAddress = ElectronicAddress()))).isErr)
    }
}

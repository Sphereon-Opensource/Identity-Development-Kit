package com.sphereon.catalog.eu

import com.sphereon.catalog.eu.error.CatalogError
import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.model.AttributeEntry
import com.sphereon.catalog.eu.model.CatalogueFinding
import com.sphereon.catalog.eu.model.EaaSchemeEntry
import com.sphereon.catalog.eu.model.FindingSeverity
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.catalog.eu.resolution.CatalogIdentifierMethods
import com.sphereon.catalog.eu.spi.CatalogScope
import com.sphereon.catalog.eu.testutil.readTestResource
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EuCatalogueModelsSerializationTest {
    private val parser = DefaultEuCatalogueXmlParser()
    private val json = Json

    @Test
    fun attributeEntryRoundTripsThroughJson() {
        val entry = parser.parseAttributeEntry(readTestResource("eu-catalogues/synthetic/attributes/eu.europa.ec.eudi.pid.1/family_name.xml").encodeToByteArray()).value.value
        val text = json.encodeToString(AttributeEntry.serializer(), entry)
        assertEquals(entry, json.decodeFromString(AttributeEntry.serializer(), text))
    }

    @Test
    fun schemeEntryRoundTripsThroughJson() {
        val entry = parser.parseSchemeEntry(readTestResource("eu-catalogues/synthetic/schemes/eu-pid.xml").encodeToByteArray()).value.value
        val text = json.encodeToString(EaaSchemeEntry.serializer(), entry)
        assertEquals(entry, json.decodeFromString(EaaSchemeEntry.serializer(), text))
    }

    @Test
    fun errorIsSerializableAndCarriesItsCode() {
        val error = CatalogError(CatalogErrorCode.SCHEMA_VIOLATION, "Required element missing", "CatalogueInformation")
        val text = json.encodeToString(CatalogError.serializer(), error)
        val back = json.decodeFromString(CatalogError.serializer(), text)
        assertEquals(error, back)
        assertEquals("CATALOG_SCHEMA_VIOLATION", back.code)
        assertTrue(back.message.defaultMessage.contains("CatalogueInformation"))
    }

    @Test
    fun findingAndScopeRoundTrip() {
        val finding = CatalogueFinding("X", FindingSeverity.WARNING, "a/b", "m")
        assertEquals(finding, json.decodeFromString(CatalogueFinding.serializer(), json.encodeToString(CatalogueFinding.serializer(), finding)))
        val scope = CatalogScope("t", "d", null)
        assertEquals(scope, json.decodeFromString(CatalogScope.serializer(), json.encodeToString(CatalogScope.serializer(), scope)))
    }

    @Test
    fun identifierMethodNamesAreDistinctAndPrefixed() {
        assertEquals(6, CatalogIdentifierMethods.ALL.size)
        assertTrue(CatalogIdentifierMethods.ALL.all { it.startsWith("catalog_") })
    }
}

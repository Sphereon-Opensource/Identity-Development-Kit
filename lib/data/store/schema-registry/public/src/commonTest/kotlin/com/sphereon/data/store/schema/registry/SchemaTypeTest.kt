package com.sphereon.data.store.schema.registry

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Locks down the [SchemaType] enum surface before the schema/credential-definition model
 * is changed. Verifies that the four types required by the enterprise semantic model
 * (JSON_SCHEMA, JSON_LD_CONTEXT, OCA_BUNDLE, plus XML_SCHEMA and SD_JWT_VCT already
 * present) are stable enum members, each with the expected wire content-type and file
 * extension.
 */
class SchemaTypeTest {
    @Test
    fun schemaTypeIncludesJsonSchema() {
        assertContains(SchemaType.entries, SchemaType.JSON_SCHEMA)
    }

    @Test
    fun schemaTypeIncludesJsonLdContext() {
        assertContains(SchemaType.entries, SchemaType.JSON_LD_CONTEXT)
    }

    @Test
    fun schemaTypeIncludesOcaBundle() {
        assertContains(SchemaType.entries, SchemaType.OCA_BUNDLE)
    }

    @Test
    fun jsonSchemaHasCorrectContentTypeAndExtension() {
        assertEquals("application/schema+json", SchemaType.JSON_SCHEMA.defaultContentType)
        assertEquals(".json", SchemaType.JSON_SCHEMA.fileExtension)
    }

    @Test
    fun jsonLdContextHasCorrectContentTypeAndExtension() {
        assertEquals("application/ld+json", SchemaType.JSON_LD_CONTEXT.defaultContentType)
        assertEquals(".jsonld", SchemaType.JSON_LD_CONTEXT.fileExtension)
    }

    @Test
    fun ocaBundleHasCorrectContentTypeAndExtension() {
        assertEquals("application/json", SchemaType.OCA_BUNDLE.defaultContentType)
        assertEquals(".json", SchemaType.OCA_BUNDLE.fileExtension)
    }

    @Test
    fun allFiveEntriesArePresentAndStable() {
        val names = SchemaType.entries.map { it.name }
        assertContains(names, "JSON_SCHEMA")
        assertContains(names, "XML_SCHEMA")
        assertContains(names, "SD_JWT_VCT")
        assertContains(names, "JSON_LD_CONTEXT")
        assertContains(names, "OCA_BUNDLE")
        assertEquals(5, SchemaType.entries.size)
    }
}

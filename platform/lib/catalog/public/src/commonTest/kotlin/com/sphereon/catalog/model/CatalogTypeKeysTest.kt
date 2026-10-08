/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogTypeKeysTest {
    @Test
    fun remintedHostedSchemaUriStillUsesFormatVct() {
        val record =
            record(
                formats = listOf("dc+sd-jwt"),
                uri = "https://tenant.example/public/catalogs/webuild-pid/api/v1/schemas/schema-ud/formats/dc+sd-jwt",
                formatJson = """{"vct":"urn:eudi:pid:1"}""",
            )
        val key = CatalogTypeKeys.of(record)
        assertEquals(AttestationTypeKeyKind.VCT, key.kind)
        assertEquals("urn:eudi:pid:1", key.value)
        assertTrue(CatalogTypeKeys.matches(record, setOf("urn:eudi:pid:1")))
        assertFalse(
            CatalogTypeKeys.matches(
                record,
                setOf(
                    record.schema.schemaURIs
                        .single()
                        .uri
                )
            )
        )
    }

    @Test
    fun mdocUsesDocTypeNotHostedUri() {
        val record =
            record(
                formats = listOf("mso_mdoc"),
                uri = "https://tenant.example/public/catalogs/webuild-pid/api/v1/schemas/schema-mdoc/formats/mso_mdoc",
                formatJson = """{"properties":{"docType":{"const":"eu.europa.ec.eudi.pid.1"}}}""",
            )
        assertEquals("eu.europa.ec.eudi.pid.1", CatalogTypeKeys.of(record).value)
        assertTrue(CatalogTypeKeys.matches(record, setOf("eu.europa.ec.eudi.pid.1")))
    }

    @Test
    fun linkedDesignIdMatchesBoundDesigns() {
        val record =
            record(
                formats = listOf("mso_mdoc"),
                uri = "https://tenant.example/public/catalogs/x/api/v1/schemas/s/formats/mso_mdoc",
                formatJson = """{"docType":"eu.europa.ec.eudi.pid.1"}""",
                linkedDesignId = "design-1",
            )
        assertTrue(CatalogTypeKeys.matches(record, emptySet(), setOf("design-1")))
        assertFalse(CatalogTypeKeys.matches(record, emptySet(), setOf("design-other")))
    }

    private fun record(
        formats: List<String>,
        uri: String,
        formatJson: String,
        linkedDesignId: String? = null,
    ) = AttestationSchemaRecord(
        catalogId = "cat-pid",
        schema =
            SchemaMeta(
                id = "schema-pid",
                version = "1.0.0",
                rulebookURI = "https://example.test/rulebook",
                attestationLoS = "iso_18045_high",
                bindingType = "key",
                supportedFormats = formats,
                schemaURIs = listOf(SchemaUriRef(formats.single(), uri)),
            ),
        provenance = CatalogSchemaProvenance.AUTHORED,
        linkedDesignId = linkedDesignId,
        documents =
            listOf(
                AttestationSchemaDocument(
                    kind = CatalogDocumentKind.FORMAT,
                    formatIdentifier = formats.single(),
                    mediaType = "application/json",
                    bytes = formatJson.encodeToByteArray(),
                ),
            ),
    )
}

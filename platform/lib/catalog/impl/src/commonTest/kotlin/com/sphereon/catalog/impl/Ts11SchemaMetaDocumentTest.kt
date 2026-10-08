/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Ts11SchemaMetaDocumentTest {
    @Test
    fun officialRequiredFieldsMatchValidator() {
        assertEquals(
            setOf(
                "version",
                "rulebookURI",
                "attestationLoS",
                "bindingType",
                "supportedFormats",
                "schemaURIs",
            ),
            Ts11SchemaMetaDocument.requiredFields,
        )
    }

    @Test
    fun serializedSchemaMetaUsesOfficialFieldNames() {
        val schema =
            SchemaMeta(
                id = "11111111-1111-1111-1111-111111111111",
                version = "1.0.0",
                rulebookURI = "https://example.test/rulebook",
                attestationLoS = "iso_18045_high",
                bindingType = "key",
                supportedFormats = listOf("dc+sd-jwt"),
                schemaURIs = listOf(SchemaUriRef("dc+sd-jwt", "https://example.test/pid.vctm.json")),
            )
        val encoded = Json.encodeToString(SchemaMeta.serializer(), schema)
        val keys = Json.parseToJsonElement(encoded).jsonObject.keys
        assertTrue(keys.containsAll(Ts11SchemaMetaDocument.requiredFields + "id"))
        assertTrue("attestationLoS" in keys)
        assertTrue("rulebookURI" in keys)
        assertTrue("schemaURIs" in keys)
        assertEquals(
            setOf("version", "rulebookURI", "attestationLoS", "bindingType", "supportedFormats", "schemaURIs", "id", "trustedAuthorities"),
            Ts11SchemaMetaDocument.allowedFields,
        )
    }
}

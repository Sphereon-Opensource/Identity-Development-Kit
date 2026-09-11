/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class Ts11OfficialSchemaResourceTest {
    @Test
    fun officialResourceMatchesAllowedFieldsAndForbidsAdditionalProperties() {
        val stream =
            checkNotNull(
                Ts11SchemaMetaDocument::class.java.getResourceAsStream(Ts11SchemaMetaDocument.RESOURCE)
                    ?: Thread.currentThread().contextClassLoader.getResourceAsStream(
                        Ts11SchemaMetaDocument.RESOURCE.removePrefix("/"),
                    ),
            )
        val raw = stream.bufferedReader().use { it.readText() }
        val root = Json.parseToJsonElement(raw).jsonObject
        val properties = assertNotNull(root["properties"]).jsonObject
        assertEquals(Ts11SchemaMetaDocument.allowedFields, properties.keys)
        assertFalse(root["additionalProperties"]!!.toString().toBoolean())
    }
}

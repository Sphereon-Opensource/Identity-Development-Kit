/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.mapping

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttributeMapperTest {
    @Test
    fun emptyMappingsReturnsInputUnchanged() {
        val input = mapOf("email" to JsonPrimitive("a@b.test"))
        val result = applyAttributeMappings(input, emptyList())
        assertTrue(result.isOk)
        assertEquals(input, result.value)
    }

    @Test
    fun mappingProjectsSourceValueOntoTargetKey() {
        val input = mapOf("name" to JsonPrimitive("Alice"))
        val result = applyAttributeMappings(input, listOf(AttributeMapping(source = "name", target = "givenName")))
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("Alice"), result.value["givenName"])
        // Source key is preserved (additive semantics).
        assertEquals(JsonPrimitive("Alice"), result.value["name"])
    }

    @Test
    fun absentOptionalSourceIsSilentlySkipped() {
        val input = mapOf("email" to JsonPrimitive("a@b.test"))
        val result = applyAttributeMappings(input, listOf(AttributeMapping(source = "name", target = "givenName")))
        assertTrue(result.isOk)
        assertNull(result.value["givenName"])
        assertEquals(JsonPrimitive("a@b.test"), result.value["email"])
    }

    @Test
    fun absentRequiredSourceFailsClosed() {
        val input = mapOf("email" to JsonPrimitive("a@b.test"))
        val result =
            applyAttributeMappings(
                input,
                listOf(AttributeMapping(source = "name", target = "givenName", required = true)),
            )
        assertTrue(result.isErr)
        val error = result.error
        assertNotNull(error)
        assertEquals("missing_required_mapping_attributes", error.code)
        assertTrue(error.message.defaultMessage?.contains("name -> givenName") == true)
    }

    @Test
    fun multipleMappingsAppliedInOrderLastWins() {
        val input =
            mapOf(
                "first_name" to JsonPrimitive("Alice"),
                "preferred_name" to JsonPrimitive("Ali"),
            )
        val result =
            applyAttributeMappings(
                input,
                listOf(
                    AttributeMapping(source = "first_name", target = "givenName"),
                    AttributeMapping(source = "preferred_name", target = "givenName"),
                ),
            )
        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("Ali"), result.value["givenName"])
    }
}

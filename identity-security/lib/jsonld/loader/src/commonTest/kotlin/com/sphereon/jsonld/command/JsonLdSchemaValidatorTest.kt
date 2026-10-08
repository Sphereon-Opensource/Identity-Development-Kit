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

package com.sphereon.jsonld.command

import com.sphereon.jsonld.JsonLdError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonLdSchemaValidatorTest {
    /** Minimal Draft 2020-12 schema for a tiny "Widget" credential subject. */
    private val widgetSchema: JsonObject =
        buildJsonObject {
            put("\$schema", "https://json-schema.org/draft/2020-12/schema")
            put("\$id", "https://test.example/widget.json")
            put("type", "object")
            putJsonArray("required") {
                add(JsonPrimitive("id"))
                add(JsonPrimitive("size"))
            }
            putJsonObject("properties") {
                putJsonObject("id") { put("type", "string") }
                putJsonObject("size") {
                    put("type", "integer")
                    put("minimum", 1)
                }
            }
        }

    private val registry =
        MapBackedJsonLdSchemaRegistry(
            mapOf(
                "Widget" to
                    jsonLdSchemaEntry(
                        schemaUri = "https://test.example/widget.json",
                        schema = widgetSchema,
                    ),
            ),
        )

    private val validator = JsonLdSchemaValidator(registry)

    @Test
    fun acceptsValidPayload() {
        val payload =
            buildJsonObject {
                put("id", "w-1")
                put("size", 5)
            }
        val result =
            validator.validate(
                ValidateJsonLdSchemaInput(payload = payload, credentialType = "Widget"),
            )
        assertTrue(result.isOk, "valid payload must validate")
        assertEquals("https://test.example/widget.json", result.value.schemaUri)
    }

    @Test
    fun reportsMissingRequiredField() {
        val payload =
            buildJsonObject {
                put("id", "w-1")
                // size missing
            }
        val result =
            validator.validate(
                ValidateJsonLdSchemaInput(payload = payload, credentialType = "Widget"),
            )
        assertTrue(result.isErr)
        val typed = assertNotNull(result.error as? JsonLdError.JsonSchemaValidationFailed)
        assertEquals("https://test.example/widget.json", typed.schemaUri)
        assertTrue(typed.violations.isNotEmpty(), "expected at least one violation")
    }

    @Test
    fun reportsTypeMismatch() {
        val payload =
            buildJsonObject {
                put("id", "w-1")
                put("size", "five") // wrong type
            }
        val result =
            validator.validate(
                ValidateJsonLdSchemaInput(payload = payload, credentialType = "Widget"),
            )
        assertTrue(result.isErr)
        val typed = assertNotNull(result.error as? JsonLdError.JsonSchemaValidationFailed)
        assertTrue(typed.violations.any { it.message.contains("integer", ignoreCase = true) })
    }

    @Test
    fun returnsNoSchemaRegisteredForUnknownCredentialType() {
        val payload = buildJsonObject { put("anything", JsonPrimitive("goes")) }
        val result =
            validator.validate(
                ValidateJsonLdSchemaInput(payload = payload, credentialType = "NeverRegistered"),
            )
        assertTrue(result.isErr)
        val typed = assertNotNull(result.error as? JsonLdError.NoSchemaRegistered)
        assertEquals("NeverRegistered", typed.credentialType)
    }

    @Test
    fun defaultRegistryBundlesUntpSchemas() {
        val default = DefaultJsonLdSchemaRegistry()
        val expected =
            setOf(
                "DigitalProductPassport",
                "DigitalConformityCredential",
                "DigitalTraceabilityEvent",
                "DigitalFacilityRecord",
                "DigitalIdentityAnchor",
                "RegisteredIdentity",
            )
        assertEquals(expected, default.listTypes())
        val dpp = assertNotNull(default.get("DigitalProductPassport"))
        assertEquals(
            "https://untp.unece.org/artefacts/schema/v0.7.0/dpp/DigitalProductPassport.json",
            dpp.schemaUri,
        )
    }
}

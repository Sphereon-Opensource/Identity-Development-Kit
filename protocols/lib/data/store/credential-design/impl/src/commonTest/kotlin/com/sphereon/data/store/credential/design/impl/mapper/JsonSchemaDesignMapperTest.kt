/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.credential.design.impl.mapper

import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.ClaimValueKind
import com.sphereon.data.store.credential.design.model.ClaimWidgetHint
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonSchemaDesignMapperTest {
    private val mapper = JsonSchemaDesignMapper()

    // ---- deriveHints: basic type mapping ----

    @Test
    fun deriveHintsStringType() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.STRING, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.TEXT, result.fieldHints[0].widgetHint)
    }

    @Test
    fun deriveHintsBooleanType() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("active") {
                        put("type", "boolean")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.BOOLEAN, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.CHECKBOX, result.fieldHints[0].widgetHint)
    }

    @Test
    fun deriveHintsIntegerType() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("age") {
                        put("type", "integer")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.INTEGER, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.TEXT, result.fieldHints[0].widgetHint)
    }

    @Test
    fun deriveHintsNumberType() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("score") {
                        put("type", "number")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.NUMBER, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.TEXT, result.fieldHints[0].widgetHint)
    }

    @Test
    fun deriveHintsArrayType() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("tags") {
                        put("type", "array")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.ARRAY, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.LIST, result.fieldHints[0].widgetHint)
    }

    @Test
    fun deriveHintsObjectType() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("address") {
                        put("type", "object")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.OBJECT, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.GROUP, result.fieldHints[0].widgetHint)
    }

    @Test
    fun deriveHintsUnknownType() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("custom") {
                        put("type", "custom_type")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.UNKNOWN, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.TEXT, result.fieldHints[0].widgetHint)
    }

    // ---- deriveHints: format mapping ----

    @Test
    fun deriveHintsDateFormat() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("birthDate") {
                        put("type", "string")
                        put("format", "date")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.DATE, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.DATE, result.fieldHints[0].widgetHint)
        assertEquals("date", result.fieldHints[0].formatHint)
    }

    @Test
    fun deriveHintsDateTimeFormat() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("issuedAt") {
                        put("type", "string")
                        put("format", "date-time")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.DATE_TIME, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.DATE_TIME, result.fieldHints[0].widgetHint)
        assertEquals("date-time", result.fieldHints[0].formatHint)
    }

    @Test
    fun deriveHintsUriFormat() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("website") {
                        put("type", "string")
                        put("format", "uri")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.URI, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.URI, result.fieldHints[0].widgetHint)
    }

    @Test
    fun deriveHintsIriFormat() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("resource") {
                        put("type", "string")
                        put("format", "iri")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(ClaimValueKind.URI, result.fieldHints[0].valueKind)
    }

    // ---- deriveHints: contentEncoding / contentMediaType ----

    @Test
    fun deriveHintsBase64ContentEncoding() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("document") {
                        put("type", "string")
                        put("contentEncoding", "base64")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.BINARY, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.FILE, result.fieldHints[0].widgetHint)
        assertEquals("base64", result.fieldHints[0].contentEncoding)
    }

    @Test
    fun deriveHintsImageContentMediaType() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("photo") {
                        put("type", "string")
                        put("contentMediaType", "image/png")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimValueKind.IMAGE, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.IMAGE, result.fieldHints[0].widgetHint)
        assertEquals("image/png", result.fieldHints[0].contentMediaType)
    }

    @Test
    fun deriveHintsImageJpegContentMediaType() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("avatar") {
                        put("type", "string")
                        put("contentMediaType", "image/jpeg")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(ClaimValueKind.IMAGE, result.fieldHints[0].valueKind)
    }

    @Test
    fun deriveHintsMarkdownContentMediaType() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("notes") {
                        put("type", "string")
                        put("contentMediaType", "text/markdown")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(ClaimValueKind.MARKDOWN, result.fieldHints[0].valueKind)
        assertEquals(ClaimWidgetHint.MARKDOWN, result.fieldHints[0].widgetHint)
    }

    // ---- deriveHints: cardinality ----

    @Test
    fun deriveHintsMinMaxItems() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("roles") {
                        put("type", "array")
                        put("minItems", 1)
                        put("maxItems", 5)
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertNotNull(result.fieldHints[0].cardinality)
        assertEquals(1, result.fieldHints[0].cardinality!!.min)
        assertEquals(5, result.fieldHints[0].cardinality!!.max)
    }

    @Test
    fun deriveHintsMinItemsOnly() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("items") {
                        put("type", "array")
                        put("minItems", 2)
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertNotNull(result.fieldHints[0].cardinality)
        assertEquals(2, result.fieldHints[0].cardinality!!.min)
        assertEquals(null, result.fieldHints[0].cardinality!!.max)
    }

    @Test
    fun deriveHintsMaxItemsOnly() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("items") {
                        put("type", "array")
                        put("maxItems", 10)
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertNotNull(result.fieldHints[0].cardinality)
        assertEquals(null, result.fieldHints[0].cardinality!!.min)
        assertEquals(10, result.fieldHints[0].cardinality!!.max)
    }

    @Test
    fun deriveHintsNoCardinalityWhenNoMinMaxItems() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("simple") {
                        put("type", "string")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(null, result.fieldHints[0].cardinality)
    }

    // ---- deriveHints: enum -> PICKLIST widget ----

    @Test
    fun deriveHintsEnumProducesPicklistWidget() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("status") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add(JsonPrimitive("active"))
                            add(JsonPrimitive("inactive"))
                            add(JsonPrimitive("pending"))
                        }
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(ClaimWidgetHint.PICKLIST, result.fieldHints[0].widgetHint)
    }

    // ---- deriveHints: path and ordering ----

    @Test
    fun deriveHintsPathIsPropertySegment() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("email") {
                        put("type", "string")
                    }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(1, result.fieldHints.size)
        assertEquals(listOf(ClaimPathSegment.Property("email")), result.fieldHints[0].path)
    }

    @Test
    fun deriveHintsDefaultOrderingMatchesFieldHints() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("first") { put("type", "string") }
                    putJsonObject("second") { put("type", "integer") }
                    putJsonObject("third") { put("type", "boolean") }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(3, result.fieldHints.size)
        assertEquals(result.fieldHints.map { it.path }, result.defaultOrdering)
    }

    // ---- deriveHints: empty schema ----

    @Test
    fun deriveHintsEmptySchemaReturnsEmptyFieldHints() {
        val schema = buildJsonObject {}

        val result = mapper.deriveHints(schema)

        assertTrue(result.fieldHints.isEmpty())
        assertTrue(result.defaultOrdering.isEmpty())
    }

    @Test
    fun deriveHintsNoPropertiesReturnsEmptyFieldHints() {
        val schema =
            buildJsonObject {
                put("type", "object")
            }

        val result = mapper.deriveHints(schema)

        assertTrue(result.fieldHints.isEmpty())
    }

    // ---- deriveClaims tests ----

    @Test
    fun deriveClaimsTitleAndDescription() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("fullName") {
                        put("type", "string")
                        put("title", "Full Name")
                        put("description", "The full legal name")
                    }
                }
            }

        val result = mapper.deriveClaims(schema)

        assertEquals(1, result.size)
        assertEquals(1, result[0].labels.size)
        assertEquals("Full Name", result[0].labels[0].label)
        assertEquals("The full legal name", result[0].labels[0].description)
        assertEquals("", result[0].labels[0].locale)
    }

    @Test
    fun deriveClaimsNoTitleFallsBackToPropertyName() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("givenName") {
                        put("type", "string")
                    }
                }
            }

        val result = mapper.deriveClaims(schema)

        assertEquals(1, result.size)
        assertEquals("givenName", result[0].labels[0].label)
    }

    @Test
    fun deriveClaimsRequiredProperty() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("name") { put("type", "string") }
                    putJsonObject("email") { put("type", "string") }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("email"))
                }
            }

        val result = mapper.deriveClaims(schema)

        assertEquals(2, result.size)
        val nameResult = result.first { it.path == listOf(ClaimPathSegment.Property("name")) }
        val emailResult = result.first { it.path == listOf(ClaimPathSegment.Property("email")) }
        assertEquals(false, nameResult.mandatory)
        assertTrue(emailResult.mandatory)
    }

    @Test
    fun deriveClaimsEnumProducesEntryCodesAndPicklist() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("country") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add(JsonPrimitive("NL"))
                            add(JsonPrimitive("DE"))
                            add(JsonPrimitive("FR"))
                        }
                    }
                }
            }

        val result = mapper.deriveClaims(schema)

        assertEquals(1, result.size)
        assertNotNull(result[0].entryCodes)
        assertEquals(listOf("NL", "DE", "FR"), result[0].entryCodes)
        assertEquals(ClaimWidgetHint.PICKLIST, result[0].widgetHint)
    }

    @Test
    fun deriveClaimsNoEnumNoWidgetHint() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("name") {
                        put("type", "string")
                    }
                }
            }

        val result = mapper.deriveClaims(schema)

        assertEquals(null, result[0].widgetHint)
        assertEquals(null, result[0].entryCodes)
    }

    @Test
    fun deriveClaimsOrderIsSequential() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("first") { put("type", "string") }
                    putJsonObject("second") { put("type", "string") }
                    putJsonObject("third") { put("type", "string") }
                }
            }

        val result = mapper.deriveClaims(schema)

        assertEquals(3, result.size)
        assertEquals(0, result[0].order)
        assertEquals(1, result[1].order)
        assertEquals(2, result[2].order)
    }

    @Test
    fun deriveClaimsPathIsPropertySegment() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("email") {
                        put("type", "string")
                    }
                }
            }

        val result = mapper.deriveClaims(schema)

        assertEquals(listOf(ClaimPathSegment.Property("email")), result[0].path)
    }

    @Test
    fun deriveClaimsEmptySchemaReturnsEmptyList() {
        val schema = buildJsonObject {}

        val result = mapper.deriveClaims(schema)

        assertTrue(result.isEmpty())
    }

    @Test
    fun deriveClaimsNoPropertiesReturnsEmptyList() {
        val schema =
            buildJsonObject {
                put("type", "object")
            }

        val result = mapper.deriveClaims(schema)

        assertTrue(result.isEmpty())
    }

    @Test
    fun deriveClaimsDescriptionWithoutTitle() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("fieldX") {
                        put("type", "string")
                        put("description", "A description only")
                    }
                }
            }

        val result = mapper.deriveClaims(schema)

        assertEquals(1, result.size)
        assertEquals("fieldX", result[0].labels[0].label)
        assertEquals("A description only", result[0].labels[0].description)
    }

    // ---- deriveHints: multiple properties at once ----

    @Test
    fun deriveHintsMultipleProperties() {
        val schema =
            buildJsonObject {
                putJsonObject("properties") {
                    putJsonObject("name") { put("type", "string") }
                    putJsonObject("age") { put("type", "integer") }
                    putJsonObject("verified") { put("type", "boolean") }
                }
            }

        val result = mapper.deriveHints(schema)

        assertEquals(3, result.fieldHints.size)

        val kinds = result.fieldHints.map { it.valueKind }.toSet()
        assertTrue(kinds.contains(ClaimValueKind.STRING))
        assertTrue(kinds.contains(ClaimValueKind.INTEGER))
        assertTrue(kinds.contains(ClaimValueKind.BOOLEAN))
    }
}

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

import com.sphereon.data.store.credential.design.model.RenderVariantKind
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class W3cRenderMethodDesignMapperTest {
    private val mapper = W3cRenderMethodDesignMapper()

    // ---- parseRenderMethod: SVG template ----

    @Test
    fun parseRenderMethodSvgTemplate() {
        val obj =
            buildJsonObject {
                put("type", "SvgRenderingTemplate2024")
                put("id", "https://example.com/template.svg")
                put("name", "Identity Card")
                put("mediaType", "image/svg+xml")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        assertEquals(RenderVariantKind.W3C_RENDER_METHOD, result.kind)
        assertNotNull(result.w3cRenderMethod)
        assertEquals("SvgRenderingTemplate2024", result.w3cRenderMethod!!.type)
        assertEquals("svg-mustache", result.w3cRenderMethod!!.renderSuite)
        assertEquals("https://example.com/template.svg", result.w3cRenderMethod!!.uri)
        assertEquals("Identity Card", result.w3cRenderMethod!!.name)
        assertEquals("image/svg+xml", result.w3cRenderMethod!!.mediaType)
    }

    @Test
    fun parseRenderMethodSvgMustacheType() {
        val obj =
            buildJsonObject {
                put("type", "svg-mustache")
                put("id", "https://example.com/card.svg")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        assertEquals("svg-mustache", result.w3cRenderMethod!!.renderSuite)
    }

    // ---- parseRenderMethod: PDF template ----

    @Test
    fun parseRenderMethodPdfTemplate() {
        val obj =
            buildJsonObject {
                put("type", "PdfRenderingTemplate2024")
                put("id", "https://example.com/template.pdf")
                put("mediaType", "application/pdf")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        assertEquals("pdf-mustache", result.w3cRenderMethod!!.renderSuite)
        assertEquals("https://example.com/template.pdf", result.w3cRenderMethod!!.uri)
    }

    @Test
    fun parseRenderMethodPdfMustacheType() {
        val obj =
            buildJsonObject {
                put("type", "pdf-mustache")
                put("id", "https://example.com/doc.pdf")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        assertEquals("pdf-mustache", result.w3cRenderMethod!!.renderSuite)
    }

    // ---- parseRenderMethod: all fields ----

    @Test
    fun parseRenderMethodAllFields() {
        val obj =
            buildJsonObject {
                put("type", "SvgRenderingTemplate2024")
                put("id", "https://example.com/full-template.svg")
                put("name", "Full Template")
                put("description", "A complete render template")
                put("mediaType", "image/svg+xml")
                put("digestMultibase", "zQmHash123")
                putJsonArray("renderProperties") {
                    add(JsonPrimitive("given_name"))
                    add(JsonPrimitive("family_name"))
                    add(JsonPrimitive("birth_date"))
                }
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        val ref = result.w3cRenderMethod!!
        assertEquals("SvgRenderingTemplate2024", ref.type)
        assertEquals("svg-mustache", ref.renderSuite)
        assertEquals("https://example.com/full-template.svg", ref.uri)
        assertEquals("Full Template", ref.name)
        assertEquals("A complete render template", ref.description)
        assertEquals("image/svg+xml", ref.mediaType)
        assertEquals("zQmHash123", ref.digestMultibase)
        assertNotNull(ref.renderProperties)
        assertEquals(listOf("given_name", "family_name", "birth_date"), ref.renderProperties)
    }

    // ---- parseRenderMethod: uri fallback to url ----

    @Test
    fun parseRenderMethodUriFromUrlFallback() {
        val obj =
            buildJsonObject {
                put("type", "SvgRenderingTemplate2024")
                put("url", "https://example.com/fallback.svg")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        assertEquals("https://example.com/fallback.svg", result.w3cRenderMethod!!.uri)
    }

    @Test
    fun parseRenderMethodUriEmptyWhenNoIdOrUrl() {
        val obj =
            buildJsonObject {
                put("type", "SvgRenderingTemplate2024")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        assertEquals("", result.w3cRenderMethod!!.uri)
    }

    @Test
    fun parseRenderMethodIdTakesPrecedenceOverUrl() {
        val obj =
            buildJsonObject {
                put("type", "SvgRenderingTemplate2024")
                put("id", "https://example.com/id.svg")
                put("url", "https://example.com/url.svg")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        assertEquals("https://example.com/id.svg", result.w3cRenderMethod!!.uri)
    }

    // ---- parseRenderMethod: unknown type -> null renderSuite ----

    @Test
    fun parseRenderMethodUnknownTypeNullRenderSuite() {
        val obj =
            buildJsonObject {
                put("type", "CustomRenderType")
                put("id", "https://example.com/custom")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        assertEquals("CustomRenderType", result.w3cRenderMethod!!.type)
        assertEquals(null, result.w3cRenderMethod!!.renderSuite)
    }

    // ---- parseRenderMethod: missing type -> null ----

    @Test
    fun parseRenderMethodMissingTypeReturnsNull() {
        val obj =
            buildJsonObject {
                put("id", "https://example.com/no-type")
                put("name", "No Type")
            }

        val result = mapper.parseRenderMethod(obj)

        assertEquals(null, result)
    }

    // ---- parseRenderMethod: tenantId and kind ----

    @Test
    fun parseRenderMethodSetsEmptyTenantId() {
        val obj =
            buildJsonObject {
                put("type", "SvgRenderingTemplate2024")
                put("id", "https://example.com/template.svg")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        assertEquals("", result.tenantId)
    }

    @Test
    fun parseRenderMethodSetsW3cRenderMethodKind() {
        val obj =
            buildJsonObject {
                put("type", "SvgRenderingTemplate2024")
                put("id", "https://example.com/template.svg")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        assertEquals(RenderVariantKind.W3C_RENDER_METHOD, result.kind)
    }

    // ---- parseRenderMethods: array tests ----

    @Test
    fun parseRenderMethodsMultipleElements() {
        val array =
            buildJsonArray {
                addJsonObject {
                    put("type", "SvgRenderingTemplate2024")
                    put("id", "https://example.com/svg-template.svg")
                    put("name", "SVG Card")
                }
                addJsonObject {
                    put("type", "PdfRenderingTemplate2024")
                    put("id", "https://example.com/pdf-template.pdf")
                    put("name", "PDF Card")
                }
            }

        val result = mapper.parseRenderMethods(array)

        assertEquals(2, result.size)
        assertEquals("svg-mustache", result[0].w3cRenderMethod!!.renderSuite)
        assertEquals("pdf-mustache", result[1].w3cRenderMethod!!.renderSuite)
    }

    @Test
    fun parseRenderMethodsEmptyArrayReturnsEmptyList() {
        val array = buildJsonArray {}

        val result = mapper.parseRenderMethods(array)

        assertTrue(result.isEmpty())
    }

    @Test
    fun parseRenderMethodsSkipsInvalidElements() {
        val array =
            buildJsonArray {
                add(JsonPrimitive("not an object"))
                addJsonObject {
                    put("type", "SvgRenderingTemplate2024")
                    put("id", "https://example.com/valid.svg")
                }
                add(JsonPrimitive(42))
            }

        val result = mapper.parseRenderMethods(array)

        assertEquals(1, result.size)
        assertEquals("https://example.com/valid.svg", result[0].w3cRenderMethod!!.uri)
    }

    @Test
    fun parseRenderMethodsSkipsObjectsWithoutType() {
        val array =
            buildJsonArray {
                addJsonObject {
                    put("id", "https://example.com/no-type")
                    put("name", "Missing Type")
                }
                addJsonObject {
                    put("type", "SvgRenderingTemplate2024")
                    put("id", "https://example.com/valid.svg")
                }
            }

        val result = mapper.parseRenderMethods(array)

        assertEquals(1, result.size)
        assertEquals("https://example.com/valid.svg", result[0].w3cRenderMethod!!.uri)
    }

    // ---- parseRenderMethod: optional fields default to null ----

    @Test
    fun parseRenderMethodOptionalFieldsDefaultToNull() {
        val obj =
            buildJsonObject {
                put("type", "SvgRenderingTemplate2024")
            }

        val result = mapper.parseRenderMethod(obj)

        assertNotNull(result)
        val ref = result.w3cRenderMethod!!
        assertEquals(null, ref.name)
        assertEquals(null, ref.description)
        assertEquals(null, ref.mediaType)
        assertEquals(null, ref.digestMultibase)
        assertEquals(null, ref.renderProperties)
    }
}

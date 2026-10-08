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

package com.sphereon.jsonld

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinkedDataDocumentTest {
    @Test
    fun documentIriParsesValidUrl() {
        val doc =
            LinkedDataDocument(
                documentUrl = "https://www.w3.org/ns/credentials/v2",
                content = JsonObject(emptyMap()),
            )
        val iri = assertNotNull(doc.documentIri())
        assertEquals("https", iri.scheme)
        assertEquals("www.w3.org", iri.authority)
        assertEquals("/ns/credentials/v2", iri.path)
    }

    @Test
    fun contextIriIsNullWhenAbsent() {
        val doc =
            LinkedDataDocument(
                documentUrl = "https://example.com/x",
                content = JsonObject(emptyMap()),
            )
        assertNull(doc.contextIri())
    }

    @Test
    fun contextIriParsesWhenPresent() {
        val doc =
            LinkedDataDocument(
                documentUrl = "https://example.com/x",
                content = JsonObject(emptyMap()),
                contextUrl = "https://example.com/context.jsonld",
            )
        val ctx = assertNotNull(doc.contextIri())
        assertEquals("/context.jsonld", ctx.path)
    }

    @Test
    fun roundTripsThroughJsonSerialization() {
        val doc =
            LinkedDataDocument(
                documentUrl = "https://vocabulary.uncefact.org/untp/",
                content =
                    buildJsonObject {
                        put("@context", JsonPrimitive("https://www.w3.org/ns/credentials/v2"))
                    },
                contentType = "application/ld+json",
            )
        val json =
            Json {
                encodeDefaults = false
                explicitNulls = false
            }
        val encoded = json.encodeToString(LinkedDataDocument.serializer(), doc)
        val decoded = json.decodeFromString(LinkedDataDocument.serializer(), encoded)
        assertEquals(doc, decoded)
    }
}

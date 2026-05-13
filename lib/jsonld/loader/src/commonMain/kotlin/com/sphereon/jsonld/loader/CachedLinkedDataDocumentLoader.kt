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

package com.sphereon.jsonld.loader

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.cache.ScopedCache
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.LinkedDataDocument
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

/**
 * Decorator that caches successful results from [next] in an app-scoped
 * [ScopedCache] keyed by IRI.
 *
 * Cache value is the raw JSON-LD body as a JSON string; the decorator parses
 * it back to [JsonObject] on hit and serializes on miss-then-store. Cache
 * scope is **app**, not tenant: canonical W3C and UNTP `@context` documents
 * are global and identical for every tenant. Tenant-scoped pin policies are
 * enforced by [IntegrityPinningLinkedDataDocumentLoader] (closer to the
 * network), not by partitioning the cache.
 *
 * Cache failures (deserialization errors on hit, serialization errors on
 * store) are logged via the underlying cache backend but do not propagate;
 * the decorator falls through to [next] so a corrupt cache entry never
 * blocks resolution.
 */
class CachedLinkedDataDocumentLoader(
    private val next: LinkedDataDocumentLoader,
    private val cache: ScopedCache<String, String>,
    private val ttl: Duration? = null,
    private val json: Json = DEFAULT_JSON,
) : LinkedDataDocumentLoader {
    override suspend fun loadDocument(iri: String): IdkResult<LinkedDataDocument, JsonLdError> {
        val cached = cache.getApp(iri)
        if (cached != null) {
            val parsed =
                try {
                    json.parseToJsonElement(cached) as? JsonObject
                } catch (expected: SerializationException) {
                    // Corrupt cache entry: drop it and fall through.
                    cache.removeApp(iri)
                    null
                }
            if (parsed != null) {
                return Ok(
                    LinkedDataDocument(
                        documentUrl = iri,
                        content = parsed,
                        contentType = "application/ld+json",
                    ),
                )
            }
        }

        val downstream = next.loadDocument(iri)
        if (downstream.isOk) {
            val doc = downstream.value
            try {
                cache.putApp(iri, json.encodeToString(JsonObject.serializer(), doc.content as JsonObject), ttl)
            } catch (expected: SerializationException) {
                // Couldn't serialize for cache; not fatal — return the live document.
            }
        }
        return downstream
    }

    private companion object {
        val DEFAULT_JSON =
            Json {
                encodeDefaults = false
                explicitNulls = false
                ignoreUnknownKeys = true
            }
    }
}

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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.LinkedDataDocument
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Last-resort loader: fetches a JSON-LD document from the network via the
 * supplied [HttpClient] (typically obtained from
 * [com.sphereon.ktor.http.client.provider.HttpClientFactory]).
 *
 * Sends `Accept: application/ld+json, application/json` so the upstream may
 * select the canonical media type. On non-2xx, network errors, or unparseable
 * bodies, returns [JsonLdError.LoadingDocumentFailed] with a structured
 * `reason` for diagnostics.
 *
 * Pure terminator — does not delegate to any [LinkedDataDocumentLoader.next];
 * compose with [BuiltInContextLinkedDataDocumentLoader],
 * [CachedLinkedDataDocumentLoader], and
 * [IntegrityPinningLinkedDataDocumentLoader] via [DefaultLinkedDataDocumentLoader]
 * to add bundling, caching, and pin verification.
 */
class HttpLinkedDataDocumentLoader(
    private val httpClient: HttpClient,
    private val json: Json = DEFAULT_JSON,
) : LinkedDataDocumentLoader {
    override suspend fun loadDocument(iri: String): IdkResult<LinkedDataDocument, JsonLdError> {
        val response =
            try {
                httpClient.get(iri) {
                    accept(ContentType.parse("application/ld+json"))
                    accept(ContentType.Application.Json)
                }
            } catch (expected: Exception) {
                return Err(
                    JsonLdError.LoadingDocumentFailed(
                        iri = iri,
                        reason = "HTTP request failed: ${expected.message ?: expected::class.simpleName}",
                        exception = expected,
                    ),
                )
            }

        if (!response.status.isSuccess()) {
            return Err(
                JsonLdError.LoadingDocumentFailed(
                    iri = iri,
                    reason = "HTTP ${response.status.value} ${response.status.description}",
                ),
            )
        }

        val body =
            try {
                response.bodyAsText()
            } catch (expected: Exception) {
                return Err(
                    JsonLdError.LoadingDocumentFailed(
                        iri = iri,
                        reason = "Failed to read response body: ${expected.message ?: expected::class.simpleName}",
                        exception = expected,
                    ),
                )
            }

        val content =
            try {
                json.parseToJsonElement(body) as? JsonObject
            } catch (expected: SerializationException) {
                return Err(
                    JsonLdError.LoadingDocumentFailed(
                        iri = iri,
                        reason = "Response body is not valid JSON: ${expected.message ?: ""}",
                        exception = expected,
                    ),
                )
            } ?: return Err(
                JsonLdError.LoadingDocumentFailed(
                    iri = iri,
                    reason = "Response body is not a JSON object",
                ),
            )

        val contentType = response.headers["Content-Type"]
        return Ok(
            LinkedDataDocument(
                documentUrl = iri,
                content = content,
                contentType = contentType,
            ),
        )
    }

    private companion object {
        val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    }
}

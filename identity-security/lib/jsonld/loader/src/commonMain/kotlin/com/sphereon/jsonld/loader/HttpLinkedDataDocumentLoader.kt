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
import com.sphereon.jsonld.Iri
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.LinkedDataDocument
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Last-resort loader: fetches a JSON-LD document from the network through the
 * supplied [HttpClientFactory]. The factory is always asked for a client with
 * automatic redirects disabled so every hop can be checked by this loader.
 *
 * Sends `Accept: application/ld+json, application/json` so the upstream may
 * select the canonical media type. JSON-LD and JSON responses are parsed as
 * remote documents. For a JSON response, one HTTP Link relation with
 * `rel="http://www.w3.org/ns/json-ld#context"` is allowed and is resolved
 * against the final URL after redirects.
 *
 * On non-2xx, unsupported media types, network errors, malformed JSON, or
 * malformed/multiple context links, returns
 * [JsonLdError.LoadingDocumentFailed] with a structured `reason` for
 * diagnostics. This deliberately fails closed: a response whose bytes or
 * metadata are ambiguous is never exposed as a JSON-LD remote document.
 *
 * Pure terminator — does not delegate to any [LinkedDataDocumentLoader.next];
 * compose with [BuiltInContextLinkedDataDocumentLoader],
 * [CachedLinkedDataDocumentLoader], and
 * [IntegrityPinningLinkedDataDocumentLoader] via [DefaultLinkedDataDocumentLoader]
 * to add bundling, caching, and pin verification.
 */
internal class HttpLinkedDataDocumentLoader(
    private val httpClientFactory: HttpClientFactory,
    private val options: HttpClientOptions,
    private val json: Json = DEFAULT_JSON,
    private val policy: JsonLdDocumentLoadingPolicy,
) : LinkedDataDocumentLoader {
    /**
     * The factory is the trusted platform boundary for transport configuration.
     * A client is created for one load and closed by [loadDocument] after all
     * redirects and alternate representations have been resolved.
     */
    private fun createHttpClient(): Result<HttpClient> {
        try {
            val manualRedirectOptions = options.copy(followRedirects = false)
            check(httpClientFactory.isSupportedOptions(manualRedirectOptions)) {
                "HTTP client factory does not support manual redirects"
            }
            return Result.success(httpClientFactory.createClient(manualRedirectOptions))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: Exception) {
            return Result.failure(expected)
        }
    }

    override suspend fun loadDocument(iri: String): IdkResult<LinkedDataDocument, JsonLdError> {
        val clientResult = createHttpClient()
        return clientResult.fold(
            onSuccess = { client ->
                try {
                    loadDocument(iri, setOf(iri), client)
                } finally {
                    // The factory-created client is owned by this load operation.
                    // This covers typed failures and coroutine cancellation too.
                    client.close()
                }
            },
            onFailure = { expected ->
                Err(
                    JsonLdError.LoadingDocumentFailed(
                        iri = iri,
                        reason = "HTTP client construction failed: ${expected.message ?: expected::class.simpleName}",
                        exception = expected,
                    ),
                )
            },
        )
    }

    private suspend fun loadDocument(
        iri: String,
        visited: Set<String>,
        httpClient: HttpClient,
    ): IdkResult<LinkedDataDocument, JsonLdError> {
        if (visited.size > MAX_DOCUMENT_HOPS) {
            return loadingFailure(iri, "JSON-LD document resolution exceeded the maximum hop count")
        }
        val requestedIri = Iri.tryParse(iri)
        if (requestedIri == null || !requestedIri.isAbsolute) {
            return Err(JsonLdError.InvalidIri(rawValue = iri))
        }
        if (requestedIri.hasUserInfo() || !requestedIri.hasHttpScheme()) {
            return loadingFailure(iri, "JSON-LD remote documents require an HTTP(S) IRI without userinfo")
        }
        if (!policy.isAllowed(iri)) {
            return Err(JsonLdError.DocumentNotAllowed(iri = iri))
        }

        val response =
            try {
                httpClient.get(iri) {
                    headers.append(HttpHeaders.Accept, ACCEPT_HEADER)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                return Err(
                    JsonLdError.LoadingDocumentFailed(
                        iri = iri,
                        reason = "HTTP request failed: ${expected.message ?: expected::class.simpleName}",
                        exception = expected,
                    ),
                )
            }

        if (response.status.value in REDIRECT_STATUS_CODES) {
            val location = response.headers[HttpHeaders.Location]
                ?: return loadingFailure(iri, "HTTP redirect response has no Location header")
            val responseBase = Iri.tryParse(response.request.url.toString())
                ?: return loadingFailure(iri, "HTTP redirect response has an invalid base URL")
            val redirectIri = responseBase.resolve(location)
                ?.takeIf { it.isAbsolute && it.hasHttpScheme() && !it.hasUserInfo() }
                ?: return loadingFailure(iri, "HTTP redirect Location is not a valid HTTP(S) IRI")
            val redirect = redirectIri.toString()
            if (redirect in visited) {
                return loadingFailure(iri, "Cyclic HTTP redirect")
            }
            if (!policy.isAllowed(redirect)) {
                return Err(
                    JsonLdError.DocumentNotAllowed(
                        iri = redirect,
                        reason = "HTTP redirect target is outside the JSON-LD document allowlist",
                    ),
                )
            }
            return loadDocument(redirect, visited + redirect, httpClient)
        }

        if (!response.status.isSuccess()) {
            return Err(
                JsonLdError.LoadingDocumentFailed(
                    iri = iri,
                    reason = "HTTP ${response.status.value} ${response.status.description}",
                ),
            )
        }

        val finalUrl = response.request.url.toString()
        val finalIri = Iri.tryParse(finalUrl)
        if (finalIri == null || !finalIri.isAbsolute) {
            return loadingFailure(iri, "HTTP response contained an invalid final document URL: $finalUrl")
        }
        if (finalIri.hasUserInfo() || !finalIri.hasHttpScheme()) {
            return loadingFailure(iri, "HTTP response contained a non-HTTP(S) or userinfo-bearing final URL")
        }
        if (!policy.isAllowed(finalUrl)) {
            return Err(
                JsonLdError.DocumentNotAllowed(
                    iri = finalUrl,
                    reason = "resolved document URL is outside the JSON-LD document allowlist",
                ),
            )
        }

        val rawContentType = response.headers[HttpHeaders.ContentType]
        val mediaType = parseContentType(rawContentType)
            ?: return loadingFailure(iri, "Response has no valid Content-Type")

        val links =
            try {
                parseLinks(
                    headers = response.headers.getAll(HttpHeaders.Link).orEmpty(),
                    baseUrl = finalIri,
                )
            } catch (expected: IllegalArgumentException) {
                return loadingFailure(iri, expected.message ?: "Malformed Link header")
            }

        val contextLinks = links.filter { RELATION_CONTEXT in it.relations }
        if (contextLinks.size > 1) {
            return loadingFailure(iri, "More than one JSON-LD context link")
        }

        if (!mediaType.isJsonLd) {
            val alternates = links.filter { it.isJsonLdAlternate }
            if (alternates.size > 1) {
                return loadingFailure(iri, "More than one JSON-LD alternate link")
            }
            val alternate = alternates.singleOrNull()
                ?: return loadingFailure(iri, "Unsupported JSON-LD media type: ${mediaType.value}")
            if (alternate.target in visited) {
                return loadingFailure(iri, "Cyclic JSON-LD alternate link")
            }
            if (!policy.isAllowed(alternate.target)) {
                return Err(JsonLdError.DocumentNotAllowed(iri = alternate.target))
            }
            return loadDocument(alternate.target, visited + alternate.target, httpClient)
        }

        val contextLink = contextLinks.singleOrNull()?.target
        if (contextLink != null && !policy.isAllowed(contextLink)) {
            return Err(
                JsonLdError.DocumentNotAllowed(
                    iri = contextLink,
                    reason = "HTTP context link is outside the JSON-LD document allowlist",
                ),
            )
        }

        val body =
            try {
                response.bodyAsText()
            } catch (cancelled: CancellationException) {
                throw cancelled
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
                json.parseToJsonElement(body)
            } catch (expected: SerializationException) {
                return Err(
                    JsonLdError.LoadingDocumentFailed(
                        iri = iri,
                        reason = "Response body is not valid JSON: ${expected.message ?: ""}",
                        exception = expected,
                    ),
                )
            }

        if (content !is JsonObject && content !is JsonArray) {
            return loadingFailure(iri, "Response body is not a JSON object or array")
        }

        return Ok(
            LinkedDataDocument(
                documentUrl = finalUrl,
                content = content,
                contextUrl = if (mediaType.value == APPLICATION_LD_JSON) null else contextLink,
                contentType = rawContentType?.trim(),
                profile = mediaType.profile,
            ),
        )
    }

    private fun loadingFailure(
        iri: String,
        reason: String,
    ): IdkResult<LinkedDataDocument, JsonLdError> =
        Err(JsonLdError.LoadingDocumentFailed(iri = iri, reason = reason))

    /** Parsed Content-Type media type and the JSON-LD `profile` parameter. */
    private data class ParsedContentType(
        val value: String,
        val profile: String?,
    ) {
        val isJsonLd: Boolean
            get() = value == APPLICATION_LD_JSON || value == APPLICATION_JSON ||
                (value.startsWith("application/") && value.endsWith("+json"))
    }

    private fun parseContentType(raw: String?): ParsedContentType? {
        if (raw == null) return null
        val parts = splitHeader(raw)
        val mediaType = parts.firstOrNull()?.trim()?.lowercase() ?: return null
        if (!MEDIA_TYPE.matches(mediaType)) return null

        var profile: String? = null
        for (parameter in parts.drop(1)) {
            val equals = parameter.indexOf('=')
            if (equals <= 0) return null
            val name = parameter.substring(0, equals).trim().lowercase()
            val value = parameter.substring(equals + 1).trim()
            if (name.isEmpty() || value.isEmpty()) return null
            val unquoted = unquote(value) ?: return null
            if (name == PROFILE_PARAMETER) {
                if (profile != null) return null
                profile = unquoted
            }
        }
        return ParsedContentType(mediaType, profile)
    }

    /**
     * Parses Link header fields sufficiently for RFC 8288 link-values. Commas
     * inside a URI reference or quoted parameter are not separators.
     */
    private data class ParsedLink(
        val target: String,
        val relations: Set<String>,
        val type: String?,
    ) {
        val isJsonLdAlternate: Boolean
            get() = "alternate" in relations && type == APPLICATION_LD_JSON
    }

    private fun parseLinks(
        headers: List<String>,
        baseUrl: Iri,
    ): List<ParsedLink> {
        val parsed = mutableListOf<ParsedLink>()
        for (header in headers) {
            val links = splitHeader(header, separator = ',')
            if (header.isNotBlank() && links.isEmpty()) {
                throw IllegalArgumentException("Malformed Link header")
            }
            for (link in links) {
                val value = link.trim()
                if (value.isEmpty()) throw IllegalArgumentException("Empty Link value")
                if (!value.startsWith('<')) throw IllegalArgumentException("Link value must start with '<'")
                val end = value.indexOf('>')
                if (end <= 1) throw IllegalArgumentException("Link value has no target URI")
                val target = value.substring(1, end)
                val parameters = value.substring(end + 1)
                val resolved = baseUrl.resolve(target)?.takeIf { it.isAbsolute }
                    ?: throw IllegalArgumentException("Context link URI is not a valid absolute IRI")
                val linkParameters = parseLinkParameters(parameters)
                parsed += ParsedLink(
                    target = resolved.toString(),
                    relations = linkParameters.relations,
                    type = linkParameters.type,
                )
            }
        }
        return parsed
    }

    private data class LinkParameters(
        val relations: Set<String>,
        val type: String?,
    )

    private fun parseLinkParameters(raw: String): LinkParameters {
        if (raw.isBlank()) return LinkParameters(emptySet(), null)
        var remaining = raw
        var relation: String? = null
        var type: String? = null
        while (remaining.isNotEmpty()) {
            if (!remaining.startsWith(';')) throw IllegalArgumentException("Malformed Link parameters")
            remaining = remaining.substring(1).trimStart()
            if (remaining.isEmpty()) throw IllegalArgumentException("Malformed Link parameter")
            val next = nextParameterBoundary(remaining)
            val parameter = remaining.substring(0, next).trim()
            remaining = remaining.substring(next).trimStart()
            val equals = parameter.indexOf('=')
            if (equals <= 0) throw IllegalArgumentException("Link parameter has no value")
            val name = parameter.substring(0, equals).trim().lowercase()
            val value = unquote(parameter.substring(equals + 1).trim())
                ?: throw IllegalArgumentException("Malformed Link parameter value")
            if (name == "rel") {
                if (relation != null) throw IllegalArgumentException("Duplicate Link rel parameter")
                relation = value
            } else if (name == "type") {
                if (type != null) throw IllegalArgumentException("Duplicate Link type parameter")
                type = value.lowercase()
            }
        }
        return LinkParameters(
            relations = relation?.split(RELATION_WHITESPACE)?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
            type = type,
        )
    }

    private fun nextParameterBoundary(value: String): Int {
        var quoted = false
        var escaped = false
        for (index in value.indices) {
            val character = value[index]
            if (escaped) {
                escaped = false
            } else if (character == '\\' && quoted) {
                escaped = true
            } else if (character == '"') {
                quoted = !quoted
            } else if (character == ';' && !quoted) {
                return index
            }
        }
        if (quoted) throw IllegalArgumentException("Unterminated quoted Link parameter")
        return value.length
    }

    private fun unquote(value: String): String? {
        if (value.startsWith('"')) {
            if (value.length < 2 || !value.endsWith('"')) return null
            return value.substring(1, value.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return value.takeIf { TOKEN.matches(it) }
    }

    private fun splitHeader(
        value: String,
        separator: Char = ';',
    ): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var angle = false
        var quoted = false
        var escaped = false
        for (index in value.indices) {
            val character = value[index]
            if (escaped) {
                escaped = false
            } else if (character == '\\' && quoted) {
                escaped = true
            } else if (character == '"') {
                quoted = !quoted
            } else if (character == '<' && !quoted) {
                angle = true
            } else if (character == '>' && !quoted) {
                angle = false
            } else if (character == separator && !quoted && !angle) {
                parts += value.substring(start, index)
                start = index + 1
            }
        }
        if (quoted || angle) return emptyList()
        parts += value.substring(start)
        return parts
    }

    private companion object {
        const val ACCEPT_HEADER = "application/ld+json, application/json"
        const val APPLICATION_LD_JSON = "application/ld+json"
        const val APPLICATION_JSON = "application/json"
        const val PROFILE_PARAMETER = "profile"
        const val RELATION_CONTEXT = "http://www.w3.org/ns/json-ld#context"
        val MEDIA_TYPE = Regex("^[^\\s/;]+/[^\\s/;]+$")
        val TOKEN = Regex("""^[^\s;,<>]+$""")
        val RELATION_WHITESPACE = Regex("\\s+")
        const val MAX_DOCUMENT_HOPS = 8
        val REDIRECT_STATUS_CODES = setOf(300, 301, 302, 303, 307, 308)
        val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    }
}

private fun Iri.hasUserInfo(): Boolean = authority?.contains('@') == true

private fun Iri.hasHttpScheme(): Boolean = scheme.equals("http", ignoreCase = true) ||
    scheme.equals("https", ignoreCase = true)

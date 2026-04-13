/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.did.methods.web

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.models.DidDocument
import com.sphereon.did.resolver.DidDereferenceOptions
import com.sphereon.did.resolver.DidDereferenceResult
import com.sphereon.did.resolver.DidDereferencingMetadata
import com.sphereon.did.resolver.DidDocumentMetadata
import com.sphereon.did.resolver.DidResolutionMetadata
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolutionResult
import com.sphereon.did.resolver.DidResolver
import com.sphereon.did.utils.ParsedDid
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * Resolver for the did:web DID method.
 *
 * did:web resolves DID documents by fetching them from a web location
 * via HTTPS. The DID is mapped to a URL following the did:web specification.
 *
 * Format: did:web:<domain>[:path]
 *
 * Examples:
 * - did:web:example.com -> https://example.com/.well-known/did.json
 * - did:web:example.com:user:alice -> https://example.com/user/alice/did.json
 *
 * @see <a href="https://w3c-ccg.github.io/did-method-web/">did:web Method Specification</a>
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DidResolver>())
@ContributesBinding(SessionScope::class, binding = binding<WebDidResolver>())
class WebDidResolverImpl(
    private val httpClientFactory: HttpClientFactory
) : WebDidResolver {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override val supportedMethods: List<String> = listOf(WebDidCapabilities.METHOD)

    override val capabilities: DidMethodCapabilities = WebDidCapabilities.CAPABILITIES

    override suspend fun resolve(
        did: String,
        options: DidResolutionOptions
    ): IdkResult<DidResolutionResult, IdkError> {
        // Parse the DID
        val parsed = ParsedDid.tryParse(did)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        if (parsed.method != WebDidCapabilities.METHOD) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Unsupported DID method: ${parsed.method}. Expected: ${WebDidCapabilities.METHOD}"
            ))
        }

        // Validate the did:web format
        if (!WebDidUrlBuilder.isValidDidWeb(did)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Invalid did:web format: $did"
            ))
        }

        // Convert DID to URL
        val url = try {
            WebDidUrlBuilder.didToUrl(did)
        } catch (e: Exception) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Failed to convert DID to URL: ${e.message}"
            ))
        }

        // Fetch the DID document
        val documentJson = fetchDidDocument(url).getOrElse {
            return Err(it)
        }

        // Parse the DID document
        val didDocument = try {
            json.decodeFromString<DidDocument>(documentJson)
        } catch (e: Exception) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Failed to parse DID document: ${e.message}"
            ))
        }

        // Validate that the document ID matches the DID
        if (didDocument.id != did) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "DID document ID mismatch: expected '$did', got '${didDocument.id}'"
            ))
        }

        // Build verification methods by purpose map
        val vmByPurpose = didDocument.getVerificationMethodsByPurpose()

        return Ok(DidResolutionResult(
            didDocument = didDocument,
            didResolutionMetadata = DidResolutionMetadata(
                contentType = "application/did+ld+json"
            ),
            didDocumentMetadata = DidDocumentMetadata(),
            verificationMethodsByPurpose = vmByPurpose
        ))
    }

    override suspend fun dereference(
        didUrl: String,
        options: DidDereferenceOptions
    ): IdkResult<DidDereferenceResult, IdkError> {
        // Parse DID URL (may include fragment like #key-1)
        val parsed = ParsedDid.tryParse(didUrl)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID URL: $didUrl"))

        // First resolve the full document
        val resolutionResult = resolve(parsed.did, DidResolutionOptions()).getOrElse {
            return Err(it)
        }

        val document = resolutionResult.didDocument
            ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID document not found for: ${parsed.did}"))

        // If there's a fragment, find the specific resource
        val fragment = parsed.fragment
        if (fragment != null) {
            // Try verification method first
            val vm = document.getVerificationMethodById(fragment)
            if (vm != null) {
                return Ok(DidDereferenceResult(
                    contentType = "application/did+ld+json",
                    verificationMethod = vm,
                    dereferencingMetadata = DidDereferencingMetadata()
                ))
            }

            // Try service
            val service = document.service?.find { it.id == fragment || it.id.endsWith("#$fragment") }
            if (service != null) {
                return Ok(DidDereferenceResult(
                    contentType = "application/did+ld+json",
                    service = service,
                    dereferencingMetadata = DidDereferencingMetadata()
                ))
            }

            return Err(IdkError.NOT_FOUND_ERROR(
                message = "Fragment not found in DID document: #$fragment"
            ))
        }

        // Check for service query parameter
        val serviceQuery = parsed.query?.get("service")
        if (serviceQuery != null) {
            val service = document.service?.find { it.id == serviceQuery || it.id.endsWith("#$serviceQuery") }
            if (service != null) {
                return Ok(DidDereferenceResult(
                    contentType = "application/did+ld+json",
                    service = service,
                    dereferencingMetadata = DidDereferencingMetadata()
                ))
            }
            return Err(IdkError.NOT_FOUND_ERROR(
                message = "Service not found in DID document: $serviceQuery"
            ))
        }

        // No fragment or query - return the whole document
        return Ok(DidDereferenceResult(
            contentType = "application/did+ld+json",
            didDocument = document,
            dereferencingMetadata = DidDereferencingMetadata()
        ))
    }

    /**
     * Fetches a DID document from the given URL.
     */
    private suspend fun fetchDidDocument(url: String): IdkResult<String, IdkError> {
        val client = httpClientFactory.createClient(HttpClientOptions())

        return try {
            val response: HttpResponse = client.get(url)

            if (!response.status.isSuccess()) {
                return Err(IdkError.NOT_FOUND_ERROR(
                    message = "Failed to fetch DID document from $url: HTTP ${response.status.value}"
                ))
            }

            val contentType = response.headers["Content-Type"]
            if (contentType != null &&
                "application/json" !in contentType &&
                "application/did+ld+json" !in contentType &&
                "application/did+json" !in contentType) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Unexpected content type: $contentType. Expected JSON."
                ))
            }

            Ok(response.bodyAsText())
        } catch (e: Exception) {
            Err(IdkError.fromString(
                message = "Failed to fetch DID document from $url: ${e.message}",
                code = "HTTP_REQUEST_FAILED",
                exception = e
            ))
        } finally {
            client.close()
        }
    }
}

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
 *
 */

package com.sphereon.did.methods.web

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.PrincipalConfigService
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
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.utils.ParsedDid
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

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
    private val httpClientFactory: HttpClientFactory,
    private val configService: PrincipalConfigService,
    // Lazy provider breaks the DI cycle: the registry holds this resolver in
    // its `Set<DidResolver>`, and this resolver consults the registry to
    // optionally upgrade did:web → did:webvh.
    private val resolverRegistryProvider: Provider<DidResolverRegistry>,
) : WebDidResolver {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override val supportedMethods: List<String> = listOf(WebDidCapabilities.METHOD)

    override val capabilities: DidMethodCapabilities = WebDidCapabilities.CAPABILITIES

    override suspend fun resolve(
        did: String,
        options: DidResolutionOptions,
    ): IdkResult<DidResolutionResult, IdkError> {
        // Parse the DID
        val parsed =
            ParsedDid.tryParse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        if (parsed.method != WebDidCapabilities.METHOD) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Unsupported DID method: ${parsed.method}. Expected: ${WebDidCapabilities.METHOD}",
                ),
            )
        }

        // Validate the did:web format
        if (!WebDidUrlBuilder.isValidDidWeb(did)) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Invalid did:web format: $did",
                ),
            )
        }

        // Convert DID to URL
        val url =
            try {
                WebDidUrlBuilder.didToUrl(did)
            } catch (expected: Exception) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Failed to convert DID to URL: ${expected.message}",
                    ),
                )
            }

        // Fetch the DID document
        val documentJson =
            fetchDidDocument(url).getOrElse {
                return Err(it)
            }

        // Parse the DID document
        val didDocument =
            try {
                json.decodeFromString<DidDocument>(documentJson)
            } catch (expected: Exception) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Failed to parse DID document: ${expected.message}",
                    ),
                )
            }

        // Validate that the document ID matches the DID. The caller may pass a DID URL
        // (with path/query/fragment) per W3C DID Core §3.2; the document `id` is always
        // the bare DID, so compare against the stripped form.
        val bareDid = WebDidUrlBuilder.stripDidUrlSyntax(did)
        if (didDocument.id != bareDid) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "DID document ID mismatch: expected '$bareDid', got '${didDocument.id}'",
                ),
            )
        }

        // Build verification methods by purpose map
        val vmByPurpose = didDocument.getVerificationMethodsByPurpose()

        val baseResult =
            DidResolutionResult(
                didDocument = didDocument,
                didResolutionMetadata =
                    DidResolutionMetadata(
                        contentType = "application/did+ld+json",
                    ),
                didDocumentMetadata = DidDocumentMetadata(),
                verificationMethodsByPurpose = vmByPurpose,
            )
        return Ok(maybeUpgradeToWebvh(baseResult, options))
    }

    /**
     * Per webvh v1.0 §"Publishing a Parallel did:web DID": when a `did:web`
     * document carries `alsoKnownAs: ["did:webvh:..."]`, the verifiable
     * webvh log is the authoritative source. Upgrade the resolution by
     * delegating to a registered `webvh` resolver (if any) and returning
     * its result instead of the static `did.json`.
     *
     * Asymmetric, web-only optimisation:
     * - The webvh module does not need to know this exists.
     * - When no `webvh` resolver is registered (verifier-only build that
     *   doesn't include `did:webvh`), the upgrade is a no-op.
     * - On upgrade failure (network error, log replay rejected) we fall
     *   back to the plain `did:web` result rather than failing the whole
     *   resolution; the static document is still useful and the caller can
     *   pursue trust validation separately.
     *
     * Disabled by setting `did.web.upgrade-to-webvh.enabled=false` in
     * principal config.
     */
    private suspend fun maybeUpgradeToWebvh(
        baseResult: DidResolutionResult,
        options: DidResolutionOptions,
    ): DidResolutionResult {
        if (!isUpgradeEnabled()) {
            return baseResult
        }
        val webvhAka =
            baseResult.didDocument
                ?.alsoKnownAs
                ?.firstOrNull { it.startsWith(DID_WEBVH_PREFIX) }
                ?: return baseResult
        val webvhResolver = resolverRegistryProvider().getResolver(WEBVH_METHOD) ?: return baseResult
        val upgraded = webvhResolver.resolve(webvhAka, options)
        if (upgraded.isErr) {
            return baseResult
        }
        return upgraded.value
    }

    private fun isUpgradeEnabled(): Boolean =
        configService.getProperty(
            key = CONFIG_KEY_UPGRADE_TO_WEBVH,
            targetType = Boolean::class,
            defaultValue = true,
        ) ?: true

    override suspend fun dereference(
        didUrl: String,
        options: DidDereferenceOptions,
    ): IdkResult<DidDereferenceResult, IdkError> {
        // Parse DID URL (may include fragment like #key-1)
        val parsed =
            ParsedDid.tryParse(didUrl)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID URL: $didUrl"))

        // First resolve the full document
        val resolutionResult =
            resolve(parsed.did, DidResolutionOptions()).getOrElse {
                return Err(it)
            }

        val document =
            resolutionResult.didDocument
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID document not found for: ${parsed.did}"))

        // If there's a fragment, find the specific resource
        val fragment = parsed.fragment
        if (fragment != null) {
            // Try verification method first
            val vm = document.getVerificationMethodById(fragment)
            if (vm != null) {
                return Ok(
                    DidDereferenceResult(
                        contentType = "application/did+ld+json",
                        verificationMethod = vm,
                        dereferencingMetadata = DidDereferencingMetadata(),
                    ),
                )
            }

            // Try service
            val service = document.service?.find { it.id == fragment || it.id.endsWith("#$fragment") }
            if (service != null) {
                return Ok(
                    DidDereferenceResult(
                        contentType = "application/did+ld+json",
                        service = service,
                        dereferencingMetadata = DidDereferencingMetadata(),
                    ),
                )
            }

            return Err(
                IdkError.NOT_FOUND_ERROR(
                    message = "Fragment not found in DID document: #$fragment",
                ),
            )
        }

        // Check for service query parameter
        val serviceQuery = parsed.query?.get("service")
        if (serviceQuery != null) {
            val service = document.service?.find { it.id == serviceQuery || it.id.endsWith("#$serviceQuery") }
            if (service != null) {
                return Ok(
                    DidDereferenceResult(
                        contentType = "application/did+ld+json",
                        service = service,
                        dereferencingMetadata = DidDereferencingMetadata(),
                    ),
                )
            }
            return Err(
                IdkError.NOT_FOUND_ERROR(
                    message = "Service not found in DID document: $serviceQuery",
                ),
            )
        }

        // No fragment or query - return the whole document
        return Ok(
            DidDereferenceResult(
                contentType = "application/did+ld+json",
                didDocument = document,
                dereferencingMetadata = DidDereferencingMetadata(),
            ),
        )
    }

    /**
     * Fetches a DID document from the given URL.
     */
    private suspend fun fetchDidDocument(url: String): IdkResult<String, IdkError> {
        val client = httpClientFactory.createClient(HttpClientOptions())

        return try {
            val response: HttpResponse = client.get(url)

            if (!response.status.isSuccess()) {
                return Err(
                    IdkError.NOT_FOUND_ERROR(
                        message = "Failed to fetch DID document from $url: HTTP ${response.status.value}",
                    ),
                )
            }

            val contentType = response.headers["Content-Type"]
            if (contentType != null &&
                "application/json" !in contentType &&
                "application/did+ld+json" !in contentType &&
                "application/did+json" !in contentType
            ) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Unexpected content type: $contentType. Expected JSON.",
                    ),
                )
            }

            Ok(response.bodyAsText())
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    message = "Failed to fetch DID document from $url: ${expected.message}",
                    code = "HTTP_REQUEST_FAILED",
                    exception = expected,
                ),
            )
        } finally {
            client.close()
        }
    }

    companion object {
        const val CONFIG_KEY_UPGRADE_TO_WEBVH: String = "did.web.upgrade-to-webvh.enabled"
        private const val DID_WEBVH_PREFIX: String = "did:webvh:"
        private const val WEBVH_METHOD: String = "webvh"
    }
}

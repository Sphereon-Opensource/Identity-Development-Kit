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

package com.sphereon.did.methods.webvh.resolver

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.methods.webvh.WebvhDidCapabilities
import com.sphereon.did.methods.webvh.WebvhDidUrlBuilder
import com.sphereon.did.methods.webvh.log.WebvhLogReader
import com.sphereon.did.methods.webvh.model.WebvhWitnessFile
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
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * `did:webvh` v1.0 resolver: fetches `did.jsonl` (and optionally
 * `did-witness.json`) over HTTPS, parses the log, and replays it via
 * [WebvhLogReplayer] to produce a verified `DidResolutionResult`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DidResolver>())
@ContributesBinding(SessionScope::class, binding = binding<WebvhDidResolver>())
class WebvhDidResolverImpl(
    private val httpClientFactory: HttpClientFactory,
    private val replayer: WebvhLogReplayer,
) : WebvhDidResolver {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    override val supportedMethods: List<String> = listOf(WebvhDidCapabilities.METHOD)
    override val capabilities: DidMethodCapabilities = WebvhDidCapabilities.CAPABILITIES

    override suspend fun resolve(
        did: String,
        options: DidResolutionOptions,
    ): IdkResult<DidResolutionResult, IdkError> {
        val parsed =
            ParsedDid.tryParse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))
        if (parsed.method != WebvhDidCapabilities.METHOD) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported DID method: ${parsed.method}"))
        }

        val baseDid = parsed.did
        val logUrl = WebvhDidUrlBuilder.toLogUrl(baseDid).getOrElseErr { return Err(it) }
        val witnessUrl = WebvhDidUrlBuilder.toWitnessUrl(baseDid).getOrElseErr { return Err(it) }

        val logJsonl = fetchText(logUrl).getOrElseErr { return Err(it) }
        val entries = WebvhLogReader.read(logJsonl).getOrElseErr { return Err(it) }

        // Fetch witness file lazily — only when at least one entry has a witness configured.
        val anyEntryHasWitness = entries.any { it.parameters.witness != null }
        val witnessFile: WebvhWitnessFile? =
            if (anyEntryHasWitness) {
                fetchOptionalWitnessFile(witnessUrl).getOrElseErr { return Err(it) }
            } else {
                null
            }

        val replay =
            replayer
                .replay(
                    did = baseDid,
                    entries = entries,
                    witnessFile = witnessFile,
                    selector = ReplaySelector.LATEST,
                ).getOrElseErr { return Err(it) }

        val metadata =
            DidResolutionMetadata(
                contentType = CONTENT_TYPE_DID_LD_JSON,
            )
        val docMetadata =
            DidDocumentMetadata(
                versionId = replay.selectedEntry.versionId,
                // Webvh §3.5 spec note: ttl is surfaced as a string via DidResolutionMetadata extensions
                // when the metadata model supports it. Active TTL = replay.activeParameters.ttl.
            )
        val vmByPurpose = replay.didDocument.getVerificationMethodsByPurpose()

        return Ok(
            DidResolutionResult(
                didDocument = replay.didDocument,
                didResolutionMetadata = metadata,
                didDocumentMetadata = docMetadata,
                verificationMethodsByPurpose = vmByPurpose,
            ),
        )
    }

    override suspend fun dereference(
        didUrl: String,
        options: DidDereferenceOptions,
    ): IdkResult<DidDereferenceResult, IdkError> {
        val parsed =
            ParsedDid.tryParse(didUrl)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID URL: $didUrl"))
        val resolution = resolve(parsed.did, DidResolutionOptions()).getOrElseErr { return Err(it) }
        val document =
            resolution.didDocument
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "DID document not found for: ${parsed.did}"))

        val fragment = parsed.fragment
        if (fragment != null) {
            document.getVerificationMethodById(fragment)?.let { vm ->
                return Ok(
                    DidDereferenceResult(
                        contentType = CONTENT_TYPE_DID_LD_JSON,
                        verificationMethod = vm,
                        dereferencingMetadata = DidDereferencingMetadata(),
                    ),
                )
            }
            document.service?.firstOrNull { it.id == fragment || it.id.endsWith("#$fragment") }?.let { svc ->
                return Ok(
                    DidDereferenceResult(
                        contentType = CONTENT_TYPE_DID_LD_JSON,
                        service = svc,
                        dereferencingMetadata = DidDereferencingMetadata(),
                    ),
                )
            }
            return Err(IdkError.NOT_FOUND_ERROR(message = "Fragment not found in DID document: #$fragment"))
        }

        return Ok(
            DidDereferenceResult(
                contentType = CONTENT_TYPE_DID_LD_JSON,
                didDocument = document,
                dereferencingMetadata = DidDereferencingMetadata(),
            ),
        )
    }

    private suspend fun fetchText(url: String): IdkResult<String, IdkError> {
        val client = httpClientFactory.createClient(HttpClientOptions())
        return try {
            val response: HttpResponse = client.get(url)
            if (!response.status.isSuccess()) {
                return Err(IdkError.NOT_FOUND_ERROR(message = "Failed to fetch $url: HTTP ${response.status.value}"))
            }
            Ok(response.bodyAsText())
        } catch (expected: Exception) {
            Err(IdkError.fromString("HTTP fetch failed for $url: ${expected.message}", code = "HTTP_REQUEST_FAILED", exception = expected))
        } finally {
            client.close()
        }
    }

    private suspend fun fetchOptionalWitnessFile(url: String): IdkResult<WebvhWitnessFile?, IdkError> {
        val client = httpClientFactory.createClient(HttpClientOptions())
        return try {
            val response: HttpResponse = client.get(url)
            if (!response.status.isSuccess()) {
                return Ok(null)
            }
            val body = response.bodyAsText()
            if (body.isBlank()) {
                return Ok(null)
            }
            Ok(json.decodeFromString(WebvhWitnessFile.serializer(), body))
        } catch (_: Exception) {
            Ok(null)
        } finally {
            client.close()
        }
    }

    companion object {
        private const val CONTENT_TYPE_DID_LD_JSON = "application/did+ld+json"
    }
}

private inline fun <V, E> IdkResult<V, E>.getOrElseErr(onErr: (E) -> Nothing): V =
    if (isOk) {
        value
    } else {
        onErr(error)
    }

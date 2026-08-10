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

package com.sphereon.did.resolver.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.extern.DIDDocument
import com.sphereon.crypto.resolution.extern.DIDResolutionResult
import com.sphereon.crypto.resolution.extern.DidDocumentJwks
import com.sphereon.crypto.resolution.extern.ExternalIdentifierDidOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierServiceAdapter
import com.sphereon.crypto.resolution.extern.ParsedDID
import com.sphereon.crypto.resolution.extern.Service
import com.sphereon.crypto.resolution.extern.VerificationMethod
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolverRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import software.amazon.app.platform.scope.Scoped

/**
 * Service for resolving external DID identifiers.
 *
 * This service handles [ExternalIdentifierDidOpts], which represents a DID that
 * should be resolved to retrieve its DID Document and public keys.
 *
 * The service uses the [DidResolverRegistry] to delegate resolution to the
 * appropriate method-specific resolver (did:key, did:web, did:jwk, etc.).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DidExternalIdentifierResolutionService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class DidExternalIdentifierResolutionServiceImpl(
    execution: SessionExecution,
    private val resolverRegistry: DidResolverRegistry,
) : ExternalIdentifierServiceAdapter<ExternalIdentifierResult.Did>(
        supportedIdentifierMethods = listOf(IdentifierMethodDefaults.DID),
        execution = execution,
        commandId = COMMAND_ID,
    ),
    DidExternalIdentifierResolutionService,
    Scoped {
    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult,
    ): IdkResult<ExternalIdentifierResult.Did, IdkErrorType> {
        log.debug("Resolving external DID identifier: ${args.identifier.toString().take(100)}...")

        if (!supports(args)) {
            return IdkError
                .COMMAND_ARG_NOT_SUPPORTED_ERROR(
                    message = "External identifier opts for DID expected. Type ${args.method ?: args.identifier} not supported",
                ).asErrorResult()
        }

        val opts = asSupportedOpts(args).getOrElse { return Err(it) }
        val did = opts.identifier

        // Parse the DID
        val parsedDid =
            parseDid(did) ?: return IdkError
                .ILLEGAL_ARGUMENT_ERROR(
                    message = "Invalid DID format: $did",
                ).asErrorResult()

        // Get the resolver for this method
        val resolver =
            resolverRegistry.getResolver(parsedDid.method)
                ?: return IdkError
                    .NOT_FOUND_ERROR(
                        message = "No resolver found for DID method: ${parsedDid.method}",
                    ).asErrorResult()

        // Resolve the DID document, not the DID URL. Fragments are dereference
        // selectors and must not become part of method-specific document ids
        // (did:jwk would otherwise publish a malformed `#0#0` method).
        val documentDid = did.substringBefore('#')
        val resolutionResult =
            resolver
                .resolve(documentDid, DidResolutionOptions())
                .getOrElse { return Err(it) }

        // Convert DID document to crypto resolution types
        val didDocument = resolutionResult.didDocument?.let { convertDidDocument(it) }

        // Extract JWKs from verification methods
        val jwks = extractJwks(resolutionResult.didDocument)
        val didJwks = extractDidDocumentJwks(resolutionResult.didDocument)

        // A DID URL fragment identifies one verification method. Never silently
        // fall back to the first document key: multi-key hosted DIDs commonly
        // publish verifier and issuer keys in a different order.
        val keyInfo =
            selectDidKeyInfo(did, parsedDid.fragment, jwks)
                ?: return IdkError
                    .NOT_FOUND_ERROR(
                        message =
                            if (parsedDid.fragment == null) {
                                "DID document has no verification methods with public keys: $did"
                            } else {
                                "DID document does not contain exactly one public verification method for: $did"
                            },
                    ).asErrorResult()

        return ExternalIdentifierResult
            .Did(
                identifierOpts = opts,
                jwks = jwks.toTypedArray(),
                keyInfo = keyInfo,
                did = did,
                didDocument = didDocument,
                didJwks = didJwks,
                didResolutionResult =
                    DIDResolutionResult(
                        didResolutionMetadata =
                            mapOf(
                                "contentType" to (resolutionResult.didResolutionMetadata.contentType ?: "application/did+ld+json"),
                            ),
                        didDocumentMetadata =
                            buildMap {
                                resolutionResult.didDocumentMetadata.created?.let { put("created", it.toString()) }
                                resolutionResult.didDocumentMetadata.updated?.let { put("updated", it.toString()) }
                                resolutionResult.didDocumentMetadata.deactivated?.let { put("deactivated", it.toString()) }
                                resolutionResult.didDocumentMetadata.versionId?.let { put("versionId", it) }
                                resolutionResult.didDocumentMetadata.nextVersionId?.let { put("nextVersionId", it) }
                                resolutionResult.didDocumentMetadata.nextUpdate?.let { put("nextUpdate", it.toString()) }
                                resolutionResult.didDocumentMetadata.equivalentId?.let { put("equivalentId", it) }
                                resolutionResult.didDocumentMetadata.canonicalId?.let { put("canonicalId", it) }
                            },
                    ),
                didParsed = parsedDid,
            ).asOkResult()
            .also {
                log.debug("Resolved external DID identifier: $did")
            }
    }

    override suspend fun supports(args: Any): Boolean {
        // Check type and validate identifier - never throw, just return false if not supported
        return when (args) {
            is ExternalIdentifierDidOpts -> {
                isSupportedDidIdentifier(args.identifier) == null
            }

            is com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts -> {
                val methodSupported = args.method?.let { isSupportedIdentifierMethod(it) } == true
                val identifierSupported = (args.identifier as? String)?.let { isSupportedDidIdentifier(it) == null } ?: false
                methodSupported && identifierSupported
            }

            else -> {
                false
            }
        }
    }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean = isSupportedDidIdentifier(identifier) == null

    /**
     * Validates a DID identifier and returns an error if invalid, or null if valid.
     * This allows both checking support and getting specific error messages without double parsing.
     */
    private fun isSupportedDidIdentifier(identifier: Any): IdkErrorType? {
        if (identifier !is String) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "DID identifier must be a string, got: ${identifier::class.simpleName}")
        }
        val parsed =
            parseDid(identifier)
                ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $identifier")
        if (resolverRegistry.getResolver(parsed.method) == null) {
            return IdkError.NOT_FOUND_ERROR(message = "No resolver found for DID method: ${parsed.method}")
        }
        return null
    }

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.Did, IdkErrorType> {
        // Validate identifier and return proper error instead of letting framework throw
        val identifier = (opts as? ExternalIdentifierDidOpts)?.identifier ?: opts.identifier
        val validationError = isSupportedDidIdentifier(identifier)
        if (validationError != null) {
            return validationError.asErrorResult()
        }
        return execute(opts)
    }

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierDidOpts, IdkErrorType> =
        if (isSupportedOpts(opts)) {
            (opts as ExternalIdentifierDidOpts).asOkResult()
        } else {
            IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }

    private fun parseDid(did: String): ParsedDID? {
        val parts = did.split(":")
        if (parts.size < 3 || parts[0] != "did") {
            return null
        }

        val method = parts[1]
        val id = parts.drop(2).joinToString(":")

        // Check for fragment
        val fragmentIndex = id.indexOf('#')
        val (idPart, fragment) =
            if (fragmentIndex >= 0) {
                id.substring(0, fragmentIndex) to id.substring(fragmentIndex + 1)
            } else {
                id to null
            }

        // Check for path
        val pathIndex = idPart.indexOf('/')
        val (methodSpecificId, path) =
            if (pathIndex >= 0) {
                idPart.substring(0, pathIndex) to idPart.substring(pathIndex)
            } else {
                idPart to null
            }

        return ParsedDID(
            did = did,
            method = method,
            id = methodSpecificId,
            path = path,
            fragment = fragment,
        )
    }

    private fun convertDidDocument(doc: com.sphereon.did.models.DidDocument): DIDDocument =
        DIDDocument(
            id = doc.id,
            verificationMethod =
                doc.verificationMethod?.map { vm ->
                    VerificationMethod(
                        id = vm.id,
                        type = vm.type,
                        controller = vm.controller,
                        // vm.publicKeyJwk is already a Jwk which extends JwkType
                        publicKeyJwk = vm.publicKeyJwk,
                        publicKeyMultibase = vm.publicKeyMultibase,
                    )
                },
            authentication = doc.authentication?.mapNotNull { it.reference ?: it.embedded?.id },
            assertionMethod = doc.assertionMethod?.mapNotNull { it.reference ?: it.embedded?.id },
            keyAgreement = doc.keyAgreement?.mapNotNull { it.reference ?: it.embedded?.id },
            capabilityInvocation = doc.capabilityInvocation?.mapNotNull { it.reference ?: it.embedded?.id },
            capabilityDelegation = doc.capabilityDelegation?.mapNotNull { it.reference ?: it.embedded?.id },
            service =
                doc.service?.mapNotNull { svc ->
                    if (svc.type.isEmpty()) return@mapNotNull null
                    val endpoint = svc.serviceEndpointAsStringOrNull() ?: return@mapNotNull null
                    Service(
                        id = svc.id,
                        type = svc.type.first(),
                        serviceEndpoint = endpoint,
                    )
                },
        )

    private fun extractJwks(doc: com.sphereon.did.models.DidDocument?): List<ResolvedKeyInfo<JwkType>> {
        if (doc == null) {
            return emptyList()
        }

        return doc.verificationMethod?.mapNotNull { vm ->
            vm.publicKeyJwk?.let { jwk ->
                try {
                    // Create ResolvedKeyInfo with the verification method id as the kid
                    ResolvedKeyInfo(
                        kid = vm.id,
                        key = jwk,
                        keyType = jwk.kty?.let { KeyTypeMapping.fromValue(it.value) },
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } ?: emptyList()
    }

    private fun extractDidDocumentJwks(doc: com.sphereon.did.models.DidDocument?): DidDocumentJwks? {
        if (doc == null) {
            return null
        }

        val result = mutableMapOf<String, List<JwkType>>()

        // Extract JWKs from verification method sections
        doc.authentication?.let { refs ->
            val jwks =
                refs.mapNotNull { ref ->
                    getJwkFromRef(doc, ref)
                }
            if (jwks.isNotEmpty()) {
                result["authentication"] = jwks
            }
        }

        doc.assertionMethod?.let { refs ->
            val jwks =
                refs.mapNotNull { ref ->
                    getJwkFromRef(doc, ref)
                }
            if (jwks.isNotEmpty()) {
                result["assertionMethod"] = jwks
            }
        }

        doc.keyAgreement?.let { refs ->
            val jwks =
                refs.mapNotNull { ref ->
                    getJwkFromRef(doc, ref)
                }
            if (jwks.isNotEmpty()) {
                result["keyAgreement"] = jwks
            }
        }

        return result.ifEmpty { null }
    }

    private fun getJwkFromRef(
        doc: com.sphereon.did.models.DidDocument,
        ref: com.sphereon.did.models.VerificationMethodOrReference,
    ): JwkType? {
        // If embedded, get directly (publicKeyJwk is already a Jwk which extends JwkType)
        ref.embedded?.publicKeyJwk?.let { return it }

        // If reference, look up in verification methods
        val vmId = ref.reference ?: return null
        val vm =
            doc.verificationMethod?.find {
                it.id == vmId || it.id.endsWith("#$vmId") || vmId.endsWith("#${it.id.substringAfterLast('#')}")
            }
        return vm?.publicKeyJwk
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val didExternalIdentifierResolutionService: DidExternalIdentifierResolutionService
    }

    companion object {
        const val COMMAND_ID = "did.resolution.external"
    }
}

internal fun selectDidKeyInfo(
    identifier: String,
    fragment: String?,
    jwks: List<ResolvedKeyInfo<JwkType>>,
): ResolvedKeyInfo<JwkType>? {
    if (fragment == null) return jwks.firstOrNull()

    val did = identifier.substringBefore('#')
    val absoluteId = "$did#$fragment"
    return jwks.singleOrNull { keyInfo ->
        keyInfo.kid == absoluteId || keyInfo.kid == "#$fragment" || keyInfo.kid == fragment
    }
}

/**
 * Interface for DID external identifier resolution service.
 */
interface DidExternalIdentifierResolutionService : ExternalIdentifierService {
    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.Did, IdkErrorType>
}

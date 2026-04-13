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

package com.sphereon.did.methods.jwk

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.di.session.SessionScope
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.models.VerificationMethodType
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.did.resolver.DidDereferenceOptions
import com.sphereon.did.resolver.DidDereferenceResult
import com.sphereon.did.resolver.DidDereferencingMetadata
import com.sphereon.did.resolver.DidDocumentMetadata
import com.sphereon.did.resolver.DidResolutionMetadata
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.resolver.DidResolutionResult
import com.sphereon.did.resolver.DidResolver
import com.sphereon.did.utils.ParsedDid
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * Resolver for the did:jwk DID method.
 *
 * did:jwk is a static DID method where the DID is derived from a
 * base64url-encoded JWK. The DID document is computed deterministically
 * from the JWK without requiring any network or storage lookup.
 *
 * Format: did:jwk:<base64url-encoded-jwk>
 *
 * Example: did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5IiwieCI6IlFfX0x...
 *
 * @see <a href="https://github.com/quartzjer/did-jwk/blob/main/spec.md">did:jwk Specification</a>
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DidResolver>())
@ContributesBinding(SessionScope::class, binding = binding<JwkDidResolver>())
class JwkDidResolverImpl : JwkDidResolver {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override val supportedMethods: List<String> = listOf(JwkDidCapabilities.METHOD)

    override val capabilities: DidMethodCapabilities = JwkDidCapabilities.CAPABILITIES

    override suspend fun resolve(
        did: String,
        options: DidResolutionOptions,
    ): IdkResult<DidResolutionResult, IdkError> {
        // Parse the DID
        val parsed =
            ParsedDid.tryParse(did)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid DID format: $did"))

        if (parsed.method != JwkDidCapabilities.METHOD) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Unsupported DID method: ${parsed.method}. Expected: ${JwkDidCapabilities.METHOD}",
                ),
            )
        }

        // The method-specific identifier is the base64url-encoded JWK
        val base64UrlJwk = parsed.methodSpecificId

        // Decode base64url to get JWK JSON
        val jwkJson =
            try {
                base64UrlJwk.decodeFromBase64Url().decodeToString()
            } catch (expected: Exception) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Failed to decode base64url JWK: ${expected.message}",
                    ),
                )
            }

        // Parse the JWK
        val jwk =
            try {
                json.decodeFromString<Jwk>(jwkJson)
            } catch (expected: Exception) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Failed to parse JWK JSON: ${expected.message}",
                    ),
                )
            }

        // Build the DID document
        val didDocument = buildDidDocument(did, jwk)

        // Build verification methods by purpose map
        val vmByPurpose = didDocument.getVerificationMethodsByPurpose()

        return Ok(
            DidResolutionResult(
                didDocument = didDocument,
                didResolutionMetadata =
                    DidResolutionMetadata(
                        contentType = "application/did+ld+json",
                    ),
                didDocumentMetadata = DidDocumentMetadata(),
                verificationMethodsByPurpose = vmByPurpose,
            ),
        )
    }

    override suspend fun dereference(
        didUrl: String,
        options: DidDereferenceOptions,
    ): IdkResult<DidDereferenceResult, IdkError> {
        // Parse DID URL (may include fragment like #0)
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

        // If there's a fragment, find the specific verification method
        val fragment = parsed.fragment
        if (fragment != null) {
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

            return Err(
                IdkError.NOT_FOUND_ERROR(
                    message = "Fragment not found in DID document: #$fragment",
                ),
            )
        }

        // No fragment - return the whole document
        return Ok(
            DidDereferenceResult(
                contentType = "application/did+ld+json",
                didDocument = document,
                dereferencingMetadata = DidDereferencingMetadata(),
            ),
        )
    }

    /**
     * Builds a DID document from the JWK.
     *
     * Per the did:jwk spec, the verification method ID is always "#0".
     * The verification relationships are determined by the JWK's "use" parameter:
     * - "sig" or absent: authentication, assertionMethod, capabilityInvocation, capabilityDelegation
     * - "enc": keyAgreement
     */
    private fun buildDidDocument(
        did: String,
        jwk: Jwk,
    ): DidDocument {
        // The verification method ID is always #0
        val vmId = "$did#0"

        // Create the verification method
        val vm =
            VerificationMethod(
                id = vmId,
                type = VerificationMethodType.JSON_WEB_KEY_2020.value,
                controller = did,
                publicKeyJwk = jwk,
            )

        // Determine verification relationships based on JWK "use" parameter
        val use = getJwkUse(jwk)

        val authenticationRefs: List<VerificationMethodOrReference>?
        val assertionMethodRefs: List<VerificationMethodOrReference>?
        val keyAgreementRefs: List<VerificationMethodOrReference>?
        val capabilityInvocationRefs: List<VerificationMethodOrReference>?
        val capabilityDelegationRefs: List<VerificationMethodOrReference>?

        if (use == "enc") {
            // Encryption key - only for keyAgreement
            authenticationRefs = null
            assertionMethodRefs = null
            keyAgreementRefs = listOf(VerificationMethodOrReference.fromReference(vmId))
            capabilityInvocationRefs = null
            capabilityDelegationRefs = null
        } else {
            // Signing key (use == "sig" or absent) - for all signing purposes
            authenticationRefs = listOf(VerificationMethodOrReference.fromReference(vmId))
            assertionMethodRefs = listOf(VerificationMethodOrReference.fromReference(vmId))
            keyAgreementRefs = null
            capabilityInvocationRefs = listOf(VerificationMethodOrReference.fromReference(vmId))
            capabilityDelegationRefs = listOf(VerificationMethodOrReference.fromReference(vmId))
        }

        return DidDocument(
            id = did,
            verificationMethod = listOf(vm),
            authentication = authenticationRefs,
            assertionMethod = assertionMethodRefs,
            keyAgreement = keyAgreementRefs,
            capabilityInvocation = capabilityInvocationRefs,
            capabilityDelegation = capabilityDelegationRefs,
        )
    }

    /**
     * Gets the "use" parameter from a JWK, if present.
     */
    private fun getJwkUse(jwk: Jwk): String? = jwk.use

    @ContributesTo(SessionScope::class)
    interface Graph : JwkDidResolver.Graph {
        override val jwkDidResolver: JwkDidResolver
    }
}

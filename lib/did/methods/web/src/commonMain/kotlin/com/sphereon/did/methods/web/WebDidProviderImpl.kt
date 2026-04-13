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
import com.sphereon.did.manager.AddKeyOptions
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidCreateResult
import com.sphereon.did.manager.DidDeactivateOptions
import com.sphereon.did.manager.DidDeactivateResult
import com.sphereon.did.manager.DidProvider
import com.sphereon.did.manager.DidUpdateOptions
import com.sphereon.did.manager.DidUpdateResult
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethod
import com.sphereon.did.models.VerificationMethodOrReference
import com.sphereon.did.models.VerificationMethodType
import com.sphereon.did.models.VerificationPurpose
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * Provider for creating and managing did:web DIDs.
 *
 * did:web is a mutable DID method that supports the full lifecycle:
 * create, update, deactivate. The DID document is hosted at a web
 * location and must be published by the user.
 *
 * NOTE: This provider creates DID documents locally. The user is
 * responsible for publishing the document to their web server.
 *
 * @see <a href="https://w3c-ccg.github.io/did-method-web/">did:web Method Specification</a>
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DidProvider>())
class WebDidProviderImpl : DidProvider {

    override val method: String = WebDidCapabilities.METHOD

    override val capabilities: DidMethodCapabilities = WebDidCapabilities.CAPABILITIES

    override suspend fun create(
        options: DidCreateOptions
    ): IdkResult<DidCreateResult, IdkError> {
        // Validate that we have a domain
        val domain = options.domain
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "did:web creation requires a domain in options"
            ))

        // Validate domain format
        if (domain.contains("://")) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Domain should not include protocol (https://)"
            ))
        }

        // Build the DID
        val did = WebDidUrlBuilder.urlToDid(domain, options.path ?: emptyList())

        // Create verification methods from options
        val verificationMethods = mutableListOf<VerificationMethod>()
        val authenticationRefs = mutableListOf<VerificationMethodOrReference>()
        val assertionMethodRefs = mutableListOf<VerificationMethodOrReference>()
        val keyAgreementRefs = mutableListOf<VerificationMethodOrReference>()
        val capabilityInvocationRefs = mutableListOf<VerificationMethodOrReference>()
        val capabilityDelegationRefs = mutableListOf<VerificationMethodOrReference>()

        // Process verification methods from DSL (if present)
        if (options.verificationMethods.isNotEmpty()) {
            for (vmConfig in options.verificationMethods) {
                val jwk = vmConfig.publicKeyJwk ?: continue // Skip if no JWK (shouldn't happen with DSL)
                val fullVmId = vmConfig.createFullId(did)

                val vm = VerificationMethod(
                    id = fullVmId,
                    type = vmConfig.type.value,
                    controller = vmConfig.controller ?: did,
                    publicKeyJwk = jwk
                )
                verificationMethods.add(vm)

                // Add references based on purposes
                val ref = VerificationMethodOrReference.fromReference(fullVmId)
                for (purpose in vmConfig.purposes) {
                    when (purpose) {
                        VerificationPurpose.AUTHENTICATION -> authenticationRefs.add(ref)
                        VerificationPurpose.ASSERTION_METHOD -> assertionMethodRefs.add(ref)
                        VerificationPurpose.KEY_AGREEMENT -> keyAgreementRefs.add(ref)
                        VerificationPurpose.CAPABILITY_INVOCATION -> capabilityInvocationRefs.add(ref)
                        VerificationPurpose.CAPABILITY_DELEGATION -> capabilityDelegationRefs.add(ref)
                    }
                }
            }
        } else {
            // Legacy path: use publicKeyJwk directly (backward compatibility)
            options.publicKeyJwk?.let { jwk ->
                val vmId = options.verificationMethodId ?: "key-1"
                val fullVmId = "$did#$vmId"

                val vm = VerificationMethod(
                    id = fullVmId,
                    type = options.verificationMethodType?.value
                        ?: VerificationMethodType.JSON_WEB_KEY_2020.value,
                    controller = options.controller ?: did,
                    publicKeyJwk = jwk
                )
                verificationMethods.add(vm)

                // Determine purposes from options or default
                val purposes = options.purposes ?: listOf(
                    VerificationPurpose.AUTHENTICATION,
                    VerificationPurpose.ASSERTION_METHOD
                )

                purposes.forEach { purpose ->
                    val ref = VerificationMethodOrReference.fromReference(fullVmId)
                    when (purpose) {
                        VerificationPurpose.AUTHENTICATION -> authenticationRefs.add(ref)
                        VerificationPurpose.ASSERTION_METHOD -> assertionMethodRefs.add(ref)
                        VerificationPurpose.KEY_AGREEMENT -> keyAgreementRefs.add(ref)
                        VerificationPurpose.CAPABILITY_INVOCATION -> capabilityInvocationRefs.add(ref)
                        VerificationPurpose.CAPABILITY_DELEGATION -> capabilityDelegationRefs.add(ref)
                    }
                }
            }
        }

        // Build the DID document
        val document = DidDocument(
            id = did,
            controller = options.controller,
            verificationMethod = verificationMethods.takeIf { it.isNotEmpty() },
            authentication = authenticationRefs.takeIf { it.isNotEmpty() },
            assertionMethod = assertionMethodRefs.takeIf { it.isNotEmpty() },
            keyAgreement = keyAgreementRefs.takeIf { it.isNotEmpty() },
            capabilityInvocation = capabilityInvocationRefs.takeIf { it.isNotEmpty() },
            capabilityDelegation = capabilityDelegationRefs.takeIf { it.isNotEmpty() },
            service = options.services
        )

        val vmByPurpose = document.getVerificationMethodsByPurpose()

        return Ok(DidCreateResult(
            did = did,
            didDocument = document,
            verificationMethodsByPurpose = vmByPurpose
        ))
    }

    override suspend fun update(
        did: String,
        options: DidUpdateOptions
    ): IdkResult<DidUpdateResult, IdkError> {
        // Validate the DID is a did:web
        if (!did.startsWith("did:web:")) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "DID must be a did:web: $did"
            ))
        }

        // For did:web, update requires the current document
        val currentDocument = options.currentDocument
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Update requires the current DID document"
            ))

        // Apply updates to create new document
        val newDocument = applyUpdates(currentDocument, options)

        val vmByPurpose = newDocument.getVerificationMethodsByPurpose()

        return Ok(DidUpdateResult(
            did = did,
            didDocument = newDocument,
            verificationMethodsByPurpose = vmByPurpose
        ))
    }

    override suspend fun deactivate(
        did: String,
        options: DidDeactivateOptions
    ): IdkResult<DidDeactivateResult, IdkError> {
        // Validate the DID is a did:web
        if (!did.startsWith("did:web:")) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "DID must be a did:web: $did"
            ))
        }

        // For did:web deactivation, we create a minimal document
        // that indicates the DID is deactivated
        // The user must publish this to their server
        val deactivatedDocument = DidDocument(
            id = did,
            // No verification methods = deactivated
            verificationMethod = null,
            authentication = null,
            assertionMethod = null,
            keyAgreement = null,
            capabilityInvocation = null,
            capabilityDelegation = null,
            service = null
        )

        return Ok(DidDeactivateResult(
            did = did,
            deactivatedDocument = deactivatedDocument
        ))
    }

    override suspend fun addKey(
        did: String,
        options: AddKeyOptions
    ): IdkResult<DidUpdateResult, IdkError> {
        // Validate the DID is a did:web
        if (!did.startsWith("did:web:")) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "DID must be a did:web: $did"
            ))
        }

        // For did:web, addKey requires the current document
        val currentDocument = options.currentDocument
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "addKey requires the current DID document"
            ))

        // Create the new verification method
        val vmId = options.verificationMethodId ?: "key-${(currentDocument.verificationMethod?.size ?: 0) + 1}"
        val fullVmId = "$did#$vmId"

        val newVm = VerificationMethod(
            id = fullVmId,
            type = options.verificationMethodType?.value
                ?: VerificationMethodType.JSON_WEB_KEY_2020.value,
            controller = options.controller ?: did,
            publicKeyJwk = options.publicKeyJwk
        )

        // Add to verification methods
        val newVerificationMethods = (currentDocument.verificationMethod ?: emptyList()) + newVm

        // Add references based on purposes
        val purposes = options.purposes ?: listOf(VerificationPurpose.AUTHENTICATION)
        val ref = VerificationMethodOrReference.fromReference(fullVmId)

        val newAuthentication = if (purposes.contains(VerificationPurpose.AUTHENTICATION)) {
            (currentDocument.authentication ?: emptyList()) + ref
        } else {
            currentDocument.authentication
        }

        val newAssertionMethod = if (purposes.contains(VerificationPurpose.ASSERTION_METHOD)) {
            (currentDocument.assertionMethod ?: emptyList()) + ref
        } else {
            currentDocument.assertionMethod
        }

        val newKeyAgreement = if (purposes.contains(VerificationPurpose.KEY_AGREEMENT)) {
            (currentDocument.keyAgreement ?: emptyList()) + ref
        } else {
            currentDocument.keyAgreement
        }

        val newDocument = currentDocument.copy(
            verificationMethod = newVerificationMethods,
            authentication = newAuthentication,
            assertionMethod = newAssertionMethod,
            keyAgreement = newKeyAgreement
        )

        val vmByPurpose = newDocument.getVerificationMethodsByPurpose()

        return Ok(DidUpdateResult(
            did = did,
            didDocument = newDocument,
            verificationMethodsByPurpose = vmByPurpose
        ))
    }

    override suspend fun removeKey(
        did: String,
        keyId: String
    ): IdkResult<DidUpdateResult, IdkError> {
        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
            message = "removeKey requires the current DID document. Use update() with currentDocument."
        ))
    }

    override suspend fun addService(
        did: String,
        service: DidService
    ): IdkResult<DidUpdateResult, IdkError> {
        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
            message = "addService requires the current DID document. Use update() with currentDocument."
        ))
    }

    override suspend fun removeService(
        did: String,
        serviceId: String
    ): IdkResult<DidUpdateResult, IdkError> {
        return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(
            message = "removeService requires the current DID document. Use update() with currentDocument."
        ))
    }

    /**
     * Applies updates to a DID document.
     */
    private fun applyUpdates(current: DidDocument, options: DidUpdateOptions): DidDocument {
        var updated = current

        // Update controller if specified
        options.controller?.let { updated = updated.copy(controller = it) }

        // Add new verification methods
        options.addVerificationMethods?.let { newVms ->
            val currentVms = updated.verificationMethod ?: emptyList()
            updated = updated.copy(verificationMethod = currentVms + newVms)
        }

        // Remove verification methods
        options.removeVerificationMethodIds?.let { idsToRemove ->
            val currentVms = updated.verificationMethod ?: emptyList()
            val filteredVms = currentVms.filterNot { vm ->
                idsToRemove.any { id -> vm.id == id || vm.id.endsWith("#$id") }
            }
            updated = updated.copy(verificationMethod = filteredVms)

            // Also remove from relationships
            updated = updated.copy(
                authentication = removeReferences(updated.authentication, idsToRemove),
                assertionMethod = removeReferences(updated.assertionMethod, idsToRemove),
                keyAgreement = removeReferences(updated.keyAgreement, idsToRemove),
                capabilityInvocation = removeReferences(updated.capabilityInvocation, idsToRemove),
                capabilityDelegation = removeReferences(updated.capabilityDelegation, idsToRemove)
            )
        }

        // Add new services
        options.addServices?.let { newServices ->
            val currentServices = updated.service ?: emptyList()
            updated = updated.copy(service = currentServices + newServices)
        }

        // Remove services
        options.removeServiceIds?.let { idsToRemove ->
            val currentServices = updated.service ?: emptyList()
            val filteredServices = currentServices.filterNot { svc ->
                idsToRemove.any { id -> svc.id == id || svc.id.endsWith("#$id") }
            }
            updated = updated.copy(service = filteredServices.takeIf { it.isNotEmpty() })
        }

        return updated
    }

    private fun removeReferences(
        refs: List<VerificationMethodOrReference>?,
        idsToRemove: List<String>
    ): List<VerificationMethodOrReference>? {
        if (refs == null) return null
        val filtered = refs.filterNot { ref ->
            val refId = ref.reference ?: ref.embedded?.id ?: return@filterNot false
            idsToRemove.any { id -> refId == id || refId.endsWith("#$id") }
        }
        return filtered.takeIf { it.isNotEmpty() }
    }
}

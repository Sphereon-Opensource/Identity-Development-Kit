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

package com.sphereon.did.manager

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService

/**
 * Provides lifecycle operations for a specific DID method.
 *
 * Each DID method has its own provider implementation that handles
 * the method-specific logic for creating, updating, and deactivating DIDs.
 *
 * Note: No `I` prefix - implementations are suffixed with `Impl` (e.g., `KeyDidProviderImpl`).
 */
@JsExportCompat
interface DidProvider {
    /**
     * The DID method this provider handles (e.g., "key", "web", "jwk").
     */
    val method: String

    /**
     * The capabilities of this DID method.
     *
     * Used to determine which operations are supported before attempting them.
     */
    val capabilities: DidMethodCapabilities

    /**
     * Creates a new DID.
     *
     * @param options Options for DID creation including keys and services
     * @return The created DID and its document
     */
    suspend fun create(options: DidCreateOptions): IdkResult<DidCreateResult, IdkError>

    /**
     * Updates an existing DID.
     *
     * Only supported if [capabilities.lifecycle.update] is true.
     *
     * @param did The DID to update
     * @param options Update options (add/remove keys, services)
     * @return The updated DID document
     */
    suspend fun update(
        did: String,
        options: DidUpdateOptions,
    ): IdkResult<DidUpdateResult, IdkError>

    /**
     * Deactivates a DID.
     *
     * Only supported if [capabilities.lifecycle.deactivate] is true.
     *
     * @param did The DID to deactivate
     * @param options Deactivation options
     * @return The deactivation result
     */
    suspend fun deactivate(
        did: String,
        options: DidDeactivateOptions = DidDeactivateOptions(),
    ): IdkResult<DidDeactivateResult, IdkError>

    /**
     * Adds a key to an existing DID.
     *
     * Only supported if [capabilities.keyManagement.addition] is true.
     *
     * @param did The DID to add the key to
     * @param options Key addition options
     * @return The updated DID document
     */
    suspend fun addKey(
        did: String,
        options: AddKeyOptions,
    ): IdkResult<DidUpdateResult, IdkError>

    /**
     * Removes a key from a DID.
     *
     * Only supported if [capabilities.keyManagement.removal] is true.
     *
     * @param did The DID to remove the key from
     * @param keyId The verification method ID to remove
     * @param currentDocument The current DID document — required for methods
     *   that must republish the whole document (e.g. did:web has no stateful
     *   resolver). Methods that derive the document from the DID itself (e.g.
     *   did:key) ignore it. Optional so the contract stays uniform.
     * @return The updated DID document
     */
    suspend fun removeKey(
        did: String,
        keyId: String,
        currentDocument: DidDocument? = null,
    ): IdkResult<DidUpdateResult, IdkError>

    /**
     * Adds a service to a DID.
     *
     * Only supported if [capabilities.serviceManagement.addition] is true.
     *
     * @param did The DID to add the service to
     * @param service The service to add
     * @param currentDocument The current DID document (required for did:web)
     * @return The updated DID document
     */
    suspend fun addService(
        did: String,
        service: DidService,
        currentDocument: DidDocument? = null,
    ): IdkResult<DidUpdateResult, IdkError>

    /**
     * Removes a service from a DID.
     *
     * Only supported if [capabilities.serviceManagement.removal] is true.
     *
     * @param did The DID to remove the service from
     * @param serviceId The service ID to remove
     * @param currentDocument The current DID document (required for did:web)
     * @return The updated DID document
     */
    suspend fun removeService(
        did: String,
        serviceId: String,
        currentDocument: DidDocument? = null,
    ): IdkResult<DidUpdateResult, IdkError>

    /**
     * Replaces an existing verification method on a DID.
     *
     * Only supported if [capabilities.keyManagement.replacement] is true.
     * Default implementation returns UNSUPPORTED_OPERATION; methods that natively support
     * key replacement (e.g., did:web) override this to perform an atomic remove+add.
     *
     * @param did The DID owning the verification method
     * @param keyId The verification method ID (fragment or absolute) to replace
     * @param options New key material and metadata (carries `currentDocument` for did:web)
     * @return The updated DID document
     */
    suspend fun updateKey(
        did: String,
        keyId: String,
        options: AddKeyOptions,
    ): IdkResult<DidUpdateResult, IdkError> =
        Err(
            IdkError.fromString(
                message = "DID method '$method' does not support verification method replacement",
                code = "UNSUPPORTED_OPERATION",
                category = ErrorCategory.UNPROCESSABLE_ENTITY,
            ),
        )

    /**
     * Replaces an existing service entry on a DID.
     *
     * Only supported if [capabilities.serviceManagement.replacement] is true.
     * Default implementation returns UNSUPPORTED_OPERATION; methods that natively support
     * service replacement (e.g., did:web) override this.
     *
     * @param did The DID owning the service
     * @param serviceId The service ID to replace
     * @param service The replacement service definition
     * @param currentDocument The current DID document (required for did:web)
     * @return The updated DID document
     */
    suspend fun updateService(
        did: String,
        serviceId: String,
        service: DidService,
        currentDocument: DidDocument? = null,
    ): IdkResult<DidUpdateResult, IdkError> =
        Err(
            IdkError.fromString(
                message = "DID method '$method' does not support service replacement",
                code = "UNSUPPORTED_OPERATION",
                category = ErrorCategory.UNPROCESSABLE_ENTITY,
            ),
        )
}

/**
 * Registry for DID providers.
 *
 * Manages provider instances for different DID methods.
 *
 * Providers are automatically discovered via DI multibinding.
 * Any class annotated with `@ContributesIntoSet(SessionScope::class, binding = binding<DidProvider>())`
 * will be automatically registered.
 */
@JsExportCompat
interface DidProviderRegistry {
    /**
     * Gets the provider for a specific DID method.
     *
     * @param method The DID method (e.g., "key", "web")
     * @return The provider, or null if no provider is registered for this method
     */
    fun getProvider(method: String): DidProvider?

    /**
     * Gets the capabilities for a specific DID method.
     *
     * @param method The DID method
     * @return The capabilities, or null if method not supported
     */
    fun getCapabilities(method: String): DidMethodCapabilities?

    /**
     * Gets all supported DID methods.
     *
     * @return List of supported method names
     */
    fun getSupportedMethods(): List<String>
}

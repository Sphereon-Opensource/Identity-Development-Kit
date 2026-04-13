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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.models.DidService
import com.sphereon.did.models.VerificationMethodConfig

/**
 * Central DID management service.
 *
 * Provides a unified API for managing DIDs across different methods.
 * Delegates to method-specific [DidProvider] implementations.
 *
 * Note: No `I` prefix - implementation is `DidManagerServiceImpl`.
 */
interface DidManager {
    // =====================
    // Registry Access
    // =====================

    /**
     * Gets the capabilities for a specific DID method.
     *
     * @param method The DID method (e.g., "key", "web")
     * @return The capabilities, or null if method not supported
     */
    fun getCapabilities(method: String): DidMethodCapabilities?

    /**
     * Gets all supported DID methods.
     *
     * @return List of supported method names
     */
    fun getSupportedMethods(): List<String>

    // =====================
    // DID Lifecycle
    // =====================

    /**
     * Creates a new DID.
     *
     * @param options Options for DID creation
     * @return The created ManagedDid
     */
    suspend fun create(options: DidCreateOptions): IdkResult<ManagedDid, IdkError>

    /**
     * Gets a managed DID by its identifier.
     *
     * @param did The DID to retrieve
     * @return The managed DID, or error if not found
     */
    suspend fun get(did: String): IdkResult<ManagedDid, IdkError>

    /**
     * Gets a managed DID by its alias.
     *
     * @param alias The alias to search for
     * @return The managed DID, or error if not found
     */
    suspend fun getByAlias(alias: String): IdkResult<ManagedDid, IdkError>

    /**
     * Lists managed DIDs with optional filtering.
     *
     * @param filter Optional filter criteria
     * @return List of matching managed DIDs
     */
    suspend fun list(filter: DidFilter? = null): IdkResult<List<ManagedDid>, IdkError>

    /**
     * Updates an existing DID.
     *
     * @param did The DID to update
     * @param options Update options
     * @return The updated ManagedDid
     */
    suspend fun update(
        did: String,
        options: DidUpdateOptions,
    ): IdkResult<ManagedDid, IdkError>

    /**
     * Deactivates a DID.
     *
     * @param did The DID to deactivate
     * @param options Optional deactivation options
     * @return Success or error
     */
    suspend fun deactivate(
        did: String,
        options: DidDeactivateOptions = DidDeactivateOptions(),
    ): IdkResult<Unit, IdkError>

    /**
     * Deletes a DID from local storage.
     *
     * Note: This only removes the local record. The DID may still be
     * resolvable if it exists externally (e.g., did:web on a domain).
     *
     * @param did The DID to delete
     * @return Success or error
     */
    suspend fun delete(did: String): IdkResult<Unit, IdkError>

    // =====================
    // Key Management
    // =====================

    /**
     * Adds a verification method to a DID.
     *
     * Delegates to the provider if the method supports key addition.
     *
     * @param did The DID to add the key to
     * @param config Configuration for the verification method
     * @return The updated ManagedDid
     */
    suspend fun addVerificationMethod(
        did: String,
        config: VerificationMethodConfig,
    ): IdkResult<ManagedDid, IdkError>

    /**
     * Removes a verification method from a DID.
     *
     * Delegates to the provider if the method supports key removal.
     *
     * @param did The DID to remove the key from
     * @param verificationMethodId The verification method ID (fragment) to remove
     * @return The updated ManagedDid
     */
    suspend fun removeVerificationMethod(
        did: String,
        verificationMethodId: String,
    ): IdkResult<ManagedDid, IdkError>

    // =====================
    // Service Management
    // =====================

    /**
     * Adds a service to a DID.
     *
     * Delegates to the provider if the method supports service addition.
     *
     * @param did The DID to add the service to
     * @param service The service to add
     * @return The updated ManagedDid
     */
    suspend fun addService(
        did: String,
        service: DidService,
    ): IdkResult<ManagedDid, IdkError>

    /**
     * Removes a service from a DID.
     *
     * Delegates to the provider if the method supports service removal.
     *
     * @param did The DID to remove the service from
     * @param serviceId The service ID (fragment) to remove
     * @return The updated ManagedDid
     */
    suspend fun removeService(
        did: String,
        serviceId: String,
    ): IdkResult<ManagedDid, IdkError>

    // =====================
    // Import/Export
    // =====================

    /**
     * Imports an external DID (not created by this system).
     *
     * Used to track DIDs received from other parties.
     *
     * @param did The DID to import
     * @param alias Optional alias for the DID
     * @return The imported ManagedDid
     */
    suspend fun import(
        did: String,
        alias: String? = null,
    ): IdkResult<ManagedDid, IdkError>
}

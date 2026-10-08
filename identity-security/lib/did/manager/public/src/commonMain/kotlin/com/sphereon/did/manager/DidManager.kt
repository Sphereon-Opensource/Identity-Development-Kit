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
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.models.DidDocument
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
@JsExportCompat
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
     * Returns the total number of managed DIDs matching [filter] without applying pagination
     * (i.e. ignores `filter.page` and `filter.size`). Use this together with [list] to drive
     * paged UIs.
     */
    suspend fun count(filter: DidFilter? = null): IdkResult<Long, IdkError>

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
    // External DID tracking
    // =====================

    /**
     * Tracks an externally-managed DID locally.
     *
     * The manager resolves the DID document via the resolver registry and persists an
     * aggregate with role = `EXTERNAL` containing only the DID string (and minimal record
     * metadata). The resolved document is written to the IDK CacheService so subsequent
     * reads avoid re-resolving until the cache entry expires.
     *
     * "External" means the DID is owned and operated elsewhere — this call does not import
     * key material, decompose the document into VM/service rows, or bind anything to the KMS.
     * Use `create(...)` for DIDs whose keys live in this platform's KMS.
     *
     * @param did The DID to track
     * @param alias Optional human-readable alias
     * @return The tracked ManagedDid (with `role = EXTERNAL`)
     */
    suspend fun trackExternal(
        did: String,
        alias: String? = null,
    ): IdkResult<ManagedDid, IdkError>

    /**
     * Forces a re-resolution of an EXTERNAL DID and refreshes the cached document.
     *
     * Invalidates any cached entry for [did], calls the resolver registry, stores the freshly
     * resolved document in the IDK CacheService, and returns it. Errors when [did] is not
     * known locally or has a non-EXTERNAL role.
     */
    suspend fun refreshExternalDocument(did: String): IdkResult<DidDocument, IdkError>

    // =====================
    // Verification Method Replacement
    // =====================

    /**
     * Replaces a verification method on a DID. Enforces the
     * `keyManagement.replacement` capability — immutable methods reject with
     * `UNSUPPORTED_OPERATION`.
     */
    suspend fun updateVerificationMethod(
        did: String,
        verificationMethodId: String,
        config: VerificationMethodConfig,
    ): IdkResult<ManagedDid, IdkError>

    /**
     * Partial-update for a verification method (JSON Merge Patch semantics): each field on
     * [patch] is applied only when it is [PatchValue.Set]; [PatchValue.Unchanged] leaves the
     * existing value alone. Enforces the `keyManagement.replacement` capability — immutable
     * methods reject with `UNSUPPORTED_OPERATION` before any merge runs.
     *
     * Returns the post-patch aggregate. Callers needing the projected VM row pick it from
     * `result.verificationMethods.first { it.id == verificationMethodId }`.
     */
    suspend fun patchVerificationMethod(
        did: String,
        verificationMethodId: String,
        patch: VerificationMethodPatch,
    ): IdkResult<ManagedDid, IdkError>

    // =====================
    // Service Replacement
    // =====================

    /**
     * Replaces a service entry on a DID. Enforces the
     * `serviceManagement.replacement` capability.
     */
    suspend fun updateService(
        did: String,
        serviceId: String,
        service: DidService,
    ): IdkResult<ManagedDid, IdkError>

    /**
     * Partial-update for a service entry. Only fields in [patch] that are [PatchValue.Set] are
     * applied. Enforces the `serviceManagement.replacement` capability.
     */
    suspend fun patchService(
        did: String,
        serviceId: String,
        patch: ServicePatch,
    ): IdkResult<DidService, IdkError>

    // =====================
    // Aggregate Replace
    // =====================

    /**
     * Declarative replace-all on the aggregate metadata (alias, controllers, alsoKnownAs,
     * equivalentIds, contexts, canonicalId, deactivated flag, services, relationships).
     * Verification methods and key mappings are intentionally **not** replaced through this
     * call — those must be mutated via their dedicated manager methods to keep KMS-lifecycle
     * invariants intact.
     *
     * Enforces the method's update capability (`canUpdate()`).
     */
    suspend fun replaceDidAggregate(
        did: String,
        replacement: DidAggregateReplacement,
    ): IdkResult<ManagedDid, IdkError>

    // =====================
    // Key Mappings
    // =====================

    /**
     * Lists KMS key-mapping rows for [did]. Read-only; no capability gate.
     */
    suspend fun listKeyMappings(did: String): IdkResult<List<DidKeyMapping>, IdkError>

    /**
     * Adds a KMS key-mapping for a verification method on [did]. Validates VM membership and
     * — when the keyref-store is available — keyref-store presence. Key mappings are not
     * DID-method-capability-gated (they describe KMS bindings, not DID-document state).
     */
    suspend fun addKeyMapping(
        did: String,
        input: AddKeyMappingInput,
    ): IdkResult<DidKeyMapping, IdkError>

    /**
     * Removes a KMS key-mapping by its row id.
     */
    suspend fun removeKeyMapping(
        did: String,
        mappingId: String,
    ): IdkResult<Unit, IdkError>

    // =====================
    // Verification Relationships
    // =====================

    /**
     * Lists verification relationships for a DID, optionally filtered by purpose.
     */
    suspend fun listVerificationRelationships(
        did: String,
        purpose: String? = null,
    ): IdkResult<List<VerificationRelationship>, IdkError>

    /**
     * Adds a verification relationship linking a verification method to a purpose.
     *
     * Defaults to the *referenced* form (a `did:…#fragment` link); the VM itself remains
     * independent and can carry multiple relationships. Setting [embed] to `true` creates the
     * relationship in *embedded* form — the row points at the local VM UUID via
     * `entryEmbeddedVmId` so the document serializes the relationship as a full inline VM
     * rather than a URL reference.
     */
    suspend fun addVerificationRelationship(
        did: String,
        verificationMethodId: String,
        purpose: String,
        embed: Boolean = false,
    ): IdkResult<VerificationRelationship, IdkError>

    /**
     * Removes a verification relationship by its identifier.
     */
    suspend fun removeVerificationRelationship(
        did: String,
        relationshipId: String,
    ): IdkResult<Unit, IdkError>

    // =====================
    // Document Cache
    // =====================

    /**
     * Returns the cached resolved document for [did] if a fresh entry exists, otherwise
     * `null`. Does **not** trigger a fresh resolution — use [resolveAndCache] for that.
     */
    suspend fun getCachedDocument(did: String): IdkResult<DidDocument?, IdkError>

    /**
     * Resolves [did] (managed or external) and stores the result in the document cache,
     * returning the resolved document. Existing cache entries are overwritten.
     */
    suspend fun resolveAndCache(did: String): IdkResult<DidDocument, IdkError>

    /**
     * Marks the cache entry for [did] as stale. Subsequent reads will trigger re-resolution.
     */
    suspend fun invalidateCache(did: String): IdkResult<Unit, IdkError>

    // =====================
    // Capability Queries
    // =====================

    /**
     * Returns a simplified capability summary for [method], or `null` if the method is not
     * registered.
     */
    fun getMethodCapabilitySummary(method: String): MethodCapabilitySummary?

    /**
     * Returns full capability detail for every registered DID method.
     */
    fun listSupportedMethodsWithCapabilities(): List<DidMethodCapabilities>
}

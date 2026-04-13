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

package com.sphereon.did.resolver

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.did.capabilities.DidMethodCapabilities

/**
 * Resolves DIDs to DID Documents.
 *
 * This is the core interface for DID resolution. Implementations are provided
 * per DID method (e.g., did:key, did:web, did:jwk).
 *
 * Note: No `I` prefix - implementations are suffixed with `Impl` (e.g., `KeyDidResolverImpl`).
 *
 * @see <a href="https://www.w3.org/TR/did-core/#resolution">W3C DID Resolution</a>
 */
interface DidResolver {
    /**
     * The DID methods supported by this resolver.
     *
     * For single-method resolvers, this is typically a list with one element.
     * For aggregate resolvers, this may include multiple methods.
     */
    val supportedMethods: List<String>

    /**
     * The capabilities of the primary DID method this resolver handles.
     *
     * For aggregate resolvers, this returns capabilities for the first supported method.
     */
    val capabilities: DidMethodCapabilities

    /**
     * Resolves a DID to its DID Document.
     *
     * @param did The DID to resolve (e.g., "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
     * @param options Resolution options (caching, filtering, etc.)
     * @return The resolution result containing the DID Document and metadata
     */
    suspend fun resolve(
        did: String,
        options: DidResolutionOptions = DidResolutionOptions(),
    ): IdkResult<DidResolutionResult, IdkError>

    /**
     * Dereferences a DID URL to a specific resource (key, service, etc.).
     *
     * Supports:
     * - Kid resolution: `did:example:123#key-1`
     * - Service resolution: `did:example:123?service=hub`
     * - Full document: `did:example:123`
     *
     * @param didUrl The DID URL to dereference
     * @param options Dereference options
     * @return The dereference result containing the specific resource
     */
    suspend fun dereference(
        didUrl: String,
        options: DidDereferenceOptions = DidDereferenceOptions(),
    ): IdkResult<DidDereferenceResult, IdkError>

    /**
     * Checks if this resolver supports the given DID method.
     *
     * @param method The DID method to check (e.g., "key", "web")
     * @return true if this resolver can handle the method
     */
    fun supportsMethod(method: String): Boolean = supportedMethods.contains(method)
}

/**
 * Registry for DID resolvers.
 *
 * Manages resolver instances for different DID methods.
 * This is the main entry point for DID resolution when multiple
 * methods need to be supported.
 *
 * Resolvers are automatically discovered via DI multibinding.
 * Any class annotated with `@ContributesIntoSet(SessionScope::class, binding = binding<DidResolver>())`
 * will be automatically registered.
 */
interface DidResolverRegistry {
    /**
     * Gets the resolver for a specific DID method.
     *
     * @param method The DID method (e.g., "key", "web")
     * @return The resolver, or null if no resolver is registered for this method
     */
    fun getResolver(method: String): DidResolver?

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

    /**
     * Resolves a DID using the appropriate registered resolver.
     *
     * @param did The DID to resolve
     * @param options Resolution options
     * @return The resolution result
     */
    suspend fun resolve(
        did: String,
        options: DidResolutionOptions = DidResolutionOptions(),
    ): IdkResult<DidResolutionResult, IdkError>

    /**
     * Dereferences a DID URL using the appropriate registered resolver.
     *
     * @param didUrl The DID URL to dereference
     * @param options Dereference options
     * @return The dereference result
     */
    suspend fun dereference(
        didUrl: String,
        options: DidDereferenceOptions = DidDereferenceOptions(),
    ): IdkResult<DidDereferenceResult, IdkError>

    /**
     * Checks if a resolver is registered for the given method.
     *
     * @param method The DID method to check
     * @return true if a resolver is registered for this method
     */
    fun hasResolver(method: String): Boolean
}

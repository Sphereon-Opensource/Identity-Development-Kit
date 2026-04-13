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
 */

package com.sphereon.oauth2.server.authorization.storage

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration

/**
 * Client registry abstraction for managing OAuth 2.0 client registrations
 *
 * This registry stores information about registered OAuth 2.0 clients including:
 * - Client credentials (client_id, client_secret)
 * - Allowed grant types and response types
 * - Redirect URIs
 * - Authentication methods
 * - Token lifetimes
 * - Security settings (PKCE, PAR, DPoP)
 *
 * Implementation can be:
 * - Static configuration (JSON/YAML files)
 * - Database (SQL/NoSQL)
 * - External service (IAM system)
 * - Combination (static + dynamic registration)
 *
 * Thread Safety: Implementations MUST be thread-safe for concurrent reads.
 * Writes may be less frequent and can use appropriate locking.
 */
interface ClientRegistry {

    /**
     * Get client registration by client_id
     *
     * This is the most frequently called method and should be optimized for reads.
     * Consider caching client registrations in production.
     *
     * @param clientId Unique client identifier
     * @return Client registration if found, null if not found, or storage error
     */
    suspend fun getClient(
        clientId: String
    ): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError>

    /**
     * Register a new client
     *
     * RFC 7591: Dynamic Client Registration Protocol
     *
     * Generates a new client_id and optionally a client_secret.
     * Returns the complete client registration including generated credentials.
     *
     * @param registration Client registration data (clientId may be auto-generated)
     * @return Registered client with generated credentials, or storage error
     */
    suspend fun registerClient(
        registration: ClientRegistration
    ): IdkResult<ClientRegistration, AuthorizationServerError.StorageError>

    /**
     * Update an existing client registration
     *
     * RFC 7592: Dynamic Client Registration Management Protocol
     *
     * Only updates the fields present in the registration parameter.
     * The client_id cannot be changed.
     *
     * @param clientId The client identifier to update
     * @param registration Updated client registration data
     * @return Updated client registration, or error if not found
     */
    suspend fun updateClient(
        clientId: String,
        registration: ClientRegistration
    ): IdkResult<ClientRegistration, AuthorizationServerError>

    /**
     * Delete a client registration
     *
     * RFC 7592: Dynamic Client Registration Management Protocol
     *
     * Removes the client from the registry.
     * Consider revoking all tokens for this client before deletion.
     *
     * @param clientId The client identifier to delete
     * @return Success or error if not found
     */
    suspend fun deleteClient(
        clientId: String
    ): IdkResult<Unit, AuthorizationServerError>

    /**
     * List all registered clients
     *
     * Useful for:
     * - Administration interfaces
     * - Auditing
     * - Monitoring
     *
     * Consider pagination for large registries.
     *
     * @param limit Maximum number of clients to return
     * @param offset Offset for pagination
     * @return List of client registrations, or storage error
     */
    suspend fun listClients(
        limit: Int = 100,
        offset: Int = 0
    ): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError>

    /**
     * Find clients by name (case-insensitive search)
     *
     * Useful for administration interfaces.
     *
     * @param name Client name or partial name
     * @return List of matching clients, or storage error
     */
    suspend fun findClientsByName(
        name: String
    ): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError>

    /**
     * Check if a client exists
     *
     * Lightweight check without retrieving full registration data.
     *
     * @param clientId Client identifier
     * @return true if client exists, false otherwise, or storage error
     */
    suspend fun clientExists(
        clientId: String
    ): IdkResult<Boolean, AuthorizationServerError.StorageError>

    /**
     * Verify client credentials
     *
     * Validates that the provided client_secret matches the stored secret.
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param clientId Client identifier
     * @param clientSecret Client secret to verify
     * @return true if credentials are valid, false otherwise, or storage error
     */
    suspend fun verifyClientCredentials(
        clientId: String,
        clientSecret: String
    ): IdkResult<Boolean, AuthorizationServerError.StorageError>
}

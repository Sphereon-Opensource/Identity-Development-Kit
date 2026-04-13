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
 */

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * In-memory implementation of ClientRegistry
 *
 * IMPORTANT: This is suitable for development/testing only.
 * Production deployments should use persistent storage (SQL, etc.)
 *
 * Thread Safety: Basic implementation - consider locking for production use.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ClientRegistry>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("InMemoryClientRegistryImpl", exact = true)
class InMemoryClientRegistryImpl(
    private val backingStorage: InMemoryOAuth2BackingStorage,
) : ClientRegistry {
    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)

    override suspend fun getClient(clientId: String): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError> =
        try {
            Ok(partition.clients[clientId])
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "getClient",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun registerClient(registration: ClientRegistration): IdkResult<ClientRegistration, AuthorizationServerError.StorageError> {
        return try {
            if (partition.clients.containsKey(registration.clientId)) {
                return Err(
                    AuthorizationServerError.StorageError(
                        operation = "registerClient",
                        details = "Client with ID ${registration.clientId} already exists",
                        exception = null,
                    ),
                )
            }
            partition.clients[registration.clientId] = registration
            Ok(registration)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "registerClient",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }
    }

    override suspend fun updateClient(
        clientId: String,
        registration: ClientRegistration,
    ): IdkResult<ClientRegistration, AuthorizationServerError> {
        return try {
            if (clientId !in partition.clients) {
                return Err(AuthorizationServerError.ClientNotFound(clientId = clientId))
            }

            partition.clients[clientId] = registration
            Ok(registration)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "updateClient",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }
    }

    override suspend fun deleteClient(clientId: String): IdkResult<Unit, AuthorizationServerError> =
        try {
            partition.clients.remove(clientId)
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "deleteClient",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun listClients(
        limit: Int,
        offset: Int,
    ): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> =
        try {
            val clients =
                partition.clients.values
                    .drop(offset)
                    .take(limit)
                    .toList()
            Ok(clients)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "listClients",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun findClientsByName(name: String): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> =
        try {
            val clients =
                partition.clients.values.filter { client ->
                    client.clientName?.contains(name, ignoreCase = true) == true
                }
            Ok(clients)
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "findClientsByName",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun clientExists(clientId: String): IdkResult<Boolean, AuthorizationServerError.StorageError> =
        try {
            Ok(partition.clients.containsKey(clientId))
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "clientExists",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    override suspend fun verifyClientCredentials(
        clientId: String,
        clientSecret: String,
    ): IdkResult<Boolean, AuthorizationServerError.StorageError> =
        try {
            val client = partition.clients[clientId]
            if (client == null) {
                Ok(false)
            } else {
                // Simple string comparison for in-memory implementation
                // Production implementations should use secure password hashing (bcrypt, argon2, etc.)
                val isValid = client.clientSecret == clientSecret
                Ok(isValid)
            }
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.StorageError(
                    operation = "verifyClientCredentials",
                    details = expected.message ?: "Unknown error",
                    exception = expected,
                ),
            )
        }

    @ContributesTo(AppScope::class)
    interface Graph {
        val clientRegistry: ClientRegistry
    }
}

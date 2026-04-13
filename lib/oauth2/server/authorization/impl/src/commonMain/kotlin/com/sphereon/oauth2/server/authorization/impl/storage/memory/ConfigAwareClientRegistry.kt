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
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2ClientsConfigBinder
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Session-scoped client registry that overlays principal-resolved static client configuration
 * on top of the shared in-memory registry used by tests and dev flows.
 *
 * Configured clients are available for the current session context, while programmatically
 * registered clients remain backed by [InMemoryOAuth2BackingStorage].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ClientRegistry>())
class ConfigAwareClientRegistry(
    private val backingStorage: InMemoryOAuth2BackingStorage,
    private val configBinder: OAuth2ClientsConfigBinder,
) : ClientRegistry {
    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)
    private val configuredClients by lazy { configBinder.loadClientRegistrations() }

    override suspend fun getClient(clientId: String): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError> = withMergedClients { clients -> Ok(clients[clientId]) }

    override suspend fun registerClient(registration: ClientRegistration): IdkResult<ClientRegistration, AuthorizationServerError.StorageError> {
        val configured = configuredClients
        return if (configured.isOk) {
            val loadedConfiguredClients = configured.value
            if (loadedConfiguredClients.containsKey(registration.clientId) || partition.clients.containsKey(registration.clientId)) {
                Err(
                    AuthorizationServerError.StorageError(
                        operation = "registerClient",
                        details = "Client with ID ${registration.clientId} already exists",
                    ),
                )
            } else {
                partition.clients[registration.clientId] = registration
                Ok(registration)
            }
        } else {
            Err(configured.error)
        }
    }

    override suspend fun updateClient(
        clientId: String,
        registration: ClientRegistration,
    ): IdkResult<ClientRegistration, AuthorizationServerError> =
        withConfiguredClients { configured ->
            when {
                configured.containsKey(clientId) -> {
                    Err(
                        AuthorizationServerError.StorageError(
                            operation = "updateClient",
                            details = "Configured client '$clientId' cannot be updated at runtime",
                        ),
                    )
                }

                partition.clients.containsKey(clientId) -> {
                    partition.clients[clientId] = registration
                    Ok(registration)
                }

                else -> {
                    Err(AuthorizationServerError.ClientNotFound(clientId = clientId))
                }
            }
        }

    override suspend fun deleteClient(clientId: String): IdkResult<Unit, AuthorizationServerError> =
        withConfiguredClients { configured ->
            when {
                configured.containsKey(clientId) -> {
                    Err(
                        AuthorizationServerError.StorageError(
                            operation = "deleteClient",
                            details = "Configured client '$clientId' cannot be deleted at runtime",
                        ),
                    )
                }

                partition.clients.remove(clientId) != null -> {
                    Ok(Unit)
                }

                else -> {
                    Err(AuthorizationServerError.ClientNotFound(clientId = clientId))
                }
            }
        }

    override suspend fun listClients(
        limit: Int,
        offset: Int,
    ): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> =
        withMergedClients { clients ->
            Ok(
                clients.values
                    .drop(offset)
                    .take(limit)
                    .toList(),
            )
        }

    override suspend fun findClientsByName(name: String): IdkResult<List<ClientRegistration>, AuthorizationServerError.StorageError> =
        withMergedClients { clients ->
            Ok(
                clients.values.filter { client ->
                    client.clientName?.contains(name, ignoreCase = true) == true
                },
            )
        }

    override suspend fun clientExists(clientId: String): IdkResult<Boolean, AuthorizationServerError.StorageError> = withMergedClients { clients -> Ok(clients.containsKey(clientId)) }

    override suspend fun verifyClientCredentials(
        clientId: String,
        clientSecret: String,
    ): IdkResult<Boolean, AuthorizationServerError.StorageError> =
        withMergedClients { clients ->
            val expectedSecret = clients[clientId]?.clientSecret
            Ok(expectedSecret != null && constantTimeEquals(expectedSecret, clientSecret))
        }

    private inline fun <T> withConfiguredClients(action: (Map<String, ClientRegistration>) -> IdkResult<T, AuthorizationServerError>): IdkResult<T, AuthorizationServerError> {
        val configured = configuredClients
        return if (configured.isOk) {
            action(configured.value)
        } else {
            Err(configured.error)
        }
    }

    private inline fun <T> withMergedClients(action: (Map<String, ClientRegistration>) -> IdkResult<T, AuthorizationServerError.StorageError>): IdkResult<T, AuthorizationServerError.StorageError> {
        val configured = configuredClients
        return if (configured.isOk) {
            action(configured.value + partition.clients)
        } else {
            Err(configured.error)
        }
    }

    private fun constantTimeEquals(
        expected: String,
        actual: String,
    ): Boolean {
        var diff = expected.length xor actual.length
        val maxLength = maxOf(expected.length, actual.length)
        for (index in 0 until maxLength) {
            val expectedChar =
                if (index < expected.length) {
                    expected[index].code
                } else {
                    0
                }
            val actualChar =
                if (index < actual.length) {
                    actual[index].code
                } else {
                    0
                }
            diff = diff or (expectedChar xor actualChar)
        }
        return diff == 0
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val clientRegistry: ClientRegistry
    }
}

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
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.configContentRevision
import com.sphereon.core.api.security.ConstantTime
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2ClientsConfigBinder
import com.sphereon.oauth2.server.authorization.impl.config.OpaqueInternalClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

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
    private val execution: SessionExecution,
    private val backingStorage: InMemoryOAuth2BackingStorage,
    private val configBinder: OAuth2ClientsConfigBinder,
    private val asInstanceIdProvider: OAuth2ServerInstanceIdProvider,
    private val serversConfigProvider: OAuth2ServersConfigProvider,
    private val opaqueInternalClientSecretVerifier: OpaqueInternalClientSecretVerifier,
    private val configuredClientSetMemoizer: ConfiguredClientSetMemoizer = ConfiguredClientSetMemoizer(),
) : ClientRegistry {
    private val partitionKey = OAuth2StoragePartitionKey.appLevel()
    private val partition get() = backingStorage.getPartition(partitionKey)

    /**
     * Configured clients merged from two sources:
     *
     *  - `oauth2.clients.<id>.*` — the global public client registry consumed by the
     *    authorization code / device / pre-authorized-code flows.
     *  - `oauth2.servers.<asId>.clients.<id>.*` — active-AS-scoped public clients, overlaid
     *    on top of the global registry when an AS instance is resolved.
     *  - `oauth2.servers.<asId>.internal-clients.<role>.*` — server-to-server resource-server
     *    clients (e.g. the OID4VCI / OID4VP / introspection callers). They authenticate via
     *    `client_secret_basic` and are recognized as resource servers by the introspection
     *    command, which lifts the §2.2 confused-deputy ownership check for them.
     *
     * Keeping both lists in the same registry means client authentication code paths don't have
     * to special-case internal callers — they look up the clientId here just like any other
     * client. Internal IDs colliding with regular `oauth2.clients` IDs are rejected loudly to
     * surface operator misconfiguration.
     */
    private suspend fun configuredClientSet(): IdkResult<ConfiguredClientSet, AuthorizationServerError.StorageError> {
        val key = resolutionKey()
        return configuredClientSetMemoizer.getOrResolve(key, ::resolveConfiguredClientSet)
    }

    /**
     * Identity of the configuration this registry last resolved against: the active AS instance
     * plus the revision of every property source that can feed a client registration.
     *
     * A registry instance is `@SingleIn(SessionScope::class)` and a session is created per
     * inbound request (the transport derives its id from the request's trace id). The memoizer
     * is scoped to that request, so a newly published or revoked client is never hidden behind
     * process-wide registration metadata. It still avoids re-running three config binds for
     * repeated lookups within the same request. It contains registration metadata only; the
     * opaque-client verifier still resolves and compares the protected credential per request.
     *
     * Keying on the configuration revision keeps the re-read that tenant provisioning depends
     * on: a client published mid-request, or a secret id rotated mid-request, moves the revision
     * and the next lookup rebinds. Opaque internal client secrets are not affected either way —
     * only the credential locator is carried here, and the verifier resolves the secret itself on
     * every verification.
     */
    private fun resolutionKey(): ResolutionKey =
        ResolutionKey(
            tenantId = execution.sessionContext.context.tenant.tenantId,
            asInstanceId = asInstanceIdProvider.currentAsInstanceId(),
            configRevision = execution.conf.conf(ConfigLevel.PRINCIPAL).configContentRevision(),
        )

    private suspend fun resolveConfiguredClientSet(): IdkResult<ConfiguredClientSet, AuthorizationServerError.StorageError> {
        val started = TimeSource.Monotonic.markNow()
        val serversConfig = serversConfigProvider.getConfig()
        val activeServerId = activeServerId(serversConfig)
        val globalClients = configBinder.loadClientRegistrations()
        if (globalClients.isErr) return Err(globalClients.error)
        val serverClients = configBinder.loadClientRegistrations(activeServerId)
        if (serverClients.isErr) return Err(serverClients.error)
        val opaqueInternalClients = configBinder.loadOpaqueInternalClientRegistrations(activeServerId)
        if (opaqueInternalClients.isErr) return Err(opaqueInternalClients.error)
        val merged = LinkedHashMap(globalClients.value)
        for ((clientId, registration) in serverClients.value) {
            merged[clientId] = registration
        }
        val typedInternalClients = loadInternalClientRegistrations(serversConfig)
        for ((clientId, registration) in typedInternalClients) {
            require(!merged.containsKey(clientId)) {
                "Internal client id '$clientId' collides with an `oauth2.clients` registration"
            }
            merged[clientId] = registration
        }
        for ((clientId, configured) in opaqueInternalClients.value) {
            require(!merged.containsKey(clientId)) {
                "Opaque internal client id '$clientId' collides with another OAuth2 client registration"
            }
            merged[clientId] = configured.registration
        }
        execution.log.debug(
            "OAuth2ClientRegistry resolved request state in ${started.elapsedNow().inWholeMilliseconds}ms " +
                "(global=${globalClients.value.size}, server=${serverClients.value.size}, " +
                "typedInternal=${typedInternalClients.size}, opaqueInternal=${opaqueInternalClients.value.size})",
        )
        return Ok(
            ConfiguredClientSet(
                registrations = merged,
                opaqueInternalClients = opaqueInternalClients.value,
            ),
        )
    }

    private suspend fun configuredClients(): IdkResult<Map<String, ClientRegistration>, AuthorizationServerError.StorageError> {
        val configured = configuredClientSet()
        return if (configured.isOk) Ok(configured.value.registrations) else Err(configured.error)
    }

    private fun activeServerId(serversConfig: OAuth2ServersConfig): String =
        asInstanceIdProvider.currentAsInstanceId() ?: serversConfig.defaultServer

    private fun loadInternalClientRegistrations(serversConfig: OAuth2ServersConfig): Map<String, ClientRegistration> {
        val result = linkedMapOf<String, ClientRegistration>()
        for ((_, server) in serversConfig.servers) {
            for ((_, credentials) in server.internalClients) {
                val clientId = credentials.clientId
                if (clientId.isBlank()) continue
                result[clientId] =
                    ClientRegistration(
                        clientId = clientId,
                        clientSecret = credentials.clientSecret,
                        clientType = ClientType.CONFIDENTIAL,
                        grantTypes = credentials.grantTypes.toList(),
                        defaultAccessTokenAudience = credentials.defaultAccessTokenAudience,
                        allowedAccessTokenAudiences = credentials.allowedAccessTokenAudiences,
                        tokenEndpointAuthMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                        additionalMetadata = credentials.tenantId?.let { mapOf(TENANT_ID_CLAIM to it) }.orEmpty(),
                    )
            }
        }
        return result
    }

    internal suspend fun resolveClientRegistryRequestView(): IdkResult<ClientRegistry, AuthorizationServerError.StorageError> {
        val configured = configuredClientSet()
        if (configured.isErr) return Err(configured.error)
        return Ok(
            ConfigAwareClientRegistryRequestView(
                owner = this,
                clients = configured.value.registrations + partition.clients,
                opaqueInternalClients = configured.value.opaqueInternalClients,
                opaqueInternalClientSecretVerifier = opaqueInternalClientSecretVerifier,
            ),
        )
    }

    override suspend fun getClient(clientId: String): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError> {
        val view = resolveClientRegistryRequestView()
        return if (view.isOk) view.value.getClient(clientId) else Err(view.error)
    }

    override suspend fun registerClient(registration: ClientRegistration): IdkResult<ClientRegistration, AuthorizationServerError.StorageError> {
        val configured = configuredClients()
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
    ): IdkResult<Boolean, AuthorizationServerError.StorageError> {
        val view = resolveClientRegistryRequestView()
        return if (view.isOk) {
            view.value.verifyClientCredentials(clientId, clientSecret)
        } else {
            Err(view.error)
        }
    }

    private suspend inline fun <T> withConfiguredClients(action: (Map<String, ClientRegistration>) -> IdkResult<T, AuthorizationServerError>): IdkResult<T, AuthorizationServerError> {
        val configured = configuredClients()
        return if (configured.isOk) {
            action(configured.value)
        } else {
            Err(configured.error)
        }
    }

    private suspend inline fun <T> withMergedClients(action: (Map<String, ClientRegistration>) -> IdkResult<T, AuthorizationServerError.StorageError>): IdkResult<T, AuthorizationServerError.StorageError> {
        val configured = configuredClients()
        return if (configured.isOk) {
            action(configured.value + partition.clients)
        } else {
            Err(configured.error)
        }
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val clientRegistry: ClientRegistry
    }

    private companion object {
        const val TENANT_ID_CLAIM = "tenant_id"
    }
}

internal data class ResolutionKey(
    val tenantId: String,
    val asInstanceId: String?,
    val configRevision: Long,
)

internal data class ConfiguredClientSet(
    val registrations: Map<String, ClientRegistration>,
    val opaqueInternalClients: Map<String, OpaqueInternalClientRegistration>,
)

/**
 * Request-scoped memoization of revision-addressed OAuth client metadata.
 *
 * The key carries the tenant, active AS, and content revision, so repeated lookups in the same
 * request do not rebind configuration. It deliberately does not survive the request: tenant
 * onboarding and client administration publish dynamic registrations, and an unavailable
 * cross-replica invalidation must not leave an authorization server accepting stale clients or
 * rejecting newly-created ones until a process restart. Failures are not cached.
 */
@Inject
@SingleIn(SessionScope::class)
class ConfiguredClientSetMemoizer {
    private val stateMutex = Mutex()
    private val values = LinkedHashMap<ResolutionKey, ConfiguredClientSet>()
    private val inFlight = mutableMapOf<ResolutionKey, Mutex>()

    internal suspend fun getOrResolve(
        key: ResolutionKey,
        resolve: suspend () -> IdkResult<ConfiguredClientSet, AuthorizationServerError.StorageError>,
    ): IdkResult<ConfiguredClientSet, AuthorizationServerError.StorageError> {
        val keyMutex =
            stateMutex.withLock {
                values[key]?.let { return Ok(it) }
                inFlight.getOrPut(key) { Mutex() }
            }
        return keyMutex.withLock {
            try {
                stateMutex.withLock {
                    values[key]?.let { return@withLock Ok(it) }
                }?.let { return it }

                val resolved = resolve()
                if (resolved.isOk) {
                    stateMutex.withLock { remember(key, resolved.value) }
                }
                resolved
            } finally {
                stateMutex.withLock {
                    if (inFlight[key] === keyMutex) inFlight.remove(key)
                }
            }
        }
    }

    private fun remember(
        key: ResolutionKey,
        value: ConfiguredClientSet,
    ) {
        values[key] = value
        while (values.size > MAX_ENTRIES) {
            values.entries.iterator().run {
                if (hasNext()) {
                    next()
                    remove()
                }
            }
        }
    }

    private companion object {
        const val MAX_ENTRIES = 256
    }
}

private class ConfigAwareClientRegistryRequestView(
    private val owner: ClientRegistry,
    private val clients: Map<String, ClientRegistration>,
    private val opaqueInternalClients: Map<String, OpaqueInternalClientRegistration>,
    private val opaqueInternalClientSecretVerifier: OpaqueInternalClientSecretVerifier,
) : ClientRegistry by owner {
    override suspend fun getClient(clientId: String): IdkResult<ClientRegistration?, AuthorizationServerError.StorageError> = Ok(clients[clientId])

    override suspend fun verifyClientCredentials(
        clientId: String,
        clientSecret: String,
    ): IdkResult<Boolean, AuthorizationServerError.StorageError> {
        opaqueInternalClients[clientId]?.let { configured ->
            return opaqueInternalClientSecretVerifier.verify(configured.credential, clientSecret)
        }
        val expectedSecret = clients[clientId]?.clientSecret
        return Ok(expectedSecret != null && ConstantTime.equalsCT(expectedSecret, clientSecret))
    }
}

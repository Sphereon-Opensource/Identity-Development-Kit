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

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.core.api.conf.PrincipalConfigService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-local cache for parsed OAuth client configuration metadata.
 *
 * Principal-owned clients are keyed by every dimension used to construct their PRINCIPAL config
 * view: tenant, principal, principal classification, the exact UserScope config-view identity,
 * active authorization-server instance, content revision, and config partition. Tenant-owned
 * opaque internal clients use the narrower [TenantClientMetadataKey]: their authoritative source
 * is shared by tenant and revision and has no principal input. Values contain only parsed
 * registration metadata and opaque credential locators. Resolved client-secret material and
 * typed bootstrap secrets are deliberately excluded and remain SessionScope.
 *
 * There is no time-based freshness policy. Configuration invalidation changes
 * the applicable key's `configRevision`, making the next request resolve a new immutable
 * snapshot. Failures are never cached.
 */
@Inject
@SingleIn(AppScope::class)
class OAuth2ClientMetadataCache {
    private val stateMutex = Mutex()
    private val values = LinkedHashMap<OAuth2ClientMetadataKey, ClientMetadataSnapshot>()
    private val inFlight = mutableMapOf<OAuth2ClientMetadataKey, Mutex>()

    internal suspend fun configuredClients(
        key: ClientMetadataKey,
        resolve: () -> Map<String, ConfiguredOAuth2Client>,
    ): Map<String, ConfiguredOAuth2Client> =
        (getOrResolve(key) { ConfiguredClientsSnapshot(resolve().toMap()) } as ConfiguredClientsSnapshot).clients

    internal suspend fun opaqueInternalClients(
        key: TenantClientMetadataKey,
        resolve: () -> Map<String, OpaqueInternalClientRegistration>,
    ): Map<String, OpaqueInternalClientRegistration> =
        (getOrResolve(key) { OpaqueInternalClientsSnapshot(resolve().toMap()) } as OpaqueInternalClientsSnapshot).clients

    private suspend fun getOrResolve(
        key: OAuth2ClientMetadataKey,
        resolve: () -> ClientMetadataSnapshot,
    ): ClientMetadataSnapshot {
        val keyMutex =
            stateMutex.withLock {
                values[key]?.let { return it }
                inFlight.getOrPut(key) { Mutex() }
            }
        return keyMutex.withLock {
            try {
                stateMutex.withLock { values[key] }?.let { return it }
                val resolved = resolve()
                stateMutex.withLock { remember(key, resolved) }
                resolved
            } finally {
                stateMutex.withLock {
                    if (inFlight[key] === keyMutex) inFlight.remove(key)
                }
            }
        }
    }

    private fun remember(
        key: OAuth2ClientMetadataKey,
        value: ClientMetadataSnapshot,
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
        const val MAX_ENTRIES = 1_024
    }
}

internal sealed interface OAuth2ClientMetadataKey

internal data class ClientMetadataKey(
    val tenantId: String,
    val principalId: String,
    val principalType: String,
    val configViewIdentity: PrincipalConfigViewIdentity,
    val asInstanceId: String,
    val configRevision: Long,
    val configPartition: String,
) : OAuth2ClientMetadataKey

/**
 * Cache address for tenant-owned opaque workload registrations.
 *
 * The tenant configuration source is process-shared and revisioned independently of request
 * UserScopes. Keeping principal identity out of this key is intentional: principal-scoped
 * definitions under this partition are rejected by [OAuth2ClientsConfigBinder], so no
 * principal-dependent value can enter this snapshot.
 */
internal data class TenantClientMetadataKey(
    val tenantId: String,
    val asInstanceId: String,
    val configRevision: Long,
    val configPartition: String,
) : OAuth2ClientMetadataKey

/**
 * Referential identity of the authoritative UserScope config view.
 *
 * Content revisions are monotonic only within one ConfigEnvironment. A destroyed and recreated
 * UserScope may restart its counters, so revision plus principal id is not sufficient for an
 * AppScope key. Referential equality prevents an entry produced by the old view from being used
 * by the replacement view, even when their counters happen to match.
 */
internal class PrincipalConfigViewIdentity(
    private val configService: PrincipalConfigService,
) {
    override fun equals(other: Any?): Boolean =
        other is PrincipalConfigViewIdentity && configService === other.configService

    // Kotlin common has no portable referential identity hash. A constant is intentional:
    // ClientMetadataKey contributes the other high-cardinality dimensions to the map hash, while
    // equality remains exact even for a ConfigService implementation with value-based hashCode.
    override fun hashCode(): Int = 0

    override fun toString(): String = "PrincipalConfigViewIdentity([REDACTED])"
}

private sealed interface ClientMetadataSnapshot

private data class ConfiguredClientsSnapshot(
    val clients: Map<String, ConfiguredOAuth2Client>,
) : ClientMetadataSnapshot

private data class OpaqueInternalClientsSnapshot(
    val clients: Map<String, OpaqueInternalClientRegistration>,
) : ClientMetadataSnapshot

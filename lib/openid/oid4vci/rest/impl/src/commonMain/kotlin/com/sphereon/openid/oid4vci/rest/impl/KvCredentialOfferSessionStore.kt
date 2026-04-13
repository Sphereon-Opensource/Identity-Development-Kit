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

package com.sphereon.openid.oid4vci.rest.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.rest.CredentialOfferSession
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStatus
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStore
import com.sphereon.openid.oid4vci.rest.IssuanceCallbackConfig
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialOfferSessionStore>())
class KvCredentialOfferSessionStore(
    private val kvStoreManager: KvStoreManager,
    private val kvStoreService: KvStoreService,
    private val execution: SessionExecution,
    appLogManager: AppLogManager,
) : CredentialOfferSessionStore {
    private val log = appLogManager.withTag("KvCredentialOfferSessionStore")
    private val json = Json

    private val namespace =
        KvNamespace(
            name = "oid4vci.rest.sessions",
            codec = KotlinxSerializationJsonKvCodec(json = json, serializer = CredentialOfferSessionEntry.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vci.rest.sessions",
            scopeBinding = KvStoreScopeBinding.TENANT,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(resolveEffectiveStoreConfig(), execution)
    }

    private fun resolveEffectiveStoreConfig(): KvStoreConfigBase {
        val configured = runCatching { kvStoreService.getStoreConfig(storeConfig.id) }.getOrNull()
        val effective = configured ?: storeConfig
        require(effective.scopeBinding == storeConfig.scopeBinding) {
            "KV store '${storeConfig.id}' must use scopeBinding=${storeConfig.scopeBinding}, but was ${effective.scopeBinding}"
        }
        return effective
    }

    override suspend fun create(session: CredentialOfferSession): IdkResult<CredentialOfferSession, IdkError> {
        val entry = CredentialOfferSessionEntry.fromPublic(session)
        val expiresAt = session.expiresAt
        val ttl =
            if (expiresAt != null) {
                ((expiresAt - session.createdAt) / 1000).coerceAtLeast(1)
            } else {
                CredentialOfferSessionStore.DEFAULT_TTL_SECONDS
            }

        return kv
            .put(namespace, session.correlationId, entry, ttl.seconds)
            .map {
                session
            }.mapError { e ->
                IdkError.fromString(
                    message = "Failed to create credential offer session: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = "OID4VCI_SESSION_STORE_ERROR",
                )
            }
    }

    override suspend fun get(correlationId: String): IdkResult<CredentialOfferSession?, IdkError> =
        kv
            .get(namespace, correlationId)
            .map { entry ->
                entry?.toPublic()
            }.mapError { e ->
                IdkError.fromString(
                    message = "Failed to read credential offer session: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = "OID4VCI_SESSION_STORE_ERROR",
                )
            }

    override suspend fun update(session: CredentialOfferSession): IdkResult<CredentialOfferSession, IdkError> {
        val entry = CredentialOfferSessionEntry.fromPublic(session)
        val updateExpiresAt = session.expiresAt
        val ttl =
            if (updateExpiresAt != null) {
                val remaining = (updateExpiresAt - Clock.System.now().toEpochMilliseconds()) / 1000
                remaining.coerceAtLeast(1)
            } else {
                CredentialOfferSessionStore.DEFAULT_TTL_SECONDS
            }

        return kv
            .put(namespace, session.correlationId, entry, ttl.seconds)
            .map {
                session
            }.mapError { e ->
                IdkError.fromString(
                    message = "Failed to update credential offer session: ${e.message}",
                    exception = IllegalStateException(e.toString()),
                    code = "OID4VCI_SESSION_STORE_ERROR",
                )
            }
    }

    override suspend fun delete(correlationId: String): IdkResult<Boolean, IdkError> =
        kv.delete(namespace, correlationId).mapError { e ->
            IdkError.fromString(
                message = "Failed to delete credential offer session: ${e.message}",
                exception = IllegalStateException(e.toString()),
                code = "OID4VCI_SESSION_STORE_ERROR",
            )
        }

    @Serializable
    internal data class CredentialOfferSessionEntry(
        val correlationId: String,
        val offerId: String,
        val issuanceSessionId: String? = null,
        val status: String,
        val callbackUrl: String? = null,
        val callbackStatuses: List<String>? = null,
        val callbackIncludeIssuanceData: Boolean = false,
        val state: String? = null,
        val createdAt: Long,
        val lastUpdatedAt: Long,
        val expiresAt: Long? = null,
    ) {
        fun toPublic(): CredentialOfferSession {
            val callbackConfig =
                callbackUrl?.let { url ->
                    IssuanceCallbackConfig(
                        url = url,
                        statuses =
                            callbackStatuses?.mapNotNull { name ->
                                CredentialOfferSessionStatus.entries.firstOrNull { it.name == name }
                            } ?: emptyList(),
                        includeIssuanceData = callbackIncludeIssuanceData,
                    )
                }
            return CredentialOfferSession(
                correlationId = correlationId,
                offerId = offerId,
                issuanceSessionId = issuanceSessionId,
                status =
                    CredentialOfferSessionStatus.entries.firstOrNull { it.name == status }
                        ?: CredentialOfferSessionStatus.ERROR,
                callbackConfig = callbackConfig,
                state = state,
                createdAt = createdAt,
                lastUpdatedAt = lastUpdatedAt,
                expiresAt = expiresAt,
            )
        }

        companion object {
            fun fromPublic(session: CredentialOfferSession): CredentialOfferSessionEntry =
                CredentialOfferSessionEntry(
                    correlationId = session.correlationId,
                    offerId = session.offerId,
                    issuanceSessionId = session.issuanceSessionId,
                    status = session.status.name,
                    callbackUrl = session.callbackConfig?.url,
                    callbackStatuses = session.callbackConfig?.statuses?.map { it.name },
                    callbackIncludeIssuanceData = session.callbackConfig?.includeIssuanceData ?: false,
                    state = session.state,
                    createdAt = session.createdAt,
                    lastUpdatedAt = session.lastUpdatedAt,
                    expiresAt = session.expiresAt,
                )
        }
    }
}

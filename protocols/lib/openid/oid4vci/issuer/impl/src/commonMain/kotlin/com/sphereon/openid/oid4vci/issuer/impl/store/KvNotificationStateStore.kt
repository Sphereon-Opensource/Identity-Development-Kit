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

package com.sphereon.openid.oid4vci.issuer.impl.store

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.KvStoreVersioning
import com.sphereon.data.store.kv.KvVersionAppendResult
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.issuer.store.NotificationReceipt
import com.sphereon.openid.oid4vci.issuer.store.NotificationSessionIdentity
import com.sphereon.openid.oid4vci.issuer.store.NotificationStateStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class NotificationRecord(
    val protocolSessionId: String,
    val instanceId: String,
    val expiresAtEpochSeconds: Long,
    val event: CredentialNotificationEvent? = null,
    val processedAtEpochSeconds: Long? = null,
)

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<NotificationStateStore>())
class KvNotificationStateStore(
    private val kvStoreManager: KvStoreManager,
    private val kvStoreService: KvStoreService,
    private val execution: SessionExecution,
) : NotificationStateStore {
    private val namespace =
        KvNamespace(
            name = "oid4vci.notifications",
            codec = KotlinxSerializationJsonKvCodec(json = Json, serializer = NotificationRecord.serializer()),
        )

    private val storeConfig: KvStoreConfigBase =
        InMemoryKvStoreConfig(
            id = "oid4vci.notifications",
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

    override suspend fun registerNotification(
        notificationId: String,
        protocolSessionId: String,
        instanceId: String,
        ttlSeconds: Long,
    ): IdkResult<Unit, IdkError> {
        require(notificationId.isNotBlank()) { "notificationId must not be blank" }
        require(protocolSessionId.isNotBlank()) { "protocolSessionId must not be blank" }
        require(instanceId.isNotBlank()) { "instanceId must not be blank" }
        require(ttlSeconds > 0) { "ttlSeconds must be positive" }

        val versioning = versioningStore().getOrElse { return Err(it) }
        val existing = versioning.getHead(namespace, notificationId).getOrElse { return Err(it) }
        if (existing != null) {
            return if (existing.value.protocolSessionId == protocolSessionId && existing.value.instanceId == instanceId) {
                Ok(Unit)
            } else {
                Err(notificationBindingConflict())
            }
        }

        val now = Clock.System.now().epochSeconds
        val record =
            NotificationRecord(
                protocolSessionId = protocolSessionId,
                instanceId = instanceId,
                expiresAtEpochSeconds = now + ttlSeconds,
            )
        return when (
            val appended =
                versioning
                    .append(namespace, notificationId, expectedPreviousVersionId = null, value = record, ttl = ttlSeconds.seconds)
                    .getOrElse { return Err(it) }
        ) {
            is KvVersionAppendResult.Applied -> Ok(Unit)
            is KvVersionAppendResult.Conflict -> {
                if (
                    appended.currentHead?.value?.protocolSessionId == protocolSessionId &&
                    appended.currentHead?.value?.instanceId == instanceId
                ) {
                    Ok(Unit)
                } else {
                    Err(notificationBindingConflict())
                }
            }
        }
    }

    override suspend fun getNotificationIdentity(notificationId: String): IdkResult<NotificationSessionIdentity?, IdkError> {
        require(notificationId.isNotBlank()) { "notificationId must not be blank" }
        val versioning = versioningStore().getOrElse { return Err(it) }
        val current = versioning.getHead(namespace, notificationId).getOrElse { return Err(it) }?.value ?: return Ok(null)
        if (current.expiresAtEpochSeconds <= Clock.System.now().epochSeconds) return Ok(null)
        return Ok(
            NotificationSessionIdentity(
                protocolSessionId = current.protocolSessionId,
                instanceId = current.instanceId,
            ),
        )
    }

    override suspend fun recordNotification(
        notificationId: String,
        event: CredentialNotificationEvent,
    ): IdkResult<NotificationReceipt?, IdkError> {
        val versioning = versioningStore().getOrElse { return Err(it) }
        while (true) {
            val head = versioning.getHead(namespace, notificationId).getOrElse { return Err(it) } ?: return Ok(null)
            val current = head.value
            if (current.processedAtEpochSeconds != null) {
                return Ok(
                    NotificationReceipt(
                        protocolSessionId = current.protocolSessionId,
                        instanceId = current.instanceId,
                        firstReceipt = false,
                    ),
                )
            }

            val now = Clock.System.now().epochSeconds
            val remainingTtl = current.expiresAtEpochSeconds - now
            if (remainingTtl <= 0) return Ok(null)
            val recorded = current.copy(event = event, processedAtEpochSeconds = now)
            when (
                versioning
                    .append(namespace, notificationId, expectedPreviousVersionId = head.versionId, value = recorded, ttl = remainingTtl.seconds)
                    .getOrElse { return Err(it) }
            ) {
                is KvVersionAppendResult.Applied ->
                    return Ok(
                        NotificationReceipt(
                            protocolSessionId = current.protocolSessionId,
                            instanceId = current.instanceId,
                            firstReceipt = true,
                        ),
                    )

                is KvVersionAppendResult.Conflict -> Unit
            }
        }
    }

    private fun versioningStore(): IdkResult<KvStoreVersioning, IdkError> =
        (kv as? KvStoreVersioning)?.let(::Ok)
            ?: Err(
                IdkError.fromString(
                    code = "KV_VERSIONING_REQUIRED",
                    message = "Notification state requires an atomic versioned KV backend",
                ),
            )

    private fun notificationBindingConflict(): IdkError =
        IdkError.fromString(
            code = "NOTIFICATION_BINDING_CONFLICT",
            message = "notification_id is already bound to another protocol session",
        )
}

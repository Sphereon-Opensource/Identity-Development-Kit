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
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent
import com.sphereon.openid.oid4vci.issuer.store.NotificationStateStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Serializable
internal data class NotificationRecord(
    val notificationId: String,
    val event: CredentialNotificationEvent,
    val processedAt: Long,
)

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<NotificationStateStore>())
class KvNotificationStateStore(
    private val kvStoreManager: KvStoreManager,
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
            scopeBinding = KvStoreScopeBinding.APP,
        )

    private val kv: KvStore by lazy {
        kvStoreManager.createFromKvStoreConfig(storeConfig, execution)
    }

    override suspend fun recordNotification(
        notificationId: String,
        event: CredentialNotificationEvent,
    ): IdkResult<Unit, IdkError> {
        val record =
            NotificationRecord(
                notificationId = notificationId,
                event = event,
                processedAt = Clock.System.now().epochSeconds,
            )
        kv.put(namespace, notificationId, record, ttl = 24.hours).getOrElse { return Err(it) }
        return Ok(Unit)
    }

    override suspend fun isProcessed(notificationId: String): IdkResult<Boolean, IdkError> {
        val record = kv.get<NotificationRecord>(namespace, notificationId).getOrElse { return Err(it) }
        return Ok(record != null)
    }
}

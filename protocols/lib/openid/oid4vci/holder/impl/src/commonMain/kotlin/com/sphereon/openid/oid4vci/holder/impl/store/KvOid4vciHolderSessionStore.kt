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

package com.sphereon.openid.oid4vci.holder.impl.store

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.kv.impl.KvStoreDelegate
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSession
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciHolderSessionStore>())
class KvOid4vciHolderSessionStore(
    private val kvStoreManager: KvStoreManager,
    private val execution: SessionExecution,
) : Oid4vciHolderSessionStore {
    private val delegate = KvStoreDelegate(kvStoreManager, execution, "oid4vci-holder-sessions", Oid4vciHolderSession.serializer())
    private val kv get() = delegate.kv
    private val namespace get() = delegate.namespace

    override suspend fun create(session: Oid4vciHolderSession): IdkResult<Oid4vciHolderSession, IdkError> {
        val now = Clock.System.now().epochSeconds
        val ttlSeconds = session.expiresAt?.let { (it - now).coerceAtLeast(1) } ?: DEFAULT_TTL_SECONDS
        kv.put(namespace, session.sessionId, session, ttl = ttlSeconds.seconds).getOrElse { return Err(it) }
        return Ok(session)
    }

    override suspend fun get(sessionId: String): IdkResult<Oid4vciHolderSession?, IdkError> = kv.get(namespace, sessionId)

    override suspend fun update(session: Oid4vciHolderSession): IdkResult<Oid4vciHolderSession, IdkError> {
        val remainingSeconds =
            session.expiresAt?.let { max(1L, it - Clock.System.now().epochSeconds) }
                ?: DEFAULT_TTL_SECONDS
        kv.put(namespace, session.sessionId, session, ttl = remainingSeconds.seconds).getOrElse { return Err(it) }
        return Ok(session)
    }

    private companion object {
        const val DEFAULT_TTL_SECONDS = 3600L
    }
}

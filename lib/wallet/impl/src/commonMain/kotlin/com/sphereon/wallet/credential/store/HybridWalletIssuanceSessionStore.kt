/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.credential.store

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.HybridWalletIssuanceSessionStoreDelegate
import com.sphereon.wallet.credential.IssuanceSession
import com.sphereon.wallet.credential.IssuanceSessionStatus
import com.sphereon.wallet.credential.LocalWalletIssuanceSessionStore
import com.sphereon.wallet.credential.RemoteWalletIssuanceSessionStore
import com.sphereon.wallet.credential.SecretRef
import com.sphereon.wallet.credential.WalletDeferredAccessTokenRemoteMirrorOperation
import com.sphereon.wallet.credential.WalletDeferredAccessTokenRemoteMirrorPolicy
import com.sphereon.wallet.credential.WalletDeferredAccessTokenRemoteMirrorRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Hybrid local-first issuance-session store.
 *
 * Local blob storage is the write-through source for immediate/offline resume. The remote vault
 * delegate is updated opportunistically so deferred issuance can be resumed by another authorized
 * device or service when policy allows it.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HybridWalletIssuanceSessionStoreDelegate>())
class HybridWalletIssuanceSessionStore(
    private val localStore: LocalWalletIssuanceSessionStore,
    private val remoteStore: RemoteWalletIssuanceSessionStore,
    private val deferredAccessTokenRemoteMirrorPolicy: WalletDeferredAccessTokenRemoteMirrorPolicy =
        WalletDeferredAccessTokenRemoteMirrorPolicy.deny,
) : HybridWalletIssuanceSessionStoreDelegate {
    override suspend fun putSession(
        walletInstanceId: String,
        session: IssuanceSession,
    ): IdkResult<IssuanceSession, IdkError> {
        val local = localStore.putSession(walletInstanceId, session)
        if (local.isErr) return Err(local.error)
        remoteStore.putSession(walletInstanceId, session)
        return local
    }

    override suspend fun getSession(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<IssuanceSession?, IdkError> {
        val local = localStore.getSession(walletInstanceId, issuanceSessionId)
        if (local.isOk && local.value != null) return local

        val remote = remoteStore.getSession(walletInstanceId, issuanceSessionId)
        if (remote.isErr) {
            return if (local.isErr) Err(local.error) else Err(remote.error)
        }
        val session = remote.value ?: return if (local.isErr) Err(local.error) else Ok(null)
        val cache = localStore.putSession(walletInstanceId, session)
        return if (cache.isOk) Ok(session) else Err(cache.error)
    }

    override suspend fun listSessions(
        walletInstanceId: String,
        statuses: Set<IssuanceSessionStatus>,
    ): IdkResult<List<IssuanceSession>, IdkError> {
        val local = localStore.listSessions(walletInstanceId, statuses)
        val remote = remoteStore.listSessions(walletInstanceId, statuses)
        if (local.isErr && remote.isErr) return Err(local.error)
        if (local.isErr) return remote
        if (remote.isErr) return local

        val merged = linkedMapOf<String, IssuanceSession>()
        for (session in remote.value) merged[session.id] = session
        for (session in local.value) merged[session.id] = session
        return Ok(merged.values.toList())
    }

    override suspend fun storeDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
        accessToken: String,
    ): IdkResult<SecretRef, IdkError> {
        val local = localStore.storeDeferredAccessToken(walletInstanceId, issuanceSessionId, accessToken)
        if (local.isErr) return Err(local.error)
        if (
            canMirrorDeferredAccessToken(
                walletInstanceId,
                issuanceSessionId,
                WalletDeferredAccessTokenRemoteMirrorOperation.STORE_REMOTE_COPY,
            )
        ) {
            remoteStore.storeDeferredAccessToken(walletInstanceId, issuanceSessionId, accessToken)
        }
        return local
    }

    override suspend fun getDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<String?, IdkError> {
        val local = localStore.getDeferredAccessToken(walletInstanceId, issuanceSessionId)
        if (local.isOk && local.value != null) return local
        if (
            !canMirrorDeferredAccessToken(
                walletInstanceId,
                issuanceSessionId,
                WalletDeferredAccessTokenRemoteMirrorOperation.READ_REMOTE_COPY,
            )
        ) {
            return if (local.isErr) Err(local.error) else Ok(null)
        }

        val remote = remoteStore.getDeferredAccessToken(walletInstanceId, issuanceSessionId)
        if (remote.isErr) {
            return if (local.isErr) Err(local.error) else Err(remote.error)
        }
        val token = remote.value ?: return if (local.isErr) Err(local.error) else Ok(null)
        val cache = localStore.storeDeferredAccessToken(walletInstanceId, issuanceSessionId, token)
        return if (cache.isOk) Ok(token) else Err(cache.error)
    }

    override suspend fun deleteSession(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<Boolean, IdkError> {
        val local = localStore.deleteSession(walletInstanceId, issuanceSessionId)
        val remote = remoteStore.deleteSession(walletInstanceId, issuanceSessionId)
        return when {
            local.isOk && remote.isOk -> Ok(local.value || remote.value)
            local.isOk -> local
            remote.isOk -> remote
            else -> Err(local.error)
        }
    }

    private suspend fun canMirrorDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
        operation: WalletDeferredAccessTokenRemoteMirrorOperation,
    ): Boolean =
        deferredAccessTokenRemoteMirrorPolicy.allowRemoteMirror(
            WalletDeferredAccessTokenRemoteMirrorRequest(
                walletInstanceId = walletInstanceId,
                issuanceSessionId = issuanceSessionId,
                operation = operation,
            ),
        )
}

/**
 * Default hybrid secret policy: session metadata may mirror remotely, raw deferred access tokens do not.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletDeferredAccessTokenRemoteMirrorPolicy>())
class DefaultWalletDeferredAccessTokenRemoteMirrorPolicy : WalletDeferredAccessTokenRemoteMirrorPolicy by WalletDeferredAccessTokenRemoteMirrorPolicy.deny

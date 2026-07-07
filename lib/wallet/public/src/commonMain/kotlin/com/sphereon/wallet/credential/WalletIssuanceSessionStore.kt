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

package com.sphereon.wallet.credential

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Policy gate for copying deferred access-token secrets between local blob storage and a remote
 * vault while using hybrid issuance-session storage.
 *
 * The default policy denies remote secret mirroring. Applications that intentionally support
 * remote deferred-issuance resume can inject or construct an allowing policy without changing the
 * issuance-session store API.
 */
interface WalletDeferredAccessTokenRemoteMirrorPolicy {
    suspend fun allowRemoteMirror(request: WalletDeferredAccessTokenRemoteMirrorRequest): Boolean

    companion object {
        val deny: WalletDeferredAccessTokenRemoteMirrorPolicy =
            object : WalletDeferredAccessTokenRemoteMirrorPolicy {
                override suspend fun allowRemoteMirror(request: WalletDeferredAccessTokenRemoteMirrorRequest): Boolean = false
            }

        val allow: WalletDeferredAccessTokenRemoteMirrorPolicy =
            object : WalletDeferredAccessTokenRemoteMirrorPolicy {
                override suspend fun allowRemoteMirror(request: WalletDeferredAccessTokenRemoteMirrorRequest): Boolean = true
            }
    }
}

data class WalletDeferredAccessTokenRemoteMirrorRequest(
    val walletInstanceId: String,
    val issuanceSessionId: String,
    val operation: WalletDeferredAccessTokenRemoteMirrorOperation,
)

enum class WalletDeferredAccessTokenRemoteMirrorOperation {
    STORE_REMOTE_COPY,
    READ_REMOTE_COPY,
}

/**
 * Persistence for resumable wallet-side issuance state.
 *
 * Implementations store [IssuanceSession] metadata separately from any access-token secret so
 * list/get session APIs can resume workflows without exposing credential bodies or token material.
 */
interface WalletIssuanceSessionStore {
    suspend fun putSession(
        walletInstanceId: String,
        session: IssuanceSession,
    ): IdkResult<IssuanceSession, IdkError>

    suspend fun getSession(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<IssuanceSession?, IdkError>

    suspend fun listSessions(
        walletInstanceId: String,
        statuses: Set<IssuanceSessionStatus> = emptySet(),
    ): IdkResult<List<IssuanceSession>, IdkError>

    suspend fun storeDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
        accessToken: String,
    ): IdkResult<SecretRef, IdkError>

    suspend fun getDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<String?, IdkError>

    suspend fun deleteSession(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<Boolean, IdkError>
}

/**
 * Local issuance-session delegate backed by the platform blob abstraction.
 */
interface LocalWalletIssuanceSessionStore : WalletIssuanceSessionStore

/**
 * Remote issuance-session delegate backed by the platform vault abstraction.
 */
interface RemoteWalletIssuanceSessionStore : WalletIssuanceSessionStore

/**
 * Hybrid issuance-session delegate that composes local blob and remote vault delegates.
 */
interface HybridWalletIssuanceSessionStoreDelegate : WalletIssuanceSessionStore

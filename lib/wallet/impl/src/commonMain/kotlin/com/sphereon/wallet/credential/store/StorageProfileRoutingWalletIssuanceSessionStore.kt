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
import com.sphereon.wallet.credential.StorageProfile
import com.sphereon.wallet.credential.WalletIssuanceSessionStore
import com.sphereon.wallet.credential.WalletStorageMode
import com.sphereon.wallet.credential.WalletStorageProfileResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Routes wallet issuance-session operations through the same [StorageProfile] as credentials.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletIssuanceSessionStore>())
class StorageProfileRoutingWalletIssuanceSessionStore(
    private val storageProfileResolver: WalletStorageProfileResolver,
    private val localStore: LocalWalletIssuanceSessionStore,
    private val remoteStore: RemoteWalletIssuanceSessionStore,
    private val hybridStore: HybridWalletIssuanceSessionStoreDelegate,
) : WalletIssuanceSessionStore {
    override suspend fun putSession(
        walletInstanceId: String,
        session: IssuanceSession,
    ): IdkResult<IssuanceSession, IdkError> = delegateFor(walletInstanceId).flatMap { it.putSession(walletInstanceId, session) }

    override suspend fun getSession(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<IssuanceSession?, IdkError> = delegateFor(walletInstanceId).flatMap { it.getSession(walletInstanceId, issuanceSessionId) }

    override suspend fun listSessions(
        walletInstanceId: String,
        statuses: Set<IssuanceSessionStatus>,
    ): IdkResult<List<IssuanceSession>, IdkError> = delegateFor(walletInstanceId).flatMap { it.listSessions(walletInstanceId, statuses) }

    override suspend fun storeDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
        accessToken: String,
    ): IdkResult<SecretRef, IdkError> = delegateFor(walletInstanceId).flatMap { it.storeDeferredAccessToken(walletInstanceId, issuanceSessionId, accessToken) }

    override suspend fun getDeferredAccessToken(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<String?, IdkError> = delegateFor(walletInstanceId).flatMap { it.getDeferredAccessToken(walletInstanceId, issuanceSessionId) }

    override suspend fun deleteSession(
        walletInstanceId: String,
        issuanceSessionId: String,
    ): IdkResult<Boolean, IdkError> = delegateFor(walletInstanceId).flatMap { it.deleteSession(walletInstanceId, issuanceSessionId) }

    private suspend fun delegateFor(walletInstanceId: String): IdkResult<WalletIssuanceSessionStore, IdkError> {
        val profileResult = storageProfileResolver.resolveStorageProfile(walletInstanceId)
        if (profileResult.isErr) return Err(profileResult.error)

        val profile = profileResult.value
        val validationError = profile.validationError(walletInstanceId)
        if (validationError != null) return Err(validationError)

        return Ok(
            when (profile.mode) {
                WalletStorageMode.LOCAL -> localStore
                WalletStorageMode.REMOTE -> remoteStore
                WalletStorageMode.HYBRID -> hybridStore
            },
        )
    }

    private fun StorageProfile.validationError(walletInstanceId: String): IdkError? {
        if (this.walletInstanceId != walletInstanceId) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "StorageProfile.walletInstanceId must match walletInstanceId")
        }
        return when (mode) {
            WalletStorageMode.LOCAL -> {
                if (localStoreRef == null) {
                    IdkError.ILLEGAL_ARGUMENT_ERROR(message = "LOCAL storage profile '$id' requires localStoreRef")
                } else {
                    null
                }
            }

            WalletStorageMode.REMOTE -> {
                if (remoteVaultRef == null) {
                    IdkError.ILLEGAL_ARGUMENT_ERROR(message = "REMOTE storage profile '$id' requires remoteVaultRef")
                } else {
                    null
                }
            }

            WalletStorageMode.HYBRID -> {
                if (localStoreRef == null || remoteVaultRef == null) {
                    IdkError.ILLEGAL_ARGUMENT_ERROR(message = "HYBRID storage profile '$id' requires localStoreRef and remoteVaultRef")
                } else {
                    null
                }
            }
        }
    }
}

private suspend inline fun <T, R> IdkResult<T, IdkError>.flatMap(crossinline block: suspend (T) -> IdkResult<R, IdkError>): IdkResult<R, IdkError> = if (isOk) block(value) else Err(error)

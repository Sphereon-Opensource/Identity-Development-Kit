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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.LocalWalletCredentialStore
import com.sphereon.wallet.credential.LocalWalletIssuanceSessionStore
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletIssuanceSessionStore
import com.sphereon.wallet.credential.WalletOperationReplayResult
import com.sphereon.wallet.credential.WalletOperationSyncService
import com.sphereon.wallet.credential.WalletUnitStores
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default [WalletUnitStores] composing the session-scoped [WalletCredentialStore] and
 * [WalletIssuanceSessionStore] bindings (see [StorageProfileRoutingWalletCredentialStore] and
 * [StorageProfileRoutingWalletIssuanceSessionStore]). Pure composition: no routing or business
 * logic of its own, and no defaulted constructor parameters.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletUnitStores>())
class DefaultWalletUnitStores(
    override val credentials: WalletCredentialStore,
    override val issuanceSessions: WalletIssuanceSessionStore,
) : WalletUnitStores

/** Local profiles bind directly to their durable local credential store. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletCredentialStore>())
class LocalWalletCredentialStoreBinding(
    private val delegate: LocalWalletCredentialStore,
) : WalletCredentialStore by delegate

/** Local profiles bind directly to their durable local issuance-session store. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletIssuanceSessionStore>())
class LocalWalletIssuanceSessionStoreBinding(
    private val delegate: LocalWalletIssuanceSessionStore,
) : WalletIssuanceSessionStore by delegate

/** A local-only profile has no remote operation log to replay. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletOperationSyncService>())
class LocalWalletOperationSyncService : WalletOperationSyncService {
    override suspend fun replayPending(walletUnitId: String): IdkResult<WalletOperationReplayResult, IdkError> {
        require(walletUnitId.isNotBlank()) { "wallet_unit_id_blank" }
        return Ok(WalletOperationReplayResult(attempted = 0, applied = 0))
    }
}

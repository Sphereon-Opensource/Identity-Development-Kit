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

import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletIssuanceSessionStore
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

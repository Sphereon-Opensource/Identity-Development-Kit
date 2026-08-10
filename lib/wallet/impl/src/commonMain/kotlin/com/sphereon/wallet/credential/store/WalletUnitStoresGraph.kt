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
import com.sphereon.wallet.credential.WalletUnitStores
import com.sphereon.wallet.credential.WalletStorageProfileResolver
import dev.zacsweers.metro.ContributesTo

/**
 * Session-graph accessor for [WalletUnitStores]. Consumers outside this module reach the
 * aggregate by casting `SessionInstance.graph` to this interface (see
 * `DefaultWalletProfileHandle.credentials` and the wscd test-fixtures `WscaGraph` for the
 * same pattern).
 */
@ContributesTo(SessionScope::class)
interface WalletUnitStoresGraph {
    val walletUnitStores: WalletUnitStores
}

@ContributesTo(SessionScope::class)
interface WalletStorageProfileResolverGraph {
    val walletStorageProfileResolver: WalletStorageProfileResolver
}

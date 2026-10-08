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

package com.sphereon.wallet.wscd.testfixtures

import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.interaction.WalletAttendedAuthorizationRegistry
import com.sphereon.wallet.interaction.WalletCounterpartyEncounterRegistry
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.WalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.impl.DefaultWalletInteractionEngine
import com.sphereon.wallet.interaction.impl.InMemoryWalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.impl.InMemoryWalletInteractionSessionStore
import com.sphereon.wallet.interaction.impl.LocalWalletInteractionClient
import com.sphereon.wallet.interaction.impl.StoreBackedWalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.impl.WalletInteractionSessionStore
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import com.sphereon.wallet.interaction.WalletInteractionDiagnostics

/**
 * App-owned wallet-interaction bindings required once [lib-wallet-interaction-holder-wiring]
 * is on the [WalletAppGraph] classpath (Metro merges its `@ContributesTo` accessors).
 */
@ContributesTo(SessionScope::class)
interface WalletAppGraphInteractionBindings {
    @Provides
    @SingleIn(SessionScope::class)
    fun counterpartyEncounterRegistry(): WalletCounterpartyEncounterRegistry =
        WalletCounterpartyEncounterRegistry.none

    @Provides
    @SingleIn(SessionScope::class)
    fun attendedAuthorizationRegistry(): WalletAttendedAuthorizationRegistry =
        WalletAttendedAuthorizationRegistry.none

    @Provides
    @SingleIn(SessionScope::class)
    fun privateSessionStore(): WalletInteractionPrivateSessionStore =
        InMemoryWalletInteractionPrivateSessionStore()

    @Provides
    @SingleIn(SessionScope::class)
    fun sessionStore(): WalletInteractionSessionStore = InMemoryWalletInteractionSessionStore()

    @Provides
    @SingleIn(SessionScope::class)
    fun sensitiveInputAuthority(
        privateSessionStore: WalletInteractionPrivateSessionStore,
    ): WalletInteractionSensitiveInputAuthority =
        StoreBackedWalletInteractionSensitiveInputAuthority(privateSessionStore)

    @Provides
    @SingleIn(SessionScope::class)
    fun engine(
        privateSessionStore: WalletInteractionPrivateSessionStore,
        sessionStore: WalletInteractionSessionStore,
        sensitiveInputAuthority: WalletInteractionSensitiveInputAuthority,
    ): DefaultWalletInteractionEngine =
        DefaultWalletInteractionEngine(
            sensitiveInputAuthority = sensitiveInputAuthority,
            privateSessionStore = privateSessionStore,
            sessionStore = sessionStore,
            diagnostics = WalletInteractionDiagnostics.none,
        )

    @Provides
    @SingleIn(SessionScope::class)
    fun client(engine: DefaultWalletInteractionEngine): WalletInteractionClient =
        LocalWalletInteractionClient(engine)
}

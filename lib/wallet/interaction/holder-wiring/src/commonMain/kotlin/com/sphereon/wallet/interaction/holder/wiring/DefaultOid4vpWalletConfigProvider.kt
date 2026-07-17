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

package com.sphereon.wallet.interaction.holder.wiring

import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.holder.OID4VP_STATIC_DISCOVERY_REQUEST_OBJECT_AUDIENCE
import com.sphereon.openid.oid4vp.holder.WalletConfig
import com.sphereon.wallet.interaction.WalletInteractionContext
import com.sphereon.wallet.interaction.WalletInteractionState
import com.sphereon.wallet.interaction.protocol.oid4vp.Oid4vpWalletConfigProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default OID4VP Request Object validation configuration for the local holder.
 *
 * The local wallet uses static Verifier discovery, whose symbolic audience is fixed by
 * OpenID4VP 1.0 Final section 5.8. This value is unrelated to the wallet application's web URL.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vpWalletConfigProvider>())
class DefaultOid4vpWalletConfigProvider : Oid4vpWalletConfigProvider {
    override suspend fun walletConfig(
        context: WalletInteractionContext,
        state: WalletInteractionState,
    ): WalletConfig = defaultOid4vpWalletConfig()
}

internal fun defaultOid4vpWalletConfig(): WalletConfig =
    WalletConfig(audience = OID4VP_STATIC_DISCOVERY_REQUEST_OBJECT_AUDIENCE)

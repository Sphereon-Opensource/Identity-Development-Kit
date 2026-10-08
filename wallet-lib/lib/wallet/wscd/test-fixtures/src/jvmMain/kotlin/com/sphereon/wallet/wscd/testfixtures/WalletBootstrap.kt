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

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wsca.impl.WscaGraph

/**
 * Bootstraps a standalone wallet by assembling an App->User->Session DI graph.
 *
 * Intended for non-server processes (integration tests, dev composition roots) that need a
 * working session-scoped graph without starting a full Ktor service.
 *
 * Every consumer drives the neutral interaction engine
 * (`HeadlessWalletRunner`/`HeadlessWalletRunnerBootstrap`, see `wallet/runner`) rather than this
 * class. [wsca] is the one remaining low-level accessor, used by [WalletBootstrapTest] to
 * smoke-test that this composition root wires the local, software-backed WSCA/WSCD stack
 * correctly end to end.
 *
 * This bootstrap never touches KMS. Session-scoped Wscd implementations (SoftwareWscd et al.)
 * own their KMS wiring end to end and register lazily on first key operation
 * (SoftwareKmsProviderRegistrar), whichever consumer signs first.
 */
class WalletBootstrap private constructor(
    private val app: WalletAppGraph
) {
    /**
     * Returns the session-scoped [Wsca] (local WSCA policy surface) bound to the given
     * [sessionId]. Subsequent calls with the same [sessionId] return the same instance.
     */
    fun wsca(sessionId: String = "wallet-session"): Wsca {
        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId(sessionId, principalType = com.sphereon.di.context.PrincipalType.USER)
        return (session.graph as WscaGraph).wsca
    }

    companion object {
        /**
         * Creates a [WalletBootstrap] with a fresh [WalletAppGraph].
         */
        fun create(
            appId: String = "com.sphereon.wallet",
            profile: String = "test",
            version: String = "0.1.0",
        ): WalletBootstrap {
            registerCredentialStoreConfig()
            return WalletBootstrap(
                createWalletAppGraph(
                    application = "Wallet",
                    appId = appId,
                    profile = profile,
                    version = version,
                ),
            )
        }

        /**
         * Registers the in-memory KV + blob store configuration the wallet credential
         * store needs. [BlobWalletCredentialStore] persists credential bodies through
         * [DefaultBlobService] (blob store id "default") and indexes metadata via
         * [KvBlobMetadataIndex] (KV store id "blob.metadata"). Without these the store
         * config binders fail with "store config not found". The dot in "blob.metadata"
         * is a path delimiter in the property key (no bracket quoting needed).
         */
        private fun registerCredentialStoreConfig() {
            DefaultPrincipalMapPropertySource.addProperty("kv.stores.blob.metadata.type", "memory")
            DefaultPrincipalMapPropertySource.addProperty("kv.stores.blob.metadata.scopeBinding", "TENANT")
            DefaultPrincipalMapPropertySource.addProperty("blob.stores.default.type", "memory")
            DefaultPrincipalMapPropertySource.addProperty("blob.stores.default.scopeBinding", "TENANT")
        }
    }
}

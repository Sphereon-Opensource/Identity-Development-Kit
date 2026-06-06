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

package com.sphereon.openid.wallet.impl

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.openid.wallet.Wallet
import com.sphereon.openid.wallet.impl.di.WalletAppGraph
import com.sphereon.openid.wallet.impl.di.WalletGraph
import com.sphereon.openid.wallet.impl.di.createWalletAppGraph

/**
 * Bootstraps a standalone wallet by assembling an App->User->Session DI graph and
 * wiring a real software KMS provider into [KeyManagerService].
 *
 * Intended for non-server processes (CLI tools, mobile apps, integration tests) that
 * need a working [Wallet] without starting a full Ktor service.
 */
class WalletBootstrap private constructor(
    private val app: WalletAppGraph
) {
    /**
     * Returns a [Wallet] bound to the given [sessionId].
     *
     * On first call the session graph is constructed and a software KMS provider is
     * registered as the default provider. Subsequent calls with the same [sessionId]
     * return the same session-scoped [Wallet] instance.
     */
    fun wallet(sessionId: String = "wallet-session"): Wallet {
        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId(sessionId)

        // Register the software KMS provider on first access so createHolderKey() works
        // without the caller having to wire crypto themselves.
        val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        if (kms.getProviderIds().isEmpty()) {
            val config = SoftwareKmsProviderConfig(id = "$sessionId-software-kms")
            val factory = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider
            val provider = factory.create(config, session.asCoreApiServiceGraph().serviceExecution)
            kms.registerProvider(provider, makeDefaultKms = true)
        }

        return (session.graph as WalletGraph).wallet
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
            registerDocumentStoreConfig()
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
         * Registers the in-memory KV + blob store configuration the wallet document
         * store needs. [BlobWalletDocumentStore] persists credential bodies through
         * [DefaultBlobService] (blob store id "default") and indexes metadata via
         * [KvBlobMetadataIndex] (KV store id "blob.metadata"). Without these the store
         * config binders fail with "store config not found". The dot in "blob.metadata"
         * is a path delimiter in the property key (no bracket quoting needed).
         */
        private fun registerDocumentStoreConfig() {
            DefaultPrincipalMapPropertySource.addProperty("kv.stores.blob.metadata.type", "memory")
            DefaultPrincipalMapPropertySource.addProperty("kv.stores.blob.metadata.scopeBinding", "TENANT")
            DefaultPrincipalMapPropertySource.addProperty("blob.stores.default.type", "memory")
            DefaultPrincipalMapPropertySource.addProperty("blob.stores.default.scopeBinding", "TENANT")
        }
    }
}

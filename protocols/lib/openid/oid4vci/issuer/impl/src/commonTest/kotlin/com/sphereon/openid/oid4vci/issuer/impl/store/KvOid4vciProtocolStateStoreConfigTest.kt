/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.store

import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KvStore
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.impl.KvStoreManager
import com.sphereon.data.store.kv.impl.KvStoreService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KvOid4vciProtocolStateStoreConfigTest {
    @Test
    fun tenantScopedProtocolStateUsesTheConfiguredKvStores() =
        runTest {
            val configuredIds =
                listOf(
                    "oid4vci.offers",
                    "oid4vci.sessions",
                    "oid4vci.nonces",
                    "oid4vci.deferred",
                    "oid4vci.notifications",
                )
            val manager = ProtocolStateRecordingKvStoreManager()
            val service =
                ConfiguredKvStoreService(
                    configuredIds.associateWith { id ->
                        InMemoryKvStoreConfig(id = "configured-$id", scopeBinding = KvStoreScopeBinding.TENANT)
                    },
                )
            val execution = NoOpSessionExecution()

            KvCredentialOfferStore(manager, service, execution).get("missing-offer")
            KvCredentialIssuanceSessionStore(manager, service, execution).get("missing-session")
            KvCredentialNonceStore(manager, service, execution).consume("missing-nonce")
            KvDeferredCredentialStore(manager, service, execution).get("missing-transaction")
            KvNotificationStateStore(manager, service, execution).getNotificationIdentity("missing-notification")

            assertEquals(configuredIds.map { "configured-$it" }, manager.createdConfigIds)
        }

    @Test
    fun offerStoreRejectsAConfiguredNonTenantScope() =
        runTest {
            val store =
                KvCredentialOfferStore(
                    kvStoreManager = ProtocolStateRecordingKvStoreManager(),
                    kvStoreService =
                        ConfiguredKvStoreService(
                            mapOf(
                                "oid4vci.offers" to
                                    InMemoryKvStoreConfig(
                                        id = "mis-scoped-offers",
                                        scopeBinding = KvStoreScopeBinding.APP,
                                    ),
                            ),
                        ),
                    execution = NoOpSessionExecution(),
                )

            val error =
                try {
                    store.get("missing-offer")
                    error("expected a tenant-scope configuration rejection")
                } catch (exception: IllegalArgumentException) {
                    exception
                }

            assertTrue(error.message.orEmpty().contains("scopeBinding=TENANT"))
        }
}

private class ConfiguredKvStoreService(
    private val configs: Map<String, KvStoreConfigBase>,
) : KvStoreService {
    override fun getStoreIds(): Array<String> = configs.keys.toTypedArray()

    override fun getStoreConfig(storeId: String): KvStoreConfigBase =
        configs[storeId] ?: throw IllegalArgumentException("No configured test store: $storeId")

    override fun getStore(storeId: String): KvStore = throw UnsupportedOperationException("not used in store tests")
}

private class ProtocolStateRecordingKvStoreManager : KvStoreManager {
    private val store = SimpleTestKvStore()
    val createdConfigIds = mutableListOf<String>()

    override fun createFromKvStoreConfig(config: KvStoreConfigBase): KvStore =
        store.also { createdConfigIds += config.id }

    override fun createFromKvStoreConfig(
        config: KvStoreConfigBase,
        execution: SessionExecution?,
    ): KvStore = store.also { createdConfigIds += config.id }

    override fun createFromProperties(
        configService: ConfigService,
        execution: SessionExecution?,
    ): Set<KvStore> = setOf(store)
}

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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KvCredentialRequestIdentityStoreTest {
    private fun createStore(
        kvStoreManager: KvStoreManager = InMemoryTestKvStoreManager(),
        kvStoreService: KvStoreService = TestKvStoreService(),
    ) =
        KvCredentialRequestIdentityStore(
            kvStoreManager = kvStoreManager,
            kvStoreService = kvStoreService,
            execution = NoOpSessionExecution(),
        )

    @Test
    fun concurrentFirstWriterWins() =
        runTest {
            val store = createStore()
            val start = CompletableDeferred<Unit>()
            val results =
                coroutineScope {
                    listOf("issuer-instance-race-alpha", "issuer-instance-race-beta")
                        .map { instanceId ->
                            async {
                                start.await()
                                store.resolveOrCreate(
                                    protocolSessionId = "protocol-session-first-writer-race",
                                    instanceId = instanceId,
                                    ttlSeconds = 300,
                                )
                            }
                        }.also { start.complete(Unit) }
                        .awaitAll()
                }

            assertEquals(1, results.count { it.isOk })
            assertEquals(1, results.count { it.isErr })
            val winner = results.single { it.isOk }.getOrThrow()
            val replay =
                store
                    .resolveOrCreate(
                        protocolSessionId = winner.protocolSessionId,
                        instanceId = winner.instanceId,
                        ttlSeconds = 300,
                    ).getOrThrow()
            assertEquals(winner, replay)
        }

    @Test
    fun sameInstanceRetryIsIdempotent() =
        runTest {
            val store = createStore()
            val first =
                store
                    .resolveOrCreate(
                        protocolSessionId = "protocol-session-idempotent-retry",
                        instanceId = "issuer-instance-idempotent-retry",
                        ttlSeconds = 300,
                    ).getOrThrow()
            val retry =
                store
                    .resolveOrCreate(
                        protocolSessionId = "protocol-session-idempotent-retry",
                        instanceId = "issuer-instance-idempotent-retry",
                        ttlSeconds = 300,
                    ).getOrThrow()

            assertEquals(first, retry)
            assertEquals("issuer-instance-idempotent-retry", retry.instanceId)
        }

    @Test
    fun differentInstanceRetryConflicts() =
        runTest {
            val store = createStore()
            store
                .resolveOrCreate(
                    protocolSessionId = "protocol-session-conflicting-retry",
                    instanceId = "issuer-instance-original-binding",
                    ttlSeconds = 300,
                ).getOrThrow()

            val conflict =
                store.resolveOrCreate(
                    protocolSessionId = "protocol-session-conflicting-retry",
                    instanceId = "issuer-instance-conflicting-retry",
                    ttlSeconds = 300,
                )

            assertTrue(conflict.isErr)
            assertEquals("INVALID_STATE", conflict.error.code)
        }

    @Test
    fun missingVersioningFailsClosed() =
        runTest {
            val result =
                createStore(NonVersioningKvStoreManager())
                    .resolveOrCreate(
                        protocolSessionId = "protocol-session-versioning-required",
                        instanceId = "issuer-instance-versioning-required",
                        ttlSeconds = 300,
                    )

            assertTrue(result.isErr)
            assertEquals("KV_VERSIONING_REQUIRED", result.error.code)
        }

    @Test
    fun configuredNonTenantScopeIsRejected() =
        runTest {
            val configuredStore =
                InMemoryKvStoreConfig(
                    id = "oid4vci.credential-request-identities",
                    scopeBinding = KvStoreScopeBinding.APP,
                )
            val store =
                createStore(
                    kvStoreService = TestKvStoreService(configuredStore),
                )

            val error =
                assertFailsWith<IllegalArgumentException> {
                    store.resolveOrCreate(
                        protocolSessionId = "protocol-session-invalid-configured-scope",
                        instanceId = "issuer-instance-invalid-configured-scope",
                        ttlSeconds = 300,
                    )
                }
            assertTrue(error.message.orEmpty().contains("scopeBinding=TENANT"))
        }

    @Test
    fun configuredTenantStoreOverridesFallbackConfig() =
        runTest {
            val manager = RecordingKvStoreManager()
            val configuredStore =
                InMemoryKvStoreConfig(
                    id = "configured-tenant-credential-request-identities",
                    scopeBinding = KvStoreScopeBinding.TENANT,
                )
            createStore(
                kvStoreManager = manager,
                kvStoreService = TestKvStoreService(configuredStore),
            ).resolveOrCreate(
                protocolSessionId = "protocol-session-configured-tenant-store",
                instanceId = "issuer-instance-configured-tenant-store",
                ttlSeconds = 300,
            ).getOrThrow()

            assertEquals("configured-tenant-credential-request-identities", manager.lastConfig?.id)
            assertEquals(KvStoreScopeBinding.TENANT, manager.lastConfig?.scopeBinding)
        }
}

private class TestKvStoreService(
    private val configuredStore: KvStoreConfigBase? = null,
) : KvStoreService {
    override fun getStoreIds(): Array<String> = configuredStore?.let { arrayOf(it.id) } ?: emptyArray()

    override fun getStoreConfig(storeId: String): KvStoreConfigBase =
        configuredStore ?: throw IllegalArgumentException("No configured test store: $storeId")

    override fun getStore(storeId: String): KvStore = throw UnsupportedOperationException("not used in identity-store tests")
}

private class RecordingKvStoreManager : KvStoreManager {
    private val store: KvStore = SimpleTestKvStore()
    var lastConfig: KvStoreConfigBase? = null
        private set

    override fun createFromKvStoreConfig(config: KvStoreConfigBase): KvStore =
        store.also { lastConfig = config }

    override fun createFromKvStoreConfig(
        config: KvStoreConfigBase,
        execution: SessionExecution?,
    ): KvStore = store.also { lastConfig = config }

    override fun createFromProperties(
        configService: ConfigService,
        execution: SessionExecution?,
    ): Set<KvStore> = setOf(store)
}

private class NonVersioningKvStoreManager : KvStoreManager {
    private val store: KvStore = NonVersioningKvStore()

    override fun createFromKvStoreConfig(config: KvStoreConfigBase): KvStore = store

    override fun createFromKvStoreConfig(
        config: KvStoreConfigBase,
        execution: SessionExecution?,
    ): KvStore = store

    override fun createFromProperties(
        configService: ConfigService,
        execution: SessionExecution?,
    ): Set<KvStore> = setOf(store)
}

private class NonVersioningKvStore(
    delegate: KvStore = SimpleTestKvStore(),
) : KvStore by delegate

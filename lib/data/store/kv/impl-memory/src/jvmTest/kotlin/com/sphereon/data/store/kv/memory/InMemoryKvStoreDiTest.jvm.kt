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

package com.sphereon.data.store.kv.memory

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.data.store.kv.InMemoryKvStoreConfig
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStoreBackends
import com.sphereon.data.store.kv.KvStoreFactory
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.KvStoreVersioning
import com.sphereon.data.store.kv.KvVersionAppendResult
import com.sphereon.data.store.kv.getKvStoreFactory
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.di.context.TenantContextData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration

class InMemoryKvStoreDiTest {
    private lateinit var app: KvMemoryTestAppGraph

    private val json = Json
    private val stringNamespace =
        KvNamespace(
            name = "test",
            codec = KotlinxSerializationJsonKvCodec(json = json, serializer = String.serializer()),
        )

    @BeforeTest
    fun setup() {
        app = createKvMemoryTestAppGraph()
        // Ensure clean storage per test.
        app.inMemoryKvBackingStorage.clearAll()
    }

    @AfterTest
    fun tearDown() {
        try {
            app.destroy()
        } finally {
            // no-op
        }
    }

    @Test
    fun `SESSION scope - store instance can be recreated without losing data (session-per-request safe)`() =
        runTest {
            val tenant = tenantData("tenant-a")
            val user = app.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-a")
            val session = user.createSession(sessionId = "session-a", makeActive = true)
            val execution = session.sessionExecution

            val factory = app.kvStoreFactories.getKvStoreFactory(KvStoreBackends.MEMORY)
            val config = InMemoryKvStoreConfig(id = "kv", scopeBinding = KvStoreScopeBinding.SESSION)

            val store1 = factory.create(config, execution)
            store1.put(stringNamespace, "k1", "v1", Duration.INFINITE).getOrThrow()

            // Simulate a new request creating a new store instance for the same session partition.
            val store2 = factory.create(config, execution)
            assertEquals("v1", store2.get(stringNamespace, "k1").getOrThrow())
        }

    @Test
    fun `SESSION scope - different sessions are isolated`() =
        runTest {
            val tenant = tenantData("tenant-a")
            val user = app.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-a")

            val sessionA = user.createSession(sessionId = "session-a", makeActive = true)
            val sessionB = user.createSession(sessionId = "session-b", makeActive = true)

            val factory = app.kvStoreFactories.getKvStoreFactory(KvStoreBackends.MEMORY)
            val config = InMemoryKvStoreConfig(id = "kv", scopeBinding = KvStoreScopeBinding.SESSION)

            val storeA = factory.create(config, sessionA.sessionExecution)
            storeA.put(stringNamespace, "k1", "v1", Duration.INFINITE).getOrThrow()

            val storeB = factory.create(config, sessionB.sessionExecution)
            assertNull(storeB.get(stringNamespace, "k1").getOrThrow())
        }

    @Test
    fun `TENANT vs PRINCIPAL_TENANT - tenant scoped store is shared across principals while principal_tenant is isolated`() =
        runTest {
            val tenant = tenantData("tenant-a")
            val userA = app.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-a")
            val userB = app.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-b")

            val sessionA = userA.createSession(sessionId = "session-a", makeActive = true)
            val sessionB = userB.createSession(sessionId = "session-b", makeActive = true)

            val factory = app.kvStoreFactories.getKvStoreFactory(KvStoreBackends.MEMORY)

            val tenantConfig = InMemoryKvStoreConfig(id = "kv", scopeBinding = KvStoreScopeBinding.TENANT)
            val principalTenantConfig = InMemoryKvStoreConfig(id = "kv", scopeBinding = KvStoreScopeBinding.PRINCIPAL_TENANT)

            val tenantStoreA = factory.create(tenantConfig, sessionA.sessionExecution)
            tenantStoreA.put(stringNamespace, "k1", "v1", Duration.INFINITE).getOrThrow()

            val tenantStoreB = factory.create(tenantConfig, sessionB.sessionExecution)
            assertEquals("v1", tenantStoreB.get(stringNamespace, "k1").getOrThrow())

            val principalTenantStoreA = factory.create(principalTenantConfig, sessionA.sessionExecution)
            principalTenantStoreA.put(stringNamespace, "k2", "v2", Duration.INFINITE).getOrThrow()

            val principalTenantStoreB = factory.create(principalTenantConfig, sessionB.sessionExecution)
            assertNull(principalTenantStoreB.get(stringNamespace, "k2").getOrThrow())
        }

    @Test
    fun `TTL and cleanup - expired entries are not returned and cleanup removes them`() =
        runTest {
            val tenant = tenantData("tenant-a")
            val user = app.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-a")
            val session = user.createSession(sessionId = "session-a", makeActive = true)
            val execution = session.sessionExecution

            val factory = app.kvStoreFactories.getKvStoreFactory(KvStoreBackends.MEMORY)
            val config = InMemoryKvStoreConfig(id = "kv", scopeBinding = KvStoreScopeBinding.SESSION)
            val store = factory.create(config, execution)

            store.put(stringNamespace, "e1", "v1", Duration.ZERO).getOrThrow()
            store.put(stringNamespace, "e2", "v2", Duration.ZERO).getOrThrow()

            assertNull(store.get(stringNamespace, "e1").getOrThrow())
            assertNull(store.get(stringNamespace, "e2").getOrThrow())

            // Add two more expired entries and verify cleanup count.
            store.put(stringNamespace, "e3", "v3", Duration.ZERO).getOrThrow()
            store.put(stringNamespace, "e4", "v4", Duration.ZERO).getOrThrow()

            val removed = store.cleanupExpired().getOrThrow()
            assertEquals(2, removed)
        }

    @Test
    fun `version chain preserves history and is isolated from mutable data and delete`() =
        runTest {
            val store = versioningStore("chain")
            store.put(stringNamespace, "shared", "mutable", Duration.INFINITE).getOrThrow()

            val first =
                assertIs<KvVersionAppendResult.Applied<String>>(
                    store.append(stringNamespace, "shared", null, "v1", Duration.INFINITE).getOrThrow(),
                ).entry
            val second =
                assertIs<KvVersionAppendResult.Applied<String>>(
                    store.append(stringNamespace, "shared", first.versionId, "v2", Duration.INFINITE).getOrThrow(),
                ).entry

            assertNull(first.previousVersionId)
            assertNotEquals(first.versionId, second.versionId)
            assertEquals(first.versionId, second.previousVersionId)
            assertEquals(second, store.getHead(stringNamespace, "shared").getOrThrow())
            assertEquals(first, store.getVersion(stringNamespace, "shared", first.versionId).getOrThrow())
            assertEquals(second, store.getVersion(stringNamespace, "shared", second.versionId).getOrThrow())
            assertEquals("mutable", store.get(stringNamespace, "shared").getOrThrow())

            assertEquals(true, store.deleteVersioned(stringNamespace, "shared").getOrThrow())
            assertNull(store.getHead(stringNamespace, "shared").getOrThrow())
            assertEquals("mutable", store.get(stringNamespace, "shared").getOrThrow())
            assertEquals(false, store.deleteVersioned(stringNamespace, "shared").getOrThrow())
        }

    @Test
    fun `concurrent appenders against one predecessor have exactly one winner`() =
        runTest {
            val store = versioningStore("race")
            val root =
                assertIs<KvVersionAppendResult.Applied<String>>(
                    store.append(stringNamespace, "key", null, "root", Duration.INFINITE).getOrThrow(),
                ).entry

            val outcomes =
                coroutineScope {
                    (0 until 64)
                        .map { index ->
                            async {
                                store.append(stringNamespace, "key", root.versionId, "candidate-$index", Duration.INFINITE).getOrThrow()
                            }
                        }.awaitAll()
                }

            val applied = outcomes.filterIsInstance<KvVersionAppendResult.Applied<String>>()
            val conflicts = outcomes.filterIsInstance<KvVersionAppendResult.Conflict<String>>()
            assertEquals(1, applied.size)
            assertEquals(63, conflicts.size)
            assertEquals(applied.single().entry, store.getHead(stringNamespace, "key").getOrThrow())
            assertEquals(setOf(applied.single().entry.versionId), conflicts.mapNotNull { it.currentHead?.versionId }.toSet())
        }

    @Test
    fun `expired head removes entire chain and expected null recreates it`() =
        runTest {
            val store = versioningStore("expiry")
            val first =
                assertIs<KvVersionAppendResult.Applied<String>>(
                    store.append(stringNamespace, "key", null, "v1", Duration.INFINITE).getOrThrow(),
                ).entry
            val expiredHead =
                assertIs<KvVersionAppendResult.Applied<String>>(
                    store.append(stringNamespace, "key", first.versionId, "expired", Duration.ZERO).getOrThrow(),
                ).entry

            assertNull(store.getHead(stringNamespace, "key").getOrThrow())
            assertNull(store.getVersion(stringNamespace, "key", first.versionId).getOrThrow())

            val fresh =
                assertIs<KvVersionAppendResult.Applied<String>>(
                    store.append(stringNamespace, "key", null, "fresh", Duration.INFINITE).getOrThrow(),
                ).entry
            assertNull(fresh.previousVersionId)
            assertEquals("fresh", store.getHead(stringNamespace, "key").getOrThrow()?.value)
            assertNull(store.getVersion(stringNamespace, "key", first.versionId).getOrThrow())
            assertNull(store.getVersion(stringNamespace, "key", expiredHead.versionId).getOrThrow())
        }

    private fun versioningStore(configId: String): KvStoreVersioning {
        val tenant = tenantData("tenant-versioning")
        val user = app.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-versioning")
        val session = user.createSession(sessionId = "session-$configId", makeActive = true)
        val factory = app.kvStoreFactories.getKvStoreFactory(KvStoreBackends.MEMORY)
        return assertIs(
            factory.create(
                InMemoryKvStoreConfig(id = configId, scopeBinding = KvStoreScopeBinding.SESSION),
                session.sessionExecution,
            ),
        )
    }

    private fun tenantData(tenantId: String): TenantContextData =
        object : TenantContextData {
            override val tenantId: String = tenantId
        }
}

@DependencyGraph(AppScope::class)
abstract class KvMemoryTestAppGraph : AbstractAppGraph() {
    abstract val kvStoreFactories: Set<KvStoreFactory>
    abstract val inMemoryKvBackingStorage: InMemoryKvBackingStorage

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): KvMemoryTestAppGraph
    }
}

fun createKvMemoryTestAppGraph(
    application: Any = "KvMemoryTest",
    appId: String = "com.sphereon.kv.memory.test",
    profile: String = "test",
    version: String = "1.0.0",
): KvMemoryTestAppGraph {
    val graph =
        createGraphFactory<KvMemoryTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}

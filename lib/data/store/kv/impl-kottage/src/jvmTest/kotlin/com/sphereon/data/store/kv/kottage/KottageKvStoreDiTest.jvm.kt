/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.data.store.kv.kottage

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KottageKvStoreConfig
import com.sphereon.data.store.kv.KvStoreBackends
import com.sphereon.data.store.kv.KvStoreConfigBase
import com.sphereon.data.store.kv.KvStoreFactory
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.data.store.kv.getKvStoreFactory
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.di.context.TenantContextData
import io.github.irgaly.kottage.Kottage
import io.github.irgaly.kottage.KottageEnvironment
import io.github.irgaly.kottage.platform.KottageContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.createGraphFactory
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration

class KottageKvStoreDiTest {
    private lateinit var app: KvKottageTestAppComponent

    private val json = Json
    private val stringNamespace = KvNamespace(
        name = "test",
        codec = KotlinxSerializationJsonKvCodec(json = json, serializer = String.serializer())
    )

    @BeforeTest
    fun setup() {
        app = createKvKottageTestAppComponent()
    }

    @AfterTest
    fun tearDown() {
        try {
            runBlocking {
                // Ensure DB handles are closed before deleting temp directory.
                app.kottage.clear()
                app.kottage.close()
            }
        } finally {
            app.destroy()
        }
    }

    @Test
    fun `SESSION scope - store instance can be recreated without losing data`() = runTest {
        val tenant = tenantData("tenant-a")
        val user = app.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-a")
        val session = user.createSession(sessionId = "session-a", makeActive = true)
        val execution = session.sessionExecution

        val factory = app.kvStoreFactories.getKvStoreFactory(KvStoreBackends.KOTTAGE)
        val config = KottageKvStoreConfig(id = "kv", scopeBinding = KvStoreScopeBinding.SESSION)

        val store1 = factory.create(config, execution)
        store1.put(stringNamespace, "k1", "v1", Duration.INFINITE).getOrThrow()

        val store2 = factory.create(config, execution)
        assertEquals("v1", store2.get(stringNamespace, "k1").getOrThrow())
    }

    @Test
    fun `SESSION scope - different sessions are isolated`() = runTest {
        val tenant = tenantData("tenant-a")
        val user = app.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-a")

        val sessionA = user.createSession(sessionId = "session-a", makeActive = true)
        val sessionB = user.createSession(sessionId = "session-b", makeActive = true)

        val factory = app.kvStoreFactories.getKvStoreFactory(KvStoreBackends.KOTTAGE)
        val config = KottageKvStoreConfig(id = "kv", scopeBinding = KvStoreScopeBinding.SESSION)

        val storeA = factory.create(config, sessionA.sessionExecution)
        storeA.put(stringNamespace, "k1", "v1", Duration.INFINITE).getOrThrow()

        val storeB = factory.create(config, sessionB.sessionExecution)
        assertNull(storeB.get(stringNamespace, "k1").getOrThrow())
    }

    @Test
    fun `TTL - entries with immediate TTL are not returned`() = runTest {
        val tenant = tenantData("tenant-a")
        val user = app.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-a")
        val session = user.createSession(sessionId = "session-a", makeActive = true)

        val factory = app.kvStoreFactories.getKvStoreFactory(KvStoreBackends.KOTTAGE)
        val config = KottageKvStoreConfig(id = "kv", scopeBinding = KvStoreScopeBinding.SESSION)
        val store = factory.create(config, session.sessionExecution)

        store.put(stringNamespace, "e1", "v1", Duration.ZERO).getOrThrow()
        assertNull(store.get(stringNamespace, "e1").getOrThrow())
    }

    private fun tenantData(tenantId: String): TenantContextData {
        return object : TenantContextData {
            override val tenantId: String = tenantId
        }
    }
}

/**
 * Test-only DI binding: provides a Kottage-backed [KvStoreFactory] through AppScope multibinding,
 * matching how production implementations are wired for other backends (e.g., in-memory).
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class TestKottageKvStoreFactoryImpl(
    private val kottage: Kottage
) : KvStoreFactory {
    private val delegate = KottageKvStoreFactory(kottage)
    override val backendId: String = KvStoreBackends.KOTTAGE
    override fun create(config: KvStoreConfigBase, execution: com.sphereon.core.api.context.SessionExecution?): com.sphereon.data.store.kv.KvStore {
        return delegate.create(config, execution)
    }
}

/**
 * JVM-test-only wrapper to avoid ambiguous DI bindings for raw [String]s.
 */
data class KottageDirectoryPath(val value: String)

@DependencyGraph(AppScope::class)
abstract class KvKottageTestAppComponent : AbstractAppComponent() {

    abstract val kvStoreFactories: Set<KvStoreFactory>
    abstract val kottage: Kottage

    @Provides
    @SingleIn(AppScope::class)
    fun provideKottageDirectoryPath(): KottageDirectoryPath {
        // JVM test only: create a unique temp directory per app component.
        return KottageDirectoryPath(Files.createTempDirectory("idk-kv-kottage-").toFile().absolutePath)
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideKottageEnvironment(): KottageEnvironment {
        // JVM test only: no platform-specific context is required.
        return KottageEnvironment(context = KottageContext())
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideKottage(
        directoryPath: KottageDirectoryPath,
        environment: KottageEnvironment
    ): Kottage {
        // Use the AppScope coroutine scope so the DB is cleaned up when the app scope is destroyed.
        val scope = appScopeCoroutineScopeScoped.createChild()
        return Kottage(
            name = "kv-test",
            directoryPath = directoryPath.value,
            environment = environment,
            scope = scope,
            json = Json
        )
    }

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): KvKottageTestAppComponent
    }
}

fun createKvKottageTestAppComponent(
    application: Any = "KvKottageTest",
    appId: String = "com.sphereon.kv.kottage.test",
    profile: String = "test",
    version: String = "1.0.0",
): KvKottageTestAppComponent {
    val component = createGraphFactory<KvKottageTestAppComponent.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}

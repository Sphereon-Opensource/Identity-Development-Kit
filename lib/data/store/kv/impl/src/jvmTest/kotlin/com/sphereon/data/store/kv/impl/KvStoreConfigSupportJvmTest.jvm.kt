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

package com.sphereon.data.store.kv.impl

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.conf.DefaultAppMapPropertySource
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.data.store.kv.KotlinxSerializationJsonKvCodec
import com.sphereon.data.store.kv.KvNamespace
import com.sphereon.data.store.kv.KvStoreBackends
import com.sphereon.data.store.kv.KvStoreFactory
import com.sphereon.data.store.kv.KvStoreScopeBinding
import com.sphereon.di.app.AbstractAppComponent
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.di.context.TenantContextData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

class KvStoreConfigSupportJvmTest {
    private var app: KvImplTestAppComponent? = null

    private val json = Json
    private val stringNamespace = KvNamespace(
        name = "test",
        codec = KotlinxSerializationJsonKvCodec(json = json, serializer = String.serializer())
    )

    
    @BeforeTest
    fun setup() {
        DefaultAppMapPropertySource.getSource().clear()
        DefaultPrincipalMapPropertySource.getSource().clear()
    }

    @AfterTest
    fun tearDown() {
        try {
            app?.destroy()
        } finally {
            DefaultAppMapPropertySource.getSource().clear()
            DefaultPrincipalMapPropertySource.getSource().clear()
        }
    }

    @Test
    fun `KvStoreConfigBinder can parse kv dot stores and defaultconfigs`() = runTest {
        DefaultAppMapPropertySource.addProperties(
            mapOf(
                "kv.stores.my-store.type" to KvStoreBackends.MEMORY,
                "kv.stores.my-store.scopebinding" to KvStoreScopeBinding.SESSION.name,
                "kv.stores.my-store.defaultconfigs.foo" to "bar",
                "kv.stores.my-store.defaultconfigs.answer" to "42",
            )
        )

        val localApp = createKvImplTestAppComponent().also { app = it }
        val cfg = localApp.kvStoreConfigBinder.getKvStoreConfig(localApp.appConfigService, "my-store")
        assertEquals("my-store", cfg.id, "id should match config key")
        assertEquals(KvStoreBackends.MEMORY, cfg.backendId, "backendId should be memory")
        assertEquals(KvStoreScopeBinding.SESSION, cfg.scopeBinding, "scopeBinding should be SESSION")
        assertTrue(cfg is com.sphereon.data.store.kv.KvStoreConfigBase, "should be KvStoreConfigBase")
    }

    @Test
    fun `KvStoreService resolves store by id and creates a working KvStore instance`() = runTest {
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                // Use the explicit backendId alias and kebab-case id to ensure normalization works end-to-end.
                "kv.stores.my-store.backendId" to KvStoreBackends.MEMORY,
                "kv.stores.my-store.scopebinding" to KvStoreScopeBinding.SESSION.name,
            )
        )

        val localApp = createKvImplTestAppComponent().also { app = it }
        val tenant = tenantData("tenant-a")
        val user = localApp.userContextManager.createOrGetFromData(tenantData = tenant, principalValue = "principal-a")
        val session = user.createSession(sessionId = "session-a", makeActive = true)

        val kvStoreService = (session.component as KvStoreService.Component).kvStoreService

        val storeA = kvStoreService.getStore("my-store")
        val storeB = kvStoreService.getStore("my-store")

        assertEquals(KvStoreBackends.MEMORY, storeA.config.backendId)
        assertEquals(KvStoreScopeBinding.SESSION, storeA.config.scopeBinding)
        assertEquals(storeA.config.backendId, storeB.config.backendId)

        storeA.put(stringNamespace, "k1", "v1", Duration.INFINITE).getOrThrow()
        assertEquals("v1", storeB.get(stringNamespace, "k1").getOrThrow())
    }

    @Test
    fun `KvStoreManager createFromProperties returns only enabled stores`() = runTest {
        DefaultAppMapPropertySource.addProperties(
            mapOf(
                "kv.stores.enabled-store.type" to KvStoreBackends.MEMORY,
                "kv.stores.enabled-store.scopebinding" to KvStoreScopeBinding.APP.name,
                "kv.stores.disabled-store.type" to KvStoreBackends.MEMORY,
                "kv.stores.disabled-store.enabled" to false,
                "kv.stores.disabled-store.scopebinding" to KvStoreScopeBinding.APP.name,
            )
        )

        val localApp = createKvImplTestAppComponent().also { app = it }
        val stores = localApp.kvStoreManager.createFromProperties(localApp.appConfigService, execution = null)
        assertEquals(1, stores.size)
        assertTrue(stores.first().config.id.contains("enabled"))
    }

    private fun tenantData(tenantId: String): TenantContextData {
        return object : TenantContextData {
            override val tenantId: String = tenantId
        }
    }
}

@DependencyGraph(AppScope::class)
abstract class KvImplTestAppComponent : AbstractAppComponent() {

    override abstract val kvStoreConfigBinder: KvStoreConfigBinder
    override abstract val kvStoreManager: KvStoreManager
    abstract val kvStoreFactories: Set<KvStoreFactory>

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): KvImplTestAppComponent
    }
}

fun createKvImplTestAppComponent(
    application: Any = "KvImplTest",
    appId: String = "com.sphereon.kv.impl.test",
    profile: String = "test",
    version: String = "1.0.0",
): KvImplTestAppComponent {
    val component = createGraphFactory<KvImplTestAppComponent.Factory>().create(
        application = application,
        appId = appId,
        profile = profile,
        version = version,
        rootScopeProvider = DefaultRootScopeProvider()
    )
    component.initRootScopeProvider()
    return component
}

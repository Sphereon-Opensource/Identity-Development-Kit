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
 *
 */

package com.sphereon.crypto.core.kms

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigBootstrapGuard
import com.sphereon.core.api.conf.DefaultAppMapPropertySource
import com.sphereon.core.api.conf.DefaultSyncConfigSnapshotCache
import com.sphereon.core.api.conf.NoOpSyncSnapshotCache
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.RefreshablePropertySource
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.JvmCryptoTestAppGraph
import com.sphereon.crypto.core.createJvmCryptoTestAppGraph
import com.sphereon.crypto.core.kms.KmsProviderConfig
import com.sphereon.crypto.core.kms.KmsProviderFactory
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for KMS support classes: NoOpKeyStoreFactory, NoOpKmsProviderFactoryImpl,
 * KeyStoreManagerImpl, and KmsProviderManagerImpl.
 */
class KmsSupportClassesTest {
    val app = createJvmCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("kms-support-test")

    /**
     * Clears the sync config snapshot cache.
     * Required before tests that dynamically add properties to DefaultAppMapPropertySource,
     * because the cache may have cached empty results from graph initialization.
     */
    private fun clearConfigCache() {
        (app as DefaultSyncConfigSnapshotCache.Graph).syncConfigSnapshotCache.clear()
    }

    private class DeferredRefreshPropertySource(
        private val name: String,
    ) : PropertySource<Unit>,
        RefreshablePropertySource {
        var loaded: Boolean = false
            private set

        override val contentRevision: Long
            get() = if (loaded) 1L else 0L
        override val isPlatformSupported: Boolean = true

        override fun refreshIfNeeded() {
            if (!ConfigBootstrapGuard.isContextRegistrationInProgress()) {
                loaded = true
            }
        }

        override fun hasProperty(name: String): Boolean = false

        override fun <T : Any> getProperty(
            name: String,
            targetType: KClass<T>,
        ): T? = null

        override fun getPropertyAsString(name: String): String? = null

        override fun removeProperty(name: String) {
        }

        override fun getName(): String = name

        override fun getSource(): Unit = Unit

        override fun getAllPropertyNames(): Set<String> = emptySet()

        override fun getOrder(): Int = 0

        override fun compareTo(other: PropertySource<*>): Int = getOrder().compareTo(other.getOrder())
    }

    // =========== NoOpKeyStoreFactory Tests ===========

    @Test
    fun noOpKeyStoreFactoryShouldHaveCorrectType() {
        val factory = NoOpKeyStoreFactory()
        assertEquals(NoOpKeyStoreFactory.KEY_STORE_TYPE, factory.keyStoreType)
        assertEquals("___NO_OP___", factory.keyStoreType)
    }

    @Test
    fun noOpKeyStoreFactoryShouldThrowOnCreate() {
        val factory = NoOpKeyStoreFactory()
        val config = KeyStoreConfigImpl(id = "test", keyStoreType = "memory")

        assertFailsWith<IllegalArgumentException> {
            factory.create(config)
        }
    }

    @Test
    fun noOpKeyStoreFactoryShouldThrowWithMessage() {
        val factory = NoOpKeyStoreFactory()
        val config = KeyStoreConfigImpl(id = "test", keyStoreType = "memory")

        val exception =
            assertFailsWith<IllegalArgumentException> {
                factory.create(config)
            }

        assertTrue(exception.message?.contains("Please register your Keystore Factory") == true)
    }

    // =========== NoOpKmsProviderFactoryImpl Tests ===========

    @Test
    fun noOpKmsProviderFactoryShouldHaveCorrectType() {
        val factory = NoOpKmsProviderFactoryImpl()
        assertEquals(NoOpKmsProviderFactoryImpl.KMS_PROVIDER_TYPE, factory.kmsProviderType)
        assertEquals("___NO_OP___", factory.kmsProviderType)
    }

    @Test
    fun noOpKmsProviderFactoryShouldThrowOnCreate() {
        val factory = NoOpKmsProviderFactoryImpl()
        val config =
            KmsProviderConfig(
                id = "test-provider",
                kmsProviderType = "test",
            )

        assertFailsWith<IllegalArgumentException> {
            factory.create(config, session.asCoreApiServiceGraph().serviceExecution)
        }
    }

    @Test
    fun noOpKmsProviderFactoryShouldThrowWithMessage() {
        val factory = NoOpKmsProviderFactoryImpl()
        val config =
            KmsProviderConfig(
                id = "test-provider",
                kmsProviderType = "test",
            )

        val exception =
            assertFailsWith<IllegalArgumentException> {
                factory.create(config, session.asCoreApiServiceGraph().serviceExecution)
            }

        assertTrue(exception.message?.contains("Please register your KMS Provider Factory") == true)
    }

    // =========== KeyStoreManagerImpl Tests ===========

    @Test
    fun keyStoreManagerShouldBeAvailable() {
        app as JvmCryptoTestAppGraph
        val manager = (app as KeyStoreManager.Graph).keyStoreManager

        assertNotNull(manager)
    }

    @Test
    fun keyStoreManagerShouldThrowForUnknownType() {
        app as JvmCryptoTestAppGraph
        val manager = (app as KeyStoreManager.Graph).keyStoreManager

        val config = KeyStoreConfigImpl(id = "test", keyStoreType = "unknown-keystore-type-xyz")

        assertFailsWith<IllegalArgumentException> {
            manager.createFromKeyStoreConfig(config)
        }
    }

    @Test
    fun keyStoreManagerErrorMessageShouldContainAvailableFactories() {
        app as JvmCryptoTestAppGraph
        val manager = (app as KeyStoreManager.Graph).keyStoreManager

        val config = KeyStoreConfigImpl(id = "test", keyStoreType = "unknown-keystore-type-xyz")

        val exception =
            assertFailsWith<IllegalArgumentException> {
                manager.createFromKeyStoreConfig(config)
            }

        assertTrue(
            exception.message?.contains("available factories") == true ||
                exception.message?.contains("No keystore factory found") == true,
        )
    }

    // =========== KmsProviderManagerImpl Tests ===========

    @Test
    fun kmsProviderManagerShouldBeAvailable() {
        app as JvmCryptoTestAppGraph
        val manager = (app as KmsProviderManager.Graph).kmsProviderManager

        assertNotNull(manager)
    }

    @Test
    fun kmsProviderManagerShouldThrowForUnknownType() {
        app as JvmCryptoTestAppGraph
        val manager = (app as KmsProviderManager.Graph).kmsProviderManager

        // Create a config with an unknown provider type
        val config =
            KmsProviderConfig(
                id = "test-provider",
                kmsProviderType = "unknown-provider-type-xyz",
            )

        assertFailsWith<IllegalArgumentException> {
            manager.createFromProviderConfig(config, session.asCoreApiServiceGraph().serviceExecution)
        }
    }

    @Test
    fun kmsProviderManagerErrorMessageShouldContainAvailableFactories() {
        app as JvmCryptoTestAppGraph
        val manager = (app as KmsProviderManager.Graph).kmsProviderManager

        val config =
            KmsProviderConfig(
                id = "test-provider",
                kmsProviderType = "unknown-provider-type-xyz",
            )

        val exception =
            assertFailsWith<IllegalArgumentException> {
                manager.createFromProviderConfig(config, session.asCoreApiServiceGraph().serviceExecution)
            }

        assertTrue(
            exception.message?.contains("Available factories") == true ||
                exception.message?.contains("No KMSFactory found") == true,
        )
    }

    @Test
    fun kmsProviderManagerWithEmptyFactoriesShouldThrow() {
        // Create a manager with only the NoOp factory (which gets filtered out)
        val mockBinder = mockk<KmsProviderConfigBinder>()
        val managerWithNoFactories =
            KmsProviderManagerImpl(
                factories = setOf(NoOpKmsProviderFactoryImpl()),
                binder = mockBinder,
                snapshotCache = NoOpSyncSnapshotCache,
                appLogManager = app.appLogManager,
            )

        val config =
            KmsProviderConfig(
                id = "test-provider",
                kmsProviderType = "software",
            )

        val exception =
            assertFailsWith<IllegalArgumentException> {
                managerWithNoFactories.createFromProviderConfig(config, session.asCoreApiServiceGraph().serviceExecution)
            }

        assertTrue(exception.message?.contains("No KMSFactory found") == true)
    }

    @Test
    fun kmsProviderManagerShouldSuccessfullyCreateProviderWhenFactoryExists() {
        app as JvmCryptoTestAppGraph
        val manager = (app as KmsProviderManager.Graph).kmsProviderManager

        // Create a config with the "software" provider type which is registered
        // Must use SoftwareKmsProviderConfig as that's what the factory expects
        val config =
            SoftwareKmsProviderConfig(
                id = "test-software-provider-create",
            )

        // This should succeed because the software factory is registered
        val provider = manager.createFromProviderConfig(config, session.asCoreApiServiceGraph().serviceExecution)
        assertNotNull(provider, "Should successfully create provider when factory exists")
    }

    @Test
    fun kmsProviderManagerFactoriesFilterComparison() {
        // Tests the condition: it.kmsProviderType == config.kmsProviderType
        // This test verifies both the == true branch (match) and == false branch (no match)
        val mockBinder = mockk<KmsProviderConfigBinder>()
        val mockFactory1 = mockk<KmsProviderFactory>()
        every { mockFactory1.kmsProviderType } returns "type-a"

        val mockFactory2 = mockk<KmsProviderFactory>()
        every { mockFactory2.kmsProviderType } returns "type-b"
        every { mockFactory2.create(any(), any()) } returns mockk()

        val manager =
            KmsProviderManagerImpl(
                factories = setOf(mockFactory1, mockFactory2),
                binder = mockBinder,
                snapshotCache = NoOpSyncSnapshotCache,
                appLogManager = app.appLogManager,
            )

        // Config for type-b - should find mockFactory2 after skipping mockFactory1
        val config =
            KmsProviderConfig(
                id = "test-provider",
                kmsProviderType = "type-b",
            )

        // This exercises both branches: factory1.kmsProviderType != config (false), factory2.kmsProviderType == config (true)
        val provider = manager.createFromProviderConfig(config, session.asCoreApiServiceGraph().serviceExecution)
        assertNotNull(provider, "Should create provider from matching factory")
    }

    @Test
    fun kmsProviderManagerCachesBoundProviderConfigsByScopeAndRevision() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val snapshotCache = (app as DefaultSyncConfigSnapshotCache.Graph).syncConfigSnapshotCache
        val execution = session.asCoreApiServiceGraph().serviceExecution
        val mockBinder = mockk<KmsProviderConfigBinder>()
        val mockFactory = mockk<KmsProviderFactory>()
        val mockProvider = mockk<KmsProvider>()
        val config =
            KmsProviderConfig(
                id = "cached-provider",
                kmsProviderType = "cached-type",
            )

        snapshotCache.clear()
        every { mockBinder.getKmsProviderConfigs(configService) } returns arrayOf(config)
        every { mockFactory.kmsProviderType } returns "cached-type"
        every { mockFactory.create(config, execution) } returns mockProvider

        try {
            val manager =
                KmsProviderManagerImpl(
                    factories = setOf(mockFactory),
                    binder = mockBinder,
                    snapshotCache = snapshotCache,
                    appLogManager = app.appLogManager,
                )

            manager.createFromProperties(configService, execution)
            manager.createFromProperties(configService, execution)
            snapshotCache.invalidateByPrefix("kms.providers")
            manager.createFromProperties(configService, execution)

            verify(exactly = 1) { mockBinder.getKmsProviderConfigs(configService) }
            verify(exactly = 3) { mockFactory.create(config, execution) }
        } finally {
            snapshotCache.clear()
        }
    }

    @Test
    fun kmsProviderManagerDoesNotCacheBootstrapSnapshotBeforeRefreshableConfigLoads() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val snapshotCache = (app as DefaultSyncConfigSnapshotCache.Graph).syncConfigSnapshotCache
        val execution = session.asCoreApiServiceGraph().serviceExecution
        val refreshableSource = DeferredRefreshPropertySource("deferred-kms-provider-config")
        val mockBinder = mockk<KmsProviderConfigBinder>()
        val mockFactory = mockk<KmsProviderFactory>()
        val mockProvider = mockk<KmsProvider>()
        val config =
            KmsProviderConfig(
                id = "remote-provider",
                kmsProviderType = "refresh-type",
            )

        snapshotCache.clear()
        configService.addPropertySource(refreshableSource)
        every { mockBinder.getKmsProviderConfigs(configService) } answers {
            if (refreshableSource.loaded) arrayOf(config) else emptyArray<KmsProviderConfigBase>()
        }
        every { mockFactory.kmsProviderType } returns "refresh-type"
        every { mockFactory.create(config, execution) } returns mockProvider

        try {
            val manager =
                KmsProviderManagerImpl(
                    factories = setOf(mockFactory),
                    binder = mockBinder,
                    snapshotCache = snapshotCache,
                    appLogManager = app.appLogManager,
                )

            val bootstrapProviders =
                ConfigBootstrapGuard.withContextRegistration {
                    manager.createFromProperties(configService, execution)
                }
            assertTrue(bootstrapProviders.isEmpty())
            assertTrue(!refreshableSource.loaded)

            val refreshedProviders = manager.createFromProperties(configService, execution)

            assertEquals(1, refreshedProviders.size)
            verify(exactly = 2) { mockBinder.getKmsProviderConfigs(configService) }
            verify(exactly = 1) { mockFactory.create(config, execution) }
        } finally {
            configService.removePropertySource(refreshableSource)
            snapshotCache.clear()
        }
    }

    @Test
    fun kmsProviderManagerRefreshIsNotBlockedByUnrelatedContextRegistration() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val snapshotCache = (app as DefaultSyncConfigSnapshotCache.Graph).syncConfigSnapshotCache
        val execution = session.asCoreApiServiceGraph().serviceExecution
        val refreshableSource = DeferredRefreshPropertySource("deferred-kms-provider-config-cross-thread")
        val mockBinder = mockk<KmsProviderConfigBinder>()
        val mockFactory = mockk<KmsProviderFactory>()
        val mockProvider = mockk<KmsProvider>()
        val config =
            KmsProviderConfig(
                id = "remote-provider-cross-thread",
                kmsProviderType = "refresh-type",
            )
        val registrationStarted = CountDownLatch(1)
        val releaseRegistration = CountDownLatch(1)

        snapshotCache.clear()
        configService.addPropertySource(refreshableSource)
        every { mockBinder.getKmsProviderConfigs(configService) } answers {
            if (refreshableSource.loaded) arrayOf(config) else emptyArray<KmsProviderConfigBase>()
        }
        every { mockFactory.kmsProviderType } returns "refresh-type"
        every { mockFactory.create(config, execution) } returns mockProvider

        val registrationThread =
            Thread {
                ConfigBootstrapGuard.withContextRegistration {
                    registrationStarted.countDown()
                    releaseRegistration.await(5, TimeUnit.SECONDS)
                }
            }.apply {
                name = "kms-provider-unrelated-registration-test"
                start()
            }

        try {
            assertTrue(registrationStarted.await(5, TimeUnit.SECONDS))
            val manager =
                KmsProviderManagerImpl(
                    factories = setOf(mockFactory),
                    binder = mockBinder,
                    snapshotCache = snapshotCache,
                    appLogManager = app.appLogManager,
                )

            val providers = manager.createFromProperties(configService, execution)

            assertEquals(1, providers.size)
            assertTrue(refreshableSource.loaded)
            verify(exactly = 1) { mockBinder.getKmsProviderConfigs(configService) }
            verify(exactly = 1) { mockFactory.create(config, execution) }
        } finally {
            releaseRegistration.countDown()
            registrationThread.join(5_000)
            configService.removePropertySource(refreshableSource)
            snapshotCache.clear()
        }
    }

    // =========== KmsProviderConfigBinderImpl Tests ===========

    @Test
    fun kmsProviderConfigBinderShouldBeAvailable() {
        app as JvmCryptoTestAppGraph
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        assertNotNull(binder)
    }

    // =========== KeyStoreConfigBinderImpl Branch Tests ===========

    @Test
    fun keyStoreConfigBinderShouldFindKeystoreIdsWithTypeSuffix() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KeyStoreConfigBinder.Graph).keyStoreConfigBinder

        // Clear cache before adding test properties (cache may have empty results from initialization)
        clearConfigCache()

        // Use DefaultAppMapPropertySource which is already in the property sources
        DefaultAppMapPropertySource.addProperty("kms.keystores.test-ks-1.type", "memory")
        DefaultAppMapPropertySource.addProperty("kms.keystores.test-ks-1.id", "test-ks-1")
        DefaultAppMapPropertySource.addProperty("kms.keystores.test-ks-2.type", "memory")

        try {
            val ids = binder.getKeyStoreIds(configService)
            assertTrue(ids.contains("test-ks-1") || ids.contains("test.ks.1"), "Should find test-ks-1")
            assertTrue(ids.contains("test-ks-2") || ids.contains("test.ks.2"), "Should find test-ks-2")
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.test-ks-1.type")
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.test-ks-1.id")
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.test-ks-2.type")
            clearConfigCache() // Clear cache after cleanup
        }
    }

    @Test
    fun keyStoreConfigBinderShouldNotFindPropertiesWithoutTypeSuffix() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KeyStoreConfigBinder.Graph).keyStoreConfigBinder

        clearConfigCache()

        // Add test properties WITHOUT .type suffix
        DefaultAppMapPropertySource.addProperty("kms.keystores.no-type-ks.name", "some-name")
        DefaultAppMapPropertySource.addProperty("kms.keystores.no-type-ks.value", "some-value")

        try {
            val ids = binder.getKeyStoreIds(configService)
            assertTrue(!ids.contains("no-type-ks") && !ids.contains("no.type.ks"), "Should NOT find no-type-ks")
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.no-type-ks.name")
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.no-type-ks.value")
            clearConfigCache()
        }
    }

    @Test
    fun keyStoreConfigBinderShouldReadConfigWithTypeProperty() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KeyStoreConfigBinder.Graph).keyStoreConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.keystores.my-memory-ks.type", "memory")
        DefaultAppMapPropertySource.addProperty("kms.keystores.my-memory-ks.enabled", true)

        try {
            val config = binder.getKeyStoreConfig(configService, "my-memory-ks")
            assertEquals("memory", config.keyStoreType)
            assertTrue(config.enabled)
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.my-memory-ks.type")
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.my-memory-ks.enabled")
            clearConfigCache()
        }
    }

    @Test
    fun keyStoreConfigBinderShouldReadLowercaseFlatPropertyNames() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KeyStoreConfigBinder.Graph).keyStoreConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.keystores.lowercase-flat-ks.type", "memory")
        DefaultAppMapPropertySource.addProperty("kms.keystores.lowercase-flat-ks.keyvisibility", "private")
        DefaultAppMapPropertySource.addProperty("kms.keystores.lowercase-flat-ks.overwritealias", true)

        try {
            val config = binder.getKeyStoreConfig(configService, "lowercase-flat-ks")
            assertEquals("memory", config.keyStoreType)
            assertEquals("private", config.keyVisibility)
            assertTrue(config.overwriteAlias)
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.lowercase-flat-ks.type")
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.lowercase-flat-ks.keyvisibility")
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.lowercase-flat-ks.overwritealias")
            clearConfigCache()
        }
    }

    @Test
    fun keyStoreConfigBinderShouldReadLegacyKeyStoreTypeProperty() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KeyStoreConfigBinder.Graph).keyStoreConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.keystores.legacy-ks.keyStoreType", "memory")
        DefaultAppMapPropertySource.addProperty("kms.keystores.legacy-ks.keyVisibility", "private")

        try {
            val config = binder.getKeyStoreConfig(configService, "legacy-ks")
            assertEquals("memory", config.keyStoreType)
            assertEquals("private", config.keyVisibility)
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.legacy-ks.keyStoreType")
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.legacy-ks.keyVisibility")
            clearConfigCache()
        }
    }

    @Test
    fun keyStoreConfigBinderShouldSetIdFromProviderIdWhenNotInProperties() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KeyStoreConfigBinder.Graph).keyStoreConfigBinder

        clearConfigCache()

        // Add config WITHOUT explicit id property - tests the branch where id defaults to providerId
        DefaultAppMapPropertySource.addProperty("kms.keystores.auto-id-ks.type", "memory")
        // Note: NOT adding kms.keystores.auto-id-ks.id

        try {
            val config = binder.getKeyStoreConfig(configService, "auto-id-ks")
            assertEquals("memory", config.keyStoreType)
            // The id should be set to the providerId (normalized to auto.id.ks)
            assertTrue(config.id == "auto-id-ks" || config.id == "auto.id.ks", "ID should be auto-id-ks or auto.id.ks but was ${config.id}")
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.auto-id-ks.type")
            clearConfigCache()
        }
    }

    @Test
    fun keyStoreConfigBinderShouldThrowWhenTypeIsMissing() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KeyStoreConfigBinder.Graph).keyStoreConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.keystores.missing-type-ks.name", "test")
        // No type property!

        try {
            val exception =
                assertFailsWith<IllegalArgumentException> {
                    binder.getKeyStoreConfig(configService, "missing-type-ks")
                }
            assertTrue(exception.message?.contains("type") == true)
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.missing-type-ks.name")
            clearConfigCache()
        }
    }

    @Test
    fun keyStoreConfigBinderShouldFilterDisabledConfigs() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KeyStoreConfigBinder.Graph).keyStoreConfigBinder

        clearConfigCache()

        // Enabled keystore
        DefaultAppMapPropertySource.addProperty("kms.keystores.enabled-ks.type", "memory")
        DefaultAppMapPropertySource.addProperty("kms.keystores.enabled-ks.enabled", true)
        // Disabled keystore
        DefaultAppMapPropertySource.addProperty("kms.keystores.disabled-ks.type", "memory")
        DefaultAppMapPropertySource.addProperty("kms.keystores.disabled-ks.enabled", false)

        try {
            val configs = binder.getKeyStoreConfigs(configService)
            val enabledIds = configs.map { it.id }
            assertTrue(enabledIds.any { it.contains("enabled") && !it.contains("disabled") }, "Should include enabled keystore")
            assertTrue(enabledIds.none { it.contains("disabled-ks") || it.contains("disabled.ks") }, "Should NOT include disabled keystore")
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.enabled-ks.type")
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.enabled-ks.enabled")
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.disabled-ks.type")
            DefaultAppMapPropertySource.deleteProperty("kms.keystores.disabled-ks.enabled")
            clearConfigCache()
        }
    }

    // =========== KmsProviderConfigBinderImpl Branch Tests ===========

    @Test
    fun kmsProviderConfigBinderShouldFindProviderIdsWithTypeSuffix() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.providers.test-prov-1.type", "software")
        DefaultAppMapPropertySource.addProperty("kms.providers.test-prov-2.type", "software")

        try {
            val ids = binder.getKmsProviderIds(configService)
            assertTrue(ids.contains("test-prov-1") || ids.contains("test.prov.1"), "Should find test-prov-1")
            assertTrue(ids.contains("test-prov-2") || ids.contains("test.prov.2"), "Should find test-prov-2")
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.providers.test-prov-1.type")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.test-prov-2.type")
            clearConfigCache()
        }
    }

    @Test
    fun kmsProviderConfigBinderShouldNotFindKeystoreTypeProperties() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        clearConfigCache()

        // Add properties with keystore.type suffix (should be filtered out)
        DefaultAppMapPropertySource.addProperty("kms.providers.prov-with-ks.keystore.type", "memory")
        DefaultAppMapPropertySource.addProperty("kms.providers.prov-with-ks.type", "software") // This should be found

        try {
            val ids = binder.getKmsProviderIds(configService)
            // Should find via .type but the keystore.type should not create a separate entry
            assertTrue(ids.any { it.contains("prov-with-ks") || it.contains("prov.with.ks") }, "Should find provider via .type")
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.providers.prov-with-ks.keystore.type")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.prov-with-ks.type")
            clearConfigCache()
        }
    }

    @Test
    fun kmsProviderConfigBinderShouldReadConfigWithTypeProperty() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.providers.my-software-prov.type", "software")
        DefaultAppMapPropertySource.addProperty("kms.providers.my-software-prov.enabled", true)

        try {
            val config = binder.getKmsProviderConfig(configService, "my-software-prov")
            assertEquals("software", config.kmsProviderType)
            assertTrue(config.enabled)
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.providers.my-software-prov.type")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.my-software-prov.enabled")
            clearConfigCache()
        }
    }

    @Test
    fun kmsProviderConfigBinderShouldIgnoreTenantAdminProviderMetadata() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.providers.platform.id", "platform")
        DefaultAppMapPropertySource.addProperty("kms.providers.platform.type", "software")
        DefaultAppMapPropertySource.addProperty("kms.providers.platform.enabled", "true")
        DefaultAppMapPropertySource.addProperty("kms.providers.platform.system", "true")
        DefaultAppMapPropertySource.addProperty("kms.providers.platform.role", "PLATFORM_AUTHORIZATION_SERVER")
        DefaultAppMapPropertySource.addProperty("kms.providers.platform.autoCreateCertificate", "true")

        try {
            val config = binder.getKmsProviderConfig(configService, "platform")
            assertEquals("platform", config.id)
            assertEquals("software", config.kmsProviderType)
            assertTrue(config.enabled)
            assertTrue(config is SoftwareKmsProviderConfig)
            assertTrue(config.autoCreateCertificate)

            val configs = binder.getKmsProviderConfigs(configService)
            assertTrue(
                configs.any { it.id == "platform" && it.enabled },
                "batch provider binding should include the metadata-backed platform provider",
            )
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.providers.platform.id")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.platform.type")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.platform.enabled")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.platform.system")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.platform.role")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.platform.autoCreateCertificate")
            clearConfigCache()
        }
    }

    @Test
    fun kmsProviderConfigBinderShouldReadLowercaseFlatPropertyNames() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.providers.lowercase-flat-prov.type", "software")
        DefaultAppMapPropertySource.addProperty("kms.providers.lowercase-flat-prov.exposeprivatekeysduringgeneration", true)
        DefaultAppMapPropertySource.addProperty("kms.providers.lowercase-flat-prov.persistkeysduringgeneration", false)
        DefaultAppMapPropertySource.addProperty("kms.providers.lowercase-flat-prov.autocreatecertificate", true)
        DefaultAppMapPropertySource.addProperty("kms.providers.lowercase-flat-prov.keystore.type", "memory")
        DefaultAppMapPropertySource.addProperty("kms.providers.lowercase-flat-prov.keystore.keyvisibility", "private")
        DefaultAppMapPropertySource.addProperty("kms.providers.lowercase-flat-prov.keystore.overwritealias", true)

        try {
            val config = binder.getKmsProviderConfig(configService, "lowercase-flat-prov")
            assertEquals("software", config.kmsProviderType)
            assertTrue(config.exposePrivateKeysDuringGeneration)
            assertTrue(!config.persistKeysDuringGeneration)
            assertTrue(config.id == "lowercase-flat-prov" || config.id == "lowercase.flat.prov")

            assertTrue(config is SoftwareKmsProviderConfig)
            config as SoftwareKmsProviderConfig
            assertTrue(config.autoCreateCertificate)
            assertEquals("private", config.keyStore.keyVisibility)
            assertTrue(config.keyStore.overwriteAlias)
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.providers.lowercase-flat-prov.type")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.lowercase-flat-prov.exposeprivatekeysduringgeneration")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.lowercase-flat-prov.persistkeysduringgeneration")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.lowercase-flat-prov.autocreatecertificate")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.lowercase-flat-prov.keystore.type")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.lowercase-flat-prov.keystore.keyvisibility")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.lowercase-flat-prov.keystore.overwritealias")
            clearConfigCache()
        }
    }

    @Test
    fun kmsProviderConfigBinderShouldReadLegacyNestedKeyStoreTypeProperty() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.providers.legacy-nested-ks.type", "software")
        DefaultAppMapPropertySource.addProperty("kms.providers.legacy-nested-ks.keyStore.keyStoreType", "memory")
        DefaultAppMapPropertySource.addProperty("kms.providers.legacy-nested-ks.keyStore.keyVisibility", "private")

        try {
            val config = binder.getKmsProviderConfig(configService, "legacy-nested-ks")
            assertEquals("software", config.kmsProviderType)
            assertTrue(config is SoftwareKmsProviderConfig)
            config as SoftwareKmsProviderConfig
            assertEquals("memory", config.keyStore.keyStoreType)
            assertEquals("private", config.keyStore.keyVisibility)
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.providers.legacy-nested-ks.type")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.legacy-nested-ks.keyStore.keyStoreType")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.legacy-nested-ks.keyStore.keyVisibility")
            clearConfigCache()
        }
    }

    @Test
    fun kmsProviderConfigBinderShouldSetIdFromProviderIdWhenNotInProperties() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        clearConfigCache()

        // Add config WITHOUT explicit id property - tests the branch where id defaults to providerId
        DefaultAppMapPropertySource.addProperty("kms.providers.auto-id-prov.type", "software")
        // Note: NOT adding kms.providers.auto-id-prov.id

        try {
            val config = binder.getKmsProviderConfig(configService, "auto-id-prov")
            assertEquals("software", config.kmsProviderType)
            // The id should be set to the providerId (normalized to auto.id.prov)
            assertTrue(config.id == "auto-id-prov" || config.id == "auto.id.prov", "ID should be auto-id-prov or auto.id.prov but was ${config.id}")
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.providers.auto-id-prov.type")
            clearConfigCache()
        }
    }

    @Test
    fun kmsProviderConfigBinderShouldPreserveExplicitCanonicalProviderId() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.providers.license.id", "license")
        DefaultAppMapPropertySource.addProperty("kms.providers.license.type", "software")
        DefaultAppMapPropertySource.addProperty("kms.providers.license.enabled", true)

        try {
            val ids = binder.getKmsProviderIds(configService).toSet()
            val configs = binder.getKmsProviderConfigs(configService)

            assertTrue("license" in ids, "explicit provider id must be preserved in provider ids: $ids")
            assertTrue(configs.any { it.id == "license" }, "explicit provider id must be preserved in provider configs: ${configs.map { it.id }}")
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.providers.license.id")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.license.type")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.license.enabled")
            clearConfigCache()
        }
    }

    @Test
    fun kmsProviderConfigBinderShouldThrowWhenTypeIsMissing() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        clearConfigCache()

        DefaultAppMapPropertySource.addProperty("kms.providers.missing-type-prov.name", "test")
        // No type property!

        try {
            val exception =
                assertFailsWith<IllegalArgumentException> {
                    binder.getKmsProviderConfig(configService, "missing-type-prov")
                }
            assertTrue(exception.message?.contains("type") == true)
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.providers.missing-type-prov.name")
            clearConfigCache()
        }
    }

    @Test
    fun kmsProviderConfigBinderShouldFilterDisabledConfigs() {
        app as JvmCryptoTestAppGraph
        val configService = (app as AppConfigService.Graph).appConfigService
        val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder

        clearConfigCache()

        // Enabled provider
        DefaultAppMapPropertySource.addProperty("kms.providers.enabled-prov.type", "software")
        DefaultAppMapPropertySource.addProperty("kms.providers.enabled-prov.enabled", true)
        // Disabled provider
        DefaultAppMapPropertySource.addProperty("kms.providers.disabled-prov.type", "software")
        DefaultAppMapPropertySource.addProperty("kms.providers.disabled-prov.enabled", false)

        try {
            val configs = binder.getKmsProviderConfigs(configService)
            val enabledIds = configs.map { it.id }
            assertTrue(enabledIds.any { it.contains("enabled") && !it.contains("disabled") }, "Should include enabled provider")
            assertTrue(enabledIds.none { it.contains("disabled-prov") || it.contains("disabled.prov") }, "Should NOT include disabled provider")
        } finally {
            DefaultAppMapPropertySource.deleteProperty("kms.providers.enabled-prov.type")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.enabled-prov.enabled")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.disabled-prov.type")
            DefaultAppMapPropertySource.deleteProperty("kms.providers.disabled-prov.enabled")
            clearConfigCache()
        }
    }

    // =========== KeyStoreConfigBinderImpl Graph Access Test ===========

    @Test
    fun keyStoreConfigBinderShouldBeAvailable() {
        app as JvmCryptoTestAppGraph
        val binder = (app as KeyStoreConfigBinder.Graph).keyStoreConfigBinder

        assertNotNull(binder)
    }
}

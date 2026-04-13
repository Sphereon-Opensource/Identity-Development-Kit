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

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.JvmCryptoTestAppGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.createJvmCryptoTestAppGraph
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.core.kms.KeyStoreConfigImpl
import com.sphereon.crypto.core.kms.KeyStoreManager
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.crypto.kms.keystore.memory.MemoryKeyStoreConfig
import com.sphereon.crypto.kms.keystore.memory.MemoryKeyStoreScopeBinding
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Extension to access KeyStoreManager from app graph.
 */
fun Any.asKeyStoreManagerGraph(): KeyStoreManager.Graph = this as KeyStoreManager.Graph

/**
 * Tests for KeyStoreManager implementation.
 */
class KeyStoreManagerTest {
    private lateinit var keyStoreManager: KeyStoreManager

    val app = createJvmCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("keystore-manager-test")

    @BeforeTest
    fun setUp() {
        app as JvmCryptoTestAppGraph
        keyStoreManager = (app as KeyStoreManager.Graph).keyStoreManager
    }

    // =========== createFromKeyStoreConfig Tests ===========

    @Test
    fun createFromKeyStoreConfigShouldCreateMemoryKeyStore() =
        runTest {
            val config =
                MemoryKeyStoreConfig(
                    id = "test-memory-keystore",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                    scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
                )

            val keyStore = keyStoreManager.createFromKeyStoreConfig(config, session.asCoreApiServiceGraph().serviceExecution)

            assertNotNull(keyStore)
            assertEquals("test-memory-keystore", keyStore.id)
            assertEquals(PredefinedKeyStoreTypes.MEMORY.keyStoreType, keyStore.keyStoreType)
        }

    @Test
    fun createFromKeyStoreConfigWithoutExecutionShouldWork() =
        runTest {
            val config =
                MemoryKeyStoreConfig(
                    id = "test-memory-keystore-no-exec",
                    keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                    overwriteAlias = false,
                )

            val keyStore = keyStoreManager.createFromKeyStoreConfig(config)

            assertNotNull(keyStore)
            assertEquals("test-memory-keystore-no-exec", keyStore.id)
        }

    @Test
    fun createFromKeyStoreConfigShouldThrowForUnknownType() {
        val invalidConfig =
            KeyStoreConfigImpl(
                id = "invalid-keystore",
                keyStoreType = "unknown-type-xyz",
            )

        assertFailsWith<IllegalArgumentException> {
            keyStoreManager.createFromKeyStoreConfig(invalidConfig)
        }
    }

    // =========== KeyStore Functionality Tests ===========

    @Test
    fun memoryKeyStoreShouldSupportKeyOperations() =
        runTest {
            val config =
                MemoryKeyStoreConfig(
                    id = "functional-test-keystore",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    overwriteAlias = true,
                    scopeBinding = MemoryKeyStoreScopeBinding.SESSION.value,
                )

            val keyStore = keyStoreManager.createFromKeyStoreConfig(config, session.asCoreApiServiceGraph().serviceExecution)

            // Verify key types and signature algorithms are supported
            assertTrue(keyStore.keyTypesSupported.isNotEmpty(), "Should support some key types")
            assertTrue(keyStore.signatureAlgorithmsSupported.isNotEmpty(), "Should support some signature algorithms")
        }

    @Test
    fun memoryKeyStoreWithAppScopeShouldWork() =
        runTest {
            val config =
                MemoryKeyStoreConfig(
                    id = "app-scoped-keystore",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    scopeBinding = MemoryKeyStoreScopeBinding.APP.value,
                )

            val keyStore = keyStoreManager.createFromKeyStoreConfig(config, session.asCoreApiServiceGraph().serviceExecution)

            assertNotNull(keyStore)
            assertEquals(KeyVisibility.PRIVATE, keyStore.keyVisibility())
        }

    @Test
    fun memoryKeyStoreWithTenantScopeShouldWork() =
        runTest {
            val config =
                MemoryKeyStoreConfig(
                    id = "tenant-scoped-keystore",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    scopeBinding = MemoryKeyStoreScopeBinding.TENANT.value,
                )

            val keyStore = keyStoreManager.createFromKeyStoreConfig(config, session.asCoreApiServiceGraph().serviceExecution)

            assertNotNull(keyStore)
        }

    @Test
    fun memoryKeyStoreWithPrincipalTenantScopeShouldWork() =
        runTest {
            val config =
                MemoryKeyStoreConfig(
                    id = "principal-tenant-scoped-keystore",
                    keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    scopeBinding = MemoryKeyStoreScopeBinding.PRINCIPAL_TENANT.value,
                )

            val keyStore = keyStoreManager.createFromKeyStoreConfig(config, session.asCoreApiServiceGraph().serviceExecution)

            assertNotNull(keyStore)
        }

    @Test
    fun memoryKeyStorePublicVisibilityShouldWork() =
        runTest {
            val config =
                MemoryKeyStoreConfig(
                    id = "public-visibility-keystore",
                    keyVisibility = KeyVisibility.PUBLIC.keyVisibility,
                )

            val keyStore = keyStoreManager.createFromKeyStoreConfig(config, session.asCoreApiServiceGraph().serviceExecution)

            assertNotNull(keyStore)
            assertEquals(KeyVisibility.PUBLIC, keyStore.keyVisibility())
        }

    // =========== Empty Factories Edge Case Tests ===========

    @Test
    fun keyStoreManagerShouldThrowWhenNoFactoriesRegistered() {
        // Create a manager with empty factories to test the empty factories branch
        val mockBinder = mockk<KeyStoreConfigBinder>()
        val managerWithNoFactories = KeyStoreManagerImpl(emptySet(), mockBinder)

        val config =
            MemoryKeyStoreConfig(
                id = "test-keystore",
                keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
            )

        val exception =
            assertFailsWith<IllegalArgumentException> {
                managerWithNoFactories.createFromKeyStoreConfig(config)
            }

        assertTrue(exception.message?.contains("No keystore factories found") == true)
    }
}

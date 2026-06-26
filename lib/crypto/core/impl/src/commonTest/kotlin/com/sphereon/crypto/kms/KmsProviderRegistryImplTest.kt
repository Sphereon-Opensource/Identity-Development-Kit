/*
 * Copyright (c) 2026 Sphereon International B.V.
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

package com.sphereon.crypto.kms

import com.sphereon.core.api.conf.MutableMapPropertySource
import com.sphereon.core.api.conf.RefreshablePropertySource
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.kms.KmsProviderRegistryGraph
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import dev.whyoleg.cryptography.CryptographyProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmsProviderRegistryImplTest {
    @Test
    fun resolvesProviderAddedAfterInitialSnapshot() {
        val app = createCryptoTestAppGraph(this)
        val context = app.userContextManager.getAnonymous()
        val session = context.sessionContextManager.createOrGetFromId("kms-registry-refresh-test")
        val principalConfig = session.asCoreApiServiceGraph().serviceExecution.conf.principal

        principalConfig.addPropertySource(kmsProviderSource("initial-kms-provider", "snapshot-provider"))
        val registry = (session.graph as KmsProviderRegistryGraph).kmsProviderRegistry

        assertEquals("snapshot-provider", registry.getProviderById("snapshot-provider").id)

        principalConfig.addPropertySource(kmsProviderSource("late-kms-provider", "late-tenant-provider"))

        assertEquals("late-tenant-provider", registry.getProviderById("late-tenant-provider").id)
        assertTrue(registry.getProviderIds().contains("late-tenant-provider"))
    }

    @Test
    fun resolvesProviderAddedByRefreshableConfigSourceWithoutStructuralSourceChange() {
        val app = createCryptoTestAppGraph(this)
        val context = app.userContextManager.getAnonymous()
        val session = context.sessionContextManager.createOrGetFromId("kms-registry-refreshable-source-test")
        val principalConfig = session.asCoreApiServiceGraph().serviceExecution.conf.principal

        val source = RefreshableKmsProviderSource("refreshable-kms-provider", "snapshot-provider")
        principalConfig.addPropertySource(source)
        val registry = (session.graph as KmsProviderRegistryGraph).kmsProviderRegistry

        assertEquals("snapshot-provider", registry.getProviderById("snapshot-provider").id)

        source.publishProviderOnNextRefresh("late-refresh-provider")

        assertTrue(registry.getProviderIds().contains("late-refresh-provider"))
        assertEquals("late-refresh-provider", registry.getProviderById("late-refresh-provider").id)
    }

    @Test
    fun resolvesProviderAddedByRefreshableConfigSourceAfterInitialEmptySnapshot() {
        val app = createCryptoTestAppGraph(this)
        val context = app.userContextManager.getAnonymous()
        val session = context.sessionContextManager.createOrGetFromId("kms-registry-refreshable-empty-source-test")
        val principalConfig = session.asCoreApiServiceGraph().serviceExecution.conf.principal

        val source = RefreshableKmsProviderSource("refreshable-empty-kms-provider")
        principalConfig.addPropertySource(source)
        val registry = (session.graph as KmsProviderRegistryGraph).kmsProviderRegistry

        assertTrue(registry.getProviderIds().isEmpty())

        source.publishProviderOnNextRefresh("tenant-slug-provider")

        assertTrue(registry.getProviderIds().contains("tenant-slug-provider"))
        assertEquals("tenant-slug-provider", registry.getProviderById("tenant-slug-provider").id)
    }

    @Test
    fun removesConfigManagedProviderDisabledByRefreshableConfigSource() {
        val app = createCryptoTestAppGraph(this)
        val context = app.userContextManager.getAnonymous()
        val session = context.sessionContextManager.createOrGetFromId("kms-registry-disable-refreshable-source-test")
        val principalConfig = session.asCoreApiServiceGraph().serviceExecution.conf.principal

        val source = RefreshableKmsProviderSource("refreshable-kms-provider", "software")
        principalConfig.addPropertySource(source)
        val registry = (session.graph as KmsProviderRegistryGraph).kmsProviderRegistry

        assertTrue(registry.getProviderIds().contains("software"))

        source.disableProviderOnNextRefresh("software")

        assertFalse(
            registry.getProviderIds().contains("software"),
            "config-managed providers must be removed when platform/local config disables them",
        )
    }

    private fun kmsProviderSource(
        sourceName: String,
        providerId: String,
    ): MutableMapPropertySource =
        MutableMapPropertySource(sourceName)
            .addProperties(
                mapOf(
                    "kms.providers.$providerId.type" to "software",
                    "kms.providers.$providerId.id" to providerId,
                    "kms.providers.$providerId.enabled" to true,
                    "kms.providers.$providerId.cryptographyProvider" to CryptographyProvider.Default.name,
                ),
            )
}

private class RefreshableKmsProviderSource(
    sourceName: String,
    initialProviderId: String? = null,
) : MutableMapPropertySource(sourceName),
    RefreshablePropertySource {
    override var contentRevision: Long = 0L
        private set

    private var nextProviderId: String? = null
    private var providerIdToDisable: String? = null

    init {
        initialProviderId?.let { addProvider(it) }
    }

    fun publishProviderOnNextRefresh(providerId: String) {
        nextProviderId = providerId
    }

    fun disableProviderOnNextRefresh(providerId: String) {
        providerIdToDisable = providerId
    }

    override fun refreshIfNeeded() {
        val providerId = nextProviderId
        val disabledProviderId = providerIdToDisable
        if (providerId == null && disabledProviderId == null) return
        nextProviderId = null
        providerIdToDisable = null
        providerId?.let { addProvider(it) }
        disabledProviderId?.let {
            addProperty("kms.providers.$it.enabled", false)
        }
        contentRevision += 1L
    }

    private fun addProvider(providerId: String) {
        addProperties(
            mapOf(
                "kms.providers.$providerId.type" to "software",
                "kms.providers.$providerId.id" to providerId,
                "kms.providers.$providerId.enabled" to true,
                "kms.providers.$providerId.cryptographyProvider" to CryptographyProvider.Default.name,
            ),
        )
    }
}

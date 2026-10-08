/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.core.defaults.app

import com.sphereon.core.api.testutil.appConfigService
import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import com.sphereon.core.api.testutil.crossContextOperations
import com.sphereon.di.app.AbstractAppGraph
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RootScopeProviderTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "root-scope-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== DefaultRootScopeProvider Tests ==========

    @Test
    fun rootScopeProviderCanBeCreated() {
        val provider = DefaultRootScopeProvider()
        assertNotNull(provider)
    }

    @Test
    fun rootScopeProviderIsDestroyedInitially() {
        val provider = DefaultRootScopeProvider()
        assertTrue(provider.isDestroyed())
    }

    @Test
    fun rootScopeThrowsBeforeCreate() {
        val provider = DefaultRootScopeProvider()
        assertFailsWith<IllegalStateException> {
            provider.rootScope
        }
    }

    @Test
    fun rootScopeProviderIntegrationTest() {
        val appGraph = createAppGraph()
        try {
            // AppGraph should have a working root scope provider
            val rootScopeProvider = appGraph.rootScopeProvider
            assertNotNull(rootScopeProvider)
            assertFalse(rootScopeProvider.isDestroyed())
            assertNotNull(rootScopeProvider.rootScope)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun rootScopeProviderIsDestroyedAfterDestroy() {
        val appGraph = createAppGraph()
        appGraph.destroy()

        // After destroy, should be destroyed
        assertTrue(appGraph.rootScopeProvider.isDestroyed())
    }

    // ========== AppImpl Tests ==========

    @Test
    fun appGraphCanBeCreated() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appGraphHasRootScopeProvider() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.rootScopeProvider)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appGraphHasUserContextManager() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.userContextManager)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appGraphHasAppConfigService() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.appConfigService)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appGraphHasCrossContextOperations() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.crossContextOperations)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appGraphCanInitRootScopeProvider() {
        // Create without init
        val appGraph =
            createCoreApiTestAppGraph(
                this,
                "init-test",
                "test-profile",
                "0.0.1-TEST",
            )

        // Note: createCoreApiTestAppGraph already calls initRootScopeProvider internally
        // so the root scope provider should be ready
        assertFalse(appGraph.rootScopeProvider.isDestroyed())

        appGraph.destroy()

        // After destroy, root scope provider should be destroyed
        assertTrue(appGraph.rootScopeProvider.isDestroyed())

        // Re-init
        (appGraph as AbstractAppGraph).initRootScopeProvider()

        // After re-init, should be ready again
        assertFalse(appGraph.rootScopeProvider.isDestroyed())

        appGraph.destroy()
    }

    @Test
    fun appGraphDestroyWorks() {
        val appGraph = createAppGraph()
        assertFalse(appGraph.rootScopeProvider.isDestroyed())

        appGraph.destroy()
        assertTrue(appGraph.rootScopeProvider.isDestroyed())
    }
}

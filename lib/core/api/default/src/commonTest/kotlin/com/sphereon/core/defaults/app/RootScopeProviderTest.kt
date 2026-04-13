/*
 * (c) 2025 Sphereon International B.V.
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
import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.core.api.testutil.crossContextOperations
import com.sphereon.di.app.AbstractAppComponent
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RootScopeProviderTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "root-scope-test", "test-profile", "0.0.1-TEST"
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
        val appComponent = createAppComponent()
        try {
            // AppComponent should have a working root scope provider
            val rootScopeProvider = appComponent.rootScopeProvider
            assertNotNull(rootScopeProvider)
            assertFalse(rootScopeProvider.isDestroyed())
            assertNotNull(rootScopeProvider.rootScope)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun rootScopeProviderIsDestroyedAfterDestroy() {
        val appComponent = createAppComponent()
        appComponent.destroy()

        // After destroy, should be destroyed
        assertTrue(appComponent.rootScopeProvider.isDestroyed())
    }

    // ========== AppImpl Tests ==========

    @Test
    fun appComponentCanBeCreated() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appComponentHasRootScopeProvider() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.rootScopeProvider)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appComponentHasUserContextManager() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.userContextManager)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appComponentHasAppConfigService() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.appConfigService)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appComponentHasCrossContextOperations() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.crossContextOperations)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appComponentCanInitRootScopeProvider() {
        // Create without init
        val appComponent = createCoreApiTestAppComponent(
            this, "init-test", "test-profile", "0.0.1-TEST"
        )

        // Note: createCoreApiTestAppComponent already calls initRootScopeProvider internally
        // so the root scope provider should be ready
        assertFalse(appComponent.rootScopeProvider.isDestroyed())

        appComponent.destroy()

        // After destroy, root scope provider should be destroyed
        assertTrue(appComponent.rootScopeProvider.isDestroyed())

        // Re-init
        (appComponent as AbstractAppComponent).initRootScopeProvider()

        // After re-init, should be ready again
        assertFalse(appComponent.rootScopeProvider.isDestroyed())

        appComponent.destroy()
    }

    @Test
    fun appComponentDestroyWorks() {
        val appComponent = createAppComponent()
        assertFalse(appComponent.rootScopeProvider.isDestroyed())

        appComponent.destroy()
        assertTrue(appComponent.rootScopeProvider.isDestroyed())
    }
}

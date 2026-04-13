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

import com.sphereon.core.api.testutil.app
import com.sphereon.core.api.testutil.appConfigService
import com.sphereon.core.api.testutil.appLogger
import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.app.AbstractAppGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppImplTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "app-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== AppImpl Tests ==========

    @Test
    fun appImplHasAppId() {
        val appGraph = createAppGraph()
        try {
            assertEquals("app-test", appGraph.appId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appImplHasProfile() {
        val appGraph = createAppGraph()
        try {
            assertEquals("test-profile", appGraph.profile)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appImplHasVersion() {
        val appGraph = createAppGraph()
        try {
            assertEquals("0.0.1-TEST", appGraph.version)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appImplHasApp() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.app)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appImplHasApplication() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.application)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appImplHasPlatformInfo() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.platformInfo)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== RootScopeProvider Tests ==========

    @Test
    fun rootScopeProviderExists() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.rootScopeProvider)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun rootScopeProviderHasRootScope() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.rootScopeProvider.rootScope)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun rootScopeProviderIsNotDestroyedInitially() {
        val appGraph = createAppGraph()
        try {
            assertFalse(appGraph.rootScopeProvider.isDestroyed())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun rootScopeProviderIsDestroyedAfterDestroy() {
        val appGraph = createAppGraph()
        appGraph.destroy()
        assertTrue(appGraph.rootScopeProvider.isDestroyed())
    }

    @Test
    fun rootScopeProviderCanBeReinitialized() {
        val appGraph = createAppGraph()
        appGraph.destroy()
        assertTrue(appGraph.rootScopeProvider.isDestroyed())

        (appGraph as AbstractAppGraph).initRootScopeProvider()
        assertFalse(appGraph.rootScopeProvider.isDestroyed())

        appGraph.destroy()
    }

    // ========== Scope Hierarchy Tests ==========

    @Test
    fun rootScopeHasNoChildrenInitially() {
        val appGraph = createAppGraph()
        try {
            assertEquals(
                0,
                appGraph.rootScopeProvider.rootScope
                    .children()
                    .size,
            )
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun rootScopeHasChildAfterContextCreation() {
        val appGraph = createAppGraph()
        try {
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
            )
            assertEquals(
                1,
                appGraph.rootScopeProvider.rootScope
                    .children()
                    .size,
            )
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun rootScopeHasNoChildrenAfterContextDestruction() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertEquals(
                1,
                appGraph.rootScopeProvider.rootScope
                    .children()
                    .size,
            )

            appGraph.userContextManager.destroyById(contextInstance.contextId)
            assertEquals(
                0,
                appGraph.rootScopeProvider.rootScope
                    .children()
                    .size,
            )
        } finally {
            appGraph.destroy()
        }
    }

    // ========== UserContextManager Access Tests ==========

    @Test
    fun appGraphHasUserContextManager() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.userContextManager)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== AppConfigService Access Tests ==========

    @Test
    fun appGraphHasAppConfigService() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.appConfigService)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== App Logger Access Tests ==========

    @Test
    fun appGraphHasAppLogger() {
        val appGraph = createAppGraph()
        try {
            assertNotNull(appGraph.appLogger())
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Destroy Tests ==========

    @Test
    fun destroyCleanupContexts() {
        val appGraph = createAppGraph()
        appGraph.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user"),
        )
        assertTrue(appGraph.userContextManager.hasActive())

        appGraph.destroy()

        assertTrue(appGraph.rootScopeProvider.isDestroyed())
    }

    @Test
    fun destroyAllowsReinitialization() {
        val appGraph = createAppGraph()
        appGraph.destroy()

        // Can reinitialize
        (appGraph as AbstractAppGraph).initRootScopeProvider()
        assertFalse(appGraph.rootScopeProvider.isDestroyed())

        // Can create contexts again
        appGraph.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user"),
        )
        assertTrue(appGraph.userContextManager.hasActive())

        appGraph.destroy()
    }

    // ========== Multiple AppGraph Tests ==========

    @Test
    fun multipleAppGraphsCanCoexist() {
        val appGraph1 =
            createCoreApiTestAppGraph(
                this,
                "app-1",
                "profile-1",
                "1.0.0",
            )

        val appGraph2 =
            createCoreApiTestAppGraph(
                this,
                "app-2",
                "profile-2",
                "2.0.0",
            )

        try {
            assertEquals("app-1", appGraph1.appId)
            assertEquals("app-2", appGraph2.appId)
            assertEquals("1.0.0", appGraph1.version)
            assertEquals("2.0.0", appGraph2.version)
        } finally {
            appGraph1.destroy()
            appGraph2.destroy()
        }
    }

    // ========== Init Tests ==========

    @Test
    fun initMethodCreatesGraph() {
        val appGraph =
            createCoreApiTestAppGraph(
                testInstance = this,
                appId = "init-test",
                profile = "init-profile",
                version = "0.0.0-INIT",
            )
        try {
            assertNotNull(appGraph)
            assertEquals("init-test", appGraph.appId)
            assertEquals("init-profile", appGraph.profile)
            assertEquals("0.0.0-INIT", appGraph.version)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun initMethodInitializesRootScopeProvider() {
        val appGraph =
            createCoreApiTestAppGraph(
                testInstance = this,
                appId = "init-test",
                profile = "init-profile",
                version = "0.0.0-INIT",
            )
        try {
            assertNotNull(appGraph.rootScopeProvider)
            assertNotNull(appGraph.rootScopeProvider.rootScope)
            assertFalse(appGraph.rootScopeProvider.isDestroyed())
        } finally {
            appGraph.destroy()
        }
    }
}

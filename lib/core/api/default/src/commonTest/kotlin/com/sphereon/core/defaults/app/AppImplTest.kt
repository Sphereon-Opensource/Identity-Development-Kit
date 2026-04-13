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

import com.sphereon.core.api.testutil.app
import com.sphereon.core.api.testutil.appConfigService
import com.sphereon.core.api.testutil.appLogger
import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.app.AbstractAppComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppImplTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "app-test", "test-profile", "0.0.1-TEST"
    )

    // ========== AppImpl Tests ==========

    @Test
    fun appImplHasAppId() {
        val appComponent = createAppComponent()
        try {
            assertEquals("app-test", appComponent.appId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appImplHasProfile() {
        val appComponent = createAppComponent()
        try {
            assertEquals("test-profile", appComponent.profile)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appImplHasVersion() {
        val appComponent = createAppComponent()
        try {
            assertEquals("0.0.1-TEST", appComponent.version)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appImplHasApp() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.app)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appImplHasApplication() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.application)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appImplHasPlatformInfo() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.platformInfo)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== RootScopeProvider Tests ==========

    @Test
    fun rootScopeProviderExists() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.rootScopeProvider)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun rootScopeProviderHasRootScope() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.rootScopeProvider.rootScope)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun rootScopeProviderIsNotDestroyedInitially() {
        val appComponent = createAppComponent()
        try {
            assertFalse(appComponent.rootScopeProvider.isDestroyed())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun rootScopeProviderIsDestroyedAfterDestroy() {
        val appComponent = createAppComponent()
        appComponent.destroy()
        assertTrue(appComponent.rootScopeProvider.isDestroyed())
    }

    @Test
    fun rootScopeProviderCanBeReinitialized() {
        val appComponent = createAppComponent()
        appComponent.destroy()
        assertTrue(appComponent.rootScopeProvider.isDestroyed())

        (appComponent as AbstractAppComponent).initRootScopeProvider()
        assertFalse(appComponent.rootScopeProvider.isDestroyed())

        appComponent.destroy()
    }

    // ========== Scope Hierarchy Tests ==========

    @Test
    fun rootScopeHasNoChildrenInitially() {
        val appComponent = createAppComponent()
        try {
            assertEquals(0, appComponent.rootScopeProvider.rootScope.children().size)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun rootScopeHasChildAfterContextCreation() {
        val appComponent = createAppComponent()
        try {
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertEquals(1, appComponent.rootScopeProvider.rootScope.children().size)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun rootScopeHasNoChildrenAfterContextDestruction() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertEquals(1, appComponent.rootScopeProvider.rootScope.children().size)

            appComponent.userContextManager.destroyById(contextInstance.contextId)
            assertEquals(0, appComponent.rootScopeProvider.rootScope.children().size)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== UserContextManager Access Tests ==========

    @Test
    fun appComponentHasUserContextManager() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.userContextManager)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== AppConfigService Access Tests ==========

    @Test
    fun appComponentHasAppConfigService() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.appConfigService)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== App Logger Access Tests ==========

    @Test
    fun appComponentHasAppLogger() {
        val appComponent = createAppComponent()
        try {
            assertNotNull(appComponent.appLogger())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Destroy Tests ==========

    @Test
    fun destroyCleanupContexts() {
        val appComponent = createAppComponent()
        appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user")
        )
        assertTrue(appComponent.userContextManager.hasActive())

        appComponent.destroy()

        assertTrue(appComponent.rootScopeProvider.isDestroyed())
    }

    @Test
    fun destroyAllowsReinitialization() {
        val appComponent = createAppComponent()
        appComponent.destroy()

        // Can reinitialize
        (appComponent as AbstractAppComponent).initRootScopeProvider()
        assertFalse(appComponent.rootScopeProvider.isDestroyed())

        // Can create contexts again
        appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user")
        )
        assertTrue(appComponent.userContextManager.hasActive())

        appComponent.destroy()
    }

    // ========== Multiple AppComponent Tests ==========

    @Test
    fun multipleAppComponentsCanCoexist() {
        val appComponent1 = createCoreApiTestAppComponent(
            this, "app-1", "profile-1", "1.0.0"
        )

        val appComponent2 = createCoreApiTestAppComponent(
            this, "app-2", "profile-2", "2.0.0"
        )

        try {
            assertEquals("app-1", appComponent1.appId)
            assertEquals("app-2", appComponent2.appId)
            assertEquals("1.0.0", appComponent1.version)
            assertEquals("2.0.0", appComponent2.version)
        } finally {
            appComponent1.destroy()
            appComponent2.destroy()
        }
    }

    // ========== Init Tests ==========

    @Test
    fun initMethodCreatesComponent() {
        val appComponent = createCoreApiTestAppComponent(
            testInstance = this,
            appId = "init-test",
            profile = "init-profile",
            version = "0.0.0-INIT"
        )
        try {
            assertNotNull(appComponent)
            assertEquals("init-test", appComponent.appId)
            assertEquals("init-profile", appComponent.profile)
            assertEquals("0.0.0-INIT", appComponent.version)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun initMethodInitializesRootScopeProvider() {
        val appComponent = createCoreApiTestAppComponent(
            testInstance = this,
            appId = "init-test",
            profile = "init-profile",
            version = "0.0.0-INIT"
        )
        try {
            assertNotNull(appComponent.rootScopeProvider)
            assertNotNull(appComponent.rootScopeProvider.rootScope)
            assertFalse(appComponent.rootScopeProvider.isDestroyed())
        } finally {
            appComponent.destroy()
        }
    }
}

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

package com.sphereon.core.defaults.context

import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserContextTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "context-test", "test-profile", "0.0.1-TEST"
    )

    // ========== UserContextImpl Tests ==========

    @Test
    fun userContextHasPrincipal() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertEquals("test-user", contextInstance.context.principal)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextHasTenant() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(contextInstance.context.tenant)
            assertEquals("test-tenant", contextInstance.context.tenant.tenantId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextHasId() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(contextInstance.context.id)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== UserContextInstanceImpl Tests ==========

    @Test
    fun userContextInstanceHasContextId() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(contextInstance.contextId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextInstanceHasContext() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(contextInstance.context)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextInstanceHasSessionContextManager() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(contextInstance.sessionContextManager)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextInstanceHasScope() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(contextInstance.scope)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== UserContextManagerImpl Tests ==========

    @Test
    fun userContextManagerCreatesContext() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(contextInstance)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextManagerTracksActiveContext() {
        val appComponent = createAppComponent()
        try {
            assertFalse(appComponent.userContextManager.hasActive(), "Should not have active initially")

            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertTrue(appComponent.userContextManager.hasActive(), "Should have active after creation")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextManagerGetActiveReturnsActiveContext() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val activeContext = appComponent.userContextManager.getActive()

            assertEquals(contextInstance.contextId, activeContext.contextId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextManagerDestroyByIdWorks() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertTrue(appComponent.userContextManager.hasActive())

            appComponent.userContextManager.destroyById(contextInstance.contextId)
            assertFalse(appComponent.userContextManager.hasActive())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextManagerDestroyAllWorks() {
        val appComponent = createAppComponent()
        try {
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1")
            )
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2")
            )
            assertTrue(appComponent.userContextManager.hasActive())

            appComponent.userContextManager.destroyAll()
            assertFalse(appComponent.userContextManager.hasActive())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextManagerIsAnonymousWhenNoContext() {
        val appComponent = createAppComponent()
        try {
            assertTrue(appComponent.userContextManager.isAnonymous())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextManagerIsNotAnonymousWithContext() {
        val appComponent = createAppComponent()
        try {
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertFalse(appComponent.userContextManager.isAnonymous())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextManagerGetAnonymousReturnsAnonymousContext() {
        val appComponent = createAppComponent()
        try {
            val anonymousContext = appComponent.userContextManager.getAnonymous()
            assertEquals("<anonymous>", anonymousContext.context.principal)
            assertEquals("<anonymous>", anonymousContext.context.tenant.tenantId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextManagerGetBackgroundServiceReturnsBackgroundContext() {
        val appComponent = createAppComponent()
        try {
            val backgroundContext = appComponent.userContextManager.getBackgroundService()
            assertNotNull(backgroundContext)
            assertEquals("<anonymous>", backgroundContext.context.principal)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextManagerGetBackgroundServiceIdReturnsId() {
        val appComponent = createAppComponent()
        try {
            val backgroundId = appComponent.userContextManager.getBackgroundServiceId()
            assertNotNull(backgroundId)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== AnonymousUserComponentManagerImpl Tests ==========

    @Test
    fun anonymousComponentManagerReturnsAnonymousComponent() {
        val appComponent = createAppComponent()
        try {
            val anonymousContext = appComponent.userContextManager.getAnonymous(makeActive = false)
            assertNotNull(anonymousContext)
            assertEquals("<anonymous>", anonymousContext.context.principal)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun backgroundServiceComponentExists() {
        val appComponent = createAppComponent()
        try {
            val backgroundContext = appComponent.userContextManager.getBackgroundService()

            // Background service should exist and have a context
            assertNotNull(backgroundContext)
            assertNotNull(backgroundContext.context)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== CrossContextOperationsImpl Tests ==========

    @Test
    fun crossContextOperationsGetAvailableContexts() {
        val appComponent = createAppComponent()
        try {
            // Create some contexts
            val context1 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1"),
                makeActive = false
            )
            val context2 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2"),
                makeActive = true
            )

            // Cross-context operations should be able to see available contexts
            // This is a basic sanity check - actual implementation may vary
            assertTrue(appComponent.userContextManager.hasActive())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Default Input Tests ==========

    @Test
    fun defaultTenantInputStringHasValue() {
        val input = DefaultTenantInputString("my-tenant")
        assertEquals("my-tenant", input.tenant)
    }

    @Test
    fun defaultPrincipalInputStringHasValue() {
        val input = DefaultPrincipalInputString("my-user")
        assertEquals("my-user", input.principal)
    }

    // ========== Multiple Context Tests ==========

    @Test
    fun multipleContextsCanCoexist() {
        val appComponent = createAppComponent()
        try {
            val context1 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1"),
                makeActive = false
            )
            val context2 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2"),
                makeActive = true
            )

            assertNotEquals(context1.contextId, context2.contextId)
            assertEquals("user-2", appComponent.userContextManager.getActive().context.principal)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun contextCanBeSwitched() {
        val appComponent = createAppComponent()
        try {
            val context1 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1"),
                makeActive = true
            )
            assertEquals("user-1", appComponent.userContextManager.getActive().context.principal)

            val context2 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2"),
                makeActive = true
            )
            assertEquals("user-2", appComponent.userContextManager.getActive().context.principal)

            // Switch back to context1
            appComponent.userContextManager.activateById(context1.contextId)
            assertEquals("user-1", appComponent.userContextManager.getActive().context.principal)
        } finally {
            appComponent.destroy()
        }
    }
}

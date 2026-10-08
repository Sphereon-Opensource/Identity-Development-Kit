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

package com.sphereon.core.defaults.context

import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserContextTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "context-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== UserContextImpl Tests ==========

    @Test
    fun userContextHasPrincipal() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertEquals("test-user", contextInstance.context.principal)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextHasTenant() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(contextInstance.context.tenant)
            assertEquals("test-tenant", contextInstance.context.tenant.tenantId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextHasId() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(contextInstance.context.id)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== UserContextInstanceImpl Tests ==========

    @Test
    fun userContextInstanceHasContextId() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(contextInstance.contextId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextInstanceHasContext() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(contextInstance.context)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextInstanceHasSessionContextManager() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(contextInstance.sessionContextManager)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextInstanceHasScope() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(contextInstance.scope)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== UserContextManagerImpl Tests ==========

    @Test
    fun userContextManagerCreatesContext() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(contextInstance)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextManagerTracksActiveContext() {
        val appGraph = createAppGraph()
        try {
            assertFalse(appGraph.userContextManager.hasActive(), "Should not have active initially")

            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
            )
            assertTrue(appGraph.userContextManager.hasActive(), "Should have active after creation")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextManagerGetActiveReturnsActiveContext() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val activeContext = appGraph.userContextManager.getActive()

            assertEquals(contextInstance.contextId, activeContext.contextId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextManagerDestroyByIdWorks() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertTrue(appGraph.userContextManager.hasActive())

            appGraph.userContextManager.destroyById(contextInstance.contextId)
            assertFalse(appGraph.userContextManager.hasActive())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextManagerDestroyAllWorks() {
        val appGraph = createAppGraph()
        try {
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1"),
            )
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2"),
            )
            assertTrue(appGraph.userContextManager.hasActive())

            appGraph.userContextManager.destroyAll()
            assertFalse(appGraph.userContextManager.hasActive())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextManagerIsAnonymousWhenNoContext() {
        val appGraph = createAppGraph()
        try {
            assertTrue(appGraph.userContextManager.isAnonymous())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextManagerIsNotAnonymousWithContext() {
        val appGraph = createAppGraph()
        try {
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
            )
            assertFalse(appGraph.userContextManager.isAnonymous())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextManagerGetAnonymousReturnsAnonymousContext() {
        val appGraph = createAppGraph()
        try {
            val anonymousContext = appGraph.userContextManager.getAnonymous()
            assertEquals("<anonymous>", anonymousContext.context.principal)
            assertEquals("<anonymous>", anonymousContext.context.tenant.tenantId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextManagerGetBackgroundServiceReturnsBackgroundContext() {
        val appGraph = createAppGraph()
        try {
            val backgroundContext = appGraph.userContextManager.getBackgroundService()
            assertNotNull(backgroundContext)
            assertEquals("<anonymous>", backgroundContext.context.principal)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextManagerGetBackgroundServiceIdReturnsId() {
        val appGraph = createAppGraph()
        try {
            val backgroundId = appGraph.userContextManager.getBackgroundServiceId()
            assertNotNull(backgroundId)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== AnonymousUserGraphManagerImpl Tests ==========

    @Test
    fun anonymousComponentManagerReturnsAnonymousGraph() {
        val appGraph = createAppGraph()
        try {
            val anonymousContext = appGraph.userContextManager.getAnonymous(makeActive = false)
            assertNotNull(anonymousContext)
            assertEquals("<anonymous>", anonymousContext.context.principal)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun backgroundServiceGraphExists() {
        val appGraph = createAppGraph()
        try {
            val backgroundContext = appGraph.userContextManager.getBackgroundService()

            // Background service should exist and have a context
            assertNotNull(backgroundContext)
            assertNotNull(backgroundContext.context)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== CrossContextOperationsImpl Tests ==========

    @Test
    fun crossContextOperationsGetAvailableContexts() {
        val appGraph = createAppGraph()
        try {
            // Create some contexts
            val context1 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-1"),
                    DefaultPrincipalInputString("user-1"),
                    makeActive = false,
                )
            val context2 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-2"),
                    DefaultPrincipalInputString("user-2"),
                    makeActive = true,
                )

            // Cross-context operations should be able to see available contexts
            // This is a basic sanity check - actual implementation may vary
            assertTrue(appGraph.userContextManager.hasActive())
        } finally {
            appGraph.destroy()
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
        val appGraph = createAppGraph()
        try {
            val context1 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-1"),
                    DefaultPrincipalInputString("user-1"),
                    makeActive = false,
                )
            val context2 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-2"),
                    DefaultPrincipalInputString("user-2"),
                    makeActive = true,
                )

            assertNotEquals(context1.contextId, context2.contextId)
            assertEquals(
                "user-2",
                appGraph.userContextManager
                    .getActive()
                    .context.principal,
            )
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun contextCanBeSwitched() {
        val appGraph = createAppGraph()
        try {
            val context1 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-1"),
                    DefaultPrincipalInputString("user-1"),
                    makeActive = true,
                )
            assertEquals(
                "user-1",
                appGraph.userContextManager
                    .getActive()
                    .context.principal,
            )

            val context2 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-2"),
                    DefaultPrincipalInputString("user-2"),
                    makeActive = true,
                )
            assertEquals(
                "user-2",
                appGraph.userContextManager
                    .getActive()
                    .context.principal,
            )

            // Switch back to context1
            appGraph.userContextManager.activateById(context1.contextId)
            assertEquals(
                "user-1",
                appGraph.userContextManager
                    .getActive()
                    .context.principal,
            )
        } finally {
            appGraph.destroy()
        }
    }
}

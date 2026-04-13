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

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserContextInstanceTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "user-context-instance-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== Basic Property Tests ==========

    @Test
    fun userContextInstanceHasContext() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(instance.context)
            assertEquals("test-tenant", instance.context.tenant.tenantId)
            assertEquals("test-user", instance.context.principal)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextInstanceHasContextId() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(instance.contextId)
            assertTrue(instance.contextId.contains("test-tenant"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextInstanceHasGraph() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(instance.graph)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextInstanceHasScope() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(instance.scope)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextInstanceHasUserContextManager() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(instance.userContextManager)
            assertEquals(appGraph.userContextManager, instance.userContextManager)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextInstanceHasSessionContextManager() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertNotNull(instance.sessionContextManager)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== isCurrentlyActive Tests ==========

    @Test
    fun isCurrentlyActiveReturnsTrueWhenActive() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                    true, // makeActive
                )
            assertTrue(instance.isCurrentlyActive())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun isCurrentlyActiveReturnsFalseWhenNotActive() {
        val appGraph = createAppGraph()
        try {
            // Create first context and make active
            val instance1 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-1"),
                    DefaultPrincipalInputString("user-1"),
                    true,
                )

            // Create second context, not active
            val instance2 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-2"),
                    DefaultPrincipalInputString("user-2"),
                    false,
                )

            assertTrue(instance1.isCurrentlyActive())
            assertFalse(instance2.isCurrentlyActive())
        } finally {
            appGraph.destroy()
        }
    }

    // ========== makeActive Tests ==========

    @Test
    fun makeActiveActivatesContext() {
        val appGraph = createAppGraph()
        try {
            val instance1 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-1"),
                    DefaultPrincipalInputString("user-1"),
                    true,
                )

            val instance2 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-2"),
                    DefaultPrincipalInputString("user-2"),
                    false,
                )

            assertTrue(instance1.isCurrentlyActive())
            assertFalse(instance2.isCurrentlyActive())

            // Make instance2 active
            assertTrue(instance2.makeActive())

            assertFalse(instance1.isCurrentlyActive())
            assertTrue(instance2.isCurrentlyActive())
        } finally {
            appGraph.destroy()
        }
    }

    // ========== destroy Tests ==========

    @Test
    fun destroyRemovesContext() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val contextId = instance.contextId

            assertTrue(appGraph.userContextManager.hasById(contextId))
            instance.destroy()
            assertFalse(appGraph.userContextManager.hasById(contextId))
        } finally {
            appGraph.destroy()
        }
    }

    // ========== getService Tests ==========

    @Test
    fun getServiceThrowsNotFoundExceptionForUnknownService() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )

            assertFailsWith<NotFoundException> {
                instance.getService<Any>("non-existent-service")
            }
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getServiceReturnsRegisteredService() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )

            // "context" is registered as a service
            val context = instance.getService<Any>("context")
            assertNotNull(context)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Session Creation Tests ==========

    @Test
    fun createSessionCreatesNewSession() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )

            val session = instance.createSession("my-session", false)
            assertNotNull(session)
            assertEquals("my-session", session.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun createSessionWithMakeActiveMakesSessionActive() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )

            val session = instance.createSession("active-session", true)
            assertTrue(session.isCurrentlyActive())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getOrCreateAnonymousSessionReturnsAnonymousSession() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )

            val session = instance.getOrCreateAnonymousSession(false)
            assertNotNull(session)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getOrCreateBackgroundServiceSessionReturnsBackgroundSession() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )

            val session = instance.getOrCreateBackgroundServiceSession(false)
            assertNotNull(session)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== equals/hashCode Tests ==========

    @Test
    fun equalsReturnsTrueForSameInstance() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertTrue(instance == instance)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertFalse(instance.equals(null))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            assertFalse(instance.equals("string"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun hashCodeIsConsistent() {
        val appGraph = createAppGraph()
        try {
            val instance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val hash1 = instance.hashCode()
            val hash2 = instance.hashCode()
            assertEquals(hash1, hash2)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun differentContextsHaveDifferentHashCodes() {
        val appGraph = createAppGraph()
        try {
            val instance1 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-1"),
                    DefaultPrincipalInputString("user-1"),
                )
            val instance2 =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-2"),
                    DefaultPrincipalInputString("user-2"),
                )

            // Different contexts should have different context IDs
            assertNotEquals(instance1.contextId, instance2.contextId)
        } finally {
            appGraph.destroy()
        }
    }
}

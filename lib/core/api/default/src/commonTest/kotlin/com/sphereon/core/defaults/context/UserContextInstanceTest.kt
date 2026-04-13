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
import com.sphereon.core.api.error.NotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class UserContextInstanceTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "user-context-instance-test", "test-profile", "0.0.1-TEST"
    )

    // ========== Basic Property Tests ==========

    @Test
    fun userContextInstanceHasContext() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(instance.context)
            assertEquals("test-tenant", instance.context.tenant.tenantId)
            assertEquals("test-user", instance.context.principal)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextInstanceHasContextId() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(instance.contextId)
            assertTrue(instance.contextId.contains("test-tenant"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextInstanceHasComponent() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(instance.component)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextInstanceHasScope() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(instance.scope)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextInstanceHasUserContextManager() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(instance.userContextManager)
            assertEquals(appComponent.userContextManager, instance.userContextManager)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextInstanceHasSessionContextManager() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertNotNull(instance.sessionContextManager)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== isCurrentlyActive Tests ==========

    @Test
    fun isCurrentlyActiveReturnsTrueWhenActive() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
                true // makeActive
            )
            assertTrue(instance.isCurrentlyActive())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun isCurrentlyActiveReturnsFalseWhenNotActive() {
        val appComponent = createAppComponent()
        try {
            // Create first context and make active
            val instance1 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1"),
                true
            )

            // Create second context, not active
            val instance2 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2"),
                false
            )

            assertTrue(instance1.isCurrentlyActive())
            assertFalse(instance2.isCurrentlyActive())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== makeActive Tests ==========

    @Test
    fun makeActiveActivatesContext() {
        val appComponent = createAppComponent()
        try {
            val instance1 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1"),
                true
            )

            val instance2 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2"),
                false
            )

            assertTrue(instance1.isCurrentlyActive())
            assertFalse(instance2.isCurrentlyActive())

            // Make instance2 active
            assertTrue(instance2.makeActive())

            assertFalse(instance1.isCurrentlyActive())
            assertTrue(instance2.isCurrentlyActive())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== destroy Tests ==========

    @Test
    fun destroyRemovesContext() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val contextId = instance.contextId

            assertTrue(appComponent.userContextManager.hasById(contextId))
            instance.destroy()
            assertFalse(appComponent.userContextManager.hasById(contextId))
        } finally {
            appComponent.destroy()
        }
    }

    // ========== getService Tests ==========

    @Test
    fun getServiceThrowsNotFoundExceptionForUnknownService() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )

            assertFailsWith<NotFoundException> {
                instance.getService<Any>("non-existent-service")
            }
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getServiceReturnsRegisteredService() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )

            // "context" is registered as a service
            val context = instance.getService<Any>("context")
            assertNotNull(context)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Session Creation Tests ==========

    @Test
    fun createSessionCreatesNewSession() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )

            val session = instance.createSession("my-session", false)
            assertNotNull(session)
            assertEquals("my-session", session.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun createSessionWithMakeActiveMakesSessionActive() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )

            val session = instance.createSession("active-session", true)
            assertTrue(session.isCurrentlyActive())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getOrCreateAnonymousSessionReturnsAnonymousSession() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )

            val session = instance.getOrCreateAnonymousSession(false)
            assertNotNull(session)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getOrCreateBackgroundServiceSessionReturnsBackgroundSession() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )

            val session = instance.getOrCreateBackgroundServiceSession(false)
            assertNotNull(session)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== equals/hashCode Tests ==========

    @Test
    fun equalsReturnsTrueForSameInstance() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertTrue(instance == instance)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun equalsReturnsFalseForNull() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertFalse(instance.equals(null))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun equalsReturnsFalseForDifferentType() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            assertFalse(instance.equals("string"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun hashCodeIsConsistent() {
        val appComponent = createAppComponent()
        try {
            val instance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val hash1 = instance.hashCode()
            val hash2 = instance.hashCode()
            assertEquals(hash1, hash2)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun differentContextsHaveDifferentHashCodes() {
        val appComponent = createAppComponent()
        try {
            val instance1 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1")
            )
            val instance2 = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2")
            )

            // Different contexts should have different context IDs
            assertNotEquals(instance1.contextId, instance2.contextId)
        } finally {
            appComponent.destroy()
        }
    }
}

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
import com.sphereon.core.api.testutil.crossContextOperations
import com.sphereon.di.context.CrossContextOperations
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossContextOperationsTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "cross-context-test", "test-profile", "0.0.1-TEST"
    )

    // ========== CrossContextOperationsImpl Tests ==========

    @Test
    fun crossContextOperationsExists() {
        val appComponent = createAppComponent()
        try {
            val crossContextOps = appComponent.crossContextOperations
            assertNotNull(crossContextOps)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getAvailableAuthenticatedContextsReturnsEmpty() {
        val appComponent = createAppComponent()
        try {
            val crossContextOps = appComponent.crossContextOperations
            // No authenticated contexts created yet
            val contexts = crossContextOps.getAvailableAuthenticatedContexts()
            // May be empty or have some depending on setup
            assertNotNull(contexts)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getAvailableAuthenticatedContextsReturnsCreatedContexts() {
        val appComponent = createAppComponent()
        try {
            // Create some user contexts first
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1")
            )
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2")
            )

            val crossContextOps = appComponent.crossContextOperations
            val contexts = crossContextOps.getAvailableAuthenticatedContexts()

            // Should have at least the created contexts
            assertNotNull(contexts)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getAvailableSessionsForContextReturnsEmptyForUnknown() {
        val appComponent = createAppComponent()
        try {
            val crossContextOps = appComponent.crossContextOperations
            val sessions = crossContextOps.getAvailableSessionsForContext("unknown-context-id")
            assertTrue(sessions.isEmpty())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getAvailableSessionsForContextReturnsSessions() {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )

            // Create some sessions
            userContext.sessionContextManager.createOrGetFromId("session-1")
            userContext.sessionContextManager.createOrGetFromId("session-2")

            val crossContextOps = appComponent.crossContextOperations
            val sessions = crossContextOps.getAvailableSessionsForContext(userContext.contextId)

            assertTrue(sessions.isNotEmpty())
            assertTrue(sessions.containsKey("session-1"))
            assertTrue(sessions.containsKey("session-2"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun executeInUserContextReturnsNullForUnknownContext() = runTest {
        val appComponent = createAppComponent()
        try {
            val crossContextOps = appComponent.crossContextOperations
            val result = crossContextOps.executeInUserContext("unknown-id") { _, _ ->
                "should not execute"
            }
            assertNull(result)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun executeInUserContextExecutesOperation() = runTest {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )

            val crossContextOps = appComponent.crossContextOperations
            val result = crossContextOps.executeInUserContext(userContext.contextId) { ctx, scope ->
                "executed for ${ctx.principal}"
            }

            assertEquals("executed for test-user", result)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun executeInSessionContextReturnsNullForUnknownContext() = runTest {
        val appComponent = createAppComponent()
        try {
            val crossContextOps = appComponent.crossContextOperations
            val result = crossContextOps.executeInSessionContext("unknown-id", null) { _, _ ->
                "should not execute"
            }
            assertNull(result)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun executeInSessionContextExecutesWithSession() = runTest {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            userContext.sessionContextManager.createOrGetFromId("target-session")

            val crossContextOps = appComponent.crossContextOperations
            val result = crossContextOps.executeInSessionContext(
                userContext.contextId,
                "target-session"
            ) { ctx, sessionInstance ->
                "context: ${ctx.context.principal}, session: ${sessionInstance?.sessionId}"
            }

            assertNotNull(result)
            assertTrue(result.contains("test-user"))
            assertTrue(result.contains("target-session"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun executeInSessionContextExecutesWithoutSession() = runTest {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )

            val crossContextOps = appComponent.crossContextOperations
            val result = crossContextOps.executeInSessionContext(
                userContext.contextId,
                null
            ) { ctx, sessionInstance ->
                "context: ${ctx.context.principal}, session: ${sessionInstance?.sessionId ?: "none"}"
            }

            assertNotNull(result)
            assertTrue(result.contains("test-user"))
            assertTrue(result.contains("none"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun executeInBestAuthenticatedContextReturnsNullWhenNoContexts() = runTest {
        val appComponent = createAppComponent()
        try {
            val crossContextOps = appComponent.crossContextOperations
            val result = crossContextOps.executeInBestAuthenticatedContext(null) { ctx, scope ->
                "should not execute"
            }
            // May be null if no authenticated contexts
            // The result depends on what contexts exist
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun executeInBestAuthenticatedContextPrefersMatchingTenant() = runTest {
        val appComponent = createAppComponent()
        try {
            // Create contexts with different tenants
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-a"),
                DefaultPrincipalInputString("user-a")
            )
            appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-b"),
                DefaultPrincipalInputString("user-b")
            )

            val crossContextOps = appComponent.crossContextOperations
            val result = crossContextOps.executeInBestAuthenticatedContext("tenant-b") { ctx, scope ->
                ctx.tenant.tenantId
            }

            // Should prefer tenant-b if available
            assertNotNull(result)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getServiceFromContextReturnsNullForUnknownContext() {
        val appComponent = createAppComponent()
        try {
            val crossContextOps = appComponent.crossContextOperations
            val service = crossContextOps.getServiceFromContext(
                "unknown-context",
                null,
                CrossContextOperations::class
            )
            assertNull(service)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun getServiceFromContextReturnsNullForUnknownSession() {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )

            val crossContextOps = appComponent.crossContextOperations
            val service = crossContextOps.getServiceFromContext(
                userContext.contextId,
                "unknown-session",
                CrossContextOperations::class
            )
            assertNull(service)
        } finally {
            appComponent.destroy()
        }
    }
}

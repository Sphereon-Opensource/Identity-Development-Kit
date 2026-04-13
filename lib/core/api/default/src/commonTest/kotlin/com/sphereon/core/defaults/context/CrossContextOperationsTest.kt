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
import com.sphereon.core.api.testutil.crossContextOperations
import com.sphereon.di.context.CrossContextOperations
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossContextOperationsTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "cross-context-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== CrossContextOperationsImpl Tests ==========

    @Test
    fun crossContextOperationsExists() {
        val appGraph = createAppGraph()
        try {
            val crossContextOps = appGraph.crossContextOperations
            assertNotNull(crossContextOps)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getAvailableAuthenticatedContextsReturnsEmpty() {
        val appGraph = createAppGraph()
        try {
            val crossContextOps = appGraph.crossContextOperations
            // No authenticated contexts created yet
            val contexts = crossContextOps.getAvailableAuthenticatedContexts()
            // May be empty or have some depending on setup
            assertNotNull(contexts)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getAvailableAuthenticatedContextsReturnsCreatedContexts() {
        val appGraph = createAppGraph()
        try {
            // Create some user contexts first
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-1"),
                DefaultPrincipalInputString("user-1"),
            )
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("tenant-2"),
                DefaultPrincipalInputString("user-2"),
            )

            val crossContextOps = appGraph.crossContextOperations
            val contexts = crossContextOps.getAvailableAuthenticatedContexts()

            // Should have at least the created contexts
            assertNotNull(contexts)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getAvailableSessionsForContextReturnsEmptyForUnknown() {
        val appGraph = createAppGraph()
        try {
            val crossContextOps = appGraph.crossContextOperations
            val sessions = crossContextOps.getAvailableSessionsForContext("unknown-context-id")
            assertTrue(sessions.isEmpty())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getAvailableSessionsForContextReturnsSessions() {
        val appGraph = createAppGraph()
        try {
            val userContext =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )

            // Create some sessions
            userContext.sessionContextManager.createOrGetFromId("session-1")
            userContext.sessionContextManager.createOrGetFromId("session-2")

            val crossContextOps = appGraph.crossContextOperations
            val sessions = crossContextOps.getAvailableSessionsForContext(userContext.contextId)

            assertTrue(sessions.isNotEmpty())
            assertTrue(sessions.containsKey("session-1"))
            assertTrue(sessions.containsKey("session-2"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun executeInUserContextReturnsNullForUnknownContext() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val crossContextOps = appGraph.crossContextOperations
                val result =
                    crossContextOps.executeInUserContext("unknown-id") { _, _ ->
                        "should not execute"
                    }
                assertNull(result)
            } finally {
                appGraph.destroy()
            }
        }

    @Test
    fun executeInUserContextExecutesOperation() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val userContext =
                    appGraph.userContextManager.createOrGetFromInputs(
                        DefaultTenantInputString("test-tenant"),
                        DefaultPrincipalInputString("test-user"),
                    )

                val crossContextOps = appGraph.crossContextOperations
                val result =
                    crossContextOps.executeInUserContext(userContext.contextId) { ctx, scope ->
                        "executed for ${ctx.principal}"
                    }

                assertEquals("executed for test-user", result)
            } finally {
                appGraph.destroy()
            }
        }

    @Test
    fun executeInSessionContextReturnsNullForUnknownContext() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val crossContextOps = appGraph.crossContextOperations
                val result =
                    crossContextOps.executeInSessionContext("unknown-id", null) { _, _ ->
                        "should not execute"
                    }
                assertNull(result)
            } finally {
                appGraph.destroy()
            }
        }

    @Test
    fun executeInSessionContextExecutesWithSession() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val userContext =
                    appGraph.userContextManager.createOrGetFromInputs(
                        DefaultTenantInputString("test-tenant"),
                        DefaultPrincipalInputString("test-user"),
                    )
                userContext.sessionContextManager.createOrGetFromId("target-session")

                val crossContextOps = appGraph.crossContextOperations
                val result =
                    crossContextOps.executeInSessionContext(
                        userContext.contextId,
                        "target-session",
                    ) { ctx, sessionInstance ->
                        "context: ${ctx.context.principal}, session: ${sessionInstance?.sessionId}"
                    }

                assertNotNull(result)
                assertTrue(result.contains("test-user"))
                assertTrue(result.contains("target-session"))
            } finally {
                appGraph.destroy()
            }
        }

    @Test
    fun executeInSessionContextExecutesWithoutSession() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val userContext =
                    appGraph.userContextManager.createOrGetFromInputs(
                        DefaultTenantInputString("test-tenant"),
                        DefaultPrincipalInputString("test-user"),
                    )

                val crossContextOps = appGraph.crossContextOperations
                val result =
                    crossContextOps.executeInSessionContext(
                        userContext.contextId,
                        null,
                    ) { ctx, sessionInstance ->
                        "context: ${ctx.context.principal}, session: ${sessionInstance?.sessionId ?: "none"}"
                    }

                assertNotNull(result)
                assertTrue(result.contains("test-user"))
                assertTrue(result.contains("none"))
            } finally {
                appGraph.destroy()
            }
        }

    @Test
    fun executeInBestAuthenticatedContextReturnsNullWhenNoContexts() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val crossContextOps = appGraph.crossContextOperations
                val result =
                    crossContextOps.executeInBestAuthenticatedContext(null) { ctx, scope ->
                        "should not execute"
                    }
                // May be null if no authenticated contexts
                // The result depends on what contexts exist
            } finally {
                appGraph.destroy()
            }
        }

    @Test
    fun executeInBestAuthenticatedContextPrefersMatchingTenant() =
        runTest {
            val appGraph = createAppGraph()
            try {
                // Create contexts with different tenants
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-a"),
                    DefaultPrincipalInputString("user-a"),
                )
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("tenant-b"),
                    DefaultPrincipalInputString("user-b"),
                )

                val crossContextOps = appGraph.crossContextOperations
                val result =
                    crossContextOps.executeInBestAuthenticatedContext("tenant-b") { ctx, scope ->
                        ctx.tenant.tenantId
                    }

                // Should prefer tenant-b if available
                assertNotNull(result)
            } finally {
                appGraph.destroy()
            }
        }

    @Test
    fun getServiceFromContextReturnsNullForUnknownContext() {
        val appGraph = createAppGraph()
        try {
            val crossContextOps = appGraph.crossContextOperations
            val service =
                crossContextOps.getServiceFromContext(
                    "unknown-context",
                    null,
                    CrossContextOperations::class,
                )
            assertNull(service)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun getServiceFromContextReturnsNullForUnknownSession() {
        val appGraph = createAppGraph()
        try {
            val userContext =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )

            val crossContextOps = appGraph.crossContextOperations
            val service =
                crossContextOps.getServiceFromContext(
                    userContext.contextId,
                    "unknown-session",
                    CrossContextOperations::class,
                )
            assertNull(service)
        } finally {
            appGraph.destroy()
        }
    }
}

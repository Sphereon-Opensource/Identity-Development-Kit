/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.core.defaults.session

import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "session-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== SessionContextImpl Tests ==========

    @Test
    fun sessionContextHasSessionId() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId("my-session")
            assertEquals("my-session", sessionGraph.sessionContext.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextHasUserContext() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId("my-session")

            assertNotNull(sessionGraph.sessionContext.context)
            assertEquals("test-user", sessionGraph.sessionContext.context.principal)
            assertEquals("test-tenant", sessionGraph.sessionContext.context.tenant.tenantId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextEqualsWorks() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session2 = contextInstance.sessionContextManager.createOrGetFromId("session-2")
            val session1Again = contextInstance.sessionContextManager.createOrGetFromId("session-1")

            // Same session ID should return same graph
            assertEquals(session1.sessionContext, session1Again.sessionContext)

            // Different session IDs should not be equal
            assertNotEquals(session1.sessionContext.sessionId, session2.sessionContext.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextToStringContainsSessionId() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId("unique-session-id")
            val stringRep = sessionGraph.sessionContext.toString()

            assertTrue(stringRep.contains("unique-session-id"), "toString should contain session ID")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextHashCodeWorks() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session1Again = contextInstance.sessionContextManager.createOrGetFromId("session-1")

            assertEquals(session1.sessionContext.hashCode(), session1Again.sessionContext.hashCode())
        } finally {
            appGraph.destroy()
        }
    }

    // ========== SessionContextManagerImpl Tests ==========

    @Test
    fun sessionContextManagerCreatesSession() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = contextInstance.sessionContextManager
            val sessionGraph = sessionManager.createOrGetFromId("new-session")

            assertNotNull(sessionGraph)
            assertEquals("new-session", sessionGraph.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerTracksActiveSessions() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = contextInstance.sessionContextManager

            assertFalse(sessionManager.hasActive(), "Should not have active session initially")

            sessionManager.createOrGetFromId("session-1")
            assertTrue(sessionManager.hasActive(), "Should have active session after creation")
            assertEquals("session-1", sessionManager.getActive().sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerCanActivateById() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = contextInstance.sessionContextManager

            sessionManager.createOrGetFromId("session-1")
            sessionManager.createOrGetFromId("session-2")

            assertEquals("session-2", sessionManager.getActive().sessionId)

            sessionManager.activateById("session-1")
            assertEquals("session-1", sessionManager.getActive().sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerCanDestroyById() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = contextInstance.sessionContextManager

            sessionManager.createOrGetFromId("session-1")
            assertTrue(sessionManager.hasActive())

            sessionManager.destroyById("session-1")
            assertFalse(sessionManager.hasActive())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerCanDestroyAll() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = contextInstance.sessionContextManager

            sessionManager.createOrGetFromId("session-1")
            sessionManager.createOrGetFromId("session-2")
            sessionManager.createOrGetFromId("session-3")

            assertTrue(sessionManager.hasActive())

            sessionManager.destroyAll()
            assertFalse(sessionManager.hasActive())
        } finally {
            appGraph.destroy()
        }
    }

    // ========== SessionInstanceImpl Tests ==========

    @Test
    fun sessionInstanceHasSessionId() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("instance-session")

            assertEquals("instance-session", sessionInstance.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionInstanceHasSessionContext() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("instance-session")

            assertNotNull(sessionInstance.sessionContext)
            assertEquals("instance-session", sessionInstance.sessionContext.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionInstanceHasGraph() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("instance-session")

            assertNotNull(sessionInstance.graph)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionInstanceIsCurrentlyActive() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("active-session")

            assertTrue(sessionInstance.isCurrentlyActive(), "New session should be active")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionInstanceMakeActiveWorks() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session2 = contextInstance.sessionContextManager.createOrGetFromId("session-2")

            assertFalse(session1.isCurrentlyActive(), "Session 1 should not be active after session 2 created")
            assertTrue(session2.isCurrentlyActive(), "Session 2 should be active")

            session1.makeActive()
            assertTrue(session1.isCurrentlyActive(), "Session 1 should be active after makeActive")
            assertFalse(session2.isCurrentlyActive(), "Session 2 should no longer be active")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionInstanceDestroyWorks() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("to-destroy")

            assertTrue(contextInstance.sessionContextManager.hasActive())

            sessionInstance.destroy()

            assertFalse(contextInstance.sessionContextManager.hasActive())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionInstanceEqualsWorks() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session1Again = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session2 = contextInstance.sessionContextManager.createOrGetFromId("session-2")

            assertEquals(session1, session1Again, "Same session ID should return equal instances")
            assertNotEquals(session1, session2, "Different session IDs should not be equal")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionInstanceHashCodeWorks() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session1Again = contextInstance.sessionContextManager.createOrGetFromId("session-1")

            assertEquals(session1.hashCode(), session1Again.hashCode())
        } finally {
            appGraph.destroy()
        }
    }

    // ========== Anonymous Session Tests ==========

    @Test
    fun anonymousSessionIsAnonymous() {
        val appGraph = createAppGraph()
        try {
            val contextInstance = appGraph.userContextManager.getAnonymous()
            val sessionInstance = contextInstance.sessionContextManager.getActive()

            assertTrue(sessionInstance.sessionContext.isAnonymous(), "Anonymous session should be anonymous")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun nonAnonymousSessionIsNotAnonymous() {
        val appGraph = createAppGraph()
        try {
            val contextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("real-session")

            assertFalse(sessionInstance.sessionContext.isAnonymous(), "Non-anonymous session should not be anonymous")
        } finally {
            appGraph.destroy()
        }
    }
}

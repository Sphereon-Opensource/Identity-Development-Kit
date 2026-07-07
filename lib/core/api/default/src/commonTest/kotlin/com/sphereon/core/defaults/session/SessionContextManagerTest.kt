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
import com.sphereon.di.context.IdentityMetadata
import com.sphereon.di.context.IdentityResolutionResult
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.context.ResolutionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionContextManagerTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "session-manager-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== SessionContextManagerImpl Tests ==========

    @Test
    fun sessionContextManagerCanCreateSession() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            val sessionInstance = sessionManager.createOrGetFromId("test-session-1")
            assertNotNull(sessionInstance)
            assertEquals("test-session-1", sessionInstance.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerCreateOrGetFromIdReturnsExisting() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            val session1 = sessionManager.createOrGetFromId("test-session")
            val session2 = sessionManager.createOrGetFromId("test-session")
            assertEquals(session1.sessionId, session2.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun createOrGetFromCallbacksReusesSameSessionIdAndDestroyRemovesIt() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager
            val factory = DefaultSessionContextFactory()
            val resolution =
                IdentityResolutionResult(
                    tenantId = "test-tenant",
                    principalId = "transient-service",
                    principalType = PrincipalType.SERVICE,
                    metadata = IdentityMetadata(resolvedFrom = ResolutionSource.DEFAULT),
                )

            val first =
                sessionManager.createOrGetFromCallbacks {
                    factory.create(
                        sessionId = "transient-fetch",
                        correlationId = "transient-fetch:first",
                        resolution = resolution,
                    )
                }
            val second =
                sessionManager.createOrGetFromCallbacks {
                    factory.create(
                        sessionId = "transient-fetch",
                        correlationId = "transient-fetch:second",
                        resolution = resolution,
                    )
                }

            assertSame(first, second)

            first.destroy()

            assertFalse(sessionManager.hasById("transient-fetch"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerTracksActiveSessions() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            assertFalse(sessionManager.hasActive())

            sessionManager.createOrGetFromId("test-session", makeActive = true)
            assertTrue(sessionManager.hasActive())
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetActiveReturnsActiveSession() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            val session = sessionManager.createOrGetFromId("active-session", makeActive = true)
            val activeSession = sessionManager.getActive()
            assertEquals(session.sessionId, activeSession.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetByIdReturnsSession() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            val session = sessionManager.createOrGetFromId("find-me-session")
            val foundSession = sessionManager.getById("find-me-session")

            assertNotNull(foundSession)
            assertEquals(session.sessionId, foundSession.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetByIdReturnsNullForUnknown() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            val foundSession = sessionManager.getById("non-existent-session")
            assertNull(foundSession)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerHasByIdWorks() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            assertFalse(sessionManager.hasById("new-session"))
            sessionManager.createOrGetFromId("new-session")
            assertTrue(sessionManager.hasById("new-session"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerActivateByIdWorks() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            val session1 = sessionManager.createOrGetFromId("session-1", makeActive = true)
            val session2 = sessionManager.createOrGetFromId("session-2", makeActive = false)

            assertEquals("session-1", sessionManager.getActive().sessionId)

            assertTrue(sessionManager.activateById("session-2"))
            assertEquals("session-2", sessionManager.getActive().sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerActivateByIdReturnsFalseForUnknown() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            assertFalse(sessionManager.activateById("non-existent"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerListIdsReturnsAllSessionIds() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            sessionManager.createOrGetFromId("session-a")
            sessionManager.createOrGetFromId("session-b")
            sessionManager.createOrGetFromId("session-c")

            val ids = sessionManager.listIds()
            assertTrue(ids.contains("session-a"))
            assertTrue(ids.contains("session-b"))
            assertTrue(ids.contains("session-c"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerDestroyByIdRemovesSession() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            sessionManager.createOrGetFromId("to-destroy")
            assertTrue(sessionManager.hasById("to-destroy"))

            sessionManager.destroyById("to-destroy")
            assertFalse(sessionManager.hasById("to-destroy"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerDestroyAllClearsAllSessions() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            sessionManager.createOrGetFromId("session-1")
            sessionManager.createOrGetFromId("session-2")
            sessionManager.createOrGetFromId("session-3")

            sessionManager.destroyAll()

            assertFalse(sessionManager.hasById("session-1"))
            assertFalse(sessionManager.hasById("session-2"))
            assertFalse(sessionManager.hasById("session-3"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetAnonymousReturnsAnonymousSession() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            val anonymousSession = sessionManager.getAnonymous()
            assertNotNull(anonymousSession)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetBackgroundServiceIdReturnsId() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            val backgroundId = sessionManager.getBackgroundServiceId()
            assertNotNull(backgroundId)
            assertEquals(SessionContextManagerImpl.ANONYMOUS_SESSION_ID, backgroundId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetOrCreateBackgroundServiceReturnsSession() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            val backgroundSession = sessionManager.getOrCreateBackgroundService()
            assertNotNull(backgroundSession)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerActiveInstanceFlowExists() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            assertNotNull(sessionManager.activeInstance)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextManagerMultipleSessionsCanCoexist() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val sessionManager = userContextInstance.sessionContextManager

            val session1 = sessionManager.createOrGetFromId("multi-1")
            val session2 = sessionManager.createOrGetFromId("multi-2")
            val session3 = sessionManager.createOrGetFromId("multi-3")

            assertNotEquals(session1.sessionId, session2.sessionId)
            assertNotEquals(session2.sessionId, session3.sessionId)
            assertNotEquals(session1.sessionId, session3.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== SessionInstanceImpl Tests ==========

    @Test
    fun sessionInstanceHasSessionId() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("instance-test")
            assertNotNull(session.sessionId)
            assertEquals("instance-test", session.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionInstanceHasScope() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("scope-test")
            assertNotNull(session.scope)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionInstanceHasGraph() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("graph-test")
            assertNotNull(session.graph)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionInstanceHasSessionContext() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("context-test")
            assertNotNull(session.sessionContext)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== SessionContextImpl Tests ==========

    @Test
    fun sessionContextHasSessionId() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("session-ctx-test")
            assertNotNull(session.sessionContext.sessionId)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextHasContext() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("session-ctx-test")
            assertNotNull(session.sessionContext.context)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextToStringWorks() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("tostring-test")
            val str = session.sessionContext.toString()
            assertNotNull(str)
            assertTrue(str.contains("SessionContext"))
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextEqualsWorks() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("equals-test")
            val ctx = session.sessionContext
            assertTrue(ctx == ctx)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionContextHashCodeWorks() {
        val appGraph = createAppGraph()
        try {
            val userContextInstance =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test-tenant"),
                    DefaultPrincipalInputString("test-user"),
                )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("hashcode-test")
            val hashCode = session.sessionContext.hashCode()
            assertNotNull(hashCode)
        } finally {
            appGraph.destroy()
        }
    }
}

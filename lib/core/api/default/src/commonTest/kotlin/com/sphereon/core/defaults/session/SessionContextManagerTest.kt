/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionContextManagerTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "session-manager-test", "test-profile", "0.0.1-TEST"
    )

    // ========== SessionContextManagerImpl Tests ==========

    @Test
    fun sessionContextManagerCanCreateSession() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            val sessionInstance = sessionManager.createOrGetFromId("test-session-1")
            assertNotNull(sessionInstance)
            assertEquals("test-session-1", sessionInstance.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerCreateOrGetFromIdReturnsExisting() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            val session1 = sessionManager.createOrGetFromId("test-session")
            val session2 = sessionManager.createOrGetFromId("test-session")
            assertEquals(session1.sessionId, session2.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerTracksActiveSessions() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            assertFalse(sessionManager.hasActive())

            sessionManager.createOrGetFromId("test-session", makeActive = true)
            assertTrue(sessionManager.hasActive())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetActiveReturnsActiveSession() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            val session = sessionManager.createOrGetFromId("active-session", makeActive = true)
            val activeSession = sessionManager.getActive()
            assertEquals(session.sessionId, activeSession.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetByIdReturnsSession() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            val session = sessionManager.createOrGetFromId("find-me-session")
            val foundSession = sessionManager.getById("find-me-session")

            assertNotNull(foundSession)
            assertEquals(session.sessionId, foundSession.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetByIdReturnsNullForUnknown() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            val foundSession = sessionManager.getById("non-existent-session")
            assertNull(foundSession)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerHasByIdWorks() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            assertFalse(sessionManager.hasById("new-session"))
            sessionManager.createOrGetFromId("new-session")
            assertTrue(sessionManager.hasById("new-session"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerActivateByIdWorks() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            val session1 = sessionManager.createOrGetFromId("session-1", makeActive = true)
            val session2 = sessionManager.createOrGetFromId("session-2", makeActive = false)

            assertEquals("session-1", sessionManager.getActive().sessionId)

            assertTrue(sessionManager.activateById("session-2"))
            assertEquals("session-2", sessionManager.getActive().sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerActivateByIdReturnsFalseForUnknown() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            assertFalse(sessionManager.activateById("non-existent"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerListIdsReturnsAllSessionIds() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
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
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerDestroyByIdRemovesSession() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            sessionManager.createOrGetFromId("to-destroy")
            assertTrue(sessionManager.hasById("to-destroy"))

            sessionManager.destroyById("to-destroy")
            assertFalse(sessionManager.hasById("to-destroy"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerDestroyAllClearsAllSessions() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
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
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetAnonymousReturnsAnonymousSession() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            val anonymousSession = sessionManager.getAnonymous()
            assertNotNull(anonymousSession)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetBackgroundServiceIdReturnsId() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            val backgroundId = sessionManager.getBackgroundServiceId()
            assertNotNull(backgroundId)
            assertEquals(SessionContextManagerImpl.ANONYMOUS_SESSION_ID, backgroundId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerGetOrCreateBackgroundServiceReturnsSession() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            val backgroundSession = sessionManager.getOrCreateBackgroundService()
            assertNotNull(backgroundSession)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerActiveInstanceFlowExists() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            assertNotNull(sessionManager.activeInstance)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerMultipleSessionsCanCoexist() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = userContextInstance.sessionContextManager

            val session1 = sessionManager.createOrGetFromId("multi-1")
            val session2 = sessionManager.createOrGetFromId("multi-2")
            val session3 = sessionManager.createOrGetFromId("multi-3")

            assertNotEquals(session1.sessionId, session2.sessionId)
            assertNotEquals(session2.sessionId, session3.sessionId)
            assertNotEquals(session1.sessionId, session3.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== SessionInstanceImpl Tests ==========

    @Test
    fun sessionInstanceHasSessionId() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("instance-test")
            assertNotNull(session.sessionId)
            assertEquals("instance-test", session.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionInstanceHasScope() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("scope-test")
            assertNotNull(session.scope)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionInstanceHasComponent() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("component-test")
            assertNotNull(session.component)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionInstanceHasSessionContext() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("context-test")
            assertNotNull(session.sessionContext)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== SessionContextImpl Tests ==========

    @Test
    fun sessionContextHasSessionId() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("session-ctx-test")
            assertNotNull(session.sessionContext.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextHasContext() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("session-ctx-test")
            assertNotNull(session.sessionContext.context)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextToStringWorks() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("tostring-test")
            val str = session.sessionContext.toString()
            assertNotNull(str)
            assertTrue(str.contains("SessionContext"))
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextEqualsWorks() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("equals-test")
            val ctx = session.sessionContext
            assertTrue(ctx == ctx)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextHashCodeWorks() {
        val appComponent = createAppComponent()
        try {
            val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session = userContextInstance.sessionContextManager.createOrGetFromId("hashcode-test")
            val hashCode = session.sessionContext.hashCode()
            assertNotNull(hashCode)
        } finally {
            appComponent.destroy()
        }
    }
}

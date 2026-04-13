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
import kotlin.test.assertTrue

class SessionTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "session-test", "test-profile", "0.0.1-TEST"
    )

    // ========== SessionContextImpl Tests ==========

    @Test
    fun sessionContextHasSessionId() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionComponent = contextInstance.sessionContextManager.createOrGetFromId("my-session")
            assertEquals("my-session", sessionComponent.sessionContext.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextHasUserContext() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionComponent = contextInstance.sessionContextManager.createOrGetFromId("my-session")

            assertNotNull(sessionComponent.sessionContext.context)
            assertEquals("test-user", sessionComponent.sessionContext.context.principal)
            assertEquals("test-tenant", sessionComponent.sessionContext.context.tenant.tenantId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextEqualsWorks() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session2 = contextInstance.sessionContextManager.createOrGetFromId("session-2")
            val session1Again = contextInstance.sessionContextManager.createOrGetFromId("session-1")

            // Same session ID should return same component
            assertEquals(session1.sessionContext, session1Again.sessionContext)

            // Different session IDs should not be equal
            assertNotEquals(session1.sessionContext.sessionId, session2.sessionContext.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextToStringContainsSessionId() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionComponent = contextInstance.sessionContextManager.createOrGetFromId("unique-session-id")
            val stringRep = sessionComponent.sessionContext.toString()

            assertTrue(stringRep.contains("unique-session-id"), "toString should contain session ID")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextHashCodeWorks() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session1Again = contextInstance.sessionContextManager.createOrGetFromId("session-1")

            assertEquals(session1.sessionContext.hashCode(), session1Again.sessionContext.hashCode())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== SessionContextManagerImpl Tests ==========

    @Test
    fun sessionContextManagerCreatesSession() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = contextInstance.sessionContextManager
            val sessionComponent = sessionManager.createOrGetFromId("new-session")

            assertNotNull(sessionComponent)
            assertEquals("new-session", sessionComponent.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerTracksActiveSessions() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = contextInstance.sessionContextManager

            assertFalse(sessionManager.hasActive(), "Should not have active session initially")

            sessionManager.createOrGetFromId("session-1")
            assertTrue(sessionManager.hasActive(), "Should have active session after creation")
            assertEquals("session-1", sessionManager.getActive().sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerCanActivateById() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = contextInstance.sessionContextManager

            sessionManager.createOrGetFromId("session-1")
            sessionManager.createOrGetFromId("session-2")

            assertEquals("session-2", sessionManager.getActive().sessionId)

            sessionManager.activateById("session-1")
            assertEquals("session-1", sessionManager.getActive().sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerCanDestroyById() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = contextInstance.sessionContextManager

            sessionManager.createOrGetFromId("session-1")
            assertTrue(sessionManager.hasActive())

            sessionManager.destroyById("session-1")
            assertFalse(sessionManager.hasActive())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionContextManagerCanDestroyAll() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionManager = contextInstance.sessionContextManager

            sessionManager.createOrGetFromId("session-1")
            sessionManager.createOrGetFromId("session-2")
            sessionManager.createOrGetFromId("session-3")

            assertTrue(sessionManager.hasActive())

            sessionManager.destroyAll()
            assertFalse(sessionManager.hasActive())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== SessionInstanceImpl Tests ==========

    @Test
    fun sessionInstanceHasSessionId() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("instance-session")

            assertEquals("instance-session", sessionInstance.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionInstanceHasSessionContext() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("instance-session")

            assertNotNull(sessionInstance.sessionContext)
            assertEquals("instance-session", sessionInstance.sessionContext.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionInstanceHasComponent() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("instance-session")

            assertNotNull(sessionInstance.component)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionInstanceIsCurrentlyActive() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("active-session")

            assertTrue(sessionInstance.isCurrentlyActive(), "New session should be active")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionInstanceMakeActiveWorks() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session2 = contextInstance.sessionContextManager.createOrGetFromId("session-2")

            assertFalse(session1.isCurrentlyActive(), "Session 1 should not be active after session 2 created")
            assertTrue(session2.isCurrentlyActive(), "Session 2 should be active")

            session1.makeActive()
            assertTrue(session1.isCurrentlyActive(), "Session 1 should be active after makeActive")
            assertFalse(session2.isCurrentlyActive(), "Session 2 should no longer be active")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionInstanceDestroyWorks() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("to-destroy")

            assertTrue(contextInstance.sessionContextManager.hasActive())

            sessionInstance.destroy()

            assertFalse(contextInstance.sessionContextManager.hasActive())
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionInstanceEqualsWorks() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session1Again = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session2 = contextInstance.sessionContextManager.createOrGetFromId("session-2")

            assertEquals(session1, session1Again, "Same session ID should return equal instances")
            assertNotEquals(session1, session2, "Different session IDs should not be equal")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionInstanceHashCodeWorks() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
            val session1Again = contextInstance.sessionContextManager.createOrGetFromId("session-1")

            assertEquals(session1.hashCode(), session1Again.hashCode())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Anonymous Session Tests ==========

    @Test
    fun anonymousSessionIsAnonymous() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.getAnonymous()
            val sessionInstance = contextInstance.sessionContextManager.getActive()

            assertTrue(sessionInstance.sessionContext.isAnonymous(), "Anonymous session should be anonymous")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun nonAnonymousSessionIsNotAnonymous() {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = contextInstance.sessionContextManager.createOrGetFromId("real-session")

            assertFalse(sessionInstance.sessionContext.isAnonymous(), "Non-anonymous session should not be anonymous")
        } finally {
            appComponent.destroy()
        }
    }
}

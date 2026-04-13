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
 *
 */

package com.sphereon.di.session

import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExecutionContextTest {
    @Test
    fun shouldCreateAnExecutionContext() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")
        val contextInstance =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test@principal.com"),
                DefaultPrincipalInputString("test@principal.com"),
            )
        assertTrue { appGraph.userContextManager.hasActive() }

        val sessionContextGraph = contextInstance.sessionContextManager.createOrGetFromId(sessionId = "test-action")
        assertNotNull(sessionContextGraph)
        val sessionContext = contextInstance.sessionContextManager.getActive()
        assertNotNull(sessionContext)
        assertEquals("test-action", sessionContext.sessionId)
        println(sessionContext)

        appGraph.destroy()
    }

    @Test
    fun shouldBeAnonymousBeforeUserContextIsCreated() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")

        // Before creating any user context, should be anonymous
        assertTrue(appGraph.userContextManager.isAnonymous(), "UserContextManager should be anonymous initially")
        assertFalse(appGraph.userContextManager.hasActive(), "Should not have active context initially")

        val activeUserContext = appGraph.userContextManager.getActive()
        assertEquals("<anonymous>", activeUserContext.context.principal, "Active user context principal should be anonymous")
        assertEquals("<anonymous>", activeUserContext.context.tenant.tenantId, "Active user context tenant should be anonymous")

        appGraph.destroy()
    }

    @Test
    fun shouldNotBeAnonymousAfterUserContextIsCreated() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")

        // Initially anonymous
        assertTrue(appGraph.userContextManager.isAnonymous(), "Should be anonymous before creating context")

        // Create user context
        val contextInstance =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
            )

        // After creation, should not be anonymous
        assertFalse(appGraph.userContextManager.isAnonymous(), "UserContextManager should not be anonymous after creating context")
        assertTrue(appGraph.userContextManager.hasActive(), "Should have active context after creation")

        val activeUserContext = appGraph.userContextManager.getActive()
        assertEquals("test-user", activeUserContext.context.principal, "Active user context principal should match")
        assertEquals("test-tenant", activeUserContext.context.tenant.tenantId, "Active user context tenant should match")

        appGraph.destroy()
    }

    @Test
    fun shouldBeAnonymousAgainAfterUserContextIsDestroyed() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")

        // Create user context
        val contextInstance =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
            )

        assertFalse(appGraph.userContextManager.isAnonymous(), "Should not be anonymous after creation")

        // Destroy the context
        val contextId = contextInstance.contextId
        appGraph.userContextManager.destroyById(contextId)

        // After destruction, should be anonymous again
        assertTrue(appGraph.userContextManager.isAnonymous(), "UserContextManager should be anonymous after destroying context")
        assertFalse(appGraph.userContextManager.hasActive(), "Should not have active context after destruction")

        val activeUserContext = appGraph.userContextManager.getActive()
        assertEquals("<anonymous>", activeUserContext.context.principal, "Active user context principal should be anonymous again")
        assertEquals("<anonymous>", activeUserContext.context.tenant.tenantId, "Active user context tenant should be anonymous again")

        appGraph.destroy()
    }

    @Test
    fun shouldBeAnonymousBeforeSessionContextIsCreated() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")

        // Create user context
        val contextInstance =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
            )

        // Before creating any session, should be anonymous
        assertFalse(contextInstance.sessionContextManager.hasActive(), "Should not have active session initially")

        val activeSession = contextInstance.sessionContextManager.getActive()
        assertTrue(activeSession.sessionContext.isAnonymous(), "Session context should be anonymous initially")

        appGraph.destroy()
    }

    @Test
    fun shouldNotBeAnonymousAfterSessionContextIsCreated() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")

        // Create user context
        val contextInstance =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
            )

        // Create session context
        val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId(sessionId = "test-session")

        // After creation, should not be anonymous
        assertTrue(contextInstance.sessionContextManager.hasActive(), "Should have active session after creation")

        val activeSession = contextInstance.sessionContextManager.getActive()
        assertFalse(activeSession.sessionContext.isAnonymous(), "Session context should not be anonymous after creation")
        assertEquals("test-session", activeSession.sessionId, "Session ID should match")

        appGraph.destroy()
    }

    @Test
    fun shouldBeAnonymousAgainAfterSessionContextIsDestroyed() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")

        // Create user context
        val contextInstance =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
            )

        // Create session context
        val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId(sessionId = "test-session")

        assertFalse(sessionGraph.sessionContext.isAnonymous(), "Should not be anonymous after creation")

        // Destroy the session
        contextInstance.sessionContextManager.destroyById("test-session")

        // After destruction, should be anonymous again
        assertFalse(contextInstance.sessionContextManager.hasActive(), "Should not have active session after destruction")

        val activeSession = contextInstance.sessionContextManager.getActive()
        assertTrue(activeSession.sessionContext.isAnonymous(), "Session context should be anonymous again after destruction")

        appGraph.destroy()
    }

    @Test
    fun shouldGetOrCreateBackgroundServiceContext() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")

        // Get or create background service context
        val backgroundContext = appGraph.userContextManager.getBackgroundService()

        assertNotNull(backgroundContext, "Background service context should not be null")
        assertEquals("<anonymous>", backgroundContext.context.principal, "Background service principal should be anonymous")
        assertEquals("<anonymous>", backgroundContext.context.tenant.tenantId, "Background service tenant should be anonymous")

        // Background service should have a specific context ID
        val backgroundServiceId = appGraph.userContextManager.getBackgroundServiceId()
        assertNotNull(backgroundServiceId, "Background service ID should not be null")

        appGraph.destroy()
    }

    @Test
    fun shouldGetOrCreateAnonymousContextExplicitly() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")

        // Get or create anonymous context
        val anonymousContext = appGraph.userContextManager.getAnonymous(makeActive = false)

        assertNotNull(anonymousContext, "Anonymous context should not be null")
        assertEquals("<anonymous>", anonymousContext.context.principal, "Anonymous context principal should be anonymous")
        assertEquals("<anonymous>", anonymousContext.context.tenant.tenantId, "Anonymous context tenant should be anonymous")

        appGraph.destroy()
    }

    @Test
    fun backgroundServiceContextShouldRemainSeparateFromRegularUserContext() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")

        // Create regular user context
        val userContextInstance =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
                makeActive = true,
            )

        assertFalse(appGraph.userContextManager.isAnonymous(), "Should not be anonymous with active user context")

        // Get background service context (not making it active)
        val backgroundContext = appGraph.userContextManager.getBackgroundService()

        // User context should still be active
        assertFalse(appGraph.userContextManager.isAnonymous(), "User context should still be active")

        val activeContext = appGraph.userContextManager.getActive()
        assertEquals("test-user", activeContext.context.principal, "Active context should still be user context")

        // Background context should be different
        assertEquals("<anonymous>", backgroundContext.context.principal, "Background context should be anonymous")

        appGraph.destroy()
    }

    @Test
    fun multipleSessionsLifecycleWithIsAnonymousChecks() {
        val appGraph = createCoreApiTestAppGraph(this, "appId", "profile", "0.0.1-TEST")

        // Create user context
        val contextInstance =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user"),
            )

        // Create first session
        val session1 = contextInstance.sessionContextManager.createOrGetFromId("session-1")
        assertFalse(session1.sessionContext.isAnonymous(), "Session 1 should not be anonymous")

        // Create second session (becomes active)
        val session2 = contextInstance.sessionContextManager.createOrGetFromId("session-2")
        assertFalse(session2.sessionContext.isAnonymous(), "Session 2 should not be anonymous")
        assertEquals("session-2", contextInstance.sessionContextManager.getActive().sessionId, "Session 2 should be active")

        // Destroy active session
        contextInstance.sessionContextManager.destroyById("session-2")

        // Should fall back to anonymous (session-1 is not active)
        assertFalse(contextInstance.sessionContextManager.hasActive(), "Should not have active session after destroying active one")

        // Activate session-1
        contextInstance.sessionContextManager.activateById("session-1")
        assertTrue(contextInstance.sessionContextManager.hasActive(), "Should have active session after activating session-1")
        assertEquals("session-1", contextInstance.sessionContextManager.getActive().sessionId, "Session 1 should now be active")

        // Destroy all
        contextInstance.sessionContextManager.destroyAll()
        assertFalse(contextInstance.sessionContextManager.hasActive(), "Should not have active session after destroying all sessions")
        appGraph.userContextManager.destroyAll()
        assertTrue(appGraph.userContextManager.isAnonymous(), "Should be anonymous after destroying all sessions")

        appGraph.destroy()
    }
}

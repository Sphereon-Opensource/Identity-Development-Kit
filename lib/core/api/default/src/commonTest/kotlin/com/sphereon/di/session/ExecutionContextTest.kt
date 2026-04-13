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
 *
 */

package com.sphereon.di.session

import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
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
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")
        val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test@principal.com"),
            DefaultPrincipalInputString("test@principal.com")
        )
        assertTrue { appComponent.userContextManager.hasActive() }

        val sessionContextComponent = contextInstance.sessionContextManager.createOrGetFromId(sessionId = "test-action")
        assertNotNull(sessionContextComponent)
        val sessionContext = contextInstance.sessionContextManager.getActive()
        assertNotNull(sessionContext)
        assertEquals("test-action", sessionContext.sessionId)
        println(sessionContext)

        appComponent.destroy()

    }

    @Test
    fun shouldBeAnonymousBeforeUserContextIsCreated() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")

        // Before creating any user context, should be anonymous
        assertTrue(appComponent.userContextManager.isAnonymous(), "UserContextManager should be anonymous initially")
        assertFalse(appComponent.userContextManager.hasActive(), "Should not have active context initially")

        val activeUserContext = appComponent.userContextManager.getActive()
        assertEquals("<anonymous>", activeUserContext.context.principal, "Active user context principal should be anonymous")
        assertEquals("<anonymous>", activeUserContext.context.tenant.tenantId, "Active user context tenant should be anonymous")

        appComponent.destroy()
    }

    @Test
    fun shouldNotBeAnonymousAfterUserContextIsCreated() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")

        // Initially anonymous
        assertTrue(appComponent.userContextManager.isAnonymous(), "Should be anonymous before creating context")

        // Create user context
        val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user")
        )

        // After creation, should not be anonymous
        assertFalse(appComponent.userContextManager.isAnonymous(), "UserContextManager should not be anonymous after creating context")
        assertTrue(appComponent.userContextManager.hasActive(), "Should have active context after creation")

        val activeUserContext = appComponent.userContextManager.getActive()
        assertEquals("test-user", activeUserContext.context.principal, "Active user context principal should match")
        assertEquals("test-tenant", activeUserContext.context.tenant.tenantId, "Active user context tenant should match")

        appComponent.destroy()
    }

    @Test
    fun shouldBeAnonymousAgainAfterUserContextIsDestroyed() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")

        // Create user context
        val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user")
        )

        assertFalse(appComponent.userContextManager.isAnonymous(), "Should not be anonymous after creation")

        // Destroy the context
        val contextId = contextInstance.contextId
        appComponent.userContextManager.destroyById(contextId)

        // After destruction, should be anonymous again
        assertTrue(appComponent.userContextManager.isAnonymous(), "UserContextManager should be anonymous after destroying context")
        assertFalse(appComponent.userContextManager.hasActive(), "Should not have active context after destruction")

        val activeUserContext = appComponent.userContextManager.getActive()
        assertEquals("<anonymous>", activeUserContext.context.principal, "Active user context principal should be anonymous again")
        assertEquals("<anonymous>", activeUserContext.context.tenant.tenantId, "Active user context tenant should be anonymous again")

        appComponent.destroy()
    }

    @Test
    fun shouldBeAnonymousBeforeSessionContextIsCreated() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")

        // Create user context
        val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user")
        )

        // Before creating any session, should be anonymous
        assertFalse(contextInstance.sessionContextManager.hasActive(), "Should not have active session initially")

        val activeSession = contextInstance.sessionContextManager.getActive()
        assertTrue(activeSession.sessionContext.isAnonymous(), "Session context should be anonymous initially")

        appComponent.destroy()
    }

    @Test
    fun shouldNotBeAnonymousAfterSessionContextIsCreated() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")

        // Create user context
        val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user")
        )

        // Create session context
        val sessionComponent = contextInstance.sessionContextManager.createOrGetFromId(sessionId = "test-session")

        // After creation, should not be anonymous
        assertTrue(contextInstance.sessionContextManager.hasActive(), "Should have active session after creation")

        val activeSession = contextInstance.sessionContextManager.getActive()
        assertFalse(activeSession.sessionContext.isAnonymous(), "Session context should not be anonymous after creation")
        assertEquals("test-session", activeSession.sessionId, "Session ID should match")

        appComponent.destroy()
    }

    @Test
    fun shouldBeAnonymousAgainAfterSessionContextIsDestroyed() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")

        // Create user context
        val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user")
        )

        // Create session context
        val sessionComponent = contextInstance.sessionContextManager.createOrGetFromId(sessionId = "test-session")

        assertFalse(sessionComponent.sessionContext.isAnonymous(), "Should not be anonymous after creation")

        // Destroy the session
        contextInstance.sessionContextManager.destroyById("test-session")

        // After destruction, should be anonymous again
        assertFalse(contextInstance.sessionContextManager.hasActive(), "Should not have active session after destruction")

        val activeSession = contextInstance.sessionContextManager.getActive()
        assertTrue(activeSession.sessionContext.isAnonymous(), "Session context should be anonymous again after destruction")

        appComponent.destroy()
    }

    @Test
    fun shouldGetOrCreateBackgroundServiceContext() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")

        // Get or create background service context
        val backgroundContext = appComponent.userContextManager.getBackgroundService()

        assertNotNull(backgroundContext, "Background service context should not be null")
        assertEquals("<anonymous>", backgroundContext.context.principal, "Background service principal should be anonymous")
        assertEquals("<anonymous>", backgroundContext.context.tenant.tenantId, "Background service tenant should be anonymous")

        // Background service should have a specific context ID
        val backgroundServiceId = appComponent.userContextManager.getBackgroundServiceId()
        assertNotNull(backgroundServiceId, "Background service ID should not be null")

        appComponent.destroy()
    }

    @Test
    fun shouldGetOrCreateAnonymousContextExplicitly() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")

        // Get or create anonymous context
        val anonymousContext = appComponent.userContextManager.getAnonymous(makeActive = false)

        assertNotNull(anonymousContext, "Anonymous context should not be null")
        assertEquals("<anonymous>", anonymousContext.context.principal, "Anonymous context principal should be anonymous")
        assertEquals("<anonymous>", anonymousContext.context.tenant.tenantId, "Anonymous context tenant should be anonymous")

        appComponent.destroy()
    }

    @Test
    fun backgroundServiceContextShouldRemainSeparateFromRegularUserContext() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")

        // Create regular user context
        val userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user"),
            makeActive = true
        )

        assertFalse(appComponent.userContextManager.isAnonymous(), "Should not be anonymous with active user context")

        // Get background service context (not making it active)
        val backgroundContext = appComponent.userContextManager.getBackgroundService()

        // User context should still be active
        assertFalse(appComponent.userContextManager.isAnonymous(), "User context should still be active")

        val activeContext = appComponent.userContextManager.getActive()
        assertEquals("test-user", activeContext.context.principal, "Active context should still be user context")

        // Background context should be different
        assertEquals("<anonymous>", backgroundContext.context.principal, "Background context should be anonymous")

        appComponent.destroy()
    }

    @Test
    fun multipleSessionsLifecycleWithIsAnonymousChecks() {
        val appComponent = createCoreApiTestAppComponent(this, "appId", "profile", "0.0.1-TEST")

        // Create user context
        val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
            DefaultTenantInputString("test-tenant"),
            DefaultPrincipalInputString("test-user")
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
        appComponent.userContextManager.destroyAll()
        assertTrue(appComponent.userContextManager.isAnonymous(), "Should be anonymous after destroying all sessions")

        appComponent.destroy()
    }


}

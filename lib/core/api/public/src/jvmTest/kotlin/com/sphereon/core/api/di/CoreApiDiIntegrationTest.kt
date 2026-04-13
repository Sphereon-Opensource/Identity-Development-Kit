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

package com.sphereon.core.api.di

import com.sphereon.core.api.di.createCoreApiTestAppComponent

import com.sphereon.core.api.context.asCoreApiContextComponent
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.di.session.SessionComponent
import com.sphereon.di.session.getService
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.amazon.app.platform.scope.Scoped
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests that verify the DI hierarchy and service injection.
 *
 * These tests use the full DI component hierarchy:
 * AppComponent -> UserContextComponent -> SessionComponent
 */
class CoreApiDiIntegrationTest {

    @Test
    fun appComponentCanBeCreated() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        assertNotNull(app)
        assertNotNull(app.userContextManager)

        app.destroy()
    }

    @Test
    fun userContextCanBeCreated() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        assertNotNull(userContext)
        assertNotNull(userContext.sessionContextManager)

        app.destroy()
    }

    @Test
    fun sessionCanBeCreated() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("test-session")

        assertNotNull(session)
        assertNotNull(session.component)

        app.destroy()
    }

    @Test
    fun sessionExecutionIsAccessible() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("test-session")

        // Access SessionExecution via asCoreApiServiceComponent()
        val coreApiComponent = session.asCoreApiServiceComponent()
        val execution = coreApiComponent.serviceExecution

        assertNotNull(execution)
        assertNotNull(coreApiComponent.sessionContext)

        app.destroy()
    }

    @Test
    fun sessionLogManagerIsAccessible() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("test-session")

        // Access SessionLogManager via asCoreApiServiceComponent()
        val coreApiComponent = session.asCoreApiServiceComponent()
        val logManager = coreApiComponent.logManager

        assertNotNull(logManager)

        app.destroy()
    }

    @Test
    fun loggerCanBeCreated() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("test-session")

        // Access logger via asCoreApiServiceComponent()
        val coreApiComponent = session.asCoreApiServiceComponent()
        val logger = coreApiComponent.logger()

        assertNotNull(logger)

        app.destroy()
    }

    @Test
    fun loggerWithCustomTagCanBeCreated() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("test-session")

        // Access logger with custom tag via asCoreApiServiceComponent()
        val coreApiComponent = session.asCoreApiServiceComponent()
        val logger = coreApiComponent.loggerWithTag("custom-tag")

        assertNotNull(logger)

        app.destroy()
    }

    @Test
    fun multipleSessionsCanBeCreated() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session1 = userContext.sessionContextManager.createOrGetFromId("session-1")
        val session2 = userContext.sessionContextManager.createOrGetFromId("session-2")

        assertNotNull(session1)
        assertNotNull(session2)
        assertTrue(session1 !== session2, "Different session IDs should create different sessions")

        app.destroy()
    }

    @Test
    fun sameSessionIdReturnsSameSession() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session1 = userContext.sessionContextManager.createOrGetFromId("same-session")
        val session2 = userContext.sessionContextManager.createOrGetFromId("same-session")

        assertTrue(session1 === session2, "Same session ID should return same session instance")

        app.destroy()
    }

    @Test
    fun sessionContextHasCorrectSessionId() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("my-session-id")

        val coreApiComponent = session.asCoreApiServiceComponent()
        val sessionContext = coreApiComponent.sessionContext

        assertTrue(sessionContext.sessionId.contains("my-session-id"), "Session context should contain the session ID")

        app.destroy()
    }

    // ========== UserContextManager tests ==========

    @Test
    fun userContextManagerHasActiveWhenMakeActiveTrue() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        // getAnonymous(makeActive = true) should set the context as active
        app.userContextManager.getAnonymous(makeActive = true)
        assertTrue(app.userContextManager.hasActive())

        app.destroy()
    }

    @Test
    fun userContextManagerHasNoActiveByDefault() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        // getAnonymous() without makeActive = true should not set an active context
        app.userContextManager.getAnonymous()
        kotlin.test.assertFalse(app.userContextManager.hasActive())

        app.destroy()
    }

    @Test
    fun userContextManagerGetActive() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val anonymous = app.userContextManager.getAnonymous()
        val active = app.userContextManager.getActive()
        assertNotNull(active)

        app.destroy()
    }

    @Test
    fun userContextManagerListIds() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val anonymous = app.userContextManager.getAnonymous()
        val ids = app.userContextManager.listIds()
        assertTrue(ids.isNotEmpty())

        app.destroy()
    }

    @Test
    fun userContextManagerGetBackgroundService() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val backgroundService = app.userContextManager.getBackgroundService()
        assertNotNull(backgroundService)

        app.destroy()
    }

    @Test
    fun userContextManagerGetBackgroundServiceId() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val bgId = app.userContextManager.getBackgroundServiceId()
        assertNotNull(bgId)
        assertTrue(bgId.contains("background"))

        app.destroy()
    }

    @Test
    fun userContextManagerIsAnonymous() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        app.userContextManager.getAnonymous()
        val isAnon = app.userContextManager.isAnonymous()
        assertTrue(isAnon)

        app.destroy()
    }

    @Test
    fun userContextManagerActiveInstanceFlow() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        app.userContextManager.getAnonymous()
        val activeFlow = app.userContextManager.activeInstance
        assertNotNull(activeFlow)
        assertNotNull(activeFlow.value)

        app.destroy()
    }

    // ========== SessionContextManager tests ==========

    @Test
    fun sessionContextManagerHasActive() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("active-session")
        assertTrue(userContext.sessionContextManager.hasActive())

        app.destroy()
    }

    @Test
    fun sessionContextManagerGetActive() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        userContext.sessionContextManager.createOrGetFromId("active-session")
        val active = userContext.sessionContextManager.getActive()
        assertNotNull(active)

        app.destroy()
    }

    @Test
    fun sessionContextManagerGetById() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        userContext.sessionContextManager.createOrGetFromId("session-abc")
        val session = userContext.sessionContextManager.getById("session-abc")
        assertNotNull(session)

        app.destroy()
    }

    @Test
    fun sessionContextManagerHasById() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        userContext.sessionContextManager.createOrGetFromId("session-xyz")
        assertTrue(userContext.sessionContextManager.hasById("session-xyz"))

        app.destroy()
    }

    @Test
    fun sessionContextManagerListIds() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        userContext.sessionContextManager.createOrGetFromId("session-1")
        userContext.sessionContextManager.createOrGetFromId("session-2")
        val ids = userContext.sessionContextManager.listIds()
        assertTrue(ids.size >= 2)

        app.destroy()
    }

    @Test
    fun sessionContextManagerActivateById() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        userContext.sessionContextManager.createOrGetFromId("session-to-activate")
        val activated = userContext.sessionContextManager.activateById("session-to-activate")
        assertTrue(activated)

        app.destroy()
    }

    @Test
    fun sessionContextManagerGetAnonymous() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val anonSession = userContext.sessionContextManager.getAnonymous()
        assertNotNull(anonSession)

        app.destroy()
    }

    @Test
    fun sessionContextManagerGetOrCreateBackgroundService() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val bgSession = userContext.sessionContextManager.getOrCreateBackgroundService()
        assertNotNull(bgSession)

        app.destroy()
    }

    @Test
    fun sessionContextManagerGetBackgroundServiceId() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val bgId = userContext.sessionContextManager.getBackgroundServiceId()
        assertNotNull(bgId)

        app.destroy()
    }

    @Test
    fun sessionContextManagerActiveInstanceFlow() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        userContext.sessionContextManager.createOrGetFromId("flow-session")
        val activeFlow = userContext.sessionContextManager.activeInstance
        assertNotNull(activeFlow)

        app.destroy()
    }

    // ========== SessionInstance tests ==========

    @Test
    fun sessionInstanceSessionId() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("instance-session")
        assertTrue(session.sessionId.contains("instance-session"))

        app.destroy()
    }

    @Test
    fun sessionInstanceSessionContext() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("ctx-session")
        assertNotNull(session.sessionContext)

        app.destroy()
    }

    @Test
    fun sessionInstanceSessionExecution() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("exec-session")
        assertNotNull(session.sessionExecution)

        app.destroy()
    }

    @Test
    fun sessionInstanceComponent() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("comp-session")
        assertNotNull(session.component)

        app.destroy()
    }

    @Test
    fun sessionInstanceScope() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("scope-session")
        assertNotNull(session.scope)

        app.destroy()
    }

    @Test
    fun sessionInstanceIsCurrentlyActive() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("active-check-session")
        assertTrue(session.isCurrentlyActive())

        app.destroy()
    }

    @Test
    fun sessionInstanceMakeActive() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session1 = userContext.sessionContextManager.createOrGetFromId("session-make-active-1")
        val session2 = userContext.sessionContextManager.createOrGetFromId("session-make-active-2", makeActive = false)

        // session1 should be active
        assertTrue(session1.isCurrentlyActive())

        // Make session2 active
        val made = session2.makeActive()
        assertTrue(made)
        assertTrue(session2.isCurrentlyActive())

        app.destroy()
    }

    // ========== UserContextInstance tests ==========

    @Test
    fun userContextInstanceContextId() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        assertNotNull(userContext.contextId)

        app.destroy()
    }

    @Test
    fun userContextInstanceContext() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        assertNotNull(userContext.context)

        app.destroy()
    }

    @Test
    fun userContextInstanceComponent() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        assertNotNull(userContext.component)

        app.destroy()
    }

    @Test
    fun userContextInstanceScope() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        assertNotNull(userContext.scope)

        app.destroy()
    }

    @Test
    fun userContextInstanceUserContextManager() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        assertNotNull(userContext.userContextManager)

        app.destroy()
    }

    @Test
    fun userContextInstanceSessionContextManager() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        assertNotNull(userContext.sessionContextManager)

        app.destroy()
    }

    @Test
    fun userContextInstanceIsCurrentlyActive() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        assertTrue(userContext.isCurrentlyActive())

        app.destroy()
    }

    @Test
    fun userContextInstanceCreateSession() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.createSession("direct-session")
        assertNotNull(session)
        assertTrue(session.sessionId.contains("direct-session"))

        app.destroy()
    }

    @Test
    fun userContextInstanceGetOrCreateAnonymousSession() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val anonSession = userContext.getOrCreateAnonymousSession()
        assertNotNull(anonSession)

        app.destroy()
    }

    @Test
    fun userContextInstanceGetOrCreateBackgroundServiceSession() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val bgSession = userContext.getOrCreateBackgroundServiceSession()
        assertNotNull(bgSession)

        app.destroy()
    }

    // ========== CoreApiAppExtensionComponent tests ==========

    @Test
    fun appComponentExposesAppLogManager() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        // Cast to access CoreApiAppExtensionComponent methods
        val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionComponent
        assertNotNull(coreApiApp.appLogManager)

        app.destroy()
    }

    @Test
    fun appComponentExposesAppConfig() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionComponent
        assertNotNull(coreApiApp.appConfig)

        app.destroy()
    }

    @Test
    fun appComponentExposesServiceExecutor() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionComponent
        assertNotNull(coreApiApp.serviceExecutor)

        app.destroy()
    }

    @Test
    fun appComponentAnonymousContextComponentIsAccessible() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionComponent
        val anonComponent = coreApiApp.anonymousContextComponent.value
        assertNotNull(anonComponent)

        app.destroy()
    }

    @Test
    fun appComponentAppContextLogManagerIsAccessible() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionComponent
        assertNotNull(coreApiApp.appContextLogManager)

        app.destroy()
    }

    @Test
    fun appComponentAppLoggerWithTagWorks() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionComponent
        val logger = coreApiApp.appLoggerWithTag("test-tag")
        assertNotNull(logger)

        app.destroy()
    }

    @Test
    fun appComponentAppLoggerWorks() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionComponent
        val logger = coreApiApp.appLogger()
        assertNotNull(logger)

        app.destroy()
    }

    @Test
    fun appComponentAsAppComponentReturnsItself() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionComponent
        val asApp = coreApiApp.asAppComponent()
        assertNotNull(asApp)
        assertTrue(asApp === app)

        app.destroy()
    }

    // ========== More UserContextManager edge cases ==========

    @Test
    fun userContextManagerHasById() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        // Check for anonymous context which always exists
        assertTrue(app.userContextManager.hasById(com.sphereon.di.context.UserContext.ANONYMOUS))

        app.destroy()
    }

    @Test
    fun userContextManagerActivateByIdWithAnonymous() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val result = app.userContextManager.activateById(com.sphereon.di.context.UserContext.ANONYMOUS)
        assertTrue(result)

        app.destroy()
    }

    @Test
    fun userContextManagerDestroyAll() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        // Create some contexts first
        app.userContextManager.getAnonymous(makeActive = true)

        // Destroy all should not throw
        app.userContextManager.destroyAll()

        app.destroy()
    }

    // ========== SessionContextManager edge cases ==========

    @Test
    fun sessionContextManagerDestroyById() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("session-to-destroy")

        // Destroy the session
        userContext.sessionContextManager.destroyById(session.sessionId)

        // Session should no longer exist
        kotlin.test.assertFalse(userContext.sessionContextManager.hasById("session-to-destroy"))

        app.destroy()
    }

    @Test
    fun sessionContextManagerDestroyAll() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        userContext.sessionContextManager.createOrGetFromId("session-1")
        userContext.sessionContextManager.createOrGetFromId("session-2")

        // Destroy all should not throw
        userContext.sessionContextManager.destroyAll()

        app.destroy()
    }

    @Test
    fun sessionInstanceDestroy() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("session-to-destroy-via-instance")

        // Destroy via instance method
        session.destroy()

        // Session should be destroyed
        kotlin.test.assertFalse(userContext.sessionContextManager.hasById("session-to-destroy-via-instance"))

        app.destroy()
    }

    @Test
    fun userContextInstanceDestroy() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        // Create a non-anonymous context to destroy
        val userContext = app.userContextManager.createOrGetFromInputs(
            com.sphereon.core.defaults.context.DefaultTenantInputString("test-tenant"),
            com.sphereon.core.defaults.context.DefaultPrincipalInputString("test-principal")
        )

        val contextId = userContext.contextId

        // Destroy the context
        userContext.destroy()

        // Context should no longer exist
        kotlin.test.assertFalse(app.userContextManager.hasById(contextId))

        app.destroy()
    }

    @Test
    fun userContextInstanceMakeActive() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        // Create two contexts
        val context1 = app.userContextManager.createOrGetFromInputs(
            com.sphereon.core.defaults.context.DefaultTenantInputString("tenant-1"),
            com.sphereon.core.defaults.context.DefaultPrincipalInputString("principal-1"),
            makeActive = true
        )
        val context2 = app.userContextManager.createOrGetFromInputs(
            com.sphereon.core.defaults.context.DefaultTenantInputString("tenant-2"),
            com.sphereon.core.defaults.context.DefaultPrincipalInputString("principal-2"),
            makeActive = false
        )

        // context1 should be active
        assertTrue(context1.isCurrentlyActive())
        kotlin.test.assertFalse(context2.isCurrentlyActive())

        // Make context2 active
        val made = context2.makeActive()
        assertTrue(made)
        assertTrue(context2.isCurrentlyActive())

        app.destroy()
    }

    // ========== CoreApiContextExtensionComponent tests ==========

    @Test
    fun coreApiContextExtensionComponentLoggerWithTag() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val coreApiContext = userContext.asCoreApiContextComponent()

        // Test loggerWithTag default method
        val logger = coreApiContext.loggerWithTag("test-tag")
        assertNotNull(logger)

        app.destroy()
    }

    @Test
    fun coreApiContextExtensionComponentLoggerWithDefaultTag() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val coreApiContext = userContext.asCoreApiContextComponent()

        // Test loggerWithTag with default parameter
        val logger = coreApiContext.loggerWithTag()
        assertNotNull(logger)

        app.destroy()
    }

    @Test
    fun coreApiContextExtensionComponentAsSureComponent() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val coreApiContext = userContext.asCoreApiContextComponent()

        // Test asSureComponent default method
        val sureComponent = coreApiContext.asSureComponent()
        assertNotNull(sureComponent)
        assertTrue(sureComponent === userContext.component)

        app.destroy()
    }

    @Test
    fun coreApiContextExtensionComponentCreateExecutionContextComponent() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val coreApiContext = userContext.asCoreApiContextComponent()

        // Test createExecutionContextComponent default method
        val sessionInstance = coreApiContext.createExecutionContextComponent(userContext.context, "test-session-id")
        assertNotNull(sessionInstance)
        assertTrue(sessionInstance.sessionId.contains("test-session-id"))

        app.destroy()
    }

    @Test
    fun coreApiContextExtensionComponentExposesSessionContextManager() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val coreApiContext = userContext.asCoreApiContextComponent()

        assertNotNull(coreApiContext._sessionContextManager)

        app.destroy()
    }

    @Test
    fun coreApiContextExtensionComponentExposesLogManager() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val coreApiContext = userContext.asCoreApiContextComponent()

        assertNotNull(coreApiContext.logManager)

        app.destroy()
    }

    @Test
    fun coreApiContextExtensionComponentExposesConf() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val coreApiContext = userContext.asCoreApiContextComponent()

        assertNotNull(coreApiContext.conf)

        app.destroy()
    }

    @Test
    fun coreApiContextExtensionComponentExposesServiceExecutor() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val coreApiContext = userContext.asCoreApiContextComponent()

        assertNotNull(coreApiContext.serviceExecutor)

        app.destroy()
    }

    // ========== CoreApiSessionExtensionComponent tests ==========

    @Test
    fun coreApiSessionExtensionComponentAsSureComponent() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("sure-component-session")

        val coreApiSession = session.asCoreApiServiceComponent()
        val sureComponent = coreApiSession.asSureComponent()

        assertNotNull(sureComponent)
        assertTrue(sureComponent === session.component)

        app.destroy()
    }

    @Test
    fun sessionComponentAsCoreApiServiceComponent() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("component-cast-session")

        // Test SessionComponent.asCoreApiServiceComponent() extension function
        val sessionComponent: SessionComponent = session.component
        val coreApiSession = sessionComponent.asCoreApiServiceComponent()

        assertNotNull(coreApiSession)
        assertNotNull(coreApiSession.serviceExecution)
        assertNotNull(coreApiSession.sessionContext)
        assertNotNull(coreApiSession.logManager)

        app.destroy()
    }

    @Test
    fun coreApiSessionExtensionComponentLoggerDefaultMethod() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("logger-default-session")

        val coreApiSession = session.asCoreApiServiceComponent()

        // Test logger() which delegates to loggerWithTag()
        val logger = coreApiSession.logger()
        assertNotNull(logger)

        app.destroy()
    }

    @Test
    fun coreApiSessionExtensionComponentLoggerWithTagDefaultParameter() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("logger-tag-default-session")

        val coreApiSession = session.asCoreApiServiceComponent()

        // Test loggerWithTag() with default parameter (uses sessionContext.sessionId)
        val logger = coreApiSession.loggerWithTag()
        assertNotNull(logger)

        app.destroy()
    }

    @Test
    fun coreApiSessionExtensionComponentServiceExecutionIsSessionExecution() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("service-execution-session")

        val coreApiSession = session.asCoreApiServiceComponent()
        val serviceExecution = coreApiSession.serviceExecution

        // Verify serviceExecution is SessionExecution
        assertNotNull(serviceExecution)
        kotlin.test.assertFalse(serviceExecution.isAnonymous())

        app.destroy()
    }

    @Test
    fun coreApiSessionExtensionComponentSessionContextMatchesSessionId() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("context-match-session")

        val coreApiSession = session.asCoreApiServiceComponent()
        val sessionContext = coreApiSession.sessionContext

        assertTrue(sessionContext.sessionId.contains("context-match-session"))

        app.destroy()
    }

    // ========== SessionInstance.getService<T>() reified extension tests ==========

    @Test
    fun sessionInstanceGetServiceReifiedExtensionReturnsService() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("get-service-session")

        // First add a service using the addService method
        val testServiceValue = "test-service-value"
        session.addService("String", testServiceValue)

        // Use the reified getService<T>() extension function
        val retrievedService: String = session.getService()
        assertNotNull(retrievedService)
        assertTrue(retrievedService == testServiceValue)

        app.destroy()
    }

    @Test
    fun sessionInstanceGetServiceReifiedExtensionUsesClassName() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("get-service-classname-session")

        // Add a service with class simple name
        val testList = listOf("a", "b", "c")
        session.addService("List", testList)

        // Use the reified getService<T>() extension function - it should derive "List" from List::class
        val retrievedService: List<*> = session.getService()
        assertNotNull(retrievedService)
        assertTrue(retrievedService.size == 3)

        app.destroy()
    }

    // ========== SessionComponent.sessionScopedInstances tests (covers provideEmptyScoped) ==========

    @Test
    fun sessionComponentSessionScopedInstancesIsAccessible() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("scoped-instances-session")

        // Access sessionScopedInstances which triggers provideEmptyScoped
        val scopedInstances: Set<Scoped> = session.component.sessionScopedInstances
        assertNotNull(scopedInstances)
        // The set should contain at least the NO_OP instance from provideEmptyScoped
        assertTrue(scopedInstances.isNotEmpty())

        app.destroy()
    }

    @Test
    fun sessionComponentSessionScopedInstancesContainsNoOpScoped() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("noop-scoped-session")

        // Access sessionScopedInstances
        val scopedInstances = session.component.sessionScopedInstances

        // Check that NO_OP is in the set (from provideEmptyScoped)
        val hasNoOp = scopedInstances.any { it === Scoped.NO_OP }
        assertTrue(hasNoOp, "sessionScopedInstances should contain Scoped.NO_OP from provideEmptyScoped")

        app.destroy()
    }

    @Test
    fun sessionComponentProvideSessionScopeCoroutineScopeScopedIsAccessible() = runTest {
        val testScope = TestScope()
        val app = createCoreApiTestAppComponent(testScope)

        val userContext = app.userContextManager.getAnonymous()
        val session = userContext.sessionContextManager.createOrGetFromId("coroutine-scope-session")

        // Access the sessionScopeCoroutineScopeScoped which triggers provideSessionScopeCoroutineScopeScoped
        val coroutineScopeScoped = session.component.sessionScopeCoroutineScopeScoped
        assertNotNull(coroutineScopeScoped)

        // Also create a child scope from it which simulates what provideSessionCoroutineScope does
        val childScope = coroutineScopeScoped.createChild()
        assertNotNull(childScope)

        app.destroy()
    }
}

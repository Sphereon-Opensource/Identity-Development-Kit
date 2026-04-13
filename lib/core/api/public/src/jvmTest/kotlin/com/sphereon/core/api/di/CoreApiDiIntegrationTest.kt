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

package com.sphereon.core.api.di

import com.sphereon.core.api.context.asCoreApiContextGraph
import com.sphereon.core.api.di.createCoreApiTestAppGraph
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.session.SessionGraph
import com.sphereon.di.session.getService
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.amazon.app.platform.scope.Scoped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Integration tests that verify the DI hierarchy and service injection.
 *
 * These tests use the full DI graph hierarchy:
 * AppGraph -> UserContextGraph -> SessionGraph
 */
class CoreApiDiIntegrationTest {
    @Test
    fun appGraphCanBeCreated() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            assertNotNull(app)
            assertNotNull(app.userContextManager)

            app.destroy()
        }

    @Test
    fun userContextCanBeCreated() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            assertNotNull(userContext)
            assertNotNull(userContext.sessionContextManager)

            app.destroy()
        }

    @Test
    fun sessionCanBeCreated() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            assertNotNull(session)
            assertNotNull(session.graph)

            app.destroy()
        }

    @Test
    fun sessionExecutionIsAccessible() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            // Access SessionExecution via asCoreApiServiceGraph()
            val coreApiGraph = session.asCoreApiServiceGraph()
            val execution = coreApiGraph.serviceExecution

            assertNotNull(execution)
            assertNotNull(coreApiGraph.sessionContext)

            app.destroy()
        }

    @Test
    fun sessionLogManagerIsAccessible() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            // Access SessionLogManager via asCoreApiServiceGraph()
            val coreApiGraph = session.asCoreApiServiceGraph()
            val logManager = coreApiGraph.logManager

            assertNotNull(logManager)

            app.destroy()
        }

    @Test
    fun loggerCanBeCreated() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            // Access logger via asCoreApiServiceGraph()
            val coreApiGraph = session.asCoreApiServiceGraph()
            val logger = coreApiGraph.logger()

            assertNotNull(logger)

            app.destroy()
        }

    @Test
    fun loggerWithCustomTagCanBeCreated() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("test-session")

            // Access logger with custom tag via asCoreApiServiceGraph()
            val coreApiGraph = session.asCoreApiServiceGraph()
            val logger = coreApiGraph.loggerWithTag("custom-tag")

            assertNotNull(logger)

            app.destroy()
        }

    @Test
    fun multipleSessionsCanBeCreated() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session1 = userContext.sessionContextManager.createOrGetFromId("session-1")
            val session2 = userContext.sessionContextManager.createOrGetFromId("session-2")

            assertNotNull(session1)
            assertNotNull(session2)
            assertTrue(session1 !== session2, "Different session IDs should create different sessions")

            app.destroy()
        }

    @Test
    fun sameSessionIdReturnsSameSession() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session1 = userContext.sessionContextManager.createOrGetFromId("same-session")
            val session2 = userContext.sessionContextManager.createOrGetFromId("same-session")

            assertTrue(session1 === session2, "Same session ID should return same session instance")

            app.destroy()
        }

    @Test
    fun sessionContextHasCorrectSessionId() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("my-session-id")

            val coreApiGraph = session.asCoreApiServiceGraph()
            val sessionContext = coreApiGraph.sessionContext

            assertTrue(sessionContext.sessionId.contains("my-session-id"), "Session context should contain the session ID")

            app.destroy()
        }

    // ========== UserContextManager tests ==========

    @Test
    fun userContextManagerHasActiveWhenMakeActiveTrue() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            // getAnonymous(makeActive = true) should set the context as active
            app.userContextManager.getAnonymous(makeActive = true)
            assertTrue(app.userContextManager.hasActive())

            app.destroy()
        }

    @Test
    fun userContextManagerHasNoActiveByDefault() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            // getAnonymous() without makeActive = true should not set an active context
            app.userContextManager.getAnonymous()
            kotlin.test.assertFalse(app.userContextManager.hasActive())

            app.destroy()
        }

    @Test
    fun userContextManagerGetActive() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val anonymous = app.userContextManager.getAnonymous()
            val active = app.userContextManager.getActive()
            assertNotNull(active)

            app.destroy()
        }

    @Test
    fun userContextManagerListIds() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val anonymous = app.userContextManager.getAnonymous()
            val ids = app.userContextManager.listIds()
            assertTrue(ids.isNotEmpty())

            app.destroy()
        }

    @Test
    fun userContextManagerGetBackgroundService() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val backgroundService = app.userContextManager.getBackgroundService()
            assertNotNull(backgroundService)

            app.destroy()
        }

    @Test
    fun userContextManagerGetBackgroundServiceId() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val bgId = app.userContextManager.getBackgroundServiceId()
            assertNotNull(bgId)
            assertTrue(bgId.contains("background"))

            app.destroy()
        }

    @Test
    fun userContextManagerIsAnonymous() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            app.userContextManager.getAnonymous()
            val isAnon = app.userContextManager.isAnonymous()
            assertTrue(isAnon)

            app.destroy()
        }

    @Test
    fun userContextManagerActiveInstanceFlow() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            app.userContextManager.getAnonymous()
            val activeFlow = app.userContextManager.activeInstance
            assertNotNull(activeFlow)
            assertNotNull(activeFlow.value)

            app.destroy()
        }

    // ========== SessionContextManager tests ==========

    @Test
    fun sessionContextManagerHasActive() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("active-session")
            assertTrue(userContext.sessionContextManager.hasActive())

            app.destroy()
        }

    @Test
    fun sessionContextManagerGetActive() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            userContext.sessionContextManager.createOrGetFromId("active-session")
            val active = userContext.sessionContextManager.getActive()
            assertNotNull(active)

            app.destroy()
        }

    @Test
    fun sessionContextManagerGetById() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            userContext.sessionContextManager.createOrGetFromId("session-abc")
            val session = userContext.sessionContextManager.getById("session-abc")
            assertNotNull(session)

            app.destroy()
        }

    @Test
    fun sessionContextManagerHasById() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            userContext.sessionContextManager.createOrGetFromId("session-xyz")
            assertTrue(userContext.sessionContextManager.hasById("session-xyz"))

            app.destroy()
        }

    @Test
    fun sessionContextManagerListIds() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            userContext.sessionContextManager.createOrGetFromId("session-1")
            userContext.sessionContextManager.createOrGetFromId("session-2")
            val ids = userContext.sessionContextManager.listIds()
            assertTrue(ids.size >= 2)

            app.destroy()
        }

    @Test
    fun sessionContextManagerActivateById() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            userContext.sessionContextManager.createOrGetFromId("session-to-activate")
            val activated = userContext.sessionContextManager.activateById("session-to-activate")
            assertTrue(activated)

            app.destroy()
        }

    @Test
    fun sessionContextManagerGetAnonymous() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val anonSession = userContext.sessionContextManager.getAnonymous()
            assertNotNull(anonSession)

            app.destroy()
        }

    @Test
    fun sessionContextManagerGetOrCreateBackgroundService() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val bgSession = userContext.sessionContextManager.getOrCreateBackgroundService()
            assertNotNull(bgSession)

            app.destroy()
        }

    @Test
    fun sessionContextManagerGetBackgroundServiceId() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val bgId = userContext.sessionContextManager.getBackgroundServiceId()
            assertNotNull(bgId)

            app.destroy()
        }

    @Test
    fun sessionContextManagerActiveInstanceFlow() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            userContext.sessionContextManager.createOrGetFromId("flow-session")
            val activeFlow = userContext.sessionContextManager.activeInstance
            assertNotNull(activeFlow)

            app.destroy()
        }

    // ========== SessionInstance tests ==========

    @Test
    fun sessionInstanceSessionId() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("instance-session")
            assertTrue(session.sessionId.contains("instance-session"))

            app.destroy()
        }

    @Test
    fun sessionInstanceSessionContext() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("ctx-session")
            assertNotNull(session.sessionContext)

            app.destroy()
        }

    @Test
    fun sessionInstanceSessionExecution() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("exec-session")
            assertNotNull(session.sessionExecution)

            app.destroy()
        }

    @Test
    fun sessionInstanceGraph() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("comp-session")
            assertNotNull(session.graph)

            app.destroy()
        }

    @Test
    fun sessionInstanceScope() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("scope-session")
            assertNotNull(session.scope)

            app.destroy()
        }

    @Test
    fun sessionInstanceIsCurrentlyActive() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("active-check-session")
            assertTrue(session.isCurrentlyActive())

            app.destroy()
        }

    @Test
    fun sessionInstanceMakeActive() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

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
    fun userContextInstanceContextId() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            assertNotNull(userContext.contextId)

            app.destroy()
        }

    @Test
    fun userContextInstanceContext() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            assertNotNull(userContext.context)

            app.destroy()
        }

    @Test
    fun userContextInstanceGraph() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            assertNotNull(userContext.graph)

            app.destroy()
        }

    @Test
    fun userContextInstanceScope() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            assertNotNull(userContext.scope)

            app.destroy()
        }

    @Test
    fun userContextInstanceUserContextManager() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            assertNotNull(userContext.userContextManager)

            app.destroy()
        }

    @Test
    fun userContextInstanceSessionContextManager() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            assertNotNull(userContext.sessionContextManager)

            app.destroy()
        }

    @Test
    fun userContextInstanceIsCurrentlyActive() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            assertTrue(userContext.isCurrentlyActive())

            app.destroy()
        }

    @Test
    fun userContextInstanceCreateSession() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.createSession("direct-session")
            assertNotNull(session)
            assertTrue(session.sessionId.contains("direct-session"))

            app.destroy()
        }

    @Test
    fun userContextInstanceGetOrCreateAnonymousSession() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val anonSession = userContext.getOrCreateAnonymousSession()
            assertNotNull(anonSession)

            app.destroy()
        }

    @Test
    fun userContextInstanceGetOrCreateBackgroundServiceSession() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val bgSession = userContext.getOrCreateBackgroundServiceSession()
            assertNotNull(bgSession)

            app.destroy()
        }

    // ========== CoreApiAppExtensionGraph tests ==========

    @Test
    fun appGraphExposesAppLogManager() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            // Cast to access CoreApiAppExtensionGraph methods
            val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionGraph
            assertNotNull(coreApiApp.appLogManager)

            app.destroy()
        }

    @Test
    fun appGraphExposesAppConfig() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionGraph
            assertNotNull(coreApiApp.appConfig)

            app.destroy()
        }

    @Test
    fun appGraphExposesCommandExecutor() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionGraph
            assertNotNull(coreApiApp.commandExecutor)

            app.destroy()
        }

    @Test
    fun appGraphAnonymousContextGraphIsAccessible() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionGraph
            val anonGraph = coreApiApp.anonymousContextGraph.value
            assertNotNull(anonGraph)

            app.destroy()
        }

    @Test
    fun appGraphAppContextLogManagerIsAccessible() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionGraph
            assertNotNull(coreApiApp.appContextLogManager)

            app.destroy()
        }

    @Test
    fun appGraphAppLoggerWithTagWorks() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionGraph
            val logger = coreApiApp.appLoggerWithTag("test-tag")
            assertNotNull(logger)

            app.destroy()
        }

    @Test
    fun appGraphAppLoggerWorks() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionGraph
            val logger = coreApiApp.appLogger()
            assertNotNull(logger)

            app.destroy()
        }

    @Test
    fun appGraphExposesAppExtensionGraph() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val coreApiApp = app as com.sphereon.core.api.app.CoreApiAppExtensionGraph
            assertNotNull(coreApiApp)
            assertNotNull(coreApiApp.commandExecutor)

            app.destroy()
        }

    // ========== More UserContextManager edge cases ==========

    @Test
    fun userContextManagerHasById() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            // Check for anonymous context which always exists
            assertTrue(app.userContextManager.hasById(com.sphereon.di.context.UserContext.ANONYMOUS))

            app.destroy()
        }

    @Test
    fun userContextManagerActivateByIdWithAnonymous() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val result = app.userContextManager.activateById(com.sphereon.di.context.UserContext.ANONYMOUS)
            assertTrue(result)

            app.destroy()
        }

    @Test
    fun userContextManagerDestroyAll() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            // Create some contexts first
            app.userContextManager.getAnonymous(makeActive = true)

            // Destroy all should not throw
            app.userContextManager.destroyAll()

            app.destroy()
        }

    // ========== SessionContextManager edge cases ==========

    @Test
    fun sessionContextManagerDestroyById() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("session-to-destroy")

            // Destroy the session
            userContext.sessionContextManager.destroyById(session.sessionId)

            // Session should no longer exist
            kotlin.test.assertFalse(userContext.sessionContextManager.hasById("session-to-destroy"))

            app.destroy()
        }

    @Test
    fun sessionContextManagerDestroyAll() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            userContext.sessionContextManager.createOrGetFromId("session-1")
            userContext.sessionContextManager.createOrGetFromId("session-2")

            // Destroy all should not throw
            userContext.sessionContextManager.destroyAll()

            app.destroy()
        }

    @Test
    fun sessionInstanceDestroy() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("session-to-destroy-via-instance")

            // Destroy via instance method
            session.destroy()

            // Session should be destroyed
            kotlin.test.assertFalse(userContext.sessionContextManager.hasById("session-to-destroy-via-instance"))

            app.destroy()
        }

    @Test
    fun userContextInstanceDestroy() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            // Create a non-anonymous context to destroy
            val userContext =
                app.userContextManager.createOrGetFromInputs(
                    com.sphereon.core.defaults.context
                        .DefaultTenantInputString("test-tenant"),
                    com.sphereon.core.defaults.context
                        .DefaultPrincipalInputString("test-principal"),
                )

            val contextId = userContext.contextId

            // Destroy the context
            userContext.destroy()

            // Context should no longer exist
            kotlin.test.assertFalse(app.userContextManager.hasById(contextId))

            app.destroy()
        }

    @Test
    fun userContextInstanceMakeActive() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            // Create two contexts
            val context1 =
                app.userContextManager.createOrGetFromInputs(
                    com.sphereon.core.defaults.context
                        .DefaultTenantInputString("tenant-1"),
                    com.sphereon.core.defaults.context
                        .DefaultPrincipalInputString("principal-1"),
                    makeActive = true,
                )
            val context2 =
                app.userContextManager.createOrGetFromInputs(
                    com.sphereon.core.defaults.context
                        .DefaultTenantInputString("tenant-2"),
                    com.sphereon.core.defaults.context
                        .DefaultPrincipalInputString("principal-2"),
                    makeActive = false,
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

    // ========== CoreApiContextExtensionGraph tests ==========

    @Test
    fun coreApiContextExtensionGraphLoggerWithTag() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val coreApiContext = userContext.asCoreApiContextGraph()

            // Test loggerWithTag default method
            val logger = coreApiContext.loggerWithTag("test-tag")
            assertNotNull(logger)

            app.destroy()
        }

    @Test
    fun coreApiContextExtensionGraphLoggerWithDefaultTag() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val coreApiContext = userContext.asCoreApiContextGraph()

            // Test loggerWithTag with default parameter
            val logger = coreApiContext.loggerWithTag()
            assertNotNull(logger)

            app.destroy()
        }

    @Test
    fun coreApiContextExtensionGraphAsContribution() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val coreApiContext = userContext.asCoreApiContextGraph()

            // Verify contribution is the same object as the graph
            assertNotNull(coreApiContext)
            assertTrue(coreApiContext === userContext.graph)

            app.destroy()
        }

    @Test
    fun coreApiContextExtensionComponentCreateExecutionContextGraph() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val coreApiContext = userContext.asCoreApiContextGraph()

            // Test createExecutionContextGraph default method
            val sessionInstance = coreApiContext.createExecutionContextGraph(userContext.context, "test-session-id")
            assertNotNull(sessionInstance)
            assertTrue(sessionInstance.sessionId.contains("test-session-id"))

            app.destroy()
        }

    @Test
    fun coreApiContextExtensionGraphExposesSessionContextManager() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val coreApiContext = userContext.asCoreApiContextGraph()

            assertNotNull(coreApiContext.sessionContextManager)

            app.destroy()
        }

    @Test
    fun coreApiContextExtensionGraphExposesLogManager() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val coreApiContext = userContext.asCoreApiContextGraph()

            assertNotNull(coreApiContext.logManager)

            app.destroy()
        }

    @Test
    fun coreApiContextExtensionGraphExposesConf() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val coreApiContext = userContext.asCoreApiContextGraph()

            assertNotNull(coreApiContext.conf)

            app.destroy()
        }

    @Test
    fun coreApiContextExtensionGraphExposesCommandExecutor() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val coreApiContext = userContext.asCoreApiContextGraph()

            assertNotNull(coreApiContext.commandExecutor)

            app.destroy()
        }

    // ========== CoreApiSessionExtensionGraph tests ==========

    @Test
    fun coreApiSessionExtensionGraphAsContribution() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("sure-graph-session")

            val coreApiSession = session.asCoreApiServiceGraph()

            // Verify contribution is the same object as the graph
            assertNotNull(coreApiSession)
            assertTrue(coreApiSession === session.graph)

            app.destroy()
        }

    @Test
    fun sessionGraphAsCoreApiServiceGraph() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("graph-cast-session")

            // Test SessionGraph.asCoreApiServiceGraph() extension function
            val sessionGraph: SessionGraph = session.graph
            val coreApiSession = sessionGraph.asCoreApiServiceGraph()

            assertNotNull(coreApiSession)
            assertNotNull(coreApiSession.serviceExecution)
            assertNotNull(coreApiSession.sessionContext)
            assertNotNull(coreApiSession.logManager)

            app.destroy()
        }

    @Test
    fun coreApiSessionExtensionGraphLoggerDefaultMethod() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("logger-default-session")

            val coreApiSession = session.asCoreApiServiceGraph()

            // Test logger() which delegates to loggerWithTag()
            val logger = coreApiSession.logger()
            assertNotNull(logger)

            app.destroy()
        }

    @Test
    fun coreApiSessionExtensionGraphLoggerWithTagDefaultParameter() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("logger-tag-default-session")

            val coreApiSession = session.asCoreApiServiceGraph()

            // Test loggerWithTag() with default parameter (uses sessionContext.sessionId)
            val logger = coreApiSession.loggerWithTag()
            assertNotNull(logger)

            app.destroy()
        }

    @Test
    fun coreApiSessionExtensionGraphServiceExecutionIsSessionExecution() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("service-execution-session")

            val coreApiSession = session.asCoreApiServiceGraph()
            val serviceExecution = coreApiSession.serviceExecution

            // Verify serviceExecution is SessionExecution
            assertNotNull(serviceExecution)
            kotlin.test.assertFalse(serviceExecution.isAnonymous())

            app.destroy()
        }

    @Test
    fun coreApiSessionExtensionGraphSessionContextMatchesSessionId() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("context-match-session")

            val coreApiSession = session.asCoreApiServiceGraph()
            val sessionContext = coreApiSession.sessionContext

            assertTrue(sessionContext.sessionId.contains("context-match-session"))

            app.destroy()
        }

    // ========== SessionInstance.getService<T>() reified extension tests ==========

    @Test
    fun sessionInstanceGetServiceReifiedExtensionReturnsService() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

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
    fun sessionInstanceGetServiceReifiedExtensionUsesClassName() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

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

    // ========== SessionGraph.sessionScopedInstances tests ==========

    @Test
    fun sessionGraphSessionScopedInstancesIsAccessible() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("scoped-instances-session")

            // Access sessionScopedInstances — may be empty when no Scoped instances are contributed
            val scopedInstances: Set<Scoped> = session.graph.sessionScopedInstances
            assertNotNull(scopedInstances)

            app.destroy()
        }

    @Test
    fun sessionGraphProvideSessionScopeCoroutineScopeScopedIsAccessible() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userContext = app.userContextManager.getAnonymous()
            val session = userContext.sessionContextManager.createOrGetFromId("coroutine-scope-session")

            // Access the sessionScopeCoroutineScopeScoped which triggers provideSessionScopeCoroutineScopeScoped
            val coroutineScopeScoped = session.graph.sessionScopeCoroutineScopeScoped
            assertNotNull(coroutineScopeScoped)

            // Also create a child scope from it which simulates what provideSessionCoroutineScope does
            val childScope = coroutineScopeScoped.createChild()
            assertNotNull(childScope)

            app.destroy()
        }

    // ========== UserContext injection into SessionScope tests ==========

    @Test
    fun sessionContextInheritsUserContextFromParentScope() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userInstance = app.userContextManager.getAnonymous()
            val session = userInstance.sessionContextManager.createOrGetFromId("ctx-injection-test")

            // SessionContext should have the UserContext from the parent UserScope
            val sessionContext = session.graph.sessionContext
            assertNotNull(sessionContext.context)
            assertEquals(userInstance.context.tenant.tenantId, sessionContext.context.tenant.tenantId)

            app.destroy()
        }

    @Test
    fun sessionContextUserContextMatchesUserGraphUserContext() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userInstance = app.userContextManager.getAnonymous()
            val session = userInstance.sessionContextManager.createOrGetFromId("ctx-match-test")

            // The UserContext in SessionScope should be the same instance provided to UserScope
            val userContextFromGraph = userInstance.graph.userContext
            val userContextFromSession = session.graph.sessionContext.context
            assertSame(userContextFromGraph, userContextFromSession)

            app.destroy()
        }

    @Test
    fun sessionContextReceivesCorrectSessionId() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            val userInstance = app.userContextManager.getAnonymous()
            val session = userInstance.sessionContextManager.createOrGetFromId("my-session-id")

            // The sessionId should be the one passed to the factory
            assertEquals("my-session-id", session.graph.sessionContext.sessionId)
            assertEquals("my-session-id", session.graph.sessionId)

            app.destroy()
        }

    @Test
    fun nonAnonymousUserContextFlowsIntoSession() =
        runTest {
            val testScope = TestScope()
            val app = createCoreApiTestAppGraph(testScope)

            // Create a real (non-anonymous) user context with a specific tenant
            val tenantData =
                object : com.sphereon.di.context.TenantContextData {
                    override val tenantId = "test-tenant-123"
                }
            val userInstance =
                app.userContextManager.createOrGetFromData(
                    tenantData = tenantData,
                    principalValue = "test-principal-456",
                )
            val session = userInstance.sessionContextManager.createOrGetFromId("tenant-session")

            // Verify the tenant flows through to the session
            val sessionContext = session.graph.sessionContext
            assertEquals("test-tenant-123", sessionContext.context.tenant.tenantId)
            assertEquals("test-principal-456", sessionContext.context.principal)

            app.destroy()
        }
}

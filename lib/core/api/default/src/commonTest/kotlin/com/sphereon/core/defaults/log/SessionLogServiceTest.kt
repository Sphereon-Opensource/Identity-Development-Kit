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

package com.sphereon.core.defaults.log

import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.defaults.SessionExecutionImpl
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionLogServiceTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "session-log-service-test", "test-profile", "0.0.1-TEST"
    )

    // ========== SessionLogServiceImpl Tests ==========

    @Test
    fun sessionLogServiceHasCorrectId() {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContext.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = (sessionInstance.component as SessionExecutionImpl.Component).sessionExecution
            val logService = sessionExecution.log

            assertEquals(SessionLogServiceImpl.SERVICE_ID, logService.id)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionLogServiceHasSessionContext() {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContext.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = (sessionInstance.component as SessionExecutionImpl.Component).sessionExecution
            val logService = sessionExecution.log

            assertNotNull(logService.sessionContext)
            assertEquals("test-session", logService.sessionContext.sessionId)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionLogServiceHasLogManager() {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContext.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = (sessionInstance.component as SessionExecutionImpl.Component).sessionExecution
            val logService = sessionExecution.log

            assertNotNull(logService.logManager)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionLogServiceExecuteAsyncReturnsOk() {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContext.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = (sessionInstance.component as SessionExecutionImpl.Component).sessionExecution
            val logService = sessionExecution.log

            val logMessage = LogMessage(
                level = LogLevel.INFO,
                message = "Test log message"
            )

            val result = logService.executeAsync(logMessage)
            assertTrue(result.isOk)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionLogServiceExecuteAsyncWithDifferentLevels() {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContext.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = (sessionInstance.component as SessionExecutionImpl.Component).sessionExecution
            val logService = sessionExecution.log

            // Test all log levels
            for (level in listOf(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR)) {
                val logMessage = LogMessage(
                    level = level,
                    message = "Test message at $level"
                )
                val result = logService.executeAsync(logMessage)
                assertTrue(result.isOk, "executeAsync should succeed for level $level")
            }
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionLogServiceToAsyncReturnsAsyncService() {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContext.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = (sessionInstance.component as SessionExecutionImpl.Component).sessionExecution
            val logService = sessionExecution.log

            val asyncService = logService.toAsync()
            assertNotNull(asyncService)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionLogServiceSetConfigReturnsService() = runTest {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContext.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = (sessionInstance.component as SessionExecutionImpl.Component).sessionExecution
            val logService = sessionExecution.log

            // setConfig should return the same service for fluent API
            val config = LoggerConfig(minLevel = LogLevel.DEBUG, tag = "TestTag")

            val result = logService.setConfig(config)
            assertNotNull(result)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionLogServiceIsEnabledProperty() {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContext.sessionContextManager.createOrGetFromId("test-session")
            val sessionExecution = (sessionInstance.component as SessionExecutionImpl.Component).sessionExecution
            val logService = sessionExecution.log

            // isEnabled reflects the delegate's state
            val isEnabled = logService.isEnabled
            // Just verify it's a boolean and doesn't throw
            assertNotNull(isEnabled.toString())
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Component Interface Test ==========

    @Test
    fun sessionExecutionHasLogService() {
        val appComponent = createAppComponent()
        try {
            val userContext = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionInstance = userContext.sessionContextManager.createOrGetFromId("test-session")

            // Verify we can access log service via session execution
            val sessionExecution = (sessionInstance.component as SessionExecutionImpl.Component).sessionExecution
            assertNotNull(sessionExecution.log)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Service ID Constant Test ==========

    @Test
    fun sessionLogServiceIdConstant() {
        assertEquals("ServiceLogService", SessionLogServiceImpl.SERVICE_ID)
    }
}

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

import com.sphereon.core.api.testutil.appLogger
import com.sphereon.core.api.testutil.createCoreApiTestAppComponent
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.asCoreApiContextComponent
import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoggingTest {

    private fun createAppComponent() = createCoreApiTestAppComponent(
        this, "logging-test", "test-profile", "0.0.1-TEST"
    )

    // ========== NoLogger Tests ==========

    @Test
    fun appNoLogServiceIsNotEnabled() {
        val appComponent = createAppComponent()
        try {
            val noLogService = AppNoLogService()
            assertFalse(noLogService.isEnabled, "AppNoLogService should not be enabled")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appNoLogServiceHasAppScope() {
        val appComponent = createAppComponent()
        try {
            val noLogService = AppNoLogService()
            assertEquals(IdkScope.APP, noLogService.scope, "AppNoLogService should have APP scope")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextNoLogServiceIsNotEnabled() {
        val appComponent = createAppComponent()
        try {
            val noLogService = UserContextNoLogService()
            assertFalse(noLogService.isEnabled, "UserContextNoLogService should not be enabled")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun userContextNoLogServiceHasUserScope() {
        val appComponent = createAppComponent()
        try {
            val noLogService = UserContextNoLogService()
            assertEquals(IdkScope.USER, noLogService.scope, "UserContextNoLogService should have USER scope")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionNoLogServiceIsNotEnabled() {
        val appComponent = createAppComponent()
        try {
            val noLogService = SessionNoLogService()
            assertFalse(noLogService.isEnabled, "SessionNoLogService should not be enabled")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionNoLogServiceHasSessionScope() {
        val appComponent = createAppComponent()
        try {
            val noLogService = SessionNoLogService()
            assertEquals(IdkScope.SESSION, noLogService.scope, "SessionNoLogService should have SESSION scope")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun noLogServiceHasCorrectServiceId() {
        val appComponent = createAppComponent()
        try {
            assertEquals("NoLogger", AbstractNoLogService.SERVICE_ID)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== App Logger Integration Tests ==========

    @Test
    fun appLoggerCanBeObtained() = runTest {
        val appComponent = createAppComponent()
        try {
            val appLogger = appComponent.appLogger()
            assertNotNull(appLogger, "App logger should not be null")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun appLoggerCanLogMessage() = runTest {
        val appComponent = createAppComponent()
        try {
            val appLogger = appComponent.appLogger()
            // This should not throw
            appLogger.info("Test log message from app logger")
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Session Logger Integration Tests ==========

    @Test
    fun sessionLoggerCanBeObtained() = runTest {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionComponent = contextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionLogger = sessionComponent.asCoreApiServiceComponent().logger()
            assertNotNull(sessionLogger, "Session logger should not be null")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionLoggerCanLogMessage() = runTest {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionComponent = contextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionLogger = sessionComponent.asCoreApiServiceComponent().logger()
            // This should not throw
            sessionLogger.info("Test log message from session logger")
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionLoggerCanLogWithDifferentLevels() = runTest {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionComponent = contextInstance.sessionContextManager.createOrGetFromId("test-session")
            val sessionLogger = sessionComponent.asCoreApiServiceComponent().logger()

            // Test all log levels - these should not throw
            sessionLogger.debug("Debug message")
            sessionLogger.info("Info message")
            sessionLogger.warn("Warn message")
            sessionLogger.error("Error message")
        } finally {
            appComponent.destroy()
        }
    }

    // ========== Log Manager Tests ==========

    @Test
    fun appLogManagerCanBeObtained() {
        val appComponent = createAppComponent()
        try {
            // App log manager is accessed via appLogger
            val appLogger = appComponent.appLogger()
            assertNotNull(appLogger)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun sessionLogManagerIsCreatedForSession() = runTest {
        val appComponent = createAppComponent()
        try {
            val contextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-user")
            )
            val sessionComponent = contextInstance.sessionContextManager.createOrGetFromId("test-session")

            // Session log manager is accessed via the component
            assertNotNull(sessionComponent.component.logManager)
        } finally {
            appComponent.destroy()
        }
    }

    // ========== LogMessage Tests ==========

    @Test
    fun logMessageCanBeCreated() {
        val appComponent = createAppComponent()
        try {
            val logMessage = LogMessage(
                level = LogLevel.INFO,
                message = "Test message"
            )
            assertEquals(LogLevel.INFO, logMessage.level)
            assertEquals("Test message", logMessage.message)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun logMessageWithTagCanBeCreated() {
        val appComponent = createAppComponent()
        try {
            val logMessage = LogMessage(
                level = LogLevel.WARN,
                message = "Tagged message",
                tag = "TestTag"
            )
            assertEquals(LogLevel.WARN, logMessage.level)
            assertEquals("Tagged message", logMessage.message)
            assertEquals("TestTag", logMessage.tag)
        } finally {
            appComponent.destroy()
        }
    }

    @Test
    fun logLevelEnumValuesExist() {
        val appComponent = createAppComponent()
        try {
            // Verify all log levels exist
            assertNotNull(LogLevel.DEBUG)
            assertNotNull(LogLevel.INFO)
            assertNotNull(LogLevel.WARN)
            assertNotNull(LogLevel.ERROR)
        } finally {
            appComponent.destroy()
        }
    }
}

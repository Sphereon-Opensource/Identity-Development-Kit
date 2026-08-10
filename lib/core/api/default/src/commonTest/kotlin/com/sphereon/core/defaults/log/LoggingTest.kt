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

package com.sphereon.core.defaults.log

import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.asCoreApiContextGraph
import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.api.testutil.appLogger
import com.sphereon.core.api.testutil.createCoreApiTestAppGraph
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoggingTest {
    private fun createAppGraph() =
        createCoreApiTestAppGraph(
            this,
            "logging-test",
            "test-profile",
            "0.0.1-TEST",
        )

    // ========== NoLogger Tests ==========

    @Test
    fun appNoLogServiceIsNotEnabled() {
        val appGraph = createAppGraph()
        try {
            val noLogService = AppNoLogService()
            assertFalse(noLogService.isEnabled, "AppNoLogService should not be enabled")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun appNoLogServiceHasAppScope() {
        val appGraph = createAppGraph()
        try {
            val noLogService = AppNoLogService()
            assertEquals(IdkScope.APP, noLogService.scope, "AppNoLogService should have APP scope")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextNoLogServiceIsNotEnabled() {
        val appGraph = createAppGraph()
        try {
            val noLogService = UserContextNoLogService()
            assertFalse(noLogService.isEnabled, "UserContextNoLogService should not be enabled")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun userContextNoLogServiceHasUserScope() {
        val appGraph = createAppGraph()
        try {
            val noLogService = UserContextNoLogService()
            assertEquals(IdkScope.USER, noLogService.scope, "UserContextNoLogService should have USER scope")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionNoLogServiceIsNotEnabled() {
        val appGraph = createAppGraph()
        try {
            val noLogService = SessionNoLogService()
            assertFalse(noLogService.isEnabled, "SessionNoLogService should not be enabled")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionNoLogServiceHasSessionScope() {
        val appGraph = createAppGraph()
        try {
            val noLogService = SessionNoLogService()
            assertEquals(IdkScope.SESSION, noLogService.scope, "SessionNoLogService should have SESSION scope")
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun noLogServiceHasCorrectServiceId() {
        val appGraph = createAppGraph()
        try {
            assertEquals("NoLogger", AbstractNoLogService.SERVICE_ID)
        } finally {
            appGraph.destroy()
        }
    }

    // ========== App Logger Integration Tests ==========

    @Test
    fun appLoggerCanBeObtained() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val appLogger = appGraph.appLogger()
                assertNotNull(appLogger, "App logger should not be null")
            } finally {
                appGraph.destroy()
            }
        }

    @Test
    fun appLoggerCanLogMessage() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val appLogger = appGraph.appLogger()
                // This should not throw
                appLogger.info("Test log message from app logger")
            } finally {
                appGraph.destroy()
            }
        }

    // ========== Session Logger Integration Tests ==========

    @Test
    fun sessionLoggerCanBeObtained() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val contextInstance =
                    appGraph.userContextManager.createOrGetFromInputs(
                        DefaultTenantInputString("test-tenant"),
                        DefaultPrincipalInputString("test-user"),
                    )
                val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId("test-session", principalType = com.sphereon.di.context.PrincipalType.USER)
                val sessionLogger = sessionGraph.asCoreApiServiceGraph().logger()
                assertNotNull(sessionLogger, "Session logger should not be null")
            } finally {
                appGraph.destroy()
            }
        }

    @Test
    fun sessionLoggerCanLogMessage() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val contextInstance =
                    appGraph.userContextManager.createOrGetFromInputs(
                        DefaultTenantInputString("test-tenant"),
                        DefaultPrincipalInputString("test-user"),
                    )
                val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId("test-session", principalType = com.sphereon.di.context.PrincipalType.USER)
                val sessionLogger = sessionGraph.asCoreApiServiceGraph().logger()
                // This should not throw
                sessionLogger.info("Test log message from session logger")
            } finally {
                appGraph.destroy()
            }
        }

    @Test
    fun sessionLoggerCanLogWithDifferentLevels() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val contextInstance =
                    appGraph.userContextManager.createOrGetFromInputs(
                        DefaultTenantInputString("test-tenant"),
                        DefaultPrincipalInputString("test-user"),
                    )
                val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId("test-session", principalType = com.sphereon.di.context.PrincipalType.USER)
                val sessionLogger = sessionGraph.asCoreApiServiceGraph().logger()

                // Test all log levels - these should not throw
                sessionLogger.debug("Debug message")
                sessionLogger.info("Info message")
                sessionLogger.warn("Warn message")
                sessionLogger.error("Error message")
            } finally {
                appGraph.destroy()
            }
        }

    // ========== Log Manager Tests ==========

    @Test
    fun appLogManagerCanBeObtained() {
        val appGraph = createAppGraph()
        try {
            // App log manager is accessed via appLogger
            val appLogger = appGraph.appLogger()
            assertNotNull(appLogger)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun sessionLogManagerIsCreatedForSession() =
        runTest {
            val appGraph = createAppGraph()
            try {
                val contextInstance =
                    appGraph.userContextManager.createOrGetFromInputs(
                        DefaultTenantInputString("test-tenant"),
                        DefaultPrincipalInputString("test-user"),
                    )
                val sessionGraph = contextInstance.sessionContextManager.createOrGetFromId("test-session", principalType = com.sphereon.di.context.PrincipalType.USER)

                // Session log manager is accessed via the graph
                assertNotNull(sessionGraph.graph.logManager)
            } finally {
                appGraph.destroy()
            }
        }

    // ========== LogMessage Tests ==========

    @Test
    fun logMessageCanBeCreated() {
        val appGraph = createAppGraph()
        try {
            val logMessage =
                LogMessage(
                    level = LogLevel.INFO,
                    message = "Test message",
                )
            assertEquals(LogLevel.INFO, logMessage.level)
            assertEquals("Test message", logMessage.message)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun logMessageWithTagCanBeCreated() {
        val appGraph = createAppGraph()
        try {
            val logMessage =
                LogMessage(
                    level = LogLevel.WARN,
                    message = "Tagged message",
                    tag = "TestTag",
                )
            assertEquals(LogLevel.WARN, logMessage.level)
            assertEquals("Tagged message", logMessage.message)
            assertEquals("TestTag", logMessage.tag)
        } finally {
            appGraph.destroy()
        }
    }

    @Test
    fun logLevelEnumValuesExist() {
        val appGraph = createAppGraph()
        try {
            // Verify all log levels exist
            assertNotNull(LogLevel.DEBUG)
            assertNotNull(LogLevel.INFO)
            assertNotNull(LogLevel.WARN)
            assertNotNull(LogLevel.ERROR)
        } finally {
            appGraph.destroy()
        }
    }
}

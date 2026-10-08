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

package com.sphereon.core.log.mobile

import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LogMessage
import com.sphereon.di.context.NoOpSessionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class MobileLogServiceTest {
    @Test
    fun testMobileLogRepository() =
        runTest {
            val repository = MobileLogRepositoryImpl()

            // Test initial state
            assertEquals(0, repository.getLogCount())
            assertTrue(repository.getAllLogs().isEmpty())

            // Add a log entry
            val logEntry =
                MobileLogEntry(
                    timestamp = Clock.System.now(),
                    level = LogLevel.INFO,
                    message = "Test message",
                    tag = "TEST",
                    throwable = null,
                    sessionContextId = "test-context",
                    errorResult = null,
                )

            repository.addLog(logEntry)

            // Verify log was added
            assertEquals(1, repository.getLogCount())
            val logs = repository.getAllLogs()
            assertEquals(1, logs.size)
            assertEquals("Test message", logs[0].message)
            assertEquals(LogLevel.INFO, logs[0].level)
        }

    @Test
    fun testMobileLogEntryFromLogMessage() =
        runTest {
            val logMessage =
                LogMessage(
                    level = LogLevel.ERROR,
                    message = "Error occurred",
                    tag = "ERROR_TAG",
                    exception = RuntimeException("Test exception"),
                    errorResult = null,
                )

            val sessionContextId = NoOpSessionContext.toString()
            val entry = MobileLogEntry.fromLogMessage(logMessage, sessionContextId)

            assertEquals(LogLevel.ERROR, entry.level)
            assertEquals("Error occurred", entry.message)
            assertEquals("ERROR_TAG", entry.tag)
            assertTrue(entry.throwable?.contains("RuntimeException: Test exception") == true)
        }

    @Test
    fun testMobileLogManagerInterface() =
        runTest {
            val repository = MobileLogRepositoryImpl()
            val logManager: MobileLogManager = MobileLogManagerImpl(repository) // Use interface

            // Add some test logs
            val entries =
                listOf(
                    MobileLogEntry(
                        timestamp = Clock.System.now(),
                        level = LogLevel.DEBUG,
                        message = "Debug message",
                        tag = "DEBUG",
                        throwable = null,
                        sessionContextId = "ctx1",
                        errorResult = null,
                    ),
                    MobileLogEntry(
                        timestamp = Clock.System.now(),
                        level = LogLevel.ERROR,
                        message = "Error message",
                        tag = "ERROR",
                        throwable = "Stack trace here",
                        sessionContextId = "ctx2",
                        errorResult = "Error details",
                    ),
                )

            entries.forEach { repository.addLog(it) }

            // Test basic access
            val allLogs = logManager.getAllLogs()
            assertEquals(2, allLogs.size)

            // Test filtering by level
            val errorLogs = logManager.getErrorLogs()
            assertEquals(1, errorLogs.size)
            assertEquals(LogLevel.ERROR, errorLogs[0].level)

            // Test statistics
            val stats = logManager.getLogStatistics()
            assertEquals(2, stats.totalLogs)
            assertEquals(1, stats.logsByLevel[LogLevel.DEBUG])
            assertEquals(1, stats.logsByLevel[LogLevel.ERROR])

            // Test search
            val searchResults = logManager.searchLogs("Error")
            assertEquals(1, searchResults.size)

            // Test clear
            logManager.clearLogs()
            assertEquals(0, logManager.getLogCount())
        }

    @Test
    fun testLogExport() =
        runTest {
            val repository = MobileLogRepositoryImpl()
            val logManager: MobileLogManager = MobileLogManagerImpl(repository) // Use interface

            // Add a test log
            repository.addLog(
                MobileLogEntry(
                    timestamp = Clock.System.now(),
                    level = LogLevel.INFO,
                    message = "Export test message",
                    tag = "EXPORT",
                    throwable = null,
                    sessionContextId = "export-ctx",
                    errorResult = null,
                ),
            )

            // Test text export
            val textExport = logManager.exportLogsAsText()
            assertTrue(textExport.contains("Export test message"))
            assertTrue(textExport.contains("INFO"))

            // Test JSON export
            val jsonExport =
                logManager.exportLogs(
                    options = MobileLogExportOptions(format = ExportFormat.JSON),
                )
            assertTrue(jsonExport.contains("exportFormat"))
            assertTrue(jsonExport.contains("Export test message"))

            // Test CSV export
            val csvExport =
                logManager.exportLogs(
                    options = MobileLogExportOptions(format = ExportFormat.CSV),
                )
            assertTrue(csvExport.contains("Timestamp,Level,Message"))
            assertTrue(csvExport.contains("Export test message"))
        }

    @Test
    fun testCircularBuffer() =
        runTest {
            val repository = MobileLogRepositoryImpl()

            // Set small buffer size
            repository.setMaxLogSize(3)

            // Add more logs than buffer size
            for (i in 1..5) {
                repository.addLog(
                    MobileLogEntry(
                        timestamp = Clock.System.now(),
                        level = LogLevel.INFO,
                        message = "Message $i",
                        tag = "TEST",
                        throwable = null,
                        sessionContextId = "ctx$i",
                        errorResult = null,
                    ),
                )
            }

            // Should only have 3 logs (the most recent ones)
            val logs = repository.getAllLogs()
            assertEquals(3, logs.size)

            // Should have messages 3, 4, 5 (oldest ones removed)
            assertTrue(logs.any { it.message == "Message 3" })
            assertTrue(logs.any { it.message == "Message 4" })
            assertTrue(logs.any { it.message == "Message 5" })
        }

    @Test
    fun testInterfaceBinding() =
        runTest {
            // Test that the interface can be used properly
            val repository = MobileLogRepositoryImpl()
            val concreteManager = MobileLogManagerImpl(repository)
            val interfaceManager: MobileLogManager = concreteManager

            // Add test data through concrete implementation
            repository.addLog(
                MobileLogEntry(
                    timestamp = Clock.System.now(),
                    level = LogLevel.INFO,
                    message = "Interface test",
                    tag = "TEST",
                    throwable = null,
                    sessionContextId = "test-ctx",
                    errorResult = null,
                ),
            )

            // Access through interface should work
            val logs = interfaceManager.getAllLogs()
            assertEquals(1, logs.size)
            assertEquals("Interface test", logs[0].message)

            // All interface methods should be accessible
            val count = interfaceManager.getLogCount()
            assertEquals(1, count)

            val stats = interfaceManager.getLogStatistics()
            assertEquals(1, stats.totalLogs)
        }
}

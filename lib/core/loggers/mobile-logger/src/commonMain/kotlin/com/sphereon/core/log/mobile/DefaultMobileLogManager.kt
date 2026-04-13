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

@file:OptIn(ExperimentalJsExport::class)

package com.sphereon.core.log.mobile

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import com.sphereon.core.api.log.LogLevel
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Configuration for mobile log manager
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileLogConfig", exact = true)
@JsExport
data class MobileLogConfig(
    val maxLogSize: Int = 1000,
    val enableSystemLogging: Boolean = true,
    val enableConsoleLogging: Boolean = true,
    val defaultTag: String = "SureIDK"
)

/**
 * Filter criteria for mobile logs
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileLogFilter", exact = true)
@JsExport
data class MobileLogFilter(
    val level: LogLevel? = null,
    val tag: String? = null,
    val fromTime: Instant? = null,
    val toTime: Instant? = null,
    val searchText: String? = null
)

/**
 * Statistics about mobile logs
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileLogStats", exact = true)
@JsExport
data class MobileLogStats(
    val totalLogs: Int,
    val logsByLevel: Map<LogLevel, Int>,
    val oldestLogTime: Instant?,
    val newestLogTime: Instant?
)

/**
 * Export options for mobile logs
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileLogExportOptions", exact = true)
@JsExport
data class MobileLogExportOptions(
    val format: ExportFormat = ExportFormat.TEXT,
    val includeTimestamps: Boolean = true,
    val includeLevel: Boolean = true,
    val includeTag: Boolean = true,
    val includeContext: Boolean = true,
    val includeStackTrace: Boolean = true,
    val includeMetadata: Boolean = true
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ExportFormat", exact = true)
@JsExport
enum class ExportFormat {
    TEXT, JSON, CSV
}

/**
 * Interface for Mobile Log Manager - Main interface for mobile app developers to access logs
 *
 * This interface provides a simple API for mobile applications to:
 * - Access stored logs
 * - Filter logs by various criteria
 * - Export logs in different formats
 * - Monitor logs in real-time
 * - Configure logging behavior
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileLogManager", exact = true)
interface MobileLogManager {
    /**
     * Gets all stored logs
     */
    suspend fun getAllLogs(): List<MobileLogEntry>

    /**
     * Gets logs filtered by the specified criteria
     */
    suspend fun getFilteredLogs(filter: MobileLogFilter): List<MobileLogEntry>

    /**
     * Gets logs by level
     */
    suspend fun getLogsByLevel(level: LogLevel): List<MobileLogEntry>

    /**
     * Gets logs by tag
     */
    suspend fun getLogsByTag(tag: String): List<MobileLogEntry>

    /**
     * Gets logs from a specific time range
     */
    suspend fun getLogsByTimeRange(from: Instant, to: Instant): List<MobileLogEntry>

    /**
     * Gets statistics about the stored logs
     */
    suspend fun getLogStatistics(): MobileLogStats

    /**
     * Gets the current log count
     */
    suspend fun getLogCount(): Int

    /**
     * Clears all stored logs
     */
    suspend fun clearLogs()

    /**
     * Gets logs as a flow for real-time updates
     * This is useful for implementing live log viewers in mobile apps
     */
    val logsFlow: StateFlow<List<MobileLogEntry>>

    /**
     * Exports logs as text with default formatting
     */
    suspend fun exportLogsAsText(): String

    /**
     * Exports logs with custom formatting options
     */
    suspend fun exportLogs(
        filter: MobileLogFilter = MobileLogFilter(),
        options: MobileLogExportOptions = MobileLogExportOptions()
    ): String

    /**
     * Sets the maximum number of logs to keep in memory
     */
    suspend fun setMaxLogSize(size: Int)

    /**
     * Gets the most recent logs (useful for quick debugging)
     */
    suspend fun getRecentLogs(count: Int = 50): List<MobileLogEntry>

    /**
     * Gets error logs only (ERROR level)
     */
    suspend fun getErrorLogs(): List<MobileLogEntry>

    /**
     * Gets warning and error logs
     */
    suspend fun getWarningAndErrorLogs(): List<MobileLogEntry>

    /**
     * Searches logs by message content
     */
    suspend fun searchLogs(query: String): List<MobileLogEntry>
}

/**
 * Mobile Log Manager - Implementation for mobile app developers to access logs
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<MobileLogManager>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileLogManagerImpl", exact = true)
class MobileLogManagerImpl(
    private val repository: MobileLogRepositoryImpl
) : MobileLogManager {

    override suspend fun getAllLogs(): List<MobileLogEntry> = repository.getAllLogs()

    override suspend fun getFilteredLogs(filter: MobileLogFilter): List<MobileLogEntry> {
        var logs = repository.getAllLogs()

        filter.level?.let { level ->
            logs = logs.filter { it.level == level }
        }

        filter.tag?.let { tag ->
            logs = logs.filter { it.tag == tag }
        }

        filter.fromTime?.let { fromTime ->
            logs = logs.filter { it.timestamp >= fromTime }
        }

        filter.toTime?.let { toTime ->
            logs = logs.filter { it.timestamp <= toTime }
        }

        filter.searchText?.let { searchText ->
            logs = logs.filter { log ->
                log.message.contains(searchText, ignoreCase = true) ||
                        log.tag?.contains(searchText, ignoreCase = true) == true ||
                        log.throwable?.contains(searchText, ignoreCase = true) == true
            }
        }

        return logs
    }

    override suspend fun getLogsByLevel(level: LogLevel): List<MobileLogEntry> =
        repository.getLogsByLevel(level)

    override suspend fun getLogsByTag(tag: String): List<MobileLogEntry> =
        repository.getLogsByTag(tag)

    override suspend fun getLogsByTimeRange(from: Instant, to: Instant): List<MobileLogEntry> =
        repository.getLogsByTimeRange(from, to)

    override suspend fun getLogStatistics(): MobileLogStats {
        val logs = repository.getAllLogs()
        val logsByLevel = logs.groupingBy { it.level }.eachCount()

        return MobileLogStats(
            totalLogs = logs.size,
            logsByLevel = logsByLevel,
            oldestLogTime = logs.minByOrNull { it.timestamp }?.timestamp,
            newestLogTime = logs.maxByOrNull { it.timestamp }?.timestamp
        )
    }

    override suspend fun getLogCount(): Int = repository.getLogCount()

    override suspend fun clearLogs() = repository.clearLogs()

    override val logsFlow: StateFlow<List<MobileLogEntry>> = repository.logsFlow

    override suspend fun exportLogsAsText(): String = repository.exportLogsAsText()

    override suspend fun exportLogs(
        filter: MobileLogFilter,
        options: MobileLogExportOptions
    ): String {
        val logs = getFilteredLogs(filter)

        return when (options.format) {
            ExportFormat.TEXT -> exportAsText(logs, options)
            ExportFormat.JSON -> exportAsJson(logs, options)
            ExportFormat.CSV -> exportAsCsv(logs, options)
        }
    }

    override suspend fun setMaxLogSize(size: Int) = repository.setMaxLogSize(size)

    override suspend fun getRecentLogs(count: Int): List<MobileLogEntry> {
        return repository.getAllLogs().takeLast(count)
    }

    override suspend fun getErrorLogs(): List<MobileLogEntry> =
        getLogsByLevel(LogLevel.ERROR)

    override suspend fun getWarningAndErrorLogs(): List<MobileLogEntry> {
        return repository.getAllLogs().filter {
            it.level == LogLevel.WARN || it.level == LogLevel.ERROR
        }
    }

    override suspend fun searchLogs(query: String): List<MobileLogEntry> {
        return getFilteredLogs(MobileLogFilter(searchText = query))
    }

    // Private export methods

    private fun exportAsText(logs: List<MobileLogEntry>, options: MobileLogExportOptions): String {
        return buildString {
            appendLine("=== Mobile App Logs Export ===")
            appendLine("Export Format: TEXT")
            appendLine("Total entries: ${logs.size}")
            appendLine("=====================================")
            appendLine()

            logs.forEach { log ->
                val parts = mutableListOf<String>()

                if (options.includeTimestamps) parts.add(log.timestamp.toString())
                if (options.includeLevel) parts.add(log.level.toString())
                if (options.includeTag) parts.add("[${log.tag ?: ""}]")

                appendLine("${parts.joinToString(" ")} ${log.message}")

                if (options.includeMetadata && !log.metadata.isNullOrEmpty()) {
                    appendLine("  Metadata: ${log.metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
                }

                if (options.includeContext && log.sessionContextId != null) {
                    appendLine("  Context: ${log.sessionContextId}")
                }

                if (options.includeStackTrace && log.throwable != null) {
                    appendLine("  Exception:")
                    appendLine("    ${log.throwable}")
                }

                if (log.errorResult != null) {
                    appendLine("  Error: ${log.errorResult}")
                }

                appendLine()
            }
        }
    }

    private fun exportAsJson(logs: List<MobileLogEntry>, options: MobileLogExportOptions): String {
        // Simple JSON export - in a real implementation you might want to use a JSON library
        return buildString {
            appendLine("{")
            appendLine("  \"exportFormat\": \"JSON\",")
            appendLine("  \"totalEntries\": ${logs.size},")
            appendLine("  \"logs\": [")

            logs.forEachIndexed { index, log ->
                appendLine("    {")
                if (options.includeTimestamps) appendLine("      \"timestamp\": \"${log.timestamp}\",")
                if (options.includeLevel) appendLine("      \"level\": \"${log.level}\",")
                appendLine("      \"message\": \"${log.message.replace("\"", "\\\"")}\",")
                if (options.includeTag) {
                    appendLine("      \"tag\": \"${log.tag ?: ""}\",")
                }
                if (options.includeMetadata && !log.metadata.isNullOrEmpty()) {
                    appendLine("      \"metadata\": {${log.metadata.entries.joinToString(",") { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" }}},")
                }
                if (options.includeContext) {
                    appendLine("      \"sessionContext\": \"${log.sessionContextId?.replace("\"", "\\\"") ?: ""}\",")
                }
                if (options.includeStackTrace) {
                    appendLine("      \"throwable\": \"${log.throwable?.replace("\"", "\\\"") ?: ""}\",")
                }
                appendLine("      \"errorResult\": \"${log.errorResult?.replace("\"", "\\\"") ?: ""}\",")
                appendLine("      \"_end\": true")
                append("    }")
                if (index < logs.size - 1) appendLine(",")
                else appendLine()
            }

            appendLine("  ]")
            appendLine("}")
        }
    }

    private fun exportAsCsv(logs: List<MobileLogEntry>, options: MobileLogExportOptions): String {
        return buildString {
            // CSV Header
            val headers = mutableListOf<String>()
            if (options.includeTimestamps) headers.add("Timestamp")
            if (options.includeLevel) headers.add("Level")
            headers.add("Message")
            if (options.includeTag) headers.add("Tag")
            if (options.includeMetadata) headers.add("Metadata")
            if (options.includeContext) headers.add("Context")
            if (options.includeStackTrace) headers.add("Exception")
            headers.add("Error")

            appendLine(headers.joinToString(","))

            // CSV Data
            logs.forEach { log ->
                val values = mutableListOf<String>()
                if (options.includeTimestamps) values.add("\"${log.timestamp}\"")
                if (options.includeLevel) values.add("\"${log.level}\"")
                values.add("\"${log.message.replace("\"", "\"\"")}\"")
                if (options.includeTag) values.add("\"${log.tag ?: ""}\"")
                if (options.includeMetadata) values.add("\"${log.metadata?.entries?.joinToString("; ") { "${it.key}=${it.value}" } ?: ""}\"")
                if (options.includeContext) values.add("\"${log.sessionContextId ?: ""}\"")
                if (options.includeStackTrace) values.add("\"${log.throwable?.replace("\"", "\"\"") ?: ""}\"")
                values.add("\"${log.errorResult?.replace("\"", "\"\"") ?: ""}\"")

                appendLine(values.joinToString(","))
            }
        }
    }
}
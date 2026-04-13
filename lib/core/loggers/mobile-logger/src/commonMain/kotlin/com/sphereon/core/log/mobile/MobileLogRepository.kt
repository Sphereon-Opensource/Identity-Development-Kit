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

package com.sphereon.core.log.mobile

import com.sphereon.core.api.log.LogLevel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Interface for accessing mobile logs programmatically
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileLogRepository", exact = true)
interface MobileLogRepository {
    /**
     * Gets all stored logs
     */
    suspend fun getAllLogs(): List<MobileLogEntry>

    /**
     * Gets logs filtered by level
     */
    suspend fun getLogsByLevel(level: LogLevel): List<MobileLogEntry>

    /**
     * Gets logs filtered by tag
     */
    suspend fun getLogsByTag(tag: String): List<MobileLogEntry>

    /**
     * Gets logs from a specific time range
     */
    suspend fun getLogsByTimeRange(from: Instant, to: Instant): List<MobileLogEntry>

    /**
     * Clears all stored logs
     */
    suspend fun clearLogs()

    /**
     * Gets the current log count
     */
    suspend fun getLogCount(): Int

    /**
     * Gets logs as a flow for real-time updates
     */
    val logsFlow: StateFlow<List<MobileLogEntry>>

    /**
     * Exports logs as formatted text
     */
    suspend fun exportLogsAsText(): String

    /**
     * Sets the maximum number of logs to keep in memory
     */
    suspend fun setMaxLogSize(size: Int)
    suspend fun addLog(logEntry: MobileLogEntry)
}
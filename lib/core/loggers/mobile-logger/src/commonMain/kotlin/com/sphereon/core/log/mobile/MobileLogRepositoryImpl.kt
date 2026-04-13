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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.StateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Mobile log repository implementation using in-memory circular buffer
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<MobileLogRepository>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileLogRepositoryImpl", exact = true)
class MobileLogRepositoryImpl : MobileLogRepository {
    private val buffer = CircularLogBuffer()

    override suspend fun getAllLogs(): List<MobileLogEntry> = buffer.getAll()

    override suspend fun getLogsByLevel(level: LogLevel): List<MobileLogEntry> = buffer.getAll().filter { it.level == level }

    override suspend fun getLogsByTag(tag: String): List<MobileLogEntry> = buffer.getAll().filter { it.tag == tag }

    override suspend fun getLogsByTimeRange(
        from: Instant,
        to: Instant,
    ): List<MobileLogEntry> = buffer.getAll().filter { it.timestamp >= from && it.timestamp <= to }

    override suspend fun clearLogs() = buffer.clear()

    override suspend fun getLogCount(): Int = buffer.size()

    override val logsFlow: StateFlow<List<MobileLogEntry>> = buffer.logsFlow

    override suspend fun exportLogsAsText(): String {
        val logs = buffer.getAll()
        return buildString {
            appendLine("=== Mobile App Logs Export ===")
            appendLine("Generated: ${Clock.System.now()}")
            appendLine("Total entries: ${logs.size}")
            appendLine("=====================================")
            appendLine()

            logs.forEach { log ->
                appendLine("[${log.timestamp}] ${log.level}: ${log.message}")
                log.tag?.let { appendLine("  Tag: $it") }
                log.sessionContextId?.let { appendLine("  Context: $it") }
                log.throwable?.let {
                    appendLine("  Exception:")
                    appendLine("    $it")
                }
                log.errorResult?.let { appendLine("  Error: $it") }
                appendLine()
            }
        }
    }

    override suspend fun setMaxLogSize(size: Int) = buffer.setMaxSize(size)

    override suspend fun addLog(entry: MobileLogEntry) = buffer.add(entry)
}

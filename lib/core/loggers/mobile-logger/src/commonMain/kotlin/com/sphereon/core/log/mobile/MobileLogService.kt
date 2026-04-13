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

@file:OptIn(ExperimentalJsExport::class)

package com.sphereon.core.log.mobile

import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LogMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Data class representing a stored log entry with timestamp and session context information
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MobileLogEntry", exact = true)
@JsExport
data class MobileLogEntry(
    val timestamp: Instant,
    val level: LogLevel,
    val message: String,
    val tag: String?,
    val throwable: String?, // Throwable converted to string for serialization
    val sessionContextId: String?,
    val errorResult: String?, // Error result converted to string for serialization
    val metadata: Map<String, String>? = null, // Optional metadata key-value pairs
) {
    companion object {
        fun fromLogMessage(
            logMessage: LogMessage,
            sessionContextId: String?,
            timestamp: Instant = Clock.System.now(),
        ): MobileLogEntry =
            MobileLogEntry(
                timestamp = timestamp,
                level = logMessage.level,
                message = logMessage.message,
                tag = logMessage.tag,
                throwable = logMessage.exception?.stackTraceToString(),
                sessionContextId = sessionContextId,
                errorResult = logMessage.errorResult?.toString(),
                metadata = logMessage.metadata,
            )
    }
}

/**
 * Internal circular buffer implementation for storing logs
 */
internal class CircularLogBuffer(
    private var maxSize: Int = 5000,
) {
    private val logs = mutableListOf<MobileLogEntry>()
    private val mutex = Mutex()
    private val _logsFlow = MutableStateFlow<List<MobileLogEntry>>(emptyList())
    val logsFlow = _logsFlow.asStateFlow()

    suspend fun add(entry: MobileLogEntry) =
        mutex.withLock {
            if (logs.size >= maxSize) {
                // Remove oldest entries to maintain circular buffer behavior
                val removeCount = (logs.size - maxSize + 1)
                repeat(removeCount) { logs.removeFirstOrNull() }
            }
            logs.add(entry)
            _logsFlow.value = logs.toList()
        }

    suspend fun getAll(): List<MobileLogEntry> =
        mutex.withLock {
            logs.toList()
        }

    suspend fun clear() =
        mutex.withLock {
            logs.clear()
            _logsFlow.value = emptyList()
        }

    suspend fun size(): Int =
        mutex.withLock {
            logs.size
        }

    suspend fun setMaxSize(size: Int) =
        mutex.withLock {
            maxSize = size
            while (logs.size > maxSize) {
                logs.removeFirstOrNull()
            }
            _logsFlow.value = logs.toList()
        }
}

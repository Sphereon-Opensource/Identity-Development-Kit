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

import android.util.Log
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserScope
import com.sphereon.di.context.toSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Android-specific mobile log service that also logs to Android's system log (Logcat)
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<LogService>(), replaces = [AppMobileLogService::class])
class AndroidAppMobileLogService(
    repository: MobileLogRepository,
) : AbstractAndroidMobileLogService(NoOpSessionContext, SERVICE_ID, repository) {
    override val scope = IdkScope.APP

    companion object {
        const val SERVICE_ID = "AndroidAppMobileLogger"
    }
}

/**
 * Context-scoped Android mobile log service
 */
@Inject
@SingleIn(UserScope::class)
@ContributesIntoSet(UserScope::class, binding = binding<LogService>(), replaces = [UserContextMobileLogService::class])
class AndroidUserContextMobileLogService(
    userContextInstance: UserContextInstance,
    repository: MobileLogRepository,
) : AbstractAndroidMobileLogService(userContextInstance.toSessionContext(correlationId = IdentityConstants.ANONYMOUS_ID), SERVICE_ID, repository) {
    override val scope = IdkScope.USER

    companion object {
        const val SERVICE_ID = "AndroidUserContextMobileLogService"
    }
}

/**
 * Session-scoped Android mobile log service
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<LogService>(), replaces = [SessionMobileLogService::class])
class AndroidSessionMobileLogService(
    runtimeSessionContext: SessionContext,
    repository: MobileLogRepository,
) : AbstractAndroidMobileLogService(runtimeSessionContext, SERVICE_ID, repository) {
    override val scope = IdkScope.SESSION

    companion object {
        const val SERVICE_ID = "AndroidSessionMobileLogger"
    }
}

/**
 * Abstract Android mobile log service - optimized for performance
 */
abstract class AbstractAndroidMobileLogService(
    runtimeSessionContext: SessionContext,
    id: String,
    private val repository: MobileLogRepository,
) : AbstractMobileSessionLogService(runtimeSessionContext, id) {
    // Pre-computed values for performance optimization
    private val asyncCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val contextPrefix = " [ctx: "
    private val contextSuffix = "]"
    private val chunkPrefix = "["
    private val chunkSeparator = "/"
    private val chunkSuffix = "] "

    // Pre-computed log level to Android priority mapping
    private val logLevelToPriority =
        mapOf(
            LogLevel.TRACE to Log.VERBOSE,
            LogLevel.DEBUG to Log.DEBUG,
            LogLevel.INFO to Log.INFO,
            LogLevel.WARN to Log.WARN,
            LogLevel.ERROR to Log.ERROR,
        )

    override suspend fun doExecute(
        args: LogMessage,
        applyDuring: (LogMessage) -> LogMessage,
    ): IdkResult<Unit, IdkErrorType> {
        val processedMessage = applyDuring(args)
        val config = getConfig() // Get config once here

        // Fast path: check if logging is disabled for this level first
        if (!config.isEnabled(processedMessage.level)) {
            return IdkResult.ok(Unit)
        }

        val runtimeSessionContextId = sessionContext.toString()

        // Create log entry and add to repository
        val logEntry = MobileLogEntry.fromLogMessage(processedMessage, runtimeSessionContextId)
        repository.addLog(logEntry)

        // Fast Android logging with pre-computed values
        logToAndroid(processedMessage, config, runtimeSessionContextId)

        return IdkResult.ok(Unit)
    }

    /**
     * Optimized Android logging with pre-computed values and efficient string handling
     */
    @Suppress("MagicNumber")
    private fun logToAndroid(
        message: LogMessage,
        config: LoggerConfig,
        runtimeSessionContextId: String,
    ) {
        // Fast tag resolution using passed config
        val tag = message.tag ?: config.tag.takeIf { it.isNotEmpty() } ?: DEFAULT_TAG

        // Build message efficiently
        val baseMessage =
            buildString(message.message.length + 100) {
                // Include timestamp if configured
                if (config.includeTimestamp) {
                    append("[")
                    append(message.timestamp)
                    append("] ")
                }
                append(message.message)
                // Include metadata if present
                message.metadata?.takeIf { it.isNotEmpty() }?.let { meta ->
                    append(" {")
                    append(meta.entries.joinToString(", ") { "${it.key}=${it.value}" })
                    append("}")
                }
                // Include context
                if (runtimeSessionContextId.isNotEmpty()) {
                    append(contextPrefix)
                    append(runtimeSessionContextId)
                    append(contextSuffix)
                }
            }

        // Get Android log priority (fast lookup)
        val priority = logLevelToPriority[message.level] ?: Log.INFO

        // Handle message chunking efficiently
        if (baseMessage.length <= MAX_LOG_LENGTH) {
            // Single log call - fast path
            when (message.level) {
                LogLevel.OFF -> return

                // No logging
                else -> Log.println(priority, tag, baseMessage)
            }

            // Log throwable separately if present
            message.exception?.let { throwable ->
                Log.println(priority, tag, Log.getStackTraceString(throwable))
            }
        } else {
            // Chunked logging - less common path
            val chunks = baseMessage.chunked(MAX_LOG_LENGTH)
            val chunkCount = chunks.size

            chunks.forEachIndexed { index, chunk ->
                val chunkMessage =
                    buildString(chunk.length + 20) {
                        append(chunkPrefix)
                        append(index + 1)
                        append(chunkSeparator)
                        append(chunkCount)
                        append(chunkSuffix)
                        append(chunk)
                    }

                Log.println(priority, tag, chunkMessage)
            }

            // Log throwable for last chunk
            if (message.exception != null) {
                Log.println(priority, tag, Log.getStackTraceString(message.exception))
            }
        }
    }

    companion object {
        private const val DEFAULT_TAG = "SureIDK"
        private const val MAX_LOG_LENGTH = 4000 // Android log message limit
    }
}

/**
 * Android-specific utilities for mobile logging
 */
object AndroidMobileLogUtils {
    /**
     * Gets the appropriate Android log priority for a LogLevel
     */
    fun getAndroidLogPriority(level: LogLevel): Int =
        when (level) {
            LogLevel.TRACE -> Log.VERBOSE
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
            LogLevel.OFF -> -1 // No logging
        }

    /**
     * Checks if a log level is loggable for a given tag
     */
    fun isLoggable(
        tag: String,
        level: LogLevel,
    ): Boolean = Log.isLoggable(tag, getAndroidLogPriority(level))
}

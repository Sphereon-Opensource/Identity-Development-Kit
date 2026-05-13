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
import platform.Foundation.NSLog

/**
 * iOS-specific mobile log service that also logs to iOS's system log
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<LogService>(), replaces = [AppMobileLogService::class])
class IOSAppMobileLogService(
    private val repository: MobileLogRepository,
) : AbstractIOSMobileLogService(NoOpSessionContext, SERVICE_ID, repository) {
    override val scope: IdkScope = IdkScope.APP

    companion object {
        const val SERVICE_ID = "IOSAppMobileLogger"
    }
}

/**
 * Context-scoped iOS mobile log service
 */
@Inject
@SingleIn(UserScope::class)
@ContributesIntoSet(UserScope::class, binding = binding<LogService>(), replaces = [UserContextMobileLogService::class])
class IOSUserContextMobileLogService(
    userContextInstance: UserContextInstance,
    repository: MobileLogRepository,
) : AbstractIOSMobileLogService(userContextInstance.toSessionContext(correlationId = IdentityConstants.ANONYMOUS_ID), SERVICE_ID, repository) {
    override val scope: IdkScope = IdkScope.USER

    companion object {
        const val SERVICE_ID = "IOSUserContextMobileLogService"
    }
}

/**
 * Session-scoped iOS mobile log service
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<LogService>(), replaces = [SessionMobileLogService::class])
class IOSSessionMobileLogService(
    runtimeSessionContext: SessionContext,
    repository: MobileLogRepository,
) : AbstractIOSMobileLogService(runtimeSessionContext, SERVICE_ID, repository) {
    override val scope: IdkScope = IdkScope.SESSION

    companion object {
        const val SERVICE_ID = "IOSSessionMobileLogger"
    }
}

/**
 * Abstract iOS mobile log service - optimized for performance
 */
abstract class AbstractIOSMobileLogService(
    runtimeSessionContext: SessionContext,
    id: String,
    private val repository: MobileLogRepository,
) : AbstractMobileSessionLogService(runtimeSessionContext, id) {
    companion object {
        private const val DEFAULT_TAG = "SureIDK"
    }

    // Pre-computed values for performance optimization
    private val asyncCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tagPrefix = "["
    private val tagSuffix = "] "
    private val levelSeparator = ": "
    private val contextPrefix = " [ctx: "
    private val contextSuffix = "]"
    private val exceptionPrefix = "\nException: "

    // Pre-computed log level prefixes with emojis
    private val logLevelPrefixes =
        mapOf(
            LogLevel.TRACE to "TRACE",
            LogLevel.DEBUG to "DEBUG",
            LogLevel.INFO to "INFO",
            LogLevel.WARN to "WARN",
            LogLevel.ERROR to "ERROR",
            LogLevel.OFF to "",
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

        // Fast iOS logging with pre-computed values
        logToIOS(processedMessage, config, runtimeSessionContextId)

        return IdkResult.ok(Unit)
    }

    /**
     * Optimized iOS logging with pre-computed values and efficient string handling
     */
    private fun logToIOS(
        message: LogMessage,
        config: LoggerConfig,
        runtimeSessionContextId: String,
    ) {
        // Fast tag and level prefix resolution
        val tag = message.tag ?: config.tag.takeIf { it.isNotEmpty() } ?: DEFAULT_TAG
        val levelPrefix = logLevelPrefixes[message.level] ?: ""

        // Build log message efficiently using StringBuilder capacity estimation
        val throwableStr = message.exception?.stackTraceToString()

        val logMessage =
            buildString(message.message.length + 200) {
                // Include timestamp if configured
                if (config.includeTimestamp) {
                    append("[")
                    append(message.timestamp)
                    append("] ")
                }
                append(tagPrefix)
                append(tag)
                append(tagSuffix)
                append(levelPrefix)
                append(levelSeparator)
                append(message.message)

                // Include metadata if present
                message.metadata?.takeIf { it.isNotEmpty() }?.let { meta ->
                    append(" {")
                    append(meta.entries.joinToString(", ") { "${it.key}=${it.value}" })
                    append("}")
                }

                if (runtimeSessionContextId.isNotEmpty()) {
                    append(contextPrefix)
                    append(runtimeSessionContextId)
                    append(contextSuffix)
                }

                throwableStr?.let { throwable ->
                    append(exceptionPrefix)
                    append(throwable)
                }
            }

        // Use NSLog for iOS system logging
        NSLog(logMessage)
    }
}

/**
 * iOS-specific utilities for mobile logging
 */
object IOSMobileLogUtils {
    /**
     * Gets the appropriate log prefix for iOS logging based on LogLevel
     */
    fun getIOSLogPrefix(level: LogLevel): String =
        when (level) {
            LogLevel.TRACE -> "TRACE"
            LogLevel.DEBUG -> "DEBUG"
            LogLevel.INFO -> "INFO"
            LogLevel.WARN -> "WARN"
            LogLevel.ERROR -> "ERROR"
            LogLevel.OFF -> ""
        }

    /**
     * Creates a formatted log message for iOS
     */
    fun formatLogMessage(
        level: LogLevel,
        tag: String,
        message: String,
        context: String? = null,
    ): String =
        buildString {
            append(getIOSLogPrefix(level))
            append(" [$tag] $message")
            context?.let { append(" [ctx: $it]") }
        }
}

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

package com.sphereon.core.api.log

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asVoid
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.error.ErrorDefinitionType
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.session.Command
import com.sphereon.core.api.session.CommandAdapter
import com.sphereon.core.api.session.currentTimeMillis
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionLogService", exact = true)
interface SessionLogService : LogService {
    override val scope: IdkScope
        get() = IdkScope.SESSION
    val logManager: SessionLogManager
}

/**
 * Output format for log messages.
 */
@JsExportCompat
enum class LogOutputFormat {
    /** Plain text format with optional timestamp prefix */
    TEXT,

    /** JSON format for structured logging */
    JSON,
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("LogMessage", exact = true)
@JsExportCompat
data class LogMessage(
    val level: LogLevel = LogLevel.DEBUG,
    val message: String,
    val tag: String? = null,
    val exception: Throwable? = null,
    val errorResult: IdkResult<Nothing, *>? = null,
    /** Optional metadata key-value pairs for structured logging context */
    val metadata: Map<String, String>? = null,
    /** Timestamp in epoch milliseconds, always captured at message creation time */
    val timestamp: Long = currentTimeMillis(),
)

@JsExportCompat
enum class LogLevel(
    val value: Int,
) {
    @Suppress("detekt.MagicNumber")
    TRACE(0),

    @Suppress("detekt.MagicNumber")
    DEBUG(10),

    @Suppress("detekt.MagicNumber")
    INFO(20),

    @Suppress("detekt.MagicNumber")
    WARN(30),

    @Suppress("detekt.MagicNumber")
    ERROR(40),

    @Suppress("detekt.MagicNumber")
    OFF(100),
    ;

    override fun toString() = this.name
}

private val wildcardRegexCache = mutableMapOf<String, Regex>()

private fun wildcardMatch(
    value: String,
    pattern: String,
): Boolean {
    if (pattern == "*") {
        return true
    }
    if (!pattern.contains('*')) {
        return value == pattern
    }
    val regex =
        wildcardRegexCache.getOrPut(pattern) {
            val escaped =
                pattern
                    .replace(".", "\\.")
                    .replace("*", ".*")
            Regex("^$escaped$")
        }
    return regex.matches(value)
}

private fun patternSpecificity(pattern: String): Int = pattern.count { it != '*' }

private fun commandOrTagFromMetadata(metadata: Map<String, String>?): String? =
    metadata?.get("commandId")
        ?: metadata?.get("command.id")
        ?: metadata?.get("command_id")
        ?: metadata?.get("tag")

/**
 * Runtime log policy controls by scope, service id, and command/tag pattern.
 *
 * Rules are evaluated in this order: scope -> service pattern -> command pattern.
 * Later stages can override minimum level from earlier stages.
 */
data class LogPolicy(
    val minLevelByScope: Map<IdkScope, LogLevel> = emptyMap(),
    val minLevelByServicePattern: Map<String, LogLevel> = emptyMap(),
    val minLevelByCommandPattern: Map<String, LogLevel> = emptyMap(),
    val disabledScopes: Set<IdkScope> = emptySet(),
    val disabledServicePatterns: Set<String> = emptySet(),
    val disabledCommandPatterns: Set<String> = emptySet(),
) {
    fun isEnabled(
        level: LogLevel,
        defaultMinLevel: LogLevel,
        scope: IdkScope,
        serviceId: String,
        commandOrTag: String?,
    ): Boolean {
        if (scope in disabledScopes) {
            return false
        }
        if (disabledServicePatterns.any { wildcardMatch(serviceId, it) }) {
            return false
        }
        if (commandOrTag != null && disabledCommandPatterns.any { wildcardMatch(commandOrTag, it) }) {
            return false
        }

        var effectiveMin = minLevelByScope[scope] ?: defaultMinLevel
        minLevelByServicePattern
            .filterKeys { wildcardMatch(serviceId, it) }
            .maxByOrNull { patternSpecificity(it.key) }
            ?.value
            ?.let { effectiveMin = it }
        if (commandOrTag != null) {
            minLevelByCommandPattern
                .filterKeys { wildcardMatch(commandOrTag, it) }
                .maxByOrNull { patternSpecificity(it.key) }
                ?.value
                ?.let { effectiveMin = it }
        }
        return level.value >= effectiveMin.value
    }

    companion object {
        val AllowAll = LogPolicy()
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("LoggerConfig", exact = true)
@JsExportCompat
data class LoggerConfig(
    val minLevel: LogLevel = LogLevel.DEBUG,
    val tag: String = "sphereon",
    /** Output format for log messages */
    val outputFormat: LogOutputFormat = LogOutputFormat.TEXT,
    /** Whether to include timestamps in log output */
    val includeTimestamp: Boolean = false,
) {
    fun isEnabled(level: LogLevel) = level.value >= minLevel.value

    fun disable() = copy(minLevel = LogLevel.OFF)

    companion object {
        @JsStatic
        val Disabled = LoggerConfig(minLevel = LogLevel.OFF)

        @JsStatic
        val Debug = LoggerConfig(minLevel = LogLevel.DEBUG)

        @JsStatic
        val Default = LoggerConfig(minLevel = LogLevel.DEBUG)

        @JsStatic
        val JsonWithTimestamp =
            LoggerConfig(
                minLevel = LogLevel.DEBUG,
                outputFormat = LogOutputFormat.JSON,
                includeTimestamp = true,
            )
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("LogService", exact = true)
interface LogService : Logger {
    val sessionContext: SessionContext
    val scope: IdkScope
    val id: String

    val isEnabled: Boolean
    val policy: LogPolicy
        get() = LogPolicy.AllowAll

    fun isEnabled(
        level: LogLevel,
        tag: String? = null,
        context: SessionContext? = null,
    ): Boolean = isEnabled

    suspend fun disable() = apply { setConfig(getConfig().disable()) }

    suspend fun enable(minLevel: LogLevel = LogLevel.INFO) = apply { setConfig(getConfig().copy(minLevel)) }

    suspend fun getConfig(): LoggerConfig = LoggerConfig.Default

    suspend fun setConfig(config: LoggerConfig): LogService

    suspend fun getPolicy(): LogPolicy = policy

    suspend fun setPolicy(policy: LogPolicy): LogService = this

    override fun trace(
        message: String,
        metadata: Map<String, String>?,
    ) {
        if (!isEnabled(level = LogLevel.TRACE, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        executeAsync(LogMessage(level = LogLevel.TRACE, message = message, metadata = metadata)).asVoid()
    }

    override fun debug(
        message: String,
        metadata: Map<String, String>?,
    ) {
        if (!isEnabled(level = LogLevel.DEBUG, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        executeAsync(LogMessage(level = LogLevel.DEBUG, message = message, metadata = metadata)).asVoid()
    }

    override fun info(
        message: String,
        metadata: Map<String, String>?,
    ) {
        if (!isEnabled(level = LogLevel.INFO, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        executeAsync(LogMessage(level = LogLevel.INFO, message = message, metadata = metadata)).asVoid()
    }

    override fun warn(
        message: String,
        errorResult: IdkResult<Nothing, *>?,
        metadata: Map<String, String>?,
    ) {
        if (!isEnabled(level = LogLevel.WARN, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        executeAsync(LogMessage(level = LogLevel.WARN, message = message, errorResult = errorResult, metadata = metadata)).asVoid()
    }

    override fun error(
        message: String,
        exception: Throwable?,
        errorResult: IdkResult<Nothing, *>?,
        metadata: Map<String, String>?,
    ) {
        if (!isEnabled(level = LogLevel.ERROR, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        executeAsync(
            LogMessage(
                level = LogLevel.ERROR,
                message = message,
                errorResult = errorResult,
                exception = exception,
                metadata = metadata,
            ),
        ).asVoid()
    }

    fun trace(
        metadata: Map<String, String>? = null,
        message: () -> String,
    ) {
        if (!isEnabled(level = LogLevel.TRACE, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        executeAsync(LogMessage(level = LogLevel.TRACE, message = message(), metadata = metadata)).asVoid()
    }

    fun debug(
        metadata: Map<String, String>? = null,
        message: () -> String,
    ) {
        if (!isEnabled(level = LogLevel.DEBUG, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        executeAsync(LogMessage(level = LogLevel.DEBUG, message = message(), metadata = metadata)).asVoid()
    }

    fun info(
        metadata: Map<String, String>? = null,
        message: () -> String,
    ) {
        if (!isEnabled(level = LogLevel.INFO, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        executeAsync(LogMessage(level = LogLevel.INFO, message = message(), metadata = metadata)).asVoid()
    }

    fun warn(
        errorResult: IdkResult<Nothing, *>? = null,
        metadata: Map<String, String>? = null,
        message: () -> String,
    ) {
        if (!isEnabled(level = LogLevel.WARN, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        executeAsync(LogMessage(level = LogLevel.WARN, message = message(), errorResult = errorResult, metadata = metadata)).asVoid()
    }

    fun error(
        exception: Throwable? = null,
        errorResult: IdkResult<Nothing, *>? = null,
        metadata: Map<String, String>? = null,
        message: () -> String,
    ) {
        if (!isEnabled(level = LogLevel.ERROR, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        executeAsync(LogMessage(level = LogLevel.ERROR, message = message(), exception = exception, errorResult = errorResult, metadata = metadata)).asVoid()
    }

    /**
     * Executes log processing asynchronously and returns immediately.
     */
    fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType>

    fun toAsync(): AsyncLogService
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("AsyncLogService", exact = true)
interface AsyncLogService :
    Command<LogMessage, Unit, IdkErrorType>,
    AsyncLogger {
    val sessionContext: SessionContext

    val scope: IdkScope
    val policy: LogPolicy
        get() = LogPolicy.AllowAll

    fun isEnabled(
        level: LogLevel,
        tag: String? = null,
        context: SessionContext? = null,
    ): Boolean = isEnabled

    suspend fun disable() = apply { setConfig(getConfig().disable()) }

    suspend fun enable(minLevel: LogLevel = LogLevel.INFO) = apply { setConfig(getConfig().copy(minLevel)) }

    suspend fun getConfig(): LoggerConfig = LoggerConfig.Default

    suspend fun setConfig(config: LoggerConfig): AsyncLogService

    suspend fun getPolicy(): LogPolicy = policy

    suspend fun setPolicy(policy: LogPolicy): AsyncLogService = this

    override suspend fun trace(
        message: String,
        metadata: Map<String, String>?,
    ) {
        if (!isEnabled(level = LogLevel.TRACE, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        execute(LogMessage(level = LogLevel.TRACE, message = message, metadata = metadata)).asVoid()
    }

    override suspend fun debug(
        message: String,
        metadata: Map<String, String>?,
    ) {
        if (!isEnabled(level = LogLevel.DEBUG, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        execute(LogMessage(level = LogLevel.DEBUG, message = message, metadata = metadata)).asVoid()
    }

    override suspend fun info(
        message: String,
        metadata: Map<String, String>?,
    ) {
        if (!isEnabled(level = LogLevel.INFO, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        execute(LogMessage(level = LogLevel.INFO, message = message, metadata = metadata)).asVoid()
    }

    override suspend fun warn(
        message: String,
        errorResult: IdkResult<Nothing, *>?,
        metadata: Map<String, String>?,
    ) {
        if (!isEnabled(level = LogLevel.WARN, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        execute(LogMessage(level = LogLevel.WARN, message = message, errorResult = errorResult, metadata = metadata)).asVoid()
    }

    override suspend fun error(
        message: String,
        exception: Throwable?,
        errorResult: IdkResult<Nothing, *>?,
        metadata: Map<String, String>?,
    ) {
        if (!isEnabled(level = LogLevel.ERROR, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        execute(LogMessage(level = LogLevel.ERROR, message = message, errorResult = errorResult, exception = exception, metadata = metadata)).asVoid()
    }

    suspend fun trace(
        metadata: Map<String, String>? = null,
        message: () -> String,
    ) {
        if (!isEnabled(level = LogLevel.TRACE, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        execute(LogMessage(level = LogLevel.TRACE, message = message(), metadata = metadata)).asVoid()
    }

    suspend fun debug(
        metadata: Map<String, String>? = null,
        message: () -> String,
    ) {
        if (!isEnabled(level = LogLevel.DEBUG, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        execute(LogMessage(level = LogLevel.DEBUG, message = message(), metadata = metadata)).asVoid()
    }

    suspend fun info(
        metadata: Map<String, String>? = null,
        message: () -> String,
    ) {
        if (!isEnabled(level = LogLevel.INFO, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        execute(LogMessage(level = LogLevel.INFO, message = message(), metadata = metadata)).asVoid()
    }

    suspend fun warn(
        errorResult: IdkResult<Nothing, *>? = null,
        metadata: Map<String, String>? = null,
        message: () -> String,
    ) {
        if (!isEnabled(level = LogLevel.WARN, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        execute(LogMessage(level = LogLevel.WARN, message = message(), errorResult = errorResult, metadata = metadata)).asVoid()
    }

    suspend fun error(
        exception: Throwable? = null,
        errorResult: IdkResult<Nothing, *>? = null,
        metadata: Map<String, String>? = null,
        message: () -> String,
    ) {
        if (!isEnabled(level = LogLevel.ERROR, tag = commandOrTagFromMetadata(metadata), context = sessionContext)) {
            return
        }
        execute(LogMessage(level = LogLevel.ERROR, message = message(), exception = exception, errorResult = errorResult, metadata = metadata)).asVoid()
    }

    fun toSync(): LogService
}

interface Logger {
    fun trace(
        message: String,
        metadata: Map<String, String>? = null,
    )

    fun debug(
        message: String,
        metadata: Map<String, String>? = null,
    )

    fun info(
        message: String,
        metadata: Map<String, String>? = null,
    )

    fun warn(
        message: String,
        errorResult: IdkResult<Nothing, *>? = null,
        metadata: Map<String, String>? = null,
    )

    fun error(
        message: String,
        exception: Throwable? = null,
        errorResult: IdkResult<Nothing, *>? = null,
        metadata: Map<String, String>? = null,
    )
}

interface AsyncLogger {
    suspend fun trace(
        message: String,
        metadata: Map<String, String>? = null,
    )

    suspend fun debug(
        message: String,
        metadata: Map<String, String>? = null,
    )

    suspend fun info(
        message: String,
        metadata: Map<String, String>? = null,
    )

    suspend fun warn(
        message: String,
        errorResult: IdkResult<Nothing, *>? = null,
        metadata: Map<String, String>? = null,
    )

    suspend fun error(
        message: String,
        exception: Throwable? = null,
        errorResult: IdkResult<Nothing, *>? = null,
        metadata: Map<String, String>? = null,
    )
}

fun Set<LogService>.filterEnabled() = this.filter { it.isEnabled }.toSet()

fun Set<LogService>.filterScope(idkScope: IdkScope) = this.filter { it.scope == idkScope }.toSet()

// Interfaces to workaround qualifiers not working nicely yet across scopes
@OptIn(ExperimentalObjCName::class)
@ObjCName("AppLogManager", exact = true)
interface AppLogManager : LogManager

@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionLogManager", exact = true)
interface SessionLogManager : LogManager

@OptIn(ExperimentalObjCName::class)
@ObjCName("UserContextLogManager", exact = true)
interface UserContextLogManager : LogManager

@OptIn(ExperimentalObjCName::class)
@ObjCName("LogManager", exact = true)
interface LogManager {
    suspend fun setGlobalConfig(config: LoggerConfig): LogManager

    suspend fun getGlobalConfig(): LoggerConfig

    suspend fun setGlobalPolicy(policy: LogPolicy): LogManager = this

    suspend fun getGlobalPolicy(): LogPolicy = LogPolicy.AllowAll

    // todo: Instance configs
    fun withTagAsync(
        tag: String = "sphereon",
        config: LoggerConfig? = null,
    ): AsyncLogService

    fun withTag(
        tag: String = "sphereon",
        config: LoggerConfig? = null,
    ): LogService
}

abstract class AbstractLogService(
    id: String,
    isEnabled: Boolean = true,
    override val sessionContext: SessionContext = NoOpSessionContext,
    private var config: LoggerConfig = LoggerConfig.Default,
    private var logPolicy: LogPolicy = LogPolicy.AllowAll,
) : CommandAdapter<LogMessage, Unit, IdkErrorType>(
        id = id,
        isEnabled = isEnabled,
    ),
    LogService {
    protected val logScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val policy: LogPolicy
        get() = logPolicy

    // Use lazy initialization to ensure subclass properties (like scope) are available
    val asyncDelegate: AsyncLogService by lazy {
        object : AsyncLogService {
            override val sessionContext: SessionContext = this@AbstractLogService.sessionContext
            override val scope: IdkScope = this@AbstractLogService.scope
            override val id: String = this@AbstractLogService.id
            override val isEnabled: Boolean = this@AbstractLogService.isEnabled
            override val policy: LogPolicy
                get() = this@AbstractLogService.policy

            override fun isEnabled(
                level: LogLevel,
                tag: String?,
                context: SessionContext?,
            ): Boolean = this@AbstractLogService.isEnabled(level = level, tag = tag, context = context)

            override suspend fun setConfig(config: LoggerConfig) = apply { this@AbstractLogService.setConfig(config) }

            override suspend fun setPolicy(policy: LogPolicy) = apply { this@AbstractLogService.setPolicy(policy) }

            override suspend fun getPolicy(): LogPolicy = this@AbstractLogService.getPolicy()

            override suspend fun execute(args: LogMessage): IdkResult<Unit, IdkErrorType> = this@AbstractLogService.execute(args)

            override fun toSync(): LogService = this@AbstractLogService
        }
    }

    override suspend fun setConfig(config: LoggerConfig) =
        apply {
            this.config = config
        }

    override suspend fun getConfig(): LoggerConfig = config

    override suspend fun getPolicy(): LogPolicy = logPolicy

    override suspend fun setPolicy(policy: LogPolicy) =
        apply {
            this.logPolicy = policy
        }

    override fun isEnabled(
        level: LogLevel,
        tag: String?,
        context: SessionContext?,
    ): Boolean =
        isEnabled &&
            logPolicy.isEnabled(
                level = level,
                defaultMinLevel = config.minLevel,
                scope = scope,
                serviceId = id,
                commandOrTag = tag,
            )

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> {
        logScope.launch {
            this@AbstractLogService.execute(message)
        }
        return IdkResult.Companion.ok(Unit)
    }

    open fun cancel() {
        logScope.cancel()
    }

    override fun toAsync(): AsyncLogService = asyncDelegate
}

/**
 * Utility object for formatting log messages based on configuration.
 * Optimized for speed with pre-computed constants and efficient string building.
 */
object LogMessageFormatter {
    // Pre-computed constants for TEXT format
    private const val BRACKET_OPEN = "["
    private const val BRACKET_CLOSE = "] "
    private const val SPACE_BRACE_OPEN = " {"
    private const val BRACE_CLOSE = "}"
    private const val EXCEPTION_PREFIX = "\n  Exception: "
    private const val NEWLINE_INDENT = "\n  "
    private const val COMMA_SPACE = ", "
    private const val EQUALS = "="

    // Pre-computed constants for JSON format
    private const val JSON_OPEN = "{\"level\":\""
    private const val JSON_MESSAGE = "\",\"message\":\""
    private const val JSON_TIMESTAMP = "\",\"timestamp\":"
    private const val JSON_TAG = ",\"tag\":\""
    private const val JSON_METADATA_OPEN = ",\"metadata\":{"
    private const val JSON_EXCEPTION_OPEN = ",\"exception\":{\"message\":\""
    private const val JSON_STACKTRACE = "\",\"stackTrace\":\""
    private const val JSON_ERROR_RESULT = ",\"errorResult\":\""
    private const val JSON_QUOTE = "\""
    private const val JSON_CLOSE = "}"
    private const val JSON_COLON_QUOTE = "\":\""
    private const val JSON_COMMA = ","

    // Capacity estimation constants
    private const val METADATA_ENTRY_OVERHEAD = 3
    private const val EXCEPTION_BASE_CAPACITY = 50
    private const val TEXT_BASE_CAPACITY = 30
    private const val MAX_STACKTRACE_LINES = 30
    private const val JSON_METADATA_ENTRY_OVERHEAD = 10
    private const val JSON_BASE_CAPACITY = 100
    private const val JSON_MESSAGE_CAPACITY_MULTIPLIER = 2

    /**
     * Formats a LogMessage according to the provided LoggerConfig.
     *
     * @param message The log message to format
     * @param config The logger configuration specifying format options
     * @return Formatted string representation of the log message
     */
    fun format(
        message: LogMessage,
        config: LoggerConfig,
    ): String =
        when (config.outputFormat) {
            LogOutputFormat.TEXT -> formatText(message, config)
            LogOutputFormat.JSON -> formatJson(message, config)
        }

    private fun formatText(
        message: LogMessage,
        config: LoggerConfig,
    ): String {
        // Pre-compute capacity for efficient allocation
        val tagLen = message.tag?.length ?: 0
        val metadataLen = message.metadata?.entries?.sumOf { it.key.length + it.value.length + METADATA_ENTRY_OVERHEAD } ?: 0
        val exceptionLen = message.exception?.let { EXCEPTION_BASE_CAPACITY + (it.message?.length ?: 0) } ?: 0
        val capacity = TEXT_BASE_CAPACITY + message.message.length + tagLen + metadataLen + exceptionLen

        return StringBuilder(capacity)
            .apply {
                if (config.includeTimestamp) {
                    append(BRACKET_OPEN)
                    append(message.timestamp)
                    append(BRACKET_CLOSE)
                }
                append(BRACKET_OPEN)
                append(message.level.name)
                append(BRACKET_CLOSE)
                message.tag?.let {
                    append(BRACKET_OPEN)
                    append(it)
                    append(BRACKET_CLOSE)
                }
                append(message.message)
                message.metadata?.takeIf { it.isNotEmpty() }?.let { meta ->
                    append(SPACE_BRACE_OPEN)
                    var first = true
                    for ((key, value) in meta) {
                        if (!first) {
                            append(COMMA_SPACE)
                        }
                        append(key)
                        append(EQUALS)
                        append(value)
                        first = false
                    }
                    append(BRACE_CLOSE)
                }
                message.exception?.let { ex ->
                    append(EXCEPTION_PREFIX)
                    append(ex.message ?: ex.toString())
                    // Truncate by lines (not chars) to keep output bounded across all platforms.
                    // stackTraceToString() is the only multiplatform API; stackTrace array is JVM-only.
                    val lines = ex.stackTraceToString().lines()
                    lines.take(MAX_STACKTRACE_LINES).forEach { line ->
                        append(NEWLINE_INDENT)
                        append(line)
                    }
                    if (lines.size > MAX_STACKTRACE_LINES) {
                        append("\n  ... ${lines.size - MAX_STACKTRACE_LINES} more")
                    }
                }
            }.toString()
    }

    private fun formatJson(
        message: LogMessage,
        config: LoggerConfig,
    ): String {
        // Pre-compute capacity for efficient allocation
        val metadataLen = message.metadata?.entries?.sumOf { it.key.length + it.value.length + JSON_METADATA_ENTRY_OVERHEAD } ?: 0
        val capacity = JSON_BASE_CAPACITY + message.message.length * JSON_MESSAGE_CAPACITY_MULTIPLIER + metadataLen

        return StringBuilder(capacity)
            .apply {
                append(JSON_OPEN)
                append(message.level.name)
                append(JSON_MESSAGE)
                appendEscapedJson(message.message)
                append(JSON_QUOTE)
                if (config.includeTimestamp) {
                    append(JSON_TIMESTAMP)
                    append(message.timestamp)
                }
                message.tag?.let {
                    append(JSON_TAG)
                    appendEscapedJson(it)
                    append(JSON_QUOTE)
                }
                message.metadata?.takeIf { it.isNotEmpty() }?.let { meta ->
                    append(JSON_METADATA_OPEN)
                    var first = true
                    for ((key, value) in meta) {
                        if (!first) {
                            append(JSON_COMMA)
                        }
                        append(JSON_QUOTE)
                        appendEscapedJson(key)
                        append(JSON_COLON_QUOTE)
                        appendEscapedJson(value)
                        append(JSON_QUOTE)
                        first = false
                    }
                    append(JSON_CLOSE)
                }
                message.exception?.let { ex ->
                    append(JSON_EXCEPTION_OPEN)
                    appendEscapedJson(ex.message ?: "")
                    append(JSON_STACKTRACE)
                    appendEscapedJson(ex.stackTraceToString())
                    append(JSON_QUOTE)
                    append(JSON_CLOSE)
                }
                message.errorResult?.let { result ->
                    if (result.isErr) {
                        append(JSON_ERROR_RESULT)
                        appendEscapedJson(result.toString())
                        append(JSON_QUOTE)
                    }
                }
                append(JSON_CLOSE)
            }.toString()
    }

    /**
     * Appends a JSON-escaped string directly to StringBuilder in a single pass.
     * More efficient than creating intermediate escaped strings.
     */
    private fun StringBuilder.appendEscapedJson(str: String) {
        for (char in str) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

sealed class LogError(
    override val code: String,
    override val defaultMessage: String,
    override val i18nKey: String,
    override val severity: IdkError.Severity,
) : ErrorDefinitionType {
    internal fun asList() = listOf(ServiceDisabled, NoLoggersConfigured)

    fun getByCode(code: String): ErrorDefinitionType = asList().find { it.code == code } ?: NotFound("The error with code [$code] was not found")

    fun getByI18nKey(i18nKey: String): ErrorDefinitionType = asList().find { it.i18nKey == i18nKey } ?: NotFound("The error with i18nKey [$i18nKey] was not found")

    class NotFound(
        message: String?,
    ) : LogError(
            code = "NOT_FOUND",
            i18nKey = "errors.com.sphereon.core.log.not-found",
            defaultMessage = message ?: "Not found",
            severity = IdkError.Severity.WARNING,
        )

    object ServiceDisabled : LogError(
        code = "SERVICE_DISABLED",
        i18nKey = "errors.com.sphereon.core.log.session-disabled",
        defaultMessage = "The logging session is disabled",
        severity = IdkError.Severity.INFO,
    )

    object NoLoggersConfigured : LogError(
        code = "NO_LOGGERS_CONFIGURED",
        i18nKey = "errors.com.sphereon.core.log.no-loggers-configured",
        defaultMessage = "No loggers configured",
        severity = IdkError.Severity.ERROR,
    )
}

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

package com.sphereon.core.api.error

import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.error.IdkError.Companion.NOT_FOUND_ERROR
import com.sphereon.core.api.error.IdkError.Message
import com.sphereon.core.api.error.IdkError.Severity
import com.sphereon.core.api.session.BaseCommand
import com.sphereon.core.api.session.Command
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName
import kotlin.time.Duration

@OptIn(ExperimentalObjCName::class)
@ObjCName("IdkErrorType", exact = true)
@JsExportCompat
interface IdkErrorType {
    val code: String
    val message: Message
    val severity: Severity
    val category: ErrorCategory
        get() = ErrorCategory.INTERNAL
    val exception: Throwable?
    val causes: List<IdkErrorType>
    val meta: Map<String, Any?>

    /**
     * Whether re-attempting the operation that produced this error is likely to
     * succeed. The retry middleware combines this with the command's
     * [com.sphereon.core.api.service.contract.ExecutionTraits.isIdempotent] to decide.
     *
     * Default [Retryability.NONE] is conservative: only errors that explicitly
     * declare themselves [Retryability.TRANSIENT] or [Retryability.CONDITIONAL]
     * become retry candidates.
     */
    val retryability: Retryability
        get() = Retryability.NONE

    /**
     * Suggested minimum delay before re-attempting. When null, the retry
     * middleware uses its configured backoff schedule. Honoured only when
     * [retryability] is non-[Retryability.NONE].
     */
    val retryAfter: Duration?
        get() = null
}

@JsExportCompat
open class IdkError(
    override val code: String,
    override val message: Message,
    override val severity: Severity = Severity.ERROR,
    override val category: ErrorCategory = ErrorCategory.INTERNAL,
    override val causes: List<IdkErrorType> = mutableListOf(),
    override val meta: Map<String, Any?> = mutableMapOf(),
    override val exception: Throwable? = null,
    /**
     * The original [IdkErrorType] this IdkError was constructed from, when applicable.
     * Set by [fromDTO] so downstream consumers can re-extract the source's typed shape
     * (e.g. a sealed [IdkErrorType] subtype with extra fields) instead of dispatching off
     * `code` + `meta` magic-strings.
     *
     * Null when the IdkError was constructed directly (via [fromString] /
     * [fromDefinition] / a primary-constructor call); callers MUST treat that as
     * "the source type is unavailable" and fall back to the wire-shape.
     */
    val source: IdkErrorType? = null,
) : IdkErrorType {
    /**
     * Forwarded from [source] when this [IdkError] wraps a typed [IdkErrorType]
     * (e.g. via [fromDTO]); falls back to the interface default otherwise.
     * This keeps retry classification intact across DTO conversion.
     */
    override val retryability: Retryability
        get() = source?.retryability ?: Retryability.NONE

    override val retryAfter: Duration?
        get() = source?.retryAfter

    fun hasException(): Boolean = exception != null

    fun hasCauses(): Boolean = causes.isNotEmpty()

    fun hasMeta(): Boolean = meta.isNotEmpty()

    fun addCause(cause: IdkError): IdkError = apply { (causes as MutableList).add(cause) }

    override fun toString(): String = "IdkError(code='$code', category=$category, severity=$severity, message=$message, causes=$causes, meta=$meta, exception=$exception)"

    enum class Severity(
        val value: Int,
    ) {
        INFO(10),
        WARNING(20),
        ERROR(30),
        FATAL(40),
        ;

        fun isAtLeast(severity: Severity): Boolean = value >= severity.value

        fun isAtMost(severity: Severity): Boolean = value <= severity.value
    }

    data class Message(
        val i18nKey: String,
        val i18nParams: Map<String, Any?> = emptyMap(),
        val defaultMessage: String,
    )

    companion object {
        @JvmStatic
        @JsStatic
        fun fromDTO(error: IdkErrorType) =
            IdkError(
                code = error.code,
                message = error.message,
                severity = error.severity,
                category = error.category,
                exception = error.exception,
                causes = error.causes,
                meta = error.meta,
                // Preserve the original typed error so consumers downstream can downcast back
                // (via `IdkError.sourceAs<T>()`) instead of dispatching on `code` + `meta`
                // magic-strings. If `error` IS already an IdkError carrying its own source,
                // unwrap one level so chained fromDTO calls don't nest forever.
                source = (error as? IdkError)?.source ?: error,
            )

        @JvmStatic
        @JsStatic
        fun fromString(
            message: String,
            code: String = "UNKNOWN_ERROR",
            exception: Exception? = null,
            i18nKey: String = message,
            severity: Severity = Severity.ERROR,
            category: ErrorCategory = ErrorCategory.INTERNAL,
        ) = IdkError(
            code = code,
            message = Message(i18nKey = i18nKey, defaultMessage = message),
            severity = severity,
            category = category,
            exception = exception,
        )

        @JvmStatic
        @JsStatic
        fun fromDefinition(
            definition: ErrorDefinitionType,
            i18nParams: Map<String, Any?> = emptyMap(),
            causes: List<IdkError> = emptyList(),
            meta: Map<String, Any?> = emptyMap(),
            severity: Severity = definition.severity,
            category: ErrorCategory = definition.category,
            exception: Throwable? = null,
        ) = IdkError(
            code = definition.code,
            message =
                Message(
                    i18nKey = definition.i18nKey,
                    i18nParams = i18nParams,
                    defaultMessage = definition.defaultMessage,
                ),
            severity = severity,
            category = category,
            exception = exception,
            causes = causes,
            meta = meta,
        )

        @JvmStatic
        @JsStatic
        fun UNKNOWN_ERROR(
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            message: String = "An unknown error occurred",
            exception: Throwable? = null,
        ) = IdkError(
            code = "UNKNOWN_ERROR",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.unknown-error",
                    defaultMessage = message,
                ),
            severity = severity,
            category = ErrorCategory.INTERNAL,
            causes = causes,
            exception = exception,
        )

        @JvmStatic
        @JsStatic
        fun ILLEGAL_ARGUMENT_ERROR(
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            arg: Any? = null,
            message: String = "An illegal argument was supplied${arg?.let { ": $it" } ?: ""}",
            throwable: Throwable? = null,
        ) = IdkError(
            code = "ILLEGAL_ARGUMENT_ERROR",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.illegal-argument-error",
                    defaultMessage = message,
                ),
            severity = severity,
            category = ErrorCategory.VALIDATION,
            causes = causes,
            exception = throwable,
        )

        @JvmStatic
        @JsStatic
        fun COMMAND_ARG_NOT_SUPPORTED_ERROR(
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            command: BaseCommand<*, *, *>? = null,
            arg: Any? = null,
            message: String = "The command ${(command as? Command)?.id ?: "<unknown>"} does not support the argument${arg?.let { ": $it" } ?: ""}",
            throwable: Throwable? = null,
        ) = IdkError(
            code = "COMMAND_ARG_NOT_SUPPORTED_ERROR",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.command-argument-not-supported-error",
                    defaultMessage = message,
                ),
            severity = severity,
            category = ErrorCategory.VALIDATION,
            causes = causes,
            exception = throwable,
        )

        @JvmStatic
        @JsStatic
        fun NOT_FOUND_ERROR(
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            resource: String? = null,
            message: String = "Not found${resource?.let { ": $it" } ?: ""}",
            throwable: Throwable? = null,
        ) = IdkError(
            code = "NOT_FOUND_ERROR",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.not-found-error",
                    defaultMessage = message,
                ),
            severity = severity,
            category = ErrorCategory.NOT_FOUND,
            causes = causes,
            exception = throwable,
        )

        @JvmStatic
        @JsStatic
        fun COMMAND_DISABLED_ERROR(
            commandId: String,
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            throwable: Throwable? = null,
        ) = IdkError(
            code = "COMMAND_DISABLED",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.command-disabled",
                    i18nParams = mapOf("commandId" to commandId),
                    defaultMessage = "Command '$commandId' is disabled",
                ),
            severity = severity,
            category = ErrorCategory.UNAVAILABLE,
            causes = causes,
            exception = throwable,
            meta = mapOf("commandId" to commandId),
        )

        @JvmStatic
        @JsStatic
        fun COMMAND_SKIPPED_ERROR(
            commandId: String,
            reason: String? = null,
            severity: Severity = Severity.INFO,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            throwable: Throwable? = null,
        ) = IdkError(
            code = "COMMAND_SKIPPED",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.command-skipped",
                    i18nParams = mapOf("commandId" to commandId, "reason" to (reason ?: "skipped by extension")),
                    defaultMessage = "Command '$commandId' was skipped" + (reason?.let { ": $it" } ?: ""),
                ),
            severity = severity,
            category = ErrorCategory.INTERNAL,
            causes = causes,
            exception = throwable,
            meta = mapOf("commandId" to commandId, "reason" to reason),
        )

        @JvmStatic
        @JsStatic
        fun COMMAND_NOT_AUTHORIZED_ERROR(
            commandId: String,
            reason: String,
            actor: String? = null,
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            throwable: Throwable? = null,
        ) = IdkError(
            code = "COMMAND_NOT_AUTHORIZED",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.command-not-authorized",
                    i18nParams = mapOf("commandId" to commandId, "reason" to reason, "actor" to (actor ?: "unknown")),
                    defaultMessage = "Not authorized to execute '$commandId': $reason",
                ),
            severity = severity,
            category = ErrorCategory.FORBIDDEN,
            causes = causes,
            exception = throwable,
            meta = mapOf("commandId" to commandId, "reason" to reason, "actor" to actor),
        )

        @JvmStatic
        @JsStatic
        fun ALL_HANDLERS_FAILED_ERROR(
            errors: List<IdkError>,
            severity: Severity = Severity.ERROR,
            throwable: Throwable? = null,
        ) = IdkError(
            code = "ALL_HANDLERS_FAILED",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.all-handlers-failed",
                    defaultMessage = "All handlers failed: ${errors.map { it.message.defaultMessage }.joinToString("; ")}",
                ),
            severity = severity,
            category = ErrorCategory.INTERNAL,
            causes = errors,
            exception = throwable,
            meta = mapOf("errorCount" to errors.size),
        )

        @JvmStatic
        @JsStatic
        fun UNAUTHORIZED_ERROR(
            message: String = "Unauthorized",
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            throwable: Throwable? = null,
        ) = IdkError(
            code = "UNAUTHORIZED",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.unauthorized",
                    defaultMessage = message,
                ),
            severity = severity,
            category = ErrorCategory.UNAUTHORIZED,
            causes = causes,
            exception = throwable,
        )

        @JvmStatic
        @JsStatic
        fun FORBIDDEN_ERROR(
            message: String = "Access forbidden",
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            throwable: Throwable? = null,
        ) = IdkError(
            code = "FORBIDDEN",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.forbidden",
                    defaultMessage = message,
                ),
            severity = severity,
            category = ErrorCategory.FORBIDDEN,
            causes = causes,
            exception = throwable,
        )

        @JvmStatic
        @JsStatic
        fun QUOTA_EXCEEDED_ERROR(
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            resource: String? = null,
            message: String = "Quota exceeded${resource?.let { ": $it" } ?: ""}",
            throwable: Throwable? = null,
        ) = IdkError(
            code = "QUOTA_EXCEEDED_ERROR",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.quota-exceeded-error",
                    defaultMessage = message,
                ),
            severity = severity,
            category = ErrorCategory.RATE_LIMITED,
            causes = causes,
            exception = throwable,
        )

        @JvmStatic
        @JsStatic
        fun ALREADY_EXISTS_ERROR(
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            resource: String? = null,
            message: String = "Already exists${resource?.let { ": $it" } ?: ""}",
            throwable: Throwable? = null,
        ) = IdkError(
            code = "ALREADY_EXISTS_ERROR",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.already-exists-error",
                    defaultMessage = message,
                ),
            severity = severity,
            category = ErrorCategory.CONFLICT,
            causes = causes,
            exception = throwable,
        )

        @JvmStatic
        @JsStatic
        fun INVALID_STATE(
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            message: String = "Invalid state",
            throwable: Throwable? = null,
        ) = IdkError(
            code = "INVALID_STATE",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.invalid-state",
                    defaultMessage = message,
                ),
            severity = severity,
            category = ErrorCategory.CONFLICT,
            causes = causes,
            exception = throwable,
        )

        @JvmStatic
        @JsStatic
        fun TIMEOUT_ERROR(
            timeout: Duration? = null,
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            message: String = "Operation timed out${timeout?.let { " after $it" } ?: ""}",
            throwable: Throwable? = null,
        ): IdkError =
            object : IdkError(
                code = "TIMEOUT",
                message =
                    Message(
                        i18nKey = "com.sphereon.core.error.timeout",
                        defaultMessage = message,
                    ),
                severity = severity,
                category = ErrorCategory.UNAVAILABLE,
                causes = causes,
                exception = throwable,
                meta = timeout?.let { mapOf("timeout" to it.toString()) } ?: emptyMap(),
            ) {
                override val retryability: Retryability get() = Retryability.TRANSIENT
            }

        @JvmStatic
        @JsStatic
        fun UNSUPPORTED_OPERATION_ERROR(
            operation: String,
            reason: String? = null,
            severity: Severity = Severity.ERROR,
            causes: List<IdkErrorType> = emptyList<IdkErrorType>(),
            throwable: Throwable? = null,
        ) = IdkError(
            code = "UNSUPPORTED_OPERATION",
            message =
                Message(
                    i18nKey = "com.sphereon.core.error.unsupported-operation",
                    i18nParams = mapOf("operation" to operation, "reason" to (reason ?: "")),
                    defaultMessage = "Operation '$operation' is not supported${reason?.let { ": $it" } ?: ""}",
                ),
            severity = severity,
            category = ErrorCategory.PROTOCOL,
            causes = causes,
            exception = throwable,
            meta = mapOf("operation" to operation, "reason" to reason),
        )
    }
}

fun NotFoundException.asError() = NOT_FOUND_ERROR(resource = this.resource, message = this.message ?: "resource not found", throwable = this)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ErrorDefinitionType", exact = true)
@JsExportCompat
interface ErrorDefinitionType {
    val code: String
    val i18nKey: String
    val defaultMessage: String
    val severity: Severity
    val category: ErrorCategory
        get() = ErrorCategory.INTERNAL

    /**
     * Create an IdkError instance using this definition.
     */
    fun asError(
        i18nParams: Map<String, Any?> = emptyMap(),
        exception: Throwable? = null,
        causes: List<IdkError> = emptyList(),
        meta: Map<String, Any?> = emptyMap(),
        severity: Severity = this.severity,
        category: ErrorCategory = this.category,
    ): IdkError =
        IdkError.fromDefinition(
            definition = this,
            i18nParams = i18nParams,
            exception = exception,
            causes = causes,
            meta = meta,
            severity = severity,
            category = category,
        )

    fun asResult(
        i18nParams: Map<String, Any?> = emptyMap(),
        exception: Throwable? = null,
        causes: List<IdkError> = emptyList(),
        meta: Map<String, Any?> = emptyMap(),
        severity: Severity = this.severity,
        category: ErrorCategory = this.category,
    ) = IdkErrorResult(asError(i18nParams, exception, causes, meta, severity, category))
}

/**
 * Re-extract the typed source [IdkErrorType] this [IdkError] was constructed from
 * (via [IdkError.fromDTO]), if it was [T]. Returns null when the IdkError was
 * built directly (no typed source) or when the source is not a [T].
 *
 * Use this at the read side of an `IdkResult<*, IdkError>` boundary to recover the
 * typed shape of a sealed [IdkErrorType] family — instead of dispatching off
 * `error.code` + `error.meta` magic-strings.
 *
 * Example:
 * ```
 * val pending = error.sourceAs<AuthorizationServerError.RequiredActionsPending>()
 *     ?: return null  // not a required-actions payload, fall through
 * pending.actionIds.forEach { … }   // typed access — no map casts
 * ```
 */
inline fun <reified T : IdkErrorType> IdkError.sourceAs(): T? = source as? T

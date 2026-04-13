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

package com.sphereon.core.api

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.github.michaelbull.result.Err as resultErr
import com.github.michaelbull.result.Ok as resultOk
import com.github.michaelbull.result.andThen as resultAndThen
import com.github.michaelbull.result.flatMap as resultFlatMap
import com.github.michaelbull.result.fold as resultFold
import com.github.michaelbull.result.get as resultGet
import com.github.michaelbull.result.getError as resultGetError
import com.github.michaelbull.result.getOr as resultGetOr
import com.github.michaelbull.result.getOrElse as resultGetOrElse
import com.github.michaelbull.result.map as resultMap
import com.github.michaelbull.result.mapError as resultMapError
import com.github.michaelbull.result.onFailure as resultOnFailure
import com.github.michaelbull.result.onSuccess as resultOnSuccess
import com.github.michaelbull.result.recover as resultRecover

/**
 * [IdkResult] is a type that represents either success ([Ok]) or failure ([Err]).
 *
 * A [IdkResult] that [is ok][IdkResult.isOk] will have a [value][IdkResult.value] of type [V], whereas a
 * [IdkResult] that [is an error][IdkResult.isErr] will have an [error][IdkResult.error] of type [E].
 *
 * This is a wrapper around kotlin-result's Result type that exposes all methods to Swift/ObjC.
 * Unlike a pure typealias, this class provides interop with iOS platforms.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("IdkResult", exact = true)
open class IdkResult<out V, out E>
    @PublishedApi
    internal constructor(
        @PublishedApi internal val result: Result<V, E>,
    ) {
        val isOk: Boolean get() = result.isOk
        val isErr: Boolean get() = result.isErr

        /**
         * The success value. Only call this when isOk is true.
         */
        open val value: V get() = result.value

        /**
         * The error value. Only call this when isErr is true.
         */
        open val error: E get() = result.error

        /**
         * Returns the value if this is Ok, null otherwise (for destructuring)
         */
        operator fun component1(): V? = get()

        /**
         * Returns the error if this is Err, null otherwise (for destructuring)
         */
        operator fun component2(): E? = getErrorOrNull()

        override fun toString(): String = result.toString()

        inline fun onSuccess(action: (V) -> Unit): IdkResult<V, E> {
            result.resultOnSuccess(action)
            return this
        }

        inline fun onFailure(action: (E) -> Unit): IdkResult<V, E> {
            result.resultOnFailure(action)
            return this
        }

        inline fun <R> map(transform: (V) -> R): IdkResult<R, E> = IdkResult(result.resultMap(transform))

        inline fun <F> mapError(transform: (E) -> F): IdkResult<V, F> = IdkResult(result.resultMapError(transform))

        inline fun <R> flatMap(transform: (V) -> IdkResult<R, @UnsafeVariance E>): IdkResult<R, E> = IdkResult(result.resultFlatMap { transform(it).result })

        inline fun <R> andThen(transform: (V) -> IdkResult<R, @UnsafeVariance E>): IdkResult<R, E> = IdkResult(result.resultAndThen { transform(it).result })

        inline fun recover(transform: (E) -> @UnsafeVariance V): IdkResult<V, E> = IdkResult(result.resultRecover(transform))

        infix fun getOr(defaultValue: @UnsafeVariance V): V = result.resultGetOr(defaultValue)

        inline infix fun getOrElse(transform: (E) -> @UnsafeVariance V): V = result.resultGetOrElse(transform)

        inline fun <R> fold(
            success: (V) -> R,
            failure: (E) -> R,
        ): R = result.resultFold(success, failure)

        inline fun <R> mapBoth(
            success: (V) -> R,
            failure: (E) -> R,
        ): R = result.mapBoth(success, failure)

        fun get(): V? = result.resultGet()

        fun getErrorOrNull(): E? = result.resultGetError()

        fun getOrNull(): V? = get()

        fun errorOrNull(): E? = getErrorOrNull()

        /**
         * Returns the value if this is Ok, or throws an exception if this is Err.
         *
         * This converts result-based error handling to exception-based error handling.
         * Prefer `getOrElse { return Err(it) }` when working within IdkResult-based code.
         *
         * Example:
         * ```kotlin
         * val clientId = parsedUri.getFirstRequired("client_id").getOrThrow()
         * // Throws if client_id is missing
         * ```
         *
         * @throws Throwable The error converted to an exception
         */
        fun getOrThrow(): V {
            if (isOk) {
                return value
            }
            val err = error
            throw when (err) {
                is Throwable -> err
                is IdkErrorType -> err.toException()
                else -> IllegalStateException("Result contained error: $err")
            }
        }

        companion object {
            /**
             * Creates a successful IdkResult from a value
             */
            fun <V, E> ok(value: V): IdkResult<V, E> = Ok(value).asResult()

            /**
             * Creates a failed IdkResult from an error
             */
            fun <V, E> err(error: E): IdkResult<V, E> = Err(error).asResult()
        }
    }

/**
 * [Ok] represents a successful result with a value.
 * This is a subclass of IdkResult that exposes Ok to Swift/ObjC.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("Ok", exact = true)
class Ok<V>(
    private val _value: V,
) : IdkResult<V, Nothing>(resultOk(_value)) {
    override val value: V get() = _value

    /**
     * Widens the error type to allow returning IdkOk where IdkResult<V, E> is expected
     */
    @Suppress("UNCHECKED_CAST")
    fun <E> asResult(): IdkResult<V, E> = this as IdkResult<V, E>

    override fun toString(): String = "Ok($value)"

    override fun equals(other: Any?): Boolean = other is Ok<*> && value == other.value

    override fun hashCode(): Int = value.hashCode()
}

/**
 * [Err] represents a failed result with an error.
 * This is a subclass of IdkResult that exposes Err to Swift/ObjC.
 */
@OptIn(ExperimentalObjCName::class)
@JsExportCompat
@ObjCName("Err", exact = true)
class Err<E>(
    private val _error: E,
) : IdkResult<Nothing, E>(resultErr(_error)) {
    override val error: E get() = _error

    /**
     * Widens the value type to allow returning IdkErr where IdkResult<V, E> is expected
     */
    @Suppress("UNCHECKED_CAST")
    fun <V> asResult(): IdkResult<V, E> = this as IdkResult<V, E>

    override fun toString(): String = "Err($error)"

    override fun equals(other: Any?): Boolean = other is Err<*> && error == other.error

    override fun hashCode(): Int = error.hashCode()
}

// ========================================
// Conversion helpers between kotlin-result's Result and IdkResult
// ========================================

/**
 * Converts a kotlin-result Result to an IdkResult wrapper for Swift/ObjC interop
 */
fun <V, E> Result<V, E>.toIdkResult(): IdkResult<V, E> = IdkResult(this)

/**
 * Converts an IdkResult back to kotlin-result's Result for internal use
 */
fun <V, E> IdkResult<V, E>.toResult(): Result<V, E> = this.result

// ========================================
// Legacy Functions for Backward Compatibility
// ========================================

@JsExportCompat
fun <V, E> IdkIsOk(result: IdkResult<V, E>): Boolean = result.isOk

@JsExportCompat
fun <V, E> IdkIsErr(result: IdkResult<V, E>): Boolean = result.isErr

@JsExportCompat
fun <V, E> IdkGetOrNull(result: IdkResult<V, E>): V? = result.getOrNull()

@JsExportCompat
fun <V, E> IdkErrorOrNull(result: IdkResult<V, E>): E? = result.errorOrNull()

/**
 * Create a IdkResult with an Ok value.
 */
@Suppress("FunctionName")
@JsExportCompat
fun <V> IdkOkResult(value: V): IdkResult<V, Nothing> = Ok(value)

/**
 * Converts a `IdkResult` into a `Unit`. This method is typically used when the result of the operation
 * is not needed, but actions may still depend on whether the result indicates success or failure.
 *
 * If the `IdkResult` is an error, optional actions such as logging or throwing exceptions can be performed
 * based on the provided parameters.
 *
 * @param throwOnError specifies whether to throw the error if the result is an error. Defaults to `false` as the whole library depends on The IdkResult monoids returning errors.
 * So exceptions really indicate a deeper problem that is not logically expected in the control flow.
 * @param logOnError specifies whether to log a message if the result is an error. Defaults to `true`.
 * @return `Unit` after processing the result. If the result is an error and `throwOnError` is `true`, an exception is thrown instead.
 */
fun IdkResult<*, *>.asVoid(
    throwOnError: Boolean = false,
    logOnError: Boolean = true,
) {
    if (this.isErr) {
        if (logOnError) {
            // Since this method is mainly used to convert the Result monoid to a Unit, for code that is really not interested in the result, we are logging
            // internally in case exceptions should not be thrown. We are not using the logging session, as that itself could be the cause
            // Using printStackTrace as Kotlin has no multiplatform error console redirection, and it outputs to console.err on multiple platforms
            IllegalStateException(
                "Returning a void, but an error occurred. ${if (!throwOnError) {
                    "This can have undesired side effects as the error is ignored:"
                } else {
                    ":"
                }} ${this.error}",
            ).printStackTrace()
        }
        if (throwOnError) {
            // No log on exception antipa
            val error = this.error
            if (error is Throwable) {
                throw error as Throwable
            }
            error(error.toString() ?: "Unknown error in asVoid")
        }
    }
}

/**
 * Extension function to create a `SureOk` result from the receiver value.
 *
 * This function wraps the receiver value in a `SureOk` instance, which represents
 * a successful result in the context of the `IdkResult` type.
 *
 * @receiver The value to be wrapped in a `SureOk` instance.
 * @return A `IdkResult` instance encapsulating the receiver value as a successful outcome.
 */
fun <V> V.asOkResult() = IdkOkResult(this)

/**
 * Converts the calling object into a `SureErr` instance, wrapping it as an error result.
 *
 * This method is a convenience extension function that allows any type to be transformed into
 * an error representation using the `SureErr` function. The resulting value can be used in error
 * handling scenarios where a `IdkResult` with an error type is required.
 *
 * @receiver The object to be wrapped as an error result.
 * @return A `SureErr` instance containing the receiver as the error value.
 */
fun <E : IdkErrorType> E.asErrorResult() = IdkErrorResult(this)

/**
 * Converts an IdkError to a Throwable for exception-based error handling.
 *
 * This extension function provides a standardized way to convert IdkError instances
 * to exceptions across the entire codebase, avoiding code duplication.
 *
 * **Conversion Logic:**
 * - If the IdkError has an associated exception, returns that exception
 * - Otherwise, creates an IllegalStateException with the error's default message
 *
 * **Usage:**
 * ```kotlin
 * val result: IdkResult<String, IdkError> = someOperation()
 * if (!result.isOk) throw result.error.toException()
 * ```
 *
 * @receiver The IdkError to convert to an exception
 * @return A Throwable that can be thrown or used in exception-based error handling
 */
fun IdkErrorType.toException(): Throwable =
    when (this) {
        is com.sphereon.core.api.error.IdkError -> this.exception ?: IllegalStateException(this.message.defaultMessage)
        else -> IllegalStateException(this.toString())
    }

/**
 * Create a IdkResult with an Error value.
 */
@Suppress("FunctionName")
@JsExportCompat
fun <E : IdkErrorType> IdkErrorResult(error: E): IdkResult<Nothing, E> = Err(error)

// ========================================
// Result ergonomics helpers
// ========================================

/**
 * Converts a non-null value to [Ok], or a null to [Err] using the provided error factory.
 *
 * Example:
 * ```kotlin
 * val user = repository.findById(id).orErr {
 *     IdkError.NOT_FOUND_ERROR(message = "User not found: $id")
 * }
 * ```
 */
inline fun <T> T?.orErr(error: () -> IdkError): IdkResult<T, IdkError> =
    if (this != null) {
        Ok(this)
    } else {
        Err(error())
    }

/**
 * Validates a condition, returning [Ok] with [Unit] if true, or [Err] if false.
 *
 * Example:
 * ```kotlin
 * ensure(input.name.isNotBlank()) {
 *     IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Name must not be blank")
 * }.getOrElse { return Err(it) }
 * ```
 */
inline fun ensure(
    condition: Boolean,
    error: () -> IdkError,
): IdkResult<Unit, IdkError> =
    if (condition) {
        Ok(Unit)
    } else {
        Err(error())
    }

/**
 * Internal exception used for early return in [idkResult] scope.
 * Not part of the public API — caught by [idkResult] block.
 */
@PublishedApi
internal class IdkResultEarlyReturn(
    val error: IdkErrorType,
) : Exception()

/**
 * Scope for [idkResult] blocks providing [bind] syntax.
 */
class IdkResultScope
    @PublishedApi
    internal constructor() {
        /**
         * Unwraps an [IdkResult], returning the value if [Ok], or short-circuiting
         * the enclosing [idkResult] block with [Err] if this is an error.
         */
        fun <V> IdkResult<V, IdkError>.bind(): V = getOrElse { throw IdkResultEarlyReturn(it) }
    }

/**
 * Scope function for bind()-style result propagation.
 *
 * Provides an Arrow-style `either { }` block where [IdkResult.bind] unwraps
 * success values and short-circuits on first error. The block's return value
 * is wrapped in [Ok].
 *
 * Example:
 * ```kotlin
 * return idkResult {
 *     val tenantId = request.requireTenantId().bind()
 *     val id = request.requirePathParam("id").bind()
 *     val result = service.create(tenantId, id).bind()
 *     jsonResponse(200, json.encodeToString(result))
 * }
 * ```
 *
 * This is equivalent to chaining [getOrElse] with early returns, but produces
 * flatter, more readable code.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <V> idkResult(block: IdkResultScope.() -> V): IdkResult<V, IdkError> =
    try {
        Ok(IdkResultScope().block())
    } catch (e: IdkResultEarlyReturn) {
        @Suppress("UNCHECKED_CAST")
        Err(e.error as IdkError)
    }

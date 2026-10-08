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
 */

package com.sphereon.core.api

import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/*
 * Enhanced builder patterns for creating fluent APIs across all platforms.
 *
 * Provides:
 * - Type-safe builder construction
 * - Fluent API patterns
 * - Cross-platform builder consistency
 * - Validation during construction
 *
 * ## Platform Benefits:
 *
 * ### Android/Kotlin/JVM:
 * ```kotlin
 * val engagement = manager.create {
 *     engagement {
 *         qr { scheme = "mdoc:" }
 *         nfc { enabled = true }
 *     }
 *     retrieval {
 *         ble {
 *             centralClientMode = true
 *             peripheralServerMode = false
 *         }
 *     }
 * }
 * ```
 *
 * ### iOS (Swift via KMP bridges):
 * ```swift
 * let engagement = manager.create { builder in
 *     builder.engagement { engagementBuilder in
 *         engagementBuilder.qr { qrBuilder in
 *             qrBuilder.scheme = "mdoc:"
 *         }
 *     }
 * }
 * ```
 *
 * ### JS/WASM/Native:
 * ```kotlin
 * // Same fluent API works across all platforms
 * const engagement = manager.create(builder => {
 *     builder.engagement(eng => eng.qr(qr => qr.scheme = "mdoc:"))
 * })
 * ```
 */

/**
 * Base interface for all builders in the SDK.
 * Provides common validation and construction patterns.
 *
 * @param T The type that this builder constructs
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IdkBuilder", exact = true)
interface IdkBuilder<T> {
    /**
     * Validates the current builder state.
     *
     * @return List of validation errors, empty if valid
     */
    fun validate(): List<String>

    /**
     * Builds the target object.
     *
     * @return The constructed object
     * @throws IllegalStateException if validation fails
     */
    fun build(): T {
        val errors = validate()
        check(errors.isEmpty()) {
            "Builder validation failed: ${errors.joinToString(", ")}"
        }
        return buildInternal()
    }

    /**
     * Internal build method that subclasses implement.
     * Called only after validation passes.
     */
    fun buildInternal(): T
}

/**
 * Enhanced builder interface that supports fluent configuration.
 *
 * @param T The type being built
 * @param B The builder type (for fluent chaining)
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IdkFluentBuilder")
interface FluentBuilder<T, B : FluentBuilder<T, B>> : IdkBuilder<T> {
    /**
     * Returns the builder instance for fluent chaining.
     */
    @Suppress("UNCHECKED_CAST")
    fun self(): B = this as B
}

/**
 * DSL marker to prevent implicit receivers in nested builder scopes.
 */
@DslMarker
annotation class BuilderDsl

/**
 * Base class for configuration builders that use DSL syntax.
 *
 * @param T The type being configured
 */
@JsExportCompat
@BuilderDsl
abstract class ConfigBuilder<T> : IdkBuilder<T> {
    /**
     * Apply a configuration block to this builder.
     *
     * @param block The configuration block
     * @return This builder for chaining
     */
    fun configure(block: ConfigBuilder<T>.() -> Unit): ConfigBuilder<T> {
        block()
        return this
    }

    /**
     * Default validation allows all configurations.
     * Override to add specific validation rules.
     */
    override fun validate(): List<String> = emptyList()
}

/**
 * Validation result for builder construction.
 */
@JsExportCompat
sealed class BuilderValidationResult {
    object Valid : BuilderValidationResult()

    data class Invalid(
        val errors: List<String>,
    ) : BuilderValidationResult()

    val isValid: Boolean get() = this is Valid
    val isInvalid: Boolean get() = this is Invalid
}

/**
 * Utility extension for creating validation results.
 */
fun List<String>.toValidationResult(): BuilderValidationResult =
    if (isEmpty()) {
        BuilderValidationResult.Valid
    } else {
        BuilderValidationResult.Invalid(this)
    }

/**
 * Extension for safe builder construction with validation.
 * Returns a Result instead of throwing exceptions.
 *
 * @param T The type being built
 * @return IdkResult containing the built object or validation errors
 */
fun <T> IdkBuilder<T>.buildSafely(): IdkResult<T, IdkErrorType> {
    return try {
        val errors = validate()
        if (errors.isNotEmpty()) {
            val errorMessage = "Builder validation failed: ${errors.joinToString(", ")}"
            return IdkResult.err(
                com.sphereon.core.api.error.IdkError
                    .ILLEGAL_ARGUMENT_ERROR(message = errorMessage),
            )
        }
        IdkResult.ok(buildInternal())
    } catch (expected: Exception) {
        IdkResult.err(
            com.sphereon.core.api.error.IdkError
                .UNKNOWN_ERROR(exception = expected),
        )
    }
}

/**
 * Type alias for builder configuration functions.
 * Makes the API more readable and consistent.
 */
typealias BuilderConfig<T> = T.() -> Unit

/**
 * Extension function for applying configuration to any object.
 * Enables fluent configuration patterns.
 *
 * @param T The type being configured
 * @param config The configuration function
 * @return The configured object
 */
inline fun <T> T.applyConfig(config: BuilderConfig<T>): T = apply(config)

/**
 * Extension function for conditional configuration.
 * Only applies the configuration if the condition is true.
 *
 * @param T The type being configured
 * @param condition The condition to check
 * @param config The configuration function
 * @return The (possibly) configured object
 */
inline fun <T> T.applyConfigIf(
    condition: Boolean,
    config: BuilderConfig<T>,
): T =
    if (condition) {
        apply(config)
    } else {
        this
    }

/**
 * Extension function for conditional configuration based on a predicate.
 *
 * @param T The type being configured
 * @param predicate The predicate function
 * @param config The configuration function
 * @return The (possibly) configured object
 */
inline fun <T> T.applyConfigIf(
    predicate: (T) -> Boolean,
    config: BuilderConfig<T>,
): T =
    if (predicate(this)) {
        apply(config)
    } else {
        this
    }

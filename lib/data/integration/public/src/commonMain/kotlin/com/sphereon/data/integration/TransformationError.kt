/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.data.integration

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import kotlin.jvm.JvmOverloads

/**
 * The shape a deterministic transformation path ran into that it cannot address.
 *
 * The deterministic path syntax addresses object fields only. Both values name a shape the
 * executor refuses to traverse rather than silently skip.
 */
enum class TransformationPathShape {
    /** A path segment tried to descend into a JSON array. Array addressing is not part of v1 path syntax. */
    ARRAY_TRAVERSAL,

    /** A parent segment exists but holds a non-object (a primitive or JSON null), so it cannot be descended into. */
    NON_OBJECT_PARENT,
}

/**
 * Typed failures the deterministic transformation executor raises for a single step. Each case names
 * the step index and the offending path so a caller can pinpoint the misconfigured step, while the
 * stable [code] carries no payload content and is safe to persist or surface across a transport.
 *
 * Wrap a case into an [IdkError] with [asError] when returning from an `IdkResult` boundary; recover
 * the typed shape downstream with `IdkError.sourceAs<TransformationError>()`.
 */
sealed interface TransformationError : IdkErrorType {
    val stepIndex: Int
    val path: String

    fun asError(): IdkError = IdkError.fromDTO(this)

    /**
     * A source or target path descended into a shape the deterministic path syntax cannot address.
     * Raised for every deterministic step regardless of an `allowMissing` flag: an unaddressable shape
     * is a configuration error, never a tolerated absence.
     */
    data class UnsupportedPathShape
        @JvmOverloads
        constructor(
            override val stepIndex: Int,
            override val path: String,
            val fieldName: String,
            val shape: TransformationPathShape,
            override val code: String = "data.transformation.unsupported_path_shape",
            override val message: IdkError.Message =
                IdkError.Message(
                    i18nKey = "data.transformation.error.unsupported_path_shape",
                    i18nParams = mapOf("stepIndex" to stepIndex, "path" to path, "field" to fieldName, "shape" to shape.name),
                    defaultMessage =
                        "Transformation step $stepIndex '$fieldName' path '$path' cannot be addressed: ${shape.name}. " +
                            "The deterministic path syntax addresses object fields only.",
                ),
            override val severity: IdkError.Severity = IdkError.Severity.ERROR,
            override val exception: Throwable? = null,
            override val causes: List<IdkErrorType> = emptyList(),
            override val meta: Map<String, Any?> =
                mapOf("stepIndex" to stepIndex, "path" to path, "field" to fieldName, "shape" to shape.name),
        ) : TransformationError {
            override val category: ErrorCategory get() = ErrorCategory.VALIDATION
        }

    /**
     * A step required a value at [path] whose parent existed but whose leaf was absent, and the step did
     * not opt in to tolerating the absence. Distinct from [UnsupportedPathShape]: the shape was
     * addressable, the value simply was not there.
     */
    data class PathNotFound
        @JvmOverloads
        constructor(
            override val stepIndex: Int,
            override val path: String,
            val fieldName: String,
            override val code: String = "data.transformation.path_not_found",
            override val message: IdkError.Message =
                IdkError.Message(
                    i18nKey = "data.transformation.error.path_not_found",
                    i18nParams = mapOf("stepIndex" to stepIndex, "path" to path, "field" to fieldName),
                    defaultMessage =
                        "Transformation step $stepIndex '$fieldName' path '$path' was not found. " +
                            "Set allowMissing on the step to tolerate an absent value.",
                ),
            override val severity: IdkError.Severity = IdkError.Severity.ERROR,
            override val exception: Throwable? = null,
            override val causes: List<IdkErrorType> = emptyList(),
            override val meta: Map<String, Any?> =
                mapOf("stepIndex" to stepIndex, "path" to path, "field" to fieldName),
        ) : TransformationError {
            override val category: ErrorCategory get() = ErrorCategory.VALIDATION
        }

    /**
     * A REQUIRE step found its path missing, null, or blank. The value is mandatory and no flag relaxes it.
     */
    data class RequiredPathMissing
        @JvmOverloads
        constructor(
            override val stepIndex: Int,
            override val path: String,
            override val code: String = "data.transformation.required_path_missing",
            override val message: IdkError.Message =
                IdkError.Message(
                    i18nKey = "data.transformation.error.required_path_missing",
                    i18nParams = mapOf("stepIndex" to stepIndex, "path" to path),
                    defaultMessage = "Required transformation path '$path' is missing or blank at step $stepIndex.",
                ),
            override val severity: IdkError.Severity = IdkError.Severity.ERROR,
            override val exception: Throwable? = null,
            override val causes: List<IdkErrorType> = emptyList(),
            override val meta: Map<String, Any?> = mapOf("stepIndex" to stepIndex, "path" to path),
        ) : TransformationError {
            override val category: ErrorCategory get() = ErrorCategory.VALIDATION
        }
}

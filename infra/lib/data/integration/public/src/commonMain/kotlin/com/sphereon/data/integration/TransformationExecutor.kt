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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

interface TransformationExecutor {
    fun execute(request: TransformationExecutionRequest): IdkResult<TransformationExecutionResult, IdkError>
}

@JsExportCompat
@Serializable
data class TransformationExecutionRequest(
    @SerialName("definition")
    val definition: TransformationDefinition,
    @SerialName("input")
    val input: JsonElement,
    @SerialName("lookups")
    val lookups: Map<String, JsonElement> = emptyMap(),
    @SerialName("copyInput")
    val copyInput: Boolean = false,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)

@JsExportCompat
@Serializable
data class TransformationExecutionResult(
    @SerialName("output")
    val output: JsonElement,
    @SerialName("appliedSteps")
    val appliedSteps: Int,
    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Applies the deterministic transformation steps (MAP, CONSTANT, DEFAULT, REQUIRE, REDACT, LOOKUP) over
 * a JSON document.
 *
 * ## Path syntax
 *
 * Paths are dot-separated object field names, with an optional leading `$` or `$.` root marker
 * (`employee.contact.email`, `$.employee.status`). The syntax addresses JSON object fields only. Arrays
 * are out of scope for v1: a path that descends into a JSON array, or through a parent that exists but
 * holds a non-object value, fails the transformation with a [TransformationError.UnsupportedPathShape]
 * naming the step and path. This is a deliberate loud failure, not a silent skip, because a REDACT that
 * quietly no-ops on a shape it cannot address would let sensitive values pass through untransformed.
 *
 * ## Missing values
 *
 * A path whose parent is addressable but whose leaf is absent fails with
 * [TransformationError.PathNotFound] for MAP (source) and REDACT (target) unless the step sets
 * [TransformationStep.allowMissing], in which case the step is a no-op. DEFAULT exists to supply an
 * absent value and so treats a missing or null leaf as its trigger; REQUIRE always fails on a missing,
 * null, or blank value.
 */
object DeterministicTransformationExecutor : TransformationExecutor {
    override fun execute(request: TransformationExecutionRequest): IdkResult<TransformationExecutionResult, IdkError> {
        if (request.definition.language !in supportedLanguages) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Transformation language '${request.definition.language}' is not supported by the deterministic executor.",
                ),
            )
        }

        var output =
            if (request.copyInput) {
                request.input.asObjectOrEmpty()
            } else {
                JsonObject(emptyMap())
            }

        request.definition.steps.forEachIndexed { index, step ->
            output =
                when (step.stepType) {
                    TransformationStepType.MAP -> {
                        val sourcePath = step.sourcePath.requiredPath(index, "sourcePath").getOrElse { return Err(it) }
                        val targetPath = step.targetPath.requiredPath(index, "targetPath").getOrElse { return Err(it) }
                        when (val read = request.input.readPathStrict(sourcePath, index, "sourcePath").getOrElse { return Err(it) }) {
                            is PathRead.Found -> {
                                output.writePathStrict(targetPath, read.value, index, "targetPath").getOrElse { return Err(it) }
                            }

                            PathRead.Missing -> {
                                if (step.allowMissing) {
                                    output
                                } else {
                                    return Err(TransformationError.PathNotFound(index, sourcePath, "sourcePath").asError())
                                }
                            }
                        }
                    }

                    TransformationStepType.CONSTANT -> {
                        val targetPath = step.targetPath.requiredPath(index, "targetPath").getOrElse { return Err(it) }
                        val value = step.value ?: JsonNull
                        output.writePathStrict(targetPath, value, index, "targetPath").getOrElse { return Err(it) }
                    }

                    TransformationStepType.DEFAULT -> {
                        val targetPath = step.targetPath.requiredPath(index, "targetPath").getOrElse { return Err(it) }
                        val read = output.readPathStrict(targetPath, index, "targetPath").getOrElse { return Err(it) }
                        if (read.isMissingOrNull()) {
                            output.writePathStrict(targetPath, step.value ?: JsonNull, index, "targetPath").getOrElse { return Err(it) }
                        } else {
                            output
                        }
                    }

                    TransformationStepType.REQUIRE -> {
                        val sourcePath =
                            (step.sourcePath ?: step.targetPath)
                                .requiredPath(index, "sourcePath or targetPath")
                                .getOrElse { return Err(it) }
                        val value = (request.input.readPathStrict(sourcePath, index, "sourcePath").getOrElse { return Err(it) } as? PathRead.Found)?.value
                        if (value.isMissingOrNull() || value.isBlankString()) {
                            return Err(TransformationError.RequiredPathMissing(index, sourcePath).asError())
                        }
                        output
                    }

                    TransformationStepType.REDACT -> {
                        val targetPath =
                            (step.targetPath ?: step.sourcePath)
                                .requiredPath(index, "targetPath or sourcePath")
                                .getOrElse { return Err(it) }
                        when (output.readPathStrict(targetPath, index, "targetPath").getOrElse { return Err(it) }) {
                            is PathRead.Found -> {
                                val writeResult =
                                    output.writePathStrict(
                                        targetPath,
                                        JsonPrimitive(step.metadata["replacement"] ?: "[REDACTED]"),
                                        index,
                                        "targetPath",
                                    )
                                writeResult.getOrElse { return Err(it) }
                            }

                            PathRead.Missing -> {
                                if (step.allowMissing) {
                                    output
                                } else {
                                    return Err(TransformationError.PathNotFound(index, targetPath, "targetPath").asError())
                                }
                            }
                        }
                    }

                    TransformationStepType.LOOKUP -> {
                        val lookupKey =
                            (step.expression ?: step.sourcePath)
                                .requiredPath(index, "expression or sourcePath")
                                .getOrElse { return Err(it) }
                        val targetPath = step.targetPath.requiredPath(index, "targetPath").getOrElse { return Err(it) }
                        val value = request.lookups[lookupKey]
                        if (value == null) {
                            output
                        } else {
                            output.writePathStrict(targetPath, value, index, "targetPath").getOrElse { return Err(it) }
                        }
                    }

                    TransformationStepType.HASH,
                    TransformationStepType.CUSTOM,
                    -> {
                        return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Transformation step type '${step.stepType}' requires a dedicated executor.",
                            ),
                        )
                    }
                }
        }

        return Ok(
            TransformationExecutionResult(
                output = output,
                appliedSteps = request.definition.steps.size,
                metadata =
                    mapOf(
                        "transformationId" to request.definition.transformationId.value,
                        "executor" to "deterministic",
                    ),
            ),
        )
    }

    private val supportedLanguages: Set<TransformationLanguage> =
        setOf(
            TransformationLanguage.ATTRIBUTE_MAPPER,
            TransformationLanguage.CUSTOM,
        )

    private fun String?.requiredPath(
        stepIndex: Int,
        fieldName: String,
    ): IdkResult<String, IdkError> =
        this?.takeIf { it.isNotBlank() }?.let { Ok(it) }
            ?: Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Transformation step $stepIndex requires '$fieldName'.",
                ),
            )

    private fun JsonElement.asObjectOrEmpty(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

    /**
     * Outcome of reading a deterministic path: the leaf was present ([Found], possibly holding
     * [JsonNull]) or its parent existed and was an object but the leaf key was absent ([Missing]).
     * An unaddressable path shape is never modelled here; it fails the read with a typed error.
     */
    private sealed interface PathRead {
        data class Found(
            val value: JsonElement,
        ) : PathRead

        object Missing : PathRead
    }

    /**
     * Walks [path] over object fields only. Descending into a JSON array or any non-object parent fails
     * with a [TransformationError.UnsupportedPathShape]; an absent key along an addressable object path
     * yields [PathRead.Missing]. Callers decide whether a missing leaf is tolerable.
     */
    private fun JsonElement.readPathStrict(
        path: String,
        stepIndex: Int,
        fieldName: String,
    ): IdkResult<PathRead, IdkError> {
        val segments = path.toSegments()
        if (segments.isEmpty()) {
            return Ok(PathRead.Found(this))
        }
        var container: JsonElement = this
        segments.forEach { segment ->
            val obj = container as? JsonObject ?: return Err(container.unsupportedPathShape(stepIndex, path, fieldName))
            container = obj[segment] ?: return Ok(PathRead.Missing)
        }
        return Ok(PathRead.Found(container))
    }

    /**
     * Writes [value] at [path], creating intermediate objects only for genuinely absent keys. A parent
     * segment that already exists as a JSON array or any other non-object fails with a
     * [TransformationError.UnsupportedPathShape] rather than silently clobbering it.
     */
    private fun JsonObject.writePathStrict(
        path: String,
        value: JsonElement,
        stepIndex: Int,
        fieldName: String,
    ): IdkResult<JsonObject, IdkError> {
        val segments = path.toSegments()
        if (segments.isEmpty()) {
            return Ok(value as? JsonObject ?: this)
        }
        return writeSegmentsStrict(segments, value, path, stepIndex, fieldName)
    }

    private fun JsonObject.writeSegmentsStrict(
        segments: List<String>,
        value: JsonElement,
        path: String,
        stepIndex: Int,
        fieldName: String,
    ): IdkResult<JsonObject, IdkError> {
        val key = segments.first()
        val mutable = toMutableMap()
        if (segments.size == 1) {
            mutable[key] = value
            return Ok(JsonObject(mutable))
        }
        val existing = mutable[key]
        val child =
            when {
                !mutable.containsKey(key) -> JsonObject(emptyMap())
                existing is JsonObject -> existing
                else -> return Err((existing as JsonElement).unsupportedPathShape(stepIndex, path, fieldName))
            }
        val updated = child.writeSegmentsStrict(segments.drop(1), value, path, stepIndex, fieldName).getOrElse { return Err(it) }
        mutable[key] = updated
        return Ok(JsonObject(mutable))
    }

    private fun JsonElement.unsupportedPathShape(
        stepIndex: Int,
        path: String,
        fieldName: String,
    ): IdkError {
        val error =
            TransformationError.UnsupportedPathShape(
                stepIndex = stepIndex,
                path = path,
                fieldName = fieldName,
                shape = if (this is JsonArray) TransformationPathShape.ARRAY_TRAVERSAL else TransformationPathShape.NON_OBJECT_PARENT,
            )
        return error.asError()
    }

    private fun String.toSegments(): List<String> =
        trim()
            .removePrefix("$.")
            .removePrefix("$")
            .split('.')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun PathRead.isMissingOrNull(): Boolean = this is PathRead.Missing || (this as? PathRead.Found)?.value == JsonNull

    private fun JsonElement?.isMissingOrNull(): Boolean = this == null || this == JsonNull

    private fun JsonElement?.isBlankString(): Boolean = (this as? JsonPrimitive)?.contentOrNull?.isBlank() == true
}

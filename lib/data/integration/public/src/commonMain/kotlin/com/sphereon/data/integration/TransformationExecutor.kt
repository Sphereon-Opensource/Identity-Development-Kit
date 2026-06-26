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
                        val value = request.input.readPath(sourcePath)
                        if (value == null) {
                            output
                        } else {
                            output.writePath(targetPath, value)
                        }
                    }

                    TransformationStepType.CONSTANT -> {
                        val targetPath = step.targetPath.requiredPath(index, "targetPath").getOrElse { return Err(it) }
                        val value = step.value ?: JsonNull
                        output.writePath(targetPath, value)
                    }

                    TransformationStepType.DEFAULT -> {
                        val targetPath = step.targetPath.requiredPath(index, "targetPath").getOrElse { return Err(it) }
                        if (output.readPath(targetPath).isMissingOrNull()) {
                            output.writePath(targetPath, step.value ?: JsonNull)
                        } else {
                            output
                        }
                    }

                    TransformationStepType.REQUIRE -> {
                        val sourcePath =
                            (step.sourcePath ?: step.targetPath)
                                .requiredPath(index, "sourcePath or targetPath")
                                .getOrElse { return Err(it) }
                        val value = request.input.readPath(sourcePath)
                        if (value.isMissingOrNull() || value.isBlankString()) {
                            return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message = "Required transformation path '$sourcePath' is missing or blank at step $index.",
                                ),
                            )
                        }
                        output
                    }

                    TransformationStepType.REDACT -> {
                        val targetPath =
                            (step.targetPath ?: step.sourcePath)
                                .requiredPath(index, "targetPath or sourcePath")
                                .getOrElse { return Err(it) }
                        output.writePath(targetPath, JsonPrimitive(step.metadata["replacement"] ?: "[REDACTED]"))
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
                            output.writePath(targetPath, value)
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

    private fun JsonElement.readPath(path: String): JsonElement? {
        val segments = path.toSegments()
        if (segments.isEmpty()) {
            return this
        }
        var current: JsonElement = this
        segments.forEach { segment ->
            current = (current as? JsonObject)?.get(segment) ?: return null
        }
        return current
    }

    private fun JsonObject.writePath(
        path: String,
        value: JsonElement,
    ): JsonObject {
        val segments = path.toSegments()
        if (segments.isEmpty()) {
            return value as? JsonObject ?: this
        }
        return writeSegments(segments, value)
    }

    private fun JsonObject.writeSegments(
        segments: List<String>,
        value: JsonElement,
    ): JsonObject {
        val key = segments.first()
        val mutable = toMutableMap()
        if (segments.size == 1) {
            mutable[key] = value
            return JsonObject(mutable)
        }

        val child = mutable[key] as? JsonObject ?: JsonObject(emptyMap())
        mutable[key] = child.writeSegments(segments.drop(1), value)
        return JsonObject(mutable)
    }

    private fun String.toSegments(): List<String> =
        trim()
            .removePrefix("$.")
            .removePrefix("$")
            .split('.')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun JsonElement?.isMissingOrNull(): Boolean = this == null || this == JsonNull

    private fun JsonElement?.isBlankString(): Boolean = (this as? JsonPrimitive)?.contentOrNull?.isBlank() == true
}

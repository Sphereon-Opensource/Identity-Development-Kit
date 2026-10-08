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

import com.sphereon.core.api.error.sourceAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransformationExecutorTest {
    @Test
    fun mapsConstantsDefaultsLookupsAndRedactions() {
        val definition =
            TransformationDefinition(
                transformationId = TransformationId("employee-csv-to-semantic"),
                displayName = "Employee CSV to semantic",
                language = TransformationLanguage.ATTRIBUTE_MAPPER,
                steps =
                    listOf(
                        TransformationStep(
                            stepType = TransformationStepType.MAP,
                            sourcePath = "email",
                            targetPath = "employee.contact.email",
                        ),
                        TransformationStep(
                            stepType = TransformationStepType.CONSTANT,
                            targetPath = "employee.status",
                            value = JsonPrimitive("active"),
                        ),
                        TransformationStep(
                            stepType = TransformationStepType.DEFAULT,
                            targetPath = "employee.country",
                            value = JsonPrimitive("NL"),
                        ),
                        TransformationStep(
                            stepType = TransformationStepType.LOOKUP,
                            expression = "licenseType",
                            targetPath = "license.type",
                        ),
                        // REDACT now requires an addressable present value; establish it before redacting.
                        TransformationStep(
                            stepType = TransformationStepType.CONSTANT,
                            targetPath = "employee.ssn",
                            value = JsonPrimitive("123-45-6789"),
                        ),
                        TransformationStep(
                            stepType = TransformationStepType.REDACT,
                            targetPath = "employee.ssn",
                        ),
                    ),
            )

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(
                    definition = definition,
                    input =
                        buildJsonObject {
                            put("email", JsonPrimitive("employee@example.test"))
                        },
                    lookups = mapOf("licenseType" to JsonPrimitive("A")),
                ),
            )

        assertTrue(result.isOk)
        val output = result.value.output.jsonObject
        assertEquals(
            JsonPrimitive("employee@example.test"),
            output["employee"]!!.jsonObject["contact"]!!.jsonObject["email"],
        )
        assertEquals(JsonPrimitive("active"), output["employee"]!!.jsonObject["status"])
        assertEquals(JsonPrimitive("NL"), output["employee"]!!.jsonObject["country"])
        assertEquals(JsonPrimitive("A"), output["license"]!!.jsonObject["type"])
        assertEquals(JsonPrimitive("[REDACTED]"), output["employee"]!!.jsonObject["ssn"])
    }

    @Test
    fun failsWhenRequiredPathIsMissing() {
        val definition =
            TransformationDefinition(
                transformationId = TransformationId("required-email"),
                displayName = "Required email",
                language = TransformationLanguage.ATTRIBUTE_MAPPER,
                steps =
                    listOf(
                        TransformationStep(
                            stepType = TransformationStepType.REQUIRE,
                            sourcePath = "email",
                        ),
                    ),
            )

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(
                    definition = definition,
                    input =
                        buildJsonObject {
                            put("name", JsonPrimitive("Jane"))
                        },
                ),
            )

        assertTrue(result.isErr)
        assertEquals("data.transformation.required_path_missing", result.error.code)
        val typed = result.error.sourceAs<TransformationError.RequiredPathMissing>()
        assertTrue(typed != null)
        assertEquals(0, typed.stepIndex)
        assertEquals("email", typed.path)
    }

    @Test
    fun failsForDedicatedStepTypes() {
        val definition =
            TransformationDefinition(
                transformationId = TransformationId("hash-email"),
                displayName = "Hash email",
                language = TransformationLanguage.ATTRIBUTE_MAPPER,
                steps =
                    listOf(
                        TransformationStep(
                            stepType = TransformationStepType.HASH,
                            sourcePath = "email",
                            targetPath = "emailHash",
                        ),
                    ),
            )

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(
                    definition = definition,
                    input =
                        buildJsonObject {
                            put("email", JsonPrimitive("employee@example.test"))
                        },
                ),
            )

        assertTrue(result.isErr)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
    }

    @Test
    fun mapSourceDescendingIntoArrayFailsWithTypedError() {
        val definition = singleStep(TransformationStepType.MAP, sourcePath = "people.ssn", targetPath = "ssn")

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(
                    definition = definition,
                    input =
                        buildJsonObject {
                            put(
                                "people",
                                JsonArray(listOf(buildJsonObject { put("ssn", JsonPrimitive("123-45-6789")) })),
                            )
                        },
                ),
            )

        assertTrue(result.isErr)
        assertEquals("data.transformation.unsupported_path_shape", result.error.code)
        val typed = result.error.sourceAs<TransformationError.UnsupportedPathShape>()
        assertTrue(typed != null)
        assertEquals(TransformationPathShape.ARRAY_TRAVERSAL, typed.shape)
        assertEquals("people.ssn", typed.path)
        assertEquals("sourcePath", typed.fieldName)
    }

    @Test
    fun redactTargetDescendingIntoArrayFailsWithTypedError() {
        val definition = singleStep(TransformationStepType.REDACT, targetPath = "people.ssn")

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(
                    definition = definition,
                    copyInput = true,
                    input =
                        buildJsonObject {
                            put(
                                "people",
                                JsonArray(listOf(buildJsonObject { put("ssn", JsonPrimitive("123-45-6789")) })),
                            )
                        },
                ),
            )

        assertTrue(result.isErr)
        assertEquals("data.transformation.unsupported_path_shape", result.error.code)
        assertEquals(
            TransformationPathShape.ARRAY_TRAVERSAL,
            result.error.sourceAs<TransformationError.UnsupportedPathShape>()!!.shape,
        )
    }

    @Test
    fun mapSourceThroughNonObjectParentFailsWithTypedError() {
        val definition = singleStep(TransformationStepType.MAP, sourcePath = "ssn.value", targetPath = "ssn")

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(
                    definition = definition,
                    input = buildJsonObject { put("ssn", JsonPrimitive("123-45-6789")) },
                ),
            )

        assertTrue(result.isErr)
        assertEquals("data.transformation.unsupported_path_shape", result.error.code)
        assertEquals(
            TransformationPathShape.NON_OBJECT_PARENT,
            result.error.sourceAs<TransformationError.UnsupportedPathShape>()!!.shape,
        )
    }

    @Test
    fun constantTargetThroughNonObjectParentFailsWithTypedError() {
        val definition = singleStep(TransformationStepType.CONSTANT, targetPath = "ssn.value", value = JsonPrimitive("x"))

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(
                    definition = definition,
                    copyInput = true,
                    input = buildJsonObject { put("ssn", JsonPrimitive("123-45-6789")) },
                ),
            )

        assertTrue(result.isErr)
        assertEquals("data.transformation.unsupported_path_shape", result.error.code)
        assertEquals(
            TransformationPathShape.NON_OBJECT_PARENT,
            result.error.sourceAs<TransformationError.UnsupportedPathShape>()!!.shape,
        )
    }

    @Test
    fun redactMissingLeafFailsByDefault() {
        val definition =
            TransformationDefinition(
                transformationId = TransformationId("redact-missing"),
                displayName = "Redact missing",
                language = TransformationLanguage.ATTRIBUTE_MAPPER,
                steps =
                    listOf(
                        TransformationStep(
                            stepType = TransformationStepType.CONSTANT,
                            targetPath = "employee.name",
                            value = JsonPrimitive("Jane"),
                        ),
                        TransformationStep(
                            stepType = TransformationStepType.REDACT,
                            targetPath = "employee.ssn",
                        ),
                    ),
            )

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(definition = definition, input = buildJsonObject {}),
            )

        assertTrue(result.isErr)
        assertEquals("data.transformation.path_not_found", result.error.code)
        val typed = result.error.sourceAs<TransformationError.PathNotFound>()
        assertTrue(typed != null)
        assertEquals("employee.ssn", typed.path)
        assertEquals("targetPath", typed.fieldName)
    }

    @Test
    fun redactMissingLeafIsNoOpWhenAllowMissing() {
        val definition =
            TransformationDefinition(
                transformationId = TransformationId("redact-missing-allowed"),
                displayName = "Redact missing allowed",
                language = TransformationLanguage.ATTRIBUTE_MAPPER,
                steps =
                    listOf(
                        TransformationStep(
                            stepType = TransformationStepType.CONSTANT,
                            targetPath = "employee.name",
                            value = JsonPrimitive("Jane"),
                        ),
                        TransformationStep(
                            stepType = TransformationStepType.REDACT,
                            targetPath = "employee.ssn",
                            allowMissing = true,
                        ),
                    ),
            )

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(definition = definition, input = buildJsonObject {}),
            )

        assertTrue(result.isOk)
        val output = result.value.output.jsonObject
        val employee = output["employee"]!!.jsonObject
        assertEquals(JsonPrimitive("Jane"), employee["name"])
        assertNull(employee["ssn"])
    }

    @Test
    fun mapMissingSourceFailsByDefault() {
        val definition = singleStep(TransformationStepType.MAP, sourcePath = "email", targetPath = "employee.email")

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(
                    definition = definition,
                    input = buildJsonObject { put("name", JsonPrimitive("Jane")) },
                ),
            )

        assertTrue(result.isErr)
        assertEquals("data.transformation.path_not_found", result.error.code)
        assertEquals(
            "email",
            result.error
                .sourceAs<TransformationError.PathNotFound>()!!
                .path,
        )
    }

    @Test
    fun mapMissingSourceIsNoOpWhenAllowMissing() {
        val definition =
            singleStep(TransformationStepType.MAP, sourcePath = "email", targetPath = "employee.email", allowMissing = true)

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(
                    definition = definition,
                    input = buildJsonObject { put("name", JsonPrimitive("Jane")) },
                ),
            )

        assertTrue(result.isOk)
        assertNull(result.value.output.jsonObject["employee"])
    }

    @Test
    fun mapPresentNullSourceIsWrittenNotTreatedAsMissing() {
        val definition = singleStep(TransformationStepType.MAP, sourcePath = "email", targetPath = "employee.email")

        val result =
            DeterministicTransformationExecutor.execute(
                TransformationExecutionRequest(
                    definition = definition,
                    input = buildJsonObject { put("email", JsonNull) },
                ),
            )

        assertTrue(result.isOk)
        assertEquals(
            JsonNull,
            result.value.output.jsonObject["employee"]!!
                .jsonObject["email"],
        )
    }

    private fun singleStep(
        stepType: TransformationStepType,
        sourcePath: String? = null,
        targetPath: String? = null,
        value: JsonPrimitive? = null,
        allowMissing: Boolean = false,
    ): TransformationDefinition =
        TransformationDefinition(
            transformationId = TransformationId("single-step"),
            displayName = "Single step",
            language = TransformationLanguage.ATTRIBUTE_MAPPER,
            steps =
                listOf(
                    TransformationStep(
                        stepType = stepType,
                        sourcePath = sourcePath,
                        targetPath = targetPath,
                        value = value,
                        allowMissing = allowMissing,
                    ),
                ),
        )
}

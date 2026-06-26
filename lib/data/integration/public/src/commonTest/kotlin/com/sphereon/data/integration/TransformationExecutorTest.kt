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

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
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
}

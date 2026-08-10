/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

import com.sphereon.oidf.conformance.OidfPlan
import com.sphereon.oidf.conformance.OidfPlanModule
import com.sphereon.oidf.conformance.OidfPlanScenario
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

@Serializable
internal data class OidfVerifierModuleExecution(
    val scenarioId: String,
    val scenarioKey: String,
    val planId: String,
    val moduleName: String,
    val moduleVariant: Map<String, String>,
    val testId: String? = null,
    val waitState: String? = null,
    val status: String? = null,
    val result: String? = null,
    val productStatus: String? = null,
    val productErrorCode: String? = null,
    val expectedProductStatus: String,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val passed: Boolean,
    val error: String? = null,
)

/** Executes one discovered verifier module and always writes a terminal JSON record. */
internal class OidfVerifierModuleExecutor(
    private val stack: OidfProductStack,
) {
    private val json = Json { prettyPrint = true }

    fun execute(
        scenario: OidfPlanScenario,
        plan: OidfPlan,
        module: OidfPlanModule,
        evidenceDirectory: Path,
    ): OidfVerifierModuleExecution {
        Files.createDirectories(evidenceDirectory)
        val startedAt = System.currentTimeMillis()
        val scenarioKey = module.scenarioKey(plan.name, plan.selectionVariant)
        val expectedProductStatus = expectedProductStatus(module.testModule)
        var testId: String? = null
        var waitState: String? = null
        var status: String? = null
        var result: String? = null
        var productStatus: String? = null
        var productErrorCode: String? = null
        var executionError: String? = null

        try {
            val started =
                stack.suite.startModule(
                    testName = module.testModule,
                    planId = plan.id,
                    variant = module.variant.toJsonObject(),
                )
            testId = started.id
            val initial =
                stack.suite.waitForState(
                    testId = started.id,
                    states = listOf("WAITING", "FINISHED", "INTERRUPTED"),
                    timeoutMs = 30_000,
                )
            require(!initial.timeout) { "OIDF verifier module did not reach WAITING" }
            require(initial.state == "WAITING") {
                "OIDF verifier module became ${initial.state} before the VDX request was delivered"
            }

            val productResult =
                OidfVerifierWalletDriver(stack, evidenceDirectory).complete(
                    testId = started.id,
                    scenario = scenario,
                    moduleName = module.testModule,
                )
            Files.writeString(
                evidenceDirectory.resolve("verifier-result.json"),
                json.encodeToString(JsonElement.serializer(), productResult),
            )
            productStatus = productResult["status"]?.jsonPrimitive?.content
            productErrorCode =
                (productResult["error"] as? kotlinx.serialization.json.JsonObject)
                    ?.get("code")
                    ?.jsonPrimitive
                    ?.content
            if (productStatus != expectedProductStatus) {
                executionError =
                    "Expected VDX product status '$expectedProductStatus' for ${module.testModule}, got '$productStatus'"
            }

            val wait =
                stack.suite.waitForState(
                    testId = started.id,
                    states = listOf("FINISHED", "INTERRUPTED"),
                    timeoutMs = 120_000,
                )
            waitState = wait.state
            require(!wait.timeout) { "OIDF verifier module timed out" }
            val info = stack.suite.info(started.id)
            status = info.status
            result = info.result
        } catch (error: Throwable) {
            executionError = listOfNotNull(executionError, error.stackTraceToString()).joinToString("\n")
            testId?.let { id ->
                runCatching { stack.suite.info(id) }
                    .getOrNull()
                    ?.let { info ->
                        status = info.status
                        result = info.result
                    }
            }
        }

        val execution =
            OidfVerifierModuleExecution(
                scenarioId = scenario.id,
                scenarioKey = scenarioKey,
                planId = plan.id,
                moduleName = module.testModule,
                moduleVariant = module.variant.toSortedMap(),
                testId = testId,
                waitState = waitState,
                status = status,
                result = result,
                productStatus = productStatus,
                productErrorCode = productErrorCode,
                expectedProductStatus = expectedProductStatus,
                startedAtEpochMs = startedAt,
                durationMs = System.currentTimeMillis() - startedAt,
                passed =
                    executionError == null &&
                        waitState == "FINISHED" &&
                        status == "FINISHED" &&
                        result == "PASSED",
                error = executionError,
            )
        Files.writeString(
            evidenceDirectory.resolve("result.json"),
            json.encodeToString(OidfVerifierModuleExecution.serializer(), execution),
        )
        return execution
    }

    private fun expectedProductStatus(moduleName: String): String =
        if (moduleName.contains("-invalid-") || moduleName.contains("-iat-in-")) {
            "error"
        } else {
            "authorization_response_verified"
        }
}

private fun Map<String, String>.toJsonObject() =
    kotlinx.serialization.json.JsonObject(mapValues { (_, value) -> kotlinx.serialization.json.JsonPrimitive(value) })

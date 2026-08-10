/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.wallet.runner.oidf

import com.sphereon.oidf.conformance.OidfConformanceSuiteClient
import com.sphereon.oidf.conformance.OidfPlan
import com.sphereon.oidf.conformance.OidfPlanModule
import com.sphereon.oidf.conformance.OidfPlanScenario
import com.sphereon.oidf.conformance.OidfSuiteConfigPreprocessor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

@Serializable
internal data class OidfWalletModuleExecution(
    val scenarioId: String,
    val scenarioKey: String,
    val planId: String,
    val moduleName: String,
    val moduleVariant: Map<String, String>,
    val testId: String? = null,
    val status: String? = null,
    val result: String? = null,
    val walletStatus: String? = null,
    val walletTerminal: Boolean? = null,
    val walletSucceeded: Boolean? = null,
    val walletErrorCode: String? = null,
    val walletErrorArguments: Map<String, String> = emptyMap(),
    val expectedWalletOutcome: String,
    val walletOutcomeMatched: Boolean,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val passed: Boolean,
    val error: String? = null,
)

/** Executes one discovered wallet module and always leaves a machine-readable result. */
internal class OidfWalletModuleExecutor(
    private val suite: OidfConformanceSuiteClient,
    private val configPreprocessor: OidfSuiteConfigPreprocessor,
    private val haip: Boolean,
    private val haipAttestationFixture: OidfHaipWalletAttestationFixture? = null,
) {
    private val json = Json { prettyPrint = true }
    private val harness =
        OidfWalletConformanceHarness(
            suite = suite,
            configPreprocessor = configPreprocessor,
            profileId = "default",
            walletUnitId = "oidf-wallet-conformance",
            haipAttestationFixture = haipAttestationFixture,
        )

    suspend fun execute(
        scenario: OidfPlanScenario,
        plan: OidfPlan,
        module: OidfPlanModule,
        evidenceDirectory: Path,
    ): OidfWalletModuleExecution {
        Files.createDirectories(evidenceDirectory)
        val startedAt = System.currentTimeMillis()
        val scenarioKey = module.scenarioKey(plan.name, plan.selectionVariant)
        var testId: String? = null
        var status: String? = null
        var result: String? = null
        var walletStatus: String? = null
        var walletTerminal: Boolean? = null
        var walletSucceeded: Boolean? = null
        var walletErrorCode: String? = null
        var walletErrorArguments: Map<String, String> = emptyMap()
        var executionError: String? = null
        val expectsRejection = module.testModule in EXPECTED_REJECTION_MODULES

        try {
            val harnessResult =
                harness.runModule(
                    plan = plan,
                    module = module,
                    haip = haip,
                    evidenceDirectory = evidenceDirectory,
                ) { startedId -> testId = startedId }
            testId = harnessResult.testId
            status = harnessResult.suiteStatus
            result = harnessResult.suiteResult
            walletStatus = harnessResult.walletOutcome.status.name
            walletTerminal = harnessResult.walletOutcome.terminal
            walletSucceeded = harnessResult.walletOutcome.succeeded
            walletErrorCode = harnessResult.walletOutcome.error?.code
            walletErrorArguments = harnessResult.walletOutcome.error?.arguments.orEmpty()
        } catch (error: Throwable) {
            executionError = error.stackTraceToString()
            testId?.let { id ->
                runCatching { suite.info(id) }
                    .getOrNull()
                    ?.let { info ->
                        status = info.status
                        result = info.result
                    }
            }
        }

        val walletOutcomeMatched =
            if (expectsRejection) {
                walletTerminal == true && walletSucceeded == false && !walletErrorCode.isNullOrBlank()
            } else {
                walletSucceeded == true
            }
        val execution =
            OidfWalletModuleExecution(
                scenarioId = scenario.id,
                scenarioKey = scenarioKey,
                planId = plan.id,
                moduleName = module.testModule,
                moduleVariant = module.variant.toSortedMap(),
                testId = testId,
                status = status,
                result = result,
                walletStatus = walletStatus,
                walletTerminal = walletTerminal,
                walletSucceeded = walletSucceeded,
                walletErrorCode = walletErrorCode,
                walletErrorArguments = walletErrorArguments,
                expectedWalletOutcome = if (expectsRejection) "REJECTED" else "COMPLETED",
                walletOutcomeMatched = walletOutcomeMatched,
                startedAtEpochMs = startedAt,
                durationMs = System.currentTimeMillis() - startedAt,
                passed =
                    executionError == null &&
                        status == "FINISHED" &&
                        result == "PASSED" &&
                        walletOutcomeMatched,
                error = executionError,
            )
        Files.writeString(
            evidenceDirectory.resolve("result.json"),
            json.encodeToString(OidfWalletModuleExecution.serializer(), execution),
        )
        return execution
    }

    fun close() = harness.close()

    private companion object {
        /**
         * These pinned OIDF client modules pass only when the client refuses the manipulated
         * response and does not continue the flow. A terminal typed wallet rejection is therefore
         * the required SUT outcome; treating only successful issuance as passing inverts the tests.
         */
        val EXPECTED_REJECTION_MODULES =
            setOf(
                "fapi2-security-profile-final-client-test-discovery-issuer-mismatch",
                "fapi2-security-profile-final-client-test-ensure-authorization-response-with-invalid-missing-state-fails",
                "fapi2-security-profile-final-client-test-ensure-authorization-response-with-invalid-state-fails",
                "fapi2-security-profile-final-client-test-invalid-authorization-response-iss",
                "fapi2-security-profile-final-client-test-remove-authorization-response-iss",
            )
    }
}

internal fun OidfPlanModule.displayName(): String =
    buildString {
        append(testModule)
        if (variant.isNotEmpty()) {
            append(" [")
            append(variant.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" })
            append(']')
        }
    }

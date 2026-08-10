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
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

@Serializable
internal data class OidfIssuerModuleExecution(
    val scenarioId: String,
    val scenarioKey: String,
    val planId: String,
    val moduleName: String,
    val moduleVariant: Map<String, String>,
    val testId: String? = null,
    val initialState: String? = null,
    val status: String? = null,
    val result: String? = null,
    val frontChannelRounds: Int = 0,
    val issuerInitiationRounds: Int = 0,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val passed: Boolean,
    val error: String? = null,
)

/** Executes one discovered issuer module, including every requested authorization round. */
internal class OidfIssuerModuleExecutor(
    private val stack: OidfProductStack,
) {
    private val json = Json { prettyPrint = true }

    fun execute(
        scenario: OidfPlanScenario,
        plan: OidfPlan,
        module: OidfPlanModule,
        evidenceDirectory: Path,
    ): OidfIssuerModuleExecution {
        Files.createDirectories(evidenceDirectory)
        val startedAt = System.currentTimeMillis()
        val scenarioKey = module.scenarioKey(plan.name, plan.selectionVariant)
        var testId: String? = null
        var initialState: String? = null
        var status: String? = null
        var result: String? = null
        var frontChannelRounds = 0
        var issuerInitiationRounds = 0
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
                    timeoutMs = INITIAL_TIMEOUT_MS,
                )
            require(!initial.timeout) { "OIDF issuer module did not reach an actionable state" }
            initialState = initial.state

            val deadline = System.currentTimeMillis() + MODULE_TIMEOUT_MS
            val initiationDriver = OidfIssuerInitiationDriver(stack)
            // One logical browser session per module. Replay/request_uri-reuse modules rely on
            // the AS session cookie established during the first authorization round; creating a
            // new cookie jar for every URL turns the second protocol request into a fresh login.
            val authorizationBrowser = OidfIssuerAuthorizationBrowser(stack)
            while (true) {
                require(System.currentTimeMillis() < deadline) {
                    "OIDF issuer module timed out without reaching a terminal state"
                }
                val info = stack.suite.info(started.id)
                status = info.status
                result = info.result
                if (info.status in TERMINAL_STATES) break

                val browser = stack.suite.browser(started.id)
                require(browser.error == null) { "OIDF issuer browser API failed: ${browser.error}" }
                require(browser.browserApiRequests.isEmpty()) {
                    "Issuer module unexpectedly requested Digital Credentials API input: ${browser.browserApiRequests}"
                }
                if (info.status == "WAITING") {
                    // Suite callbacks are synchronous: a pre-authorized multiple-client module can
                    // complete client 1 and enter the client-2 WAITING state before visit() returns,
                    // so polling never observes the intermediate RUNNING state. The suite event log
                    // provides the durable per-request signal needed to deliver exactly one offer
                    // for each VCIWaitForCredentialOffer invocation.
                    val requestedRounds =
                        stack.suite.logSourceInvocationCount(
                            testId = started.id,
                            source = CREDENTIAL_OFFER_WAIT_SOURCE,
                        )
                    val nextRound = issuerInitiationRounds + 1
                    if (requestedRounds >= nextRound) {
                        val delivered =
                            initiationDriver.deliverIfRequested(
                                testId = started.id,
                                plan = plan,
                                moduleGrantType = module.variant["vci_grant_type"],
                                evidenceDirectory = evidenceDirectory,
                                round = nextRound,
                            )
                        if (delivered) {
                            issuerInitiationRounds = nextRound
                            continue
                        }
                    }
                }
                if (browser.urls.isNotEmpty()) {
                    require(browser.urls.size == 1) {
                        "Expected one issuer authorization URL, got ${browser.urls}"
                    }
                    // BrowserControl removes a URL when markVisited succeeds. If a replay test
                    // subsequently exposes the identical URL again, it is a new required round and
                    // must not be deduplicated by URL value.
                    frontChannelRounds += 1
                    authorizationBrowser.complete(
                        testId = started.id,
                        moduleName = module.testModule,
                        frontChannelRound = frontChannelRounds,
                        evidenceDirectory = evidenceDirectory.resolve("front-channel-$frontChannelRounds"),
                    )
                } else {
                    Thread.sleep(POLL_INTERVAL_MS)
                }
            }
        } catch (error: Throwable) {
            executionError = error.stackTraceToString()
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
            OidfIssuerModuleExecution(
                scenarioId = scenario.id,
                scenarioKey = scenarioKey,
                planId = plan.id,
                moduleName = module.testModule,
                moduleVariant = module.variant.toSortedMap(),
                testId = testId,
                initialState = initialState,
                status = status,
                result = result,
                frontChannelRounds = frontChannelRounds,
                issuerInitiationRounds = issuerInitiationRounds,
                startedAtEpochMs = startedAt,
                durationMs = System.currentTimeMillis() - startedAt,
                passed = executionError == null && status == "FINISHED" && result == "PASSED",
                error = executionError,
            )
        Files.writeString(
            evidenceDirectory.resolve("result.json"),
            json.encodeToString(OidfIssuerModuleExecution.serializer(), execution),
        )
        return execution
    }

    private companion object {
        const val INITIAL_TIMEOUT_MS = 30_000L
        const val MODULE_TIMEOUT_MS = 180_000L
        const val POLL_INTERVAL_MS = 250L
        const val CREDENTIAL_OFFER_WAIT_SOURCE = "VCIWaitForCredentialOffer"
        val TERMINAL_STATES = setOf("FINISHED", "INTERRUPTED")
    }
}

private fun Map<String, String>.toJsonObject() =
    kotlinx.serialization.json.JsonObject(mapValues { (_, value) -> JsonPrimitive(value) })

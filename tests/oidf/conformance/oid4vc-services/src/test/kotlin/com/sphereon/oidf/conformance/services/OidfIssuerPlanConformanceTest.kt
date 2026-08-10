/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

import com.sphereon.oidf.conformance.OidfPlan
import com.sphereon.oidf.conformance.OidfPlanModule
import com.sphereon.oidf.conformance.OidfPlanScenario
import com.sphereon.oidf.conformance.OidfPlanScenarioManifest
import com.sphereon.oidf.conformance.OidfSuiteLock
import com.sphereon.oidf.conformance.evidenceDirectoryName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlin.io.path.Path
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
class OidfIssuerPlanConformanceTest {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }
    private val executions = Collections.synchronizedList(mutableListOf<OidfIssuerModuleExecution>())
    private val scenarioErrors = Collections.synchronizedList(mutableListOf<String>())
    private val plans = linkedMapOf<OidfPlanScenario, OidfPlan>()
    private var enabled = false
    private var planName = ""
    private lateinit var evidenceDirectory: Path
    private lateinit var lock: OidfSuiteLock
    private var stack: OidfProductStack? = null
    private var startedAtEpochMs: Long = 0

    @BeforeAll
    fun startPlanRow() {
        enabled = System.getProperty("oidf.suite.mode") == MODE
        if (!enabled) return

        startedAtEpochMs = System.currentTimeMillis()
        planName = checkNotNull(System.getProperty("oidf.plan")) { "Pass -Poidf.plan=<issuer-plan>" }
        require(planName in SUPPORTED_PLANS) {
            "Issuer runner supports ${SUPPORTED_PLANS.sorted()}, got '$planName'"
        }
        evidenceDirectory =
            Path(checkNotNull(System.getProperty("oidf.evidence.dir")))
                .resolve(planName)
        Files.createDirectories(evidenceDirectory)
        lock = OidfSuiteLock.load()
        val profile = if (planName == HAIP_PLAN) OidfProductProfile.HAIP else OidfProductProfile.FINAL
        val runningStack = OidfProductStack.start(OidfProductStackSpec(profile), evidenceDirectory, lock)
        stack = runningStack

        OidfPlanScenarioManifest.forPlan(planName).forEach { scenario ->
            try {
                val plan =
                    runningStack.suite.createPlan(
                        planName = scenario.planName,
                        configJson = runningStack.planConfig(scenario),
                        variant = scenario.variantJson(),
                    )
                require(plan.modules.isNotEmpty()) { "Scenario ${scenario.id} discovered no modules" }
                plans[scenario] = plan
            } catch (error: Throwable) {
                scenarioErrors += "${scenario.id}: ${error.stackTraceToString()}"
            }
        }

        val keys = plans.values.flatMap(OidfPlan::scenarioKeys)
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }
        require(duplicates.isEmpty()) { "Duplicate issuer scenario keys: $duplicates" }
    }

    @TestFactory
    fun `every discovered issuer module passes`(): List<DynamicNode> {
        if (!enabled) return emptyList()
        val runningStack = checkNotNull(stack)
        val executor = OidfIssuerModuleExecutor(runningStack)
        return plans.map { (scenario, plan) ->
            DynamicTest.dynamicTest(scenario.id) {
                val failures = mutableListOf<String>()
                plan.modules.forEach { module ->
                    val execution =
                        executor.execute(
                            scenario = scenario,
                            plan = plan,
                            module = module,
                            evidenceDirectory =
                                evidenceDirectory
                                    .resolve(scenario.id)
                                    .resolve(module.evidenceDirectoryName()),
                        )
                    executions += execution
                    if (!execution.passed) failures += "${module.issuerDisplayName()}: $execution"
                }
                assertTrue(failures.isEmpty(), failures.joinToString("\n"))
            }
        }
    }

    @AfterAll
    fun finishPlanRow() {
        if (!enabled) return
        val cleanupErrors = mutableListOf<String>()
        val runningStack = stack
        try {
            if (runningStack != null) {
                plans.forEach { (scenario, plan) ->
                    runCatching {
                        runningStack.suite.exportPlanHtml(
                            plan.id,
                            evidenceDirectory.resolve(scenario.id).resolve("plan-${plan.id}.zip"),
                        )
                    }.onFailure { cleanupErrors += "${scenario.id} export: ${it.stackTraceToString()}" }
                    runCatching { runningStack.suite.deletePlan(plan.id) }
                        .onFailure { cleanupErrors += "${scenario.id} delete: ${it.stackTraceToString()}" }
                }
            }
        } finally {
            runCatching { runningStack?.close() }
                .onFailure { cleanupErrors += "stack close: ${it.stackTraceToString()}" }
            scenarioErrors += cleanupErrors
            writeCoverage()
        }
        assertTrue(scenarioErrors.isEmpty(), scenarioErrors.joinToString("\n"))
    }

    private fun writeCoverage() {
        val expectedScenarioIds = OidfPlanScenarioManifest.forPlan(planName).map(OidfPlanScenario::id)
        val discovered = plans.values.flatMap(OidfPlan::scenarioKeys).sorted()
        val executed = executions.filter { it.testId != null }.map(OidfIssuerModuleExecution::scenarioKey).sorted()
        val passed = executions.filter(OidfIssuerModuleExecution::passed).map(OidfIssuerModuleExecution::scenarioKey).sorted()
        val report =
            OidfIssuerCoverageReport(
                generatedAtEpochMs = System.currentTimeMillis(),
                durationMs = System.currentTimeMillis() - startedAtEpochMs,
                vdxCommit = gitCommit(),
                vdxWorktreeDirty = gitWorktreeDirty(),
                suiteCommit = lock.upstreamCommit,
                patchSet = lock.patchSet,
                images =
                    mapOf(
                        "server" to lock.serverImage,
                        "nginx" to lock.nginxImage,
                        "mongo" to lock.mongoImage,
                    ),
                imageIds = stack?.suiteEnvironment?.imageIds.orEmpty(),
                planName = planName,
                profile = if (planName == HAIP_PLAN) "OID4VCI_HAIP_1_0" else "OID4VCI_1_0_FINAL",
                expectedScenarioIds = expectedScenarioIds,
                discoveredModuleKeys = discovered,
                executedModuleKeys = executed,
                passedModuleKeys = passed,
                missingModuleKeys = (discovered - executed.toSet()).sorted(),
                failedModuleKeys = (executed.toSet() - passed.toSet()).sorted(),
                scenarioErrors = scenarioErrors,
                executions = executions.sortedBy(OidfIssuerModuleExecution::scenarioKey),
            )
        Files.writeString(
            evidenceDirectory.resolve("oidf-coverage.json"),
            json.encodeToString(OidfIssuerCoverageReport.serializer(), report),
        )
    }

    private fun gitCommit(): String =
        git("rev-parse", "HEAD")
            .takeIf { it.matches(Regex("[0-9a-f]{40}")) }
            ?: System.getenv("GITHUB_SHA")
            ?: "unknown"

    private fun gitWorktreeDirty(): Boolean = git("status", "--porcelain").isNotBlank()

    private fun git(vararg args: String): String {
        val workspace = checkNotNull(System.getProperty("oidf.sut.workspace"))
        return runCatching {
            val process = ProcessBuilder(listOf("git", "-C", workspace) + args).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.waitFor() == 0) output else ""
        }.getOrDefault("")
    }

    private companion object {
        const val MODE = "services-issuer-plan"
        const val FINAL_PLAN = "oid4vci-1_0-issuer-test-plan"
        const val HAIP_PLAN = "oid4vci-1_0-issuer-haip-test-plan"
        val SUPPORTED_PLANS = setOf(FINAL_PLAN, HAIP_PLAN)
    }
}

@Serializable
private data class OidfIssuerCoverageReport(
    val schemaVersion: Int = 1,
    val generatedAtEpochMs: Long,
    val durationMs: Long,
    val vdxCommit: String,
    val vdxWorktreeDirty: Boolean,
    val suiteCommit: String,
    val patchSet: String,
    val images: Map<String, String>,
    val imageIds: Map<String, String>,
    val role: String = "issuer",
    val planName: String,
    val profile: String,
    val expectedScenarioIds: List<String>,
    val discoveredModuleKeys: List<String>,
    val executedModuleKeys: List<String>,
    val passedModuleKeys: List<String>,
    val missingModuleKeys: List<String>,
    val failedModuleKeys: List<String>,
    val scenarioErrors: List<String>,
    val executions: List<OidfIssuerModuleExecution>,
)

private fun OidfPlanModule.issuerDisplayName(): String =
    buildString {
        append(testModule)
        if (variant.isNotEmpty()) {
            append(" [")
            append(variant.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" })
            append(']')
        }
    }

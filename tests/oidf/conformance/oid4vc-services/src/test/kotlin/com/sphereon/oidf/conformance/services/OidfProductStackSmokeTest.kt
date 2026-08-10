/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

import com.sphereon.oidf.conformance.OidfPlanScenarioManifest
import com.sphereon.oidf.conformance.OidfRole
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OidfProductStackSmokeTest {
    @Test
    fun everyIssuerScenarioHasIsolatedClientsAndUniqueSuiteCallbackAlias() {
        val clients =
            OidfProductProfile.entries.flatMap { profile ->
                oidfIssuerScenarioClients("https://host.testcontainers.internal:8443", profile)
            }
        val issuerScenarioCount = OidfPlanScenarioManifest.scenarios.count { it.requiredPlan.role == OidfRole.ISSUER }
        assertEquals(issuerScenarioCount, clients.size)
        assertEquals(clients.size, clients.map { it.redirectUri }.toSet().size)
        assertEquals(clients.size * 2, clients.flatMap { listOf(it.clientId, it.client2Id) }.toSet().size)
        assertTrue(
            clients.all {
                it.redirectUri.matches(
                    Regex("https://host\\.testcontainers\\.internal:8443/test/a/vdx-issuer-[a-z0-9-]+/callback"),
                ) && it.clientId.endsWith(it.scenarioId) && it.client2Id.endsWith(it.scenarioId)
            },
        )
    }

    @Test
    fun composeConformanceProfilePreservesSelectedEnterpriseRuntime() {
        val composeDirectory = Path("compose")
        val ingress = composeDirectory.resolve("docker-compose.gateway.yml")

        val jvm = oidfEnterpriseComposeFiles(composeDirectory, ingress, "jvm")
        assertEquals(
            listOf("docker-compose.yml", "docker-compose.gateway.yml", "docker-compose.jvm.yml"),
            jvm.base.map { it.fileName.toString() },
        )
        assertEquals(
            listOf(
                "docker-compose.yml",
                "docker-compose.gateway.yml",
                "docker-compose.jvm.yml",
                "docker-compose.oidf-conformance.yml",
            ),
            jvm.profile.map { it.fileName.toString() },
        )

        val native = oidfEnterpriseComposeFiles(composeDirectory, ingress, "native")
        assertEquals(
            listOf("docker-compose.yml", "docker-compose.gateway.yml"),
            native.base.map { it.fileName.toString() },
        )
        assertEquals(
            listOf(
                "docker-compose.yml",
                "docker-compose.gateway.yml",
                "docker-compose.oidf-conformance.yml",
                "docker-compose.oidf-conformance.native.yml",
            ),
            native.profile.map { it.fileName.toString() },
        )
        assertFailsWith<IllegalArgumentException> {
            oidfEnterpriseComposeFiles(composeDirectory, ingress, "example")
        }
    }

    @Test
    fun composeImageCoherenceClassifiesNativeAndJvmEntrypointsWithoutFallback() {
        assertEquals(
            OidfEntrypointRuntime.NATIVE,
            oidfEntrypointRuntime(listOf("/app/app", "-Xmx384m")),
        )
        assertEquals(
            OidfEntrypointRuntime.JVM,
            oidfEntrypointRuntime(listOf("sh", "-c", "java -Xmx384m -jar /app/app.jar")),
        )
        assertNull(oidfEntrypointRuntime(listOf("sh", "-c", "echo runtime is unknown")))
        assertNull(oidfEntrypointRuntime(emptyList()))
    }

    @Test
    fun helmConformanceUpgradeRollsPodsOntoCurrentSuiteTrustMaterial() {
        val secretCommand =
            oidfEnterpriseHelmTrustSecretCommand(
                namespace = "edk-oidf",
                trustStorePath = Path("material/truststore.p12"),
            )
        assertEquals(
            "--from-file=truststore.p12=material${java.io.File.separator}truststore.p12",
            secretCommand.single { it.startsWith("--from-file=truststore.p12=") },
        )
        assertTrue(secretCommand.none { it.contains("suite-signing-ca") })

        val runtimeValues =
            oidfEnterpriseHelmRuntimeValues(
                platformBaseDomain = "saas.localtest.me",
                hostGatewayIp = "172.17.0.1",
                trustMaterialSha256 = "a".repeat(64),
                clientSecretName = "edk-enterprise-trust-domain-client",
                clientSecretKey = "client-secret",
                clientSecretProjectedPath =
                    "tenants/tenant-1/oidf-conformance/oid4vci/issuer/trust/domain/api/issuer/trust/domain/service/client-secret",
                clientSecretMountPath = "/var/run/secrets/sphereon",
            )
        assertTrue(runtimeValues.contains("ip: \"172.17.0.1\""))
        assertTrue(runtimeValues.contains("platformBaseDomain: \"saas.localtest.me\""))
        assertEquals("saas.localtest.me", oidfPlatformBaseDomain("https://platform.saas.localtest.me"))
        assertTrue(runtimeValues.contains("oidf.sphereon.com/trust-material-sha256"))
        assertTrue(runtimeValues.contains("\"${"a".repeat(64)}\""))
        assertTrue(runtimeValues.contains("secretName: edk-enterprise-trust-domain-client"))
        assertTrue(runtimeValues.contains("mountPath: /var/run/secrets/sphereon"))
        assertTrue(runtimeValues.contains("name: conformance-client-secret"))
        assertFalse(runtimeValues.contains("vault:"))
        assertEquals(2, Regex("name: conformance-client-secret").findAll(runtimeValues).count() / 2)
        assertTrue(
            runtimeValues.contains(
                "path: tenants/tenant-1/oidf-conformance/oid4vci/issuer/trust/domain/api/issuer/trust/domain/service/client-secret",
            ),
        )
        assertEquals(
            "tenants/f57ad4db-eda7-4680-878b-f78ef5b8d321/oidf-conformance/oid4vci/issuer/trust/domain/api/issuer/trust/domain/service",
            oidfKubernetesSecretPhysicalPath(
                tenantId = "f57ad4db-eda7-4680-878b-f78ef5b8d321",
                instanceId = "oidf-conformance",
                logicalKey = "oid4vci/issuer/trust-domain-api/issuer-trust-domain-service",
            ),
        )

        val runtimeValuesPath = Path("evidence/helm-runtime-values.yaml")
        val command =
            oidfEnterpriseHelmUpgradeCommand(
                release = "edk-oidf",
                chartDirectory = Path("charts/edk-enterprise"),
                namespace = "edk-oidf",
                conformanceValues = Path("charts/edk-enterprise/examples/oidf-conformance-values.yaml"),
                runtimeValues = runtimeValuesPath,
            )

        assertEquals("helm", command.first())
        assertTrue(command.contains("--reset-then-reuse-values"))
        assertFalse(command.contains("--reuse-values"))
        assertEquals(
            runtimeValuesPath.toString(),
            command[command.lastIndexOf("--values") + 1],
        )
        assertFalse(command.contains("--set-json"))
        assertFalse(command.contains("--set-string"))
        assertFailsWith<IllegalArgumentException> {
            oidfEnterpriseHelmRuntimeValues(
                platformBaseDomain = "saas.localtest.me",
                hostGatewayIp = "172.17.0.1",
                trustMaterialSha256 = "not-a-sha256",
                clientSecretName = "edk-enterprise-trust-domain-client",
                clientSecretKey = "client-secret",
                clientSecretProjectedPath = "tenants/tenant-1/oidf-conformance/client/client-secret",
                clientSecretMountPath = "/var/run/secrets/sphereon",
            )
        }
    }

    @Test
    fun realIssuerMetadataModulePassesThroughVerifiedTlsGateway() {
        runIssuerModule(
            requiredMode = "services-smoke",
            evidenceName = "issuer-final-metadata-smoke",
            moduleName = "oid4vci-1_0-issuer-metadata-test",
        )
    }

    @Test
    fun realIssuerWalletInitiatedHappyFlowPasses() {
        runIssuerModule(
            requiredMode = "services-issuer-happy",
            evidenceName = "issuer-final-wallet-initiated-happy",
            moduleName = "oid4vci-1_0-issuer-happy-flow",
        )
    }

    @Test
    fun realVerifierSdJwtHappyFlowPasses() {
        if (System.getProperty("oidf.suite.mode") != "services-verifier-happy") {
            println("Skipping verifier happy flow; pass -Poidf.suite.mode=services-verifier-happy to run it.")
            return
        }
        val scenarioId =
            System.getProperty("oidf.scenario")
                ?: "verifier-final-sd-jwt-vc-x509-hash-direct-post"
        val scenario =
            OidfPlanScenarioManifest.scenarios.single {
                it.id == scenarioId
            }
        val moduleName = System.getProperty("oidf.module") ?: "oid4vp-1final-verifier-happy-flow"
        val profile =
            if (scenario.planName == "oid4vp-1final-verifier-haip-test-plan") {
                OidfProductProfile.HAIP
            } else {
                OidfProductProfile.FINAL
            }
        val evidenceDirectory =
            Path(checkNotNull(System.getProperty("oidf.evidence.dir")))
                .resolve("${scenario.id}-$moduleName")
        OidfProductStack
            .start(
                spec = OidfProductStackSpec(profile),
                evidenceDirectory = evidenceDirectory,
            ).use { stack ->
                var planId: String? = null
                try {
                    val plan =
                        stack.suite.createPlan(
                            planName = scenario.planName,
                            configJson = stack.planConfig(scenario),
                            variant = scenario.variantJson(),
                        )
                    planId = plan.id
                    val module = plan.modules.single { it.testModule == moduleName }
                    val execution =
                        OidfVerifierModuleExecutor(stack).execute(
                            scenario = scenario,
                            plan = plan,
                            module = module,
                            evidenceDirectory = evidenceDirectory,
                        )
                    stack.suite.exportPlanHtml(plan.id, evidenceDirectory.resolve("plan-${plan.id}.zip"))
                    assertEquals(null, execution.error, execution.error)
                    assertEquals("FINISHED", execution.status)
                    assertEquals("PASSED", execution.result)
                } finally {
                    planId?.let { runCatching { stack.suite.deletePlan(it) } }
                }
            }
    }

    private fun runIssuerModule(
        requiredMode: String,
        evidenceName: String,
        moduleName: String,
    ) {
        if (System.getProperty("oidf.suite.mode") != requiredMode) {
            println("Skipping $moduleName; pass -Poidf.suite.mode=$requiredMode to run it.")
            return
        }
        val scenarioId =
            System.getProperty("oidf.scenario")
                ?: "issuer-final-sdjwt-wallet-auth-plain"
        val selectedModuleName = System.getProperty("oidf.module") ?: moduleName
        val evidenceDirectory =
            Path(checkNotNull(System.getProperty("oidf.evidence.dir")))
                .resolve("$evidenceName-$scenarioId-$selectedModuleName")
        val scenario = OidfPlanScenarioManifest.scenarios.single { it.id == scenarioId }
        val profile =
            if (scenario.planName == "oid4vci-1_0-issuer-haip-test-plan") {
                OidfProductProfile.HAIP
            } else {
                OidfProductProfile.FINAL
            }
        OidfProductStack
            .start(
                spec = OidfProductStackSpec(profile),
                evidenceDirectory = evidenceDirectory,
            ).use { stack ->
                var planId: String? = null
                try {
                    val plan =
                        stack.suite.createPlan(
                            planName = scenario.planName,
                            configJson = stack.planConfig(scenario),
                            variant = scenario.variantJson(),
                        )
                    planId = plan.id
                    val module = plan.modules.single { it.testModule == selectedModuleName }
                    val execution =
                        OidfIssuerModuleExecutor(stack).execute(
                            scenario = scenario,
                            plan = plan,
                            module = module,
                            evidenceDirectory = evidenceDirectory,
                        )
                    stack.suite.exportPlanHtml(plan.id, evidenceDirectory.resolve("plan-${plan.id}.zip"))
                    assertEquals(null, execution.error, execution.error)
                    assertEquals("FINISHED", execution.status)
                    assertEquals("PASSED", execution.result)
                } finally {
                    planId?.let { runCatching { stack.suite.deletePlan(it) } }
                }
            }
    }

}

private fun Map<String, String>.toJsonObject() =
    kotlinx.serialization.json.JsonObject(mapValues { (_, value) -> JsonPrimitive(value) })

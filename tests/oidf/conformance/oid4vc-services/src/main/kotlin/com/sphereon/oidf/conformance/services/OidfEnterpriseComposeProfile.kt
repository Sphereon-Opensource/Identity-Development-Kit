/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

// Shared by deployed issuer, verifier, and wallet conformance drivers.

import com.sphereon.oidf.conformance.OidfSuiteConfigPreprocessor
import com.sphereon.oidf.conformance.OidfSuiteEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.isRegularFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Applies the suite profile to an already provisioned production Compose deployment.
 *
 * The base deployment remains owned by `run-e2e.ps1`: databases, licensing, tenant provisioning,
 * keys, registrations, and the gateway must already exist. This adapter only force-recreates the
 * AS, issuer, and verifier with the narrow conformance overlay after the suite has generated its
 * callback certificate and signing material. The platform API is recreated with the same
 * production topology because it owns the REST provisioning routes exercised by every row.
 * All protocol policy and test data
 * are then written through the production REST APIs by the provisioner.
 */
internal class OidfEnterpriseComposeProfile(
    private val infraWorkspace: Path,
    private val composeIngress: String,
    private val composeRuntime: String,
    private val suiteContextPath: Path,
    private val enterpriseContextPath: Path,
    private val credentialsPath: Path,
    private val productProfile: OidfProductProfile,
    private val platformUrl: String,
    private val tenantId: String,
    private val kmsProviderId: String,
    private val asInstanceId: String,
    private val issuerInstanceId: String,
    private val verifierInstanceId: String,
    private val issuerSigningKeyAlias: String,
    private val verifierSigningKeyAlias: String,
    private val productBaseUrl: String,
    private val productTlsCaPath: Path,
    private val operatorUsername: String,
    private val operatorPassword: String,
    private val loginUsername: String,
    private val loginPassword: String,
    private val verifierSanDns: String,
    private val suiteEnvironment: OidfSuiteEnvironment,
    private val configPreprocessor: OidfSuiteConfigPreprocessor,
    private val evidenceDirectory: Path,
) : OidfEnterpriseDeploymentProfile {
    override val productSigningTrustAnchorPath: Path =
        evidenceDirectory.resolve("platform-config/product-signing-trust-anchors.pem")

    private val composeDirectory = infraWorkspace.resolve("deploy/edk/e2e/compose").normalize()
    private val ingressComposeFile =
        when {
            composeIngress == "localtest" -> composeDirectory.resolve("docker-compose.gateway.yml")
            BEHIND_EDGE_INGRESS.matches(composeIngress) -> {
                val environmentLabel = BEHIND_EDGE_INGRESS.matchEntire(composeIngress)!!.groupValues[1]
                composeDirectory.resolve("docker-compose.behind-edge.$environmentLabel.yml")
            }
            else -> error(
                "enterpriseContext.composeIngress must be 'localtest' or 'behind-edge-<label>', got '$composeIngress'",
            )
        }
    private val composeFiles = oidfEnterpriseComposeFiles(composeDirectory, ingressComposeFile, composeRuntime)
    private val baseComposeFiles = composeFiles.base
    private val profileComposeFiles = composeFiles.profile
    private val runtimeMaterialDirectory =
        infraWorkspace.resolve("deploy/edk/e2e/build/oidf-conformance/runtime-profile").normalize()
    private lateinit var deploymentEnvironment: Map<String, String>
    private lateinit var resolvedWalletInteractionGrpcEndpoint: String
    private lateinit var resolvedWalletUnitGrpcEndpoint: String
    override val walletInteractionGrpcEndpoint: String get() = resolvedWalletInteractionGrpcEndpoint
    override val walletUnitGrpcEndpoint: String get() = resolvedWalletUnitGrpcEndpoint

    init {
        require(composeDirectory.startsWith(infraWorkspace.toAbsolutePath().normalize())) {
            "Resolved Compose directory escapes oidf.sut.infraWorkspace: $composeDirectory"
        }
        profileComposeFiles.forEach { path ->
            require(path.isRegularFile()) { "Missing enterprise Compose input: $path" }
        }
        require(suiteContextPath.isRegularFile()) { "Missing prepared OIDF suite context: $suiteContextPath" }
    }

    override fun apply() {
        Files.createDirectories(evidenceDirectory)
        deploymentEnvironment = readProductionDeploymentEnvironment()
        assertProvisionedDeployment()

        Files.createDirectories(runtimeMaterialDirectory)
        val trustStorePath = runtimeMaterialDirectory.resolve("oidf-suite-truststore.p12")
        Files.copy(suiteEnvironment.trustStorePath, trustStorePath, StandardCopyOption.REPLACE_EXISTING)

        val issuerClients = oidfIssuerScenarioClients(suiteEnvironment.advertisedBaseUrl, productProfile)

        Files.writeString(
            evidenceDirectory.resolve("profile.json"),
            buildJsonObject {
                put("profile", productProfile.name)
                put(
                    "issuerClients",
                    buildJsonArray {
                        issuerClients.forEach { client ->
                            add(
                                buildJsonObject {
                                    put("scenarioId", client.scenarioId)
                                    put("clientId", client.clientId)
                                    put("client2Id", client.client2Id)
                                    put("redirectUri", client.redirectUri)
                                },
                            )
                        }
                    },
                )
                put("verifierSanDns", verifierSanDns)
                put("trustStoreSha256", sha256(trustStorePath))
            }.toString(),
        )

        runCompose(
            composeFiles = profileComposeFiles,
            arguments = listOf("config", "--no-interpolate"),
            output = evidenceDirectory.resolve("compose-config.unexpanded.yml"),
            environment = deploymentEnvironment,
            timeout = Duration.ofMinutes(2),
        )
        assertRuntimeImageCoherence()
        runCompose(
            composeFiles = profileComposeFiles,
            arguments =
                listOf(
                    "up",
                    "-d",
                    "--no-deps",
                    "--force-recreate",
                    "--wait",
                    "--wait-timeout",
                    "300",
                ) + COHERENCE_SERVICES,
            output = evidenceDirectory.resolve("compose-apply.log"),
            environment = deploymentEnvironment,
            timeout = Duration.ofMinutes(7),
        )
        resolvedWalletInteractionGrpcEndpoint = resolveComposeGrpcEndpoint("enterprise-wallet-interaction")
        resolvedWalletUnitGrpcEndpoint = resolveComposeGrpcEndpoint("enterprise-wallet-unit")
        provisionOidfEnterprisePlatformConfig(
            OidfEnterprisePlatformConfigSpec(
                infraWorkspace = infraWorkspace,
                suiteContextPath = suiteContextPath,
                enterpriseContextPath = enterpriseContextPath,
                credentialsPath = credentialsPath,
                outputDirectory = evidenceDirectory.resolve("platform-config"),
                deployment = "enterprise-compose",
                platformUrl = platformUrl,
                tenantId = tenantId,
                kmsProviderId = kmsProviderId,
                asInstanceId = asInstanceId,
                issuerInstanceId = issuerInstanceId,
                verifierInstanceId = verifierInstanceId,
                issuerSigningKeyAlias = issuerSigningKeyAlias,
                verifierSigningKeyAlias = verifierSigningKeyAlias,
                productBaseUrl = productBaseUrl,
                productTlsCaPath = productTlsCaPath,
                operatorUsername = operatorUsername,
                operatorPassword = operatorPassword,
                loginUsername = loginUsername,
                loginPassword = loginPassword,
                verifierSanDns = verifierSanDns,
                productProfile = productProfile,
                issuerClients = issuerClients,
                composeIngress = composeIngress,
            ),
        )
        exportServiceEvidence()
    }

    override fun exportServiceEvidence() {
        if (!::deploymentEnvironment.isInitialized) return
        runCatching {
            runCompose(
                composeFiles = profileComposeFiles,
                arguments = listOf("ps", "-a", "--format", "json") + EVIDENCE_SERVICES,
                output = evidenceDirectory.resolve("compose-ps.json"),
                environment = deploymentEnvironment,
                timeout = Duration.ofMinutes(1),
            )
        }
        runCatching {
            runCompose(
                composeFiles = profileComposeFiles,
                arguments = listOf("logs", "--no-color", "--timestamps") + COHERENCE_SERVICES,
                output = evidenceDirectory.resolve("compose-services.log"),
                environment = deploymentEnvironment,
                timeout = Duration.ofMinutes(2),
            )
        }
    }

    private fun assertProvisionedDeployment() {
        val output = evidenceDirectory.resolve("compose-preflight.txt")
        runCompose(
            composeFiles = baseComposeFiles,
            arguments = listOf("ps", "-q") + REQUIRED_RUNNING_SERVICES,
            output = output,
            environment = deploymentEnvironment,
            timeout = Duration.ofMinutes(1),
        )
        val containerIds = Files.readAllLines(output).filter(String::isNotBlank)
        require(containerIds.size == REQUIRED_RUNNING_SERVICES.size) {
            "Enterprise Compose deployment is not fully provisioned: expected ${REQUIRED_RUNNING_SERVICES.size} " +
                "running service containers, found ${containerIds.size}. Bootstrap it with run-e2e.ps1 -Gateway -Localtest -KeepUp."
        }
    }

    /**
     * Fail before Compose replaces any running product container when a mutable image tag and
     * the rendered deployment entrypoint no longer describe the same runtime layout. This catches
     * the otherwise opaque `/app/app` over a JVM image failure without imposing one runtime on a
     * deliberately mixed production deployment.
     */
    private fun assertRuntimeImageCoherence() {
        val renderedConfig =
            runComposeCapture(
                composeFiles = profileComposeFiles,
                arguments = listOf("config", "--format", "json"),
                environment = deploymentEnvironment,
                timeout = Duration.ofMinutes(2),
            )
        val services =
            Json.parseToJsonElement(renderedConfig).jsonObject["services"]?.jsonObject
                ?: error("Rendered enterprise Compose config has no services object")
        val failures = mutableListOf<String>()
        val records =
            COHERENCE_SERVICES.map { service ->
                val serviceConfig = services[service]?.jsonObject
                    ?: error("Rendered enterprise Compose config has no $service service")
                val image = serviceConfig["image"]?.jsonPrimitive?.content
                    ?: error("Rendered enterprise Compose service $service has no image")
                val effectiveEntrypoint = oidfEntrypointValues(serviceConfig["entrypoint"])
                val inspection = inspectImage(image)
                val effectiveRuntime = oidfEntrypointRuntime(effectiveEntrypoint)
                val imageRuntime = oidfEntrypointRuntime(inspection.entrypoint)
                val coherent = effectiveRuntime != null && imageRuntime != null && effectiveRuntime == imageRuntime
                if (!coherent) {
                    failures +=
                        "$service renders ${effectiveEntrypoint.joinToString(" ")} (${effectiveRuntime ?: "unknown"}) " +
                            "but $image provides ${inspection.entrypoint.joinToString(" ")} (${imageRuntime ?: "unknown"})"
                }
                buildJsonObject {
                    put("service", service)
                    put("image", image)
                    put("imageId", inspection.id)
                    put("effectiveEntrypoint", buildJsonArray { effectiveEntrypoint.forEach { add(JsonPrimitive(it)) } })
                    put("imageEntrypoint", buildJsonArray { inspection.entrypoint.forEach { add(JsonPrimitive(it)) } })
                    put("effectiveRuntime", effectiveRuntime?.name ?: "UNKNOWN")
                    put("imageRuntime", imageRuntime?.name ?: "UNKNOWN")
                    put("coherent", coherent)
                }
            }
        Files.writeString(
            evidenceDirectory.resolve("compose-image-coherence.json"),
            buildJsonObject {
                put("composeRuntime", composeRuntime)
                put("services", buildJsonArray { records.forEach { add(it) } })
            }.toString(),
        )
        require(failures.isEmpty()) {
            "Enterprise Compose runtime/image mismatch; no containers were recreated:\n${failures.joinToString("\n")}"
        }
    }

    private fun inspectImage(image: String): OidfImageInspection {
        val output =
            runCommandCapture(
                command = listOf("docker", "image", "inspect", image),
                timeout = Duration.ofMinutes(1),
            )
        val inspected = Json.parseToJsonElement(output).jsonArray.single().jsonObject
        val id = inspected["Id"]?.jsonPrimitive?.content
            ?: error("docker image inspect returned no image ID for $image")
        val entrypoint = oidfEntrypointValues(inspected["Config"]?.jsonObject?.get("Entrypoint"))
        return OidfImageInspection(id = id, entrypoint = entrypoint)
    }

    private fun resolveComposeGrpcEndpoint(service: String): String {
        val output =
            runComposeCapture(
                composeFiles = profileComposeFiles,
                arguments = listOf("port", service, "9090"),
                environment = deploymentEnvironment,
                timeout = Duration.ofMinutes(1),
            ).lineSequence().map(String::trim).filter(String::isNotBlank).lastOrNull()
                ?: error("Compose exposed no gRPC port for $service")
        val endpoint = output.removePrefix("0.0.0.0:").removePrefix("127.0.0.1:").removePrefix("[::]:")
        require(endpoint.toIntOrNull() in 1..65535) { "Invalid Compose gRPC port for $service: $output" }
        return "localhost:$endpoint"
    }

    /**
     * Compose requires these two deployment-level values while parsing the production stack.
     * Read them from the already provisioned platform container so the conformance harness does
     * not invent a second deployment configuration or fall back to a different base domain.
     * No tenant, KMS, AS, issuer, verifier, or protocol setting is sourced this way.
     */
    private fun readProductionDeploymentEnvironment(): Map<String, String> {
        val command =
            listOf(
                "docker",
                "inspect",
                "edk-enterprise-e2e-enterprise-platform-1",
                "--format",
                "{{range .Config.Env}}{{println .}}{{end}}",
            )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        require(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) {
            "Unable to inspect the provisioned enterprise platform container"
        }
        val values =
            output.lineSequence()
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
                }.toMap()
        fun required(name: String): String =
            values[name]?.takeIf(String::isNotBlank)
                ?: error("Provisioned enterprise platform container has no $name")
        return mapOf(
            "EDK_ADMIN_CONSOLE_WORKLOAD_CLIENT_SECRET" to required("ADMIN_CONSOLE_WORKLOAD_CLIENT_SECRET"),
            "EDK_PLATFORM_BASE_DOMAIN" to required("EDK_PLATFORM_BASE_DOMAIN"),
        )
    }

    private fun runCompose(
        composeFiles: List<Path>,
        arguments: List<String>,
        output: Path,
        environment: Map<String, String>,
        timeout: Duration,
    ) {
        Files.createDirectories(output.parent)
        val command =
            buildList {
                add("docker")
                add("compose")
                add("--ansi")
                add("never")
                composeFiles.forEach {
                    add("-f")
                    add(it.toString())
                }
                addAll(arguments)
            }
        val process =
            ProcessBuilder(command)
                .directory(composeDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .apply { environment().putAll(environment) }
                .start()
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            error("Timed out after $timeout: ${command.joinToString(" ")}. Output: $output")
        }
        require(process.exitValue() == 0) {
            val tail = Files.readAllLines(output).takeLast(80).joinToString("\n")
            "Command failed with exit ${process.exitValue()}: ${command.joinToString(" ")}\n$tail"
        }
    }

    private fun runComposeCapture(
        composeFiles: List<Path>,
        arguments: List<String>,
        environment: Map<String, String>,
        timeout: Duration,
    ): String {
        val command =
            buildList {
                add("docker")
                add("compose")
                add("--ansi")
                add("never")
                composeFiles.forEach {
                    add("-f")
                    add(it.toString())
                }
                addAll(arguments)
            }
        return runCommandCapture(command, timeout, environment)
    }

    private fun runCommandCapture(
        command: List<String>,
        timeout: Duration,
        environment: Map<String, String> = emptyMap(),
    ): String {
        val process =
            ProcessBuilder(command)
                .directory(composeDirectory.toFile())
                .redirectErrorStream(true)
                .apply { environment().putAll(environment) }
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        require(process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            "Timed out after $timeout: ${command.joinToString(" ")}"
        }
        require(process.exitValue() == 0) {
            "Command failed with exit ${process.exitValue()}: ${command.joinToString(" ")}\n${output.takeLast(4_000)}"
        }
        return output
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        // These workloads participate directly in OIDF protocol execution or REST provisioning.
        // Keep platform and tenant-KMS in the same image-coherence gate and forced recreation set:
        // stale mutable tags otherwise leave the live row on a mixed runtime.
        val COHERENCE_SERVICES =
            listOf(
                "enterprise-platform",
                "enterprise-tenant-kms",
                "enterprise-tenant-as",
                "enterprise-wallet-unit",
                "enterprise-wallet-interaction",
                "enterprise-issuer",
                "enterprise-verifier",
            )
        val EVIDENCE_SERVICES = COHERENCE_SERVICES
        val REQUIRED_RUNNING_SERVICES =
            listOf(
                "platform-postgres",
                "tenant-postgres",
                "enterprise-platform",
                "enterprise-tenant-kms",
                "enterprise-tenant-as",
                "enterprise-did",
                "enterprise-wallet-unit",
                "enterprise-wallet-interaction",
                "enterprise-issuer",
                "enterprise-verifier",
                "traefik",
            )
        val BEHIND_EDGE_INGRESS = Regex("behind-edge-([a-z0-9][a-z0-9-]{0,31})")
    }
}

internal enum class OidfEntrypointRuntime {
    NATIVE,
    JVM,
}

internal data class OidfImageInspection(
    val id: String,
    val entrypoint: List<String>,
)

internal fun oidfEntrypointValues(element: JsonElement?): List<String> =
    when (element) {
        null, JsonNull -> emptyList()
        is JsonArray -> element.map { it.jsonPrimitive.content }
        is JsonPrimitive -> listOf(element.content)
        else -> error("Unsupported Compose entrypoint JSON: $element")
    }

internal fun oidfEntrypointRuntime(entrypoint: List<String>): OidfEntrypointRuntime? {
    if (entrypoint.firstOrNull() == "/app/app") return OidfEntrypointRuntime.NATIVE
    val command = entrypoint.joinToString(" ")
    if (Regex("(^|\\s)java(\\s|$)").containsMatchIn(command) && command.contains("/app/app.jar")) {
        return OidfEntrypointRuntime.JVM
    }
    return null
}

internal data class OidfEnterpriseComposeFiles(
    val base: List<Path>,
    val profile: List<Path>,
)

internal fun oidfEnterpriseComposeFiles(
    composeDirectory: Path,
    ingressComposeFile: Path,
    composeRuntime: String,
): OidfEnterpriseComposeFiles {
    require(composeRuntime in setOf("jvm", "native")) {
        "oidf.sut.composeRuntime must be 'jvm' or 'native', got '$composeRuntime'"
    }
    val base =
        buildList {
            add(composeDirectory.resolve("docker-compose.yml"))
            add(ingressComposeFile)
            if (composeRuntime == "jvm") add(composeDirectory.resolve("docker-compose.jvm.yml"))
        }
    val profile =
        buildList {
            addAll(base)
            add(composeDirectory.resolve("docker-compose.oidf-conformance.yml"))
            if (composeRuntime == "native") {
                add(composeDirectory.resolve("docker-compose.oidf-conformance.native.yml"))
            }
        }
    return OidfEnterpriseComposeFiles(base = base, profile = profile)
}

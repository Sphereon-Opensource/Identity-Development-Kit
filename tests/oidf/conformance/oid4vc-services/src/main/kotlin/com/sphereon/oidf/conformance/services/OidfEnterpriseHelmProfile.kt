/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

// Shared by deployed issuer, verifier, and wallet conformance drivers.

import com.sphereon.oidf.conformance.OidfSuiteConfigPreprocessor
import com.sphereon.oidf.conformance.OidfSuiteEnvironment
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.io.path.isRegularFile
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Applies the OIDF trust profile to an existing release of the production enterprise Helm chart. */
internal class OidfEnterpriseHelmProfile(
    private val infraWorkspace: Path,
    private val namespace: String,
    private val release: String,
    private val hostGatewayIp: String,
    private val edgeMode: Boolean,
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

    private val chartDirectory = infraWorkspace.resolve("customer/edk/helm/edk-enterprise").normalize()
    private val conformanceValues = chartDirectory.resolve("examples/oidf-conformance-values.yaml")
    private val clientSecretName = "$release-trust-domain-client"
    private val clientSecretKey = "client-secret"
    private val clientSecretId = "sec_oidfTrustDomainClient000001"
    private val clientSecretLogicalKey = "oid4vci/issuer/trust-domain-api/issuer-trust-domain-service"
    private val clientSecretMountPath = "/var/run/secrets/sphereon"
    private val clientSecretPhysicalPath =
        oidfKubernetesSecretPhysicalPath(tenantId, "oidf-conformance", clientSecretLogicalKey)
    private val portForwardProcesses = mutableListOf<Process>()
    private var gatewayPortForwardProcess: Process? = null
    private lateinit var resolvedWalletInteractionGrpcEndpoint: String
    private lateinit var resolvedWalletUnitGrpcEndpoint: String
    override val walletInteractionGrpcEndpoint: String get() = resolvedWalletInteractionGrpcEndpoint
    override val walletUnitGrpcEndpoint: String get() = resolvedWalletUnitGrpcEndpoint

    init {
        val normalizedWorkspace = infraWorkspace.toAbsolutePath().normalize()
        require(chartDirectory.startsWith(normalizedWorkspace)) {
            "Resolved Helm chart escapes oidf.sut.infraWorkspace: $chartDirectory"
        }
        require(chartDirectory.resolve("Chart.yaml").isRegularFile()) { "Missing enterprise Helm chart: $chartDirectory" }
        require(conformanceValues.isRegularFile()) { "Missing enterprise Helm OIDF values: $conformanceValues" }
        require(suiteContextPath.isRegularFile()) { "Missing prepared OIDF suite context: $suiteContextPath" }
        require(enterpriseContextPath.isRegularFile()) { "Missing REST-resolved enterprise context: $enterpriseContextPath" }
        require(credentialsPath.isRegularFile()) { "Missing OIDF credentials file: $credentialsPath" }
        require(KUBERNETES_NAME.matches(namespace)) { "Invalid enterpriseContext.helm.namespace: $namespace" }
        require(KUBERNETES_NAME.matches(release)) { "Invalid enterpriseContext.helm.release: $release" }
        require(IP_ADDRESS.matches(hostGatewayIp)) { "enterpriseContext.helm.hostGatewayIp must be an IP address: $hostGatewayIp" }
    }

    override fun apply() {
        Files.createDirectories(evidenceDirectory)
        assertProvisionedRelease()

        val materialDirectory = evidenceDirectory.resolve("profile-material")
        Files.createDirectories(materialDirectory)
        val trustStorePath = materialDirectory.resolve("oidf-suite-truststore.p12")
        Files.copy(suiteEnvironment.trustStorePath, trustStorePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val trustMaterialSha256 = sha256(trustStorePath)
        val runtimeValuesPath = evidenceDirectory.resolve("helm-runtime-values.yaml")
        Files.writeString(
            runtimeValuesPath,
            oidfEnterpriseHelmRuntimeValues(
                platformBaseDomain = oidfPlatformBaseDomain(platformUrl),
                hostGatewayIp = hostGatewayIp,
                trustMaterialSha256 = trustMaterialSha256,
                clientSecretName = clientSecretName,
                clientSecretKey = clientSecretKey,
                clientSecretProjectedPath = "$clientSecretPhysicalPath/$clientSecretKey",
                clientSecretMountPath = clientSecretMountPath,
            ),
        )

        Files.writeString(
            evidenceDirectory.resolve("profile.json"),
            buildJsonObject {
                put("profile", productProfile.name)
                put("namespace", namespace)
                put("release", release)
                put("hostGatewayIp", hostGatewayIp)
                put("edgeMode", edgeMode)
                put("gatewayAccess", if (edgeMode) "external-edge" else "kubectl-port-forward")
                put("trustMaterialSha256", trustMaterialSha256)
                put("secretProvider", "kubernetes-mount")
                put("clientSecretName", clientSecretName)
                put("clientSecretKey", clientSecretKey)
                put("clientSecretMountPath", clientSecretMountPath)
                put("clientSecretPhysicalPath", clientSecretPhysicalPath)
            }.toString(),
        )

        val expectedRuntimeImageIds = assertNodeImageCoherence()
        ensureKubernetesClientSecret()

        val secretManifest =
            runCapture(
                oidfEnterpriseHelmTrustSecretCommand(namespace, trustStorePath),
                Duration.ofMinutes(1),
            )
        runCommand(
            command = listOf("kubectl", "--namespace", namespace, "apply", "--filename=-"),
            output = evidenceDirectory.resolve("kubectl-secret-apply.log"),
            timeout = Duration.ofMinutes(1),
            standardInput = secretManifest,
        )

        runCommand(
            command =
                oidfEnterpriseHelmUpgradeCommand(
                    release = release,
                    chartDirectory = chartDirectory,
                    namespace = namespace,
                    conformanceValues = conformanceValues,
                    runtimeValues = runtimeValuesPath,
                ),
            output = evidenceDirectory.resolve("helm-apply.log"),
            timeout = Duration.ofMinutes(12),
        )
        assertRunningImageCoherence(expectedRuntimeImageIds)
        if (edgeMode) {
            awaitExternalGateway()
        } else {
            startGatewayPortForward()
        }

        val issuerClients = oidfIssuerScenarioClients(suiteEnvironment.advertisedBaseUrl, productProfile)
        val platformConfigSpec =
            OidfEnterprisePlatformConfigSpec(
                infraWorkspace = infraWorkspace,
                suiteContextPath = suiteContextPath,
                enterpriseContextPath = enterpriseContextPath,
                credentialsPath = credentialsPath,
                outputDirectory = evidenceDirectory.resolve("platform-config"),
                deployment = "enterprise-helm",
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
                helmNamespace = namespace,
                helmRelease = release,
                helmHostGatewayIp = hostGatewayIp,
                helmClientSecretName = clientSecretName,
                helmClientSecretKey = clientSecretKey,
                helmClientSecretMountPath = clientSecretMountPath,
                helmClientSecretId = clientSecretId,
            )
        if (edgeMode) {
            provisionOidfEnterprisePlatformConfig(platformConfigSpec)
        } else {
            provisionPlatformConfigWithGatewayRecovery(platformConfigSpec)
        }
        resolvedWalletInteractionGrpcEndpoint = startGrpcPortForward("wallet-interaction")
        resolvedWalletUnitGrpcEndpoint = startGrpcPortForward("wallet-unit")
        exportServiceEvidence()
    }

    private fun provisionPlatformConfigWithGatewayRecovery(spec: OidfEnterprisePlatformConfigSpec) {
        for (attempt in 1..2) {
            try {
                provisionOidfEnterprisePlatformConfig(spec)
                return
            } catch (failure: Throwable) {
                val gatewayLog = evidenceDirectory.resolve("kubectl-port-forward-gateway.log")
                val gatewayOutput = runCatching { Files.readString(gatewayLog) }.getOrDefault("")
                val forwardWasLost =
                    gatewayPortForwardProcess?.isAlive == false ||
                        gatewayOutput.contains("lost connection to pod", ignoreCase = true)
                if (attempt == 2 || !forwardWasLost) throw failure

                val platformLog = spec.outputDirectory.resolve("platform-config-provision.log")
                if (platformLog.isRegularFile()) {
                    Files.copy(
                        platformLog,
                        spec.outputDirectory.resolve("platform-config-provision-attempt-$attempt.log"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    )
                }
                if (gatewayLog.isRegularFile()) {
                    Files.copy(
                        gatewayLog,
                        evidenceDirectory.resolve("kubectl-port-forward-gateway-attempt-$attempt.log"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    )
                }
                gatewayPortForwardProcess?.takeIf(Process::isAlive)?.destroyForcibly()
                startGatewayPortForward()
            }
        }
    }

    override fun close() {
        portForwardProcesses.forEach { process ->
            if (process.isAlive) process.destroy()
            if (process.isAlive && !process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        portForwardProcesses.clear()
    }

    override fun exportServiceEvidence() {
        runCatching {
            runCommand(
                command = listOf("helm", "--namespace", namespace, "get", "manifest", release),
                output = evidenceDirectory.resolve("helm-manifest.yaml"),
                timeout = Duration.ofMinutes(2),
            )
        }
        runCatching {
            runCommand(
                command =
                    listOf(
                        "kubectl",
                        "--namespace",
                        namespace,
                        "get",
                        "pods,services,deployments,gateway,httproute",
                        "--output=wide",
                    ),
                output = evidenceDirectory.resolve("kubernetes-resources.txt"),
                timeout = Duration.ofMinutes(2),
            )
        }
        runCatching {
            runCommand(
                command =
                    listOf(
                        "kubectl",
                        "--namespace",
                        namespace,
                        "logs",
                        "--selector=app.kubernetes.io/instance=$release,app.kubernetes.io/component!=vault",
                        "--all-containers=true",
                        "--prefix=true",
                        "--tail=-1",
                    ),
                output = evidenceDirectory.resolve("kubernetes-workload.log"),
                timeout = Duration.ofMinutes(3),
            )
        }
    }

    private fun assertProvisionedRelease() {
        runCommand(
            command = listOf("helm", "--namespace", namespace, "status", release, "--output=json"),
            output = evidenceDirectory.resolve("helm-preflight.json"),
            timeout = Duration.ofMinutes(1),
        )
    }

    private fun assertNodeImageCoherence(): Map<String, String> {
        val node =
            runCapture(
                listOf("kubectl", "get", "nodes", "--output=jsonpath={.items[0].metadata.name}"),
                Duration.ofMinutes(1),
            ).trim().takeIf(String::isNotBlank) ?: error("Kubernetes cluster has no node")
        runCapture(listOf("docker", "inspect", node, "--format={{.Id}}"), Duration.ofMinutes(1))

        val componentImages =
            linkedMapOf(
                "platform" to "enterprise-platform",
                "tenant-kms" to "enterprise-tenant-kms",
                "tenant-as" to "enterprise-tenant-as",
                "issuer" to "enterprise-issuer",
                "verifier" to "enterprise-verifier",
            )
        val imageDigests = linkedMapOf<String, String>()
        val runtimeImageIds = linkedMapOf<String, String>()
        for ((component, imageName) in componentImages) {
            val image = "nexus.sphereon.com/edk-docker/$imageName:0.25.0-SNAPSHOT"
            val localDigest =
                runCapture(
                    listOf("docker", "image", "inspect", image, "--format={{.Id}}"),
                    Duration.ofMinutes(1),
                ).trim()
            val nodeInspect =
                runCapture(
                    listOf("docker", "exec", node, "ctr", "-n", "k8s.io", "images", "inspect", image),
                    Duration.ofMinutes(1),
                )
            val nodeDigest = Regex("@(?:sha256:)?([0-9a-f]{64})").find(nodeInspect)?.groupValues?.get(1)?.let { "sha256:$it" }
                ?: error("Could not resolve containerd target digest for $image on Kubernetes node $node")
            require(nodeDigest == localDigest) {
                "Kubernetes node $node has stale $image ($nodeDigest); local tag is $localDigest. " +
                    "Load the local image into the node before starting Helm conformance."
            }
            val runtimeImageId =
                Regex("application/vnd\\.oci\\.image\\.config\\.v1\\+json @sha256:([0-9a-f]{64})")
                    .find(nodeInspect)
                    ?.groupValues
                    ?.get(1)
                    ?.let { "sha256:$it" }
                    ?: error("Could not resolve linux/amd64 runtime config digest for $image on Kubernetes node $node")
            imageDigests[image] = localDigest
            runtimeImageIds[component] = runtimeImageId
        }
        Files.writeString(
            evidenceDirectory.resolve("image-coherence.json"),
            buildJsonObject {
                put("node", node)
                put(
                    "images",
                    buildJsonObject {
                        imageDigests.forEach { (image, digest) -> put(image, digest) }
                    },
                )
                put(
                    "runtimeImageIds",
                    buildJsonObject {
                        runtimeImageIds.forEach { (component, digest) -> put(component, digest) }
                    },
                )
            }.toString(),
        )
        return runtimeImageIds
    }

    private fun assertRunningImageCoherence(expectedRuntimeImageIds: Map<String, String>) {
        expectedRuntimeImageIds.forEach { (component, expectedImageId) ->
            val deployment =
                runCapture(
                    listOf(
                        "kubectl",
                        "--namespace",
                        namespace,
                        "get",
                        "deployment",
                        "--selector=app.kubernetes.io/instance=$release,app.kubernetes.io/component=$component",
                        "--output=jsonpath={.items[0].metadata.name}",
                    ),
                    Duration.ofMinutes(1),
                ).trim().takeIf(String::isNotBlank)
                    ?: error("Helm release $release has no $component deployment")
            runCapture(
                listOf("kubectl", "--namespace", namespace, "rollout", "status", "deployment/$deployment", "--timeout=5m"),
                Duration.ofMinutes(6),
            )
            val runningImageId =
                runCapture(
                    listOf(
                        "kubectl",
                        "--namespace",
                        namespace,
                        "get",
                        "pods",
                        "--selector=app.kubernetes.io/instance=$release,app.kubernetes.io/component=$component",
                        "--sort-by=.metadata.creationTimestamp",
                        "--output=jsonpath={.items[-1].status.containerStatuses[0].imageID}",
                    ),
                    Duration.ofMinutes(1),
                ).trim()
            require(runningImageId == expectedImageId) {
                "Helm $component pod is running stale image $runningImageId; node-local tag resolves to $expectedImageId. " +
                    "Restart the deployment after loading the local image before starting conformance."
            }
        }
    }

    private fun ensureKubernetesClientSecret() {
        val existing =
            runCapture(
                listOf(
                    "kubectl",
                    "--namespace",
                    namespace,
                    "get",
                    "secret",
                    clientSecretName,
                    "--ignore-not-found",
                    "--output=name",
                ),
                Duration.ofMinutes(1),
            ).trim()
        if (existing.isNotBlank()) return

        val tokenBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        val manifest =
            """
            apiVersion: v1
            kind: Secret
            metadata:
              name: $clientSecretName
              namespace: $namespace
            type: Opaque
            stringData:
              $clientSecretKey: "$token"
            """.trimIndent()
        runCommand(
            command = listOf("kubectl", "--namespace", namespace, "apply", "--filename=-"),
            output = evidenceDirectory.resolve("kubectl-client-secret-apply.log"),
            timeout = Duration.ofMinutes(1),
            standardInput = manifest,
        )
    }

    private fun startGrpcPortForward(component: String): String {
        val serviceName =
            runCapture(
                listOf(
                    "kubectl",
                    "--namespace",
                    namespace,
                    "get",
                    "service",
                    "--selector=app.kubernetes.io/instance=$release,app.kubernetes.io/component=$component",
                    "--output=jsonpath={.items[0].metadata.name}",
                ),
                Duration.ofMinutes(1),
            ).trim().takeIf(String::isNotBlank)
                ?: error("Helm release $release has no $component service")
        val localPort = ServerSocket(0).use { socket -> socket.localPort }
        val log = evidenceDirectory.resolve("kubectl-port-forward-$component.log")
        val process =
            ProcessBuilder(
                "kubectl",
                "--namespace",
                namespace,
                "port-forward",
                "--address=127.0.0.1",
                "service/$serviceName",
                "$localPort:9090",
            ).directory(infraWorkspace.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start()
        portForwardProcesses += process
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline) {
            require(process.isAlive) {
                "kubectl port-forward for $component exited early: ${Files.readString(log).takeLast(2_000)}"
            }
            if (runCatching { Socket("127.0.0.1", localPort).use { } }.isSuccess) {
                return "localhost:$localPort"
            }
            Thread.sleep(200)
        }
        error("Timed out waiting for Helm $component gRPC port-forward; output: $log")
    }

    private fun startGatewayPortForward() {
        val serviceRecord =
            runCapture(
                listOf(
                    "kubectl",
                    "get",
                    "service",
                    "--all-namespaces",
                    "--selector=gateway.envoyproxy.io/owning-gateway-namespace=$namespace",
                    "--output=jsonpath={range .items[*]}{.metadata.namespace}{' '}{.metadata.name}{'\\n'}{end}",
                ),
                Duration.ofMinutes(1),
            ).lineSequence().map(String::trim).filter(String::isNotBlank).singleOrNull()
                ?: error("Helm namespace $namespace has no unique Envoy Gateway service")
        val (serviceNamespace, serviceName) = serviceRecord.split(Regex("\\s+"), limit = 2)
        val log = evidenceDirectory.resolve("kubectl-port-forward-gateway.log")
        val process =
            ProcessBuilder(
                "kubectl",
                "--namespace",
                serviceNamespace,
                "port-forward",
                "--address=0.0.0.0",
                "service/$serviceName",
                "443:443",
            ).directory(infraWorkspace.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start()
        gatewayPortForwardProcess = process
        portForwardProcesses += process
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline) {
            require(process.isAlive) {
                "kubectl Gateway port-forward exited early: ${Files.readString(log).takeLast(2_000)}"
            }
            val output = runCatching { Files.readString(log) }.getOrDefault("")
            if (output.lineSequence().any { it.trim().matches(Regex("Forwarding from 0\\.0\\.0\\.0:443 -> [0-9]+")) }) {
                return
            }
            Thread.sleep(200)
        }
        error("Timed out waiting for isolated Helm Gateway port-forward; output: $log")
    }

    private fun awaitExternalGateway() {
        val probeUrl = platformUrl.trimEnd('/') + "/.well-known/oauth-authorization-server"
        val log = evidenceDirectory.resolve("external-gateway-probe.log")
        val deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos()
        var lastFailure = ""
        while (System.nanoTime() < deadline) {
            val command =
                mutableListOf(
                    "curl",
                    "--silent",
                    "--show-error",
                    "--fail",
                ).apply {
                    // Schannel treats a local CA without online revocation metadata as an error.
                    // Chain and hostname validation remain enabled through --cacert.
                    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                        add("--ssl-no-revoke")
                    }
                    addAll(listOf("--cacert", productTlsCaPath.toString(), probeUrl))
                }
            val process =
                ProcessBuilder(command).directory(infraWorkspace.toFile())
                    .redirectErrorStream(true)
                    .start()
            val completed = process.waitFor(10, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (completed && process.exitValue() == 0) {
                Files.writeString(log, "External production Gateway reachable: $probeUrl\n$output")
                return
            }
            lastFailure = output.takeLast(2_000)
            Thread.sleep(500)
        }
        Files.writeString(log, lastFailure)
        error("Timed out waiting for external production Helm Gateway at $probeUrl: $lastFailure")
    }

    private fun runCapture(
        command: List<String>,
        timeout: Duration,
    ): String {
        val process = ProcessBuilder(command).directory(infraWorkspace.toFile()).redirectErrorStream(true).start()
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            error("Timed out after $timeout: ${command.joinToString(" ")}")
        }
        val result = process.inputStream.bufferedReader().use { it.readText() }
        require(process.exitValue() == 0) {
            "Command failed with exit ${process.exitValue()}: ${command.joinToString(" ")}\n${result.takeLast(4_000)}"
        }
        return result
    }

    private fun runCommand(
        command: List<String>,
        output: Path,
        timeout: Duration,
        standardInput: String? = null,
    ) {
        Files.createDirectories(output.parent)
        val process =
            ProcessBuilder(command)
                .directory(infraWorkspace.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start()
        if (standardInput != null) {
            process.outputStream.bufferedWriter().use { it.write(standardInput) }
        }
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            error("Timed out after $timeout: ${command.joinToString(" ")}. Output: $output")
        }
        require(process.exitValue() == 0) {
            val tail = Files.readAllLines(output).takeLast(80).joinToString("\n")
            "Command failed with exit ${process.exitValue()}: ${command.joinToString(" ")}\n$tail"
        }
    }

    private fun sha256(vararg paths: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        paths.forEach { digest.update(Files.readAllBytes(it)) }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        val KUBERNETES_NAME = Regex("[a-z0-9](?:[-a-z0-9]{0,61}[a-z0-9])?")
        val IP_ADDRESS = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}|[0-9A-Fa-f:]+")
    }
}

internal fun oidfEnterpriseHelmTrustSecretCommand(
    namespace: String,
    trustStorePath: Path,
): List<String> =
    listOf(
        "kubectl",
        "--namespace",
        namespace,
        "create",
        "secret",
        "generic",
        "edk-oidf-suite-trust",
        "--from-file=truststore.p12=$trustStorePath",
        "--dry-run=client",
        "--output=yaml",
    )

internal fun oidfEnterpriseHelmUpgradeCommand(
    release: String,
    chartDirectory: Path,
    namespace: String,
    conformanceValues: Path,
    runtimeValues: Path,
): List<String> =
    listOf(
        "helm",
        "upgrade",
        release,
        chartDirectory.toString(),
        "--namespace",
        namespace,
        "--reset-then-reuse-values",
        "--values",
        conformanceValues.toString(),
        "--values",
        runtimeValues.toString(),
        "--wait",
        "--timeout",
        "10m",
    )

internal fun oidfEnterpriseHelmRuntimeValues(
    platformBaseDomain: String,
    hostGatewayIp: String,
    trustMaterialSha256: String,
    clientSecretName: String,
    clientSecretKey: String,
    clientSecretProjectedPath: String,
    clientSecretMountPath: String,
): String {
    require(platformBaseDomain.matches(Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?"))) {
        "Helm platform base domain must be a lowercase DNS name"
    }
    require(trustMaterialSha256.matches(Regex("[0-9a-f]{64}"))) {
        "Helm OIDF trust material hash must be a lowercase SHA-256 value"
    }
    require(hostGatewayIp.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}|[0-9A-Fa-f:]+"))) {
        "Helm OIDF host gateway must be an IP address"
    }
    require(clientSecretName.matches(Regex("[a-z0-9](?:[-a-z0-9]{0,61}[a-z0-9])?"))) {
        "Helm client Secret name must be a Kubernetes DNS label"
    }
    require(clientSecretKey.matches(Regex("[-._a-zA-Z0-9]+"))) { "Invalid Kubernetes Secret data key" }
    require(clientSecretProjectedPath.matches(Regex("[a-zA-Z0-9][-_./a-zA-Z0-9]+"))) {
        "Invalid Kubernetes Secret projected path"
    }
    require(clientSecretMountPath.startsWith("/") && !clientSecretMountPath.contains("..")) {
        "Kubernetes Secret mount path must be an absolute, normalized path"
    }
    return """
        global:
          platformBaseDomain: "$platformBaseDomain"
          hostAliases:
            - ip: "$hostGatewayIp"
              hostnames:
                - "host.testcontainers.internal"
        podAnnotations:
          "oidf.sphereon.com/trust-material-sha256": "$trustMaterialSha256"
        services:
          tenant-as:
            volumeMounts:
              - name: oidf-suite-trust
                mountPath: /app/oidf
                readOnly: true
              - name: conformance-client-secret
                mountPath: $clientSecretMountPath
                readOnly: true
            volumes:
              - name: oidf-suite-trust
                secret:
                  secretName: edk-oidf-suite-trust
                  items:
                    - key: truststore.p12
                      path: truststore.p12
              - name: conformance-client-secret
                secret:
                  secretName: $clientSecretName
                  items:
                    - key: $clientSecretKey
                      path: $clientSecretProjectedPath
          issuer:
            volumeMounts:
              - name: oidf-suite-trust
                mountPath: /app/oidf
                readOnly: true
              - name: conformance-client-secret
                mountPath: $clientSecretMountPath
                readOnly: true
            volumes:
              - name: oidf-suite-trust
                secret:
                  secretName: edk-oidf-suite-trust
                  items:
                    - key: truststore.p12
                      path: truststore.p12
              - name: conformance-client-secret
                secret:
                  secretName: $clientSecretName
                  items:
                    - key: $clientSecretKey
                      path: $clientSecretProjectedPath
    """.trimIndent() + "\n"
}

internal fun oidfPlatformBaseDomain(platformUrl: String): String {
    val uri = URI(platformUrl)
    require(uri.scheme == "https" && uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "Helm platform URL must be an HTTPS origin"
    }
    val labels = requireNotNull(uri.host) { "Helm platform URL must contain a DNS host" }
        .trimEnd('.')
        .lowercase()
        .split('.')
    require(labels.size >= 3 && labels.all { it.matches(Regex("[a-z0-9](?:[-a-z0-9]*[a-z0-9])?")) }) {
        "Helm platform URL host must contain an operator label and a valid base domain"
    }
    return labels.drop(1).joinToString(".")
}

internal fun oidfKubernetesSecretPhysicalPath(
    tenantId: String,
    instanceId: String,
    logicalKey: String,
): String {
    fun segment(value: String): String = value.trim().trim('/').replace('/', '_')
    val keyPath =
        logicalKey
            .trim()
            .lowercase()
            .split(Regex("[.\\-/\\s_]+"))
            .filter(String::isNotEmpty)
            .joinToString("/")
    require(tenantId.isNotBlank() && instanceId.isNotBlank() && keyPath.isNotBlank()) {
        "Kubernetes Secret physical addressing requires tenant, instance, and logical key"
    }
    return "tenants/${segment(tenantId)}/${segment(instanceId)}/$keyPath"
}

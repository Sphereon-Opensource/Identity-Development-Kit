/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

// Shared by deployed issuer, verifier, and wallet conformance drivers.

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.isRegularFile
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class OidfEnterprisePlatformConfigSpec(
    val infraWorkspace: Path,
    val suiteContextPath: Path,
    val enterpriseContextPath: Path,
    val credentialsPath: Path,
    val outputDirectory: Path,
    val deployment: String,
    val platformUrl: String,
    val tenantId: String,
    val kmsProviderId: String,
    val asInstanceId: String,
    val issuerInstanceId: String,
    val verifierInstanceId: String,
    val issuerSigningKeyAlias: String,
    val verifierSigningKeyAlias: String,
    val productBaseUrl: String,
    val productTlsCaPath: Path,
    val operatorUsername: String,
    val operatorPassword: String,
    val loginUsername: String,
    val loginPassword: String,
    val verifierSanDns: String,
    val productProfile: OidfProductProfile,
    val issuerClients: List<OidfIssuerScenarioClients>,
    val composeIngress: String? = null,
    val helmNamespace: String? = null,
    val helmRelease: String? = null,
    val helmHostGatewayIp: String? = null,
    val helmClientSecretName: String? = null,
    val helmClientSecretKey: String? = null,
    val helmClientSecretMountPath: String? = null,
    val helmClientSecretId: String? = null,
)

internal fun provisionOidfEnterprisePlatformConfig(spec: OidfEnterprisePlatformConfigSpec): Path {
    require(spec.deployment in setOf("enterprise-compose", "enterprise-helm")) {
        "Unsupported OIDF enterprise deployment: ${spec.deployment}"
    }
    val composeDeployment = spec.deployment == "enterprise-compose"
    require(composeDeployment == (spec.composeIngress != null)) {
        "composeIngress must be present only for enterprise-compose provisioning"
    }
    val helmMetadata =
        listOf(
            spec.helmNamespace,
            spec.helmRelease,
            spec.helmHostGatewayIp,
            spec.helmClientSecretName,
            spec.helmClientSecretKey,
            spec.helmClientSecretMountPath,
            spec.helmClientSecretId,
        )
    require(if (composeDeployment) helmMetadata.all { it == null } else helmMetadata.all { !it.isNullOrBlank() }) {
        "Helm deployment and Kubernetes Secret metadata must be present only for enterprise-helm provisioning"
    }
    if (!composeDeployment) {
        require(requireNotNull(spec.helmClientSecretId).matches(Regex("^sec_[A-Za-z0-9_-]{16,128}$"))) {
            "Helm Kubernetes Secret metadata must carry an opaque server-managed secretId"
        }
    }
    val provisioner =
        spec.infraWorkspace
            .resolve("deploy/edk/e2e/scripts/provision-oidf-conformance.mjs")
            .normalize()
    require(provisioner.startsWith(spec.infraWorkspace.toAbsolutePath().normalize())) {
        "Resolved OIDF provisioner escapes oidf.sut.infraWorkspace: $provisioner"
    }
    require(provisioner.isRegularFile()) { "Missing OIDF platform-config provisioner: $provisioner" }
    require(spec.suiteContextPath.isRegularFile()) { "Missing prepared OIDF suite context: ${spec.suiteContextPath}" }
    require(spec.enterpriseContextPath.isRegularFile()) {
        "Missing REST-resolved OIDF enterprise context: ${spec.enterpriseContextPath}"
    }
    require(spec.credentialsPath.isRegularFile()) { "Missing OIDF credentials file: ${spec.credentialsPath}" }

    Files.createDirectories(spec.outputDirectory)
    val requestPath = spec.outputDirectory.resolve("oidf-provision-request.json")
    Files.writeString(
        requestPath,
        buildJsonObject {
            put("schemaVersion", 4)
            put("deployment", spec.deployment)
            put("profile", spec.productProfile.name)
            put(
                "expectedIssuerClients",
                buildJsonArray {
                    spec.issuerClients.forEach { client ->
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
            put("productTlsCaCert", spec.productTlsCaPath.toString())
            if (!composeDeployment) {
                put(
                    "kubernetesSecretProvider",
                    buildJsonObject {
                        put("namespace", requireNotNull(spec.helmNamespace))
                        put("secretName", requireNotNull(spec.helmClientSecretName))
                        put("dataKey", requireNotNull(spec.helmClientSecretKey))
                        put("mountPath", requireNotNull(spec.helmClientSecretMountPath))
                        put("secretId", requireNotNull(spec.helmClientSecretId))
                    },
                )
            }
            put(
                "credentialConfigurationIds",
                buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("EuPid"))
                    add(kotlinx.serialization.json.JsonPrimitive("Mdl"))
                },
            )
        }.toString(),
    )
    val command =
        listOf(
            "node",
            provisioner.toString(),
            "--request",
            requestPath.toString(),
            "--credentials",
            spec.credentialsPath.toString(),
            "--enterprise-context",
            spec.enterpriseContextPath.toString(),
            "--suite-context",
            spec.suiteContextPath.toString(),
            "--output",
            spec.outputDirectory.toString(),
        )
    val output = spec.outputDirectory.resolve("platform-config-provision.log")
    val process =
        ProcessBuilder(command)
            .directory(spec.infraWorkspace.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .apply {
                environment()["NODE_EXTRA_CA_CERTS"] = spec.productTlsCaPath.toString()
                val existingNodeOptions = environment()["NODE_OPTIONS"].orEmpty().trim()
                if (!existingNodeOptions.contains("--dns-result-order=")) {
                    environment()["NODE_OPTIONS"] =
                        listOf(existingNodeOptions, "--dns-result-order=ipv4first")
                            .filter(String::isNotBlank)
                            .joinToString(" ")
                }
            }
            .start()
    if (!process.waitFor(Duration.ofMinutes(3).toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly()
        error("Timed out provisioning OIDF platform config. Output: $output")
    }
    require(process.exitValue() == 0) {
        val tail = Files.readAllLines(output).takeLast(80).joinToString("\n")
        "OIDF platform-config provisioning failed with exit ${process.exitValue()}: ${command.joinToString(" ")}\n$tail"
    }
    return spec.outputDirectory.resolve("product-signing-trust-anchors.pem").also { trustAnchor ->
        require(trustAnchor.isRegularFile()) {
            "OIDF provisioner did not export tenant-KMS X.509 trust anchors: $trustAnchor"
        }
    }
}

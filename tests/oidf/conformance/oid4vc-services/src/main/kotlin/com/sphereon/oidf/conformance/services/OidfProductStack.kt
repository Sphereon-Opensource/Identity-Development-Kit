/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

import com.sphereon.oidf.conformance.OidfConformanceSuiteClient
import com.sphereon.oidf.conformance.OidfSuiteConfigPreprocessor
import com.sphereon.oidf.conformance.OidfSuiteEnvironment
import com.sphereon.oidf.conformance.OidfSuiteLock
import com.sphereon.oidf.conformance.OidfSuitePorts
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.Base64
import javax.net.ssl.SSLContext
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class OidfProductProfile {
    FINAL,
    HAIP,
}

private fun pemCertificatesAsBase64(pem: String): List<String> =
    Regex(
        "-----BEGIN CERTIFICATE-----\\s*(.*?)\\s*-----END CERTIFICATE-----",
        RegexOption.DOT_MATCHES_ALL,
    ).findAll(pem)
        .map { match ->
            val encoded = match.groupValues[1].replace(Regex("\\s+"), "")
            Base64.getDecoder().decode(encoded)
            encoded
        }.toList()

data class OidfProductStackSpec(
    val profile: OidfProductProfile,
)

/**
 * OIDF suite attached to an already provisioned enterprise deployment.
 *
 * The product side is deliberately not constructed here. CI must deploy the
 * complete EDK system with the production Compose or Helm artifacts, including
 * its databases, platform/config plane, tenant services, and public gateway.
 * This fixture owns only the ephemeral OIDF dependency and verifies that the
 * selected issuer and verifier instances are reachable through their public
 * tenant-host testing surfaces.
 */
class OidfProductStack private constructor(
    val spec: OidfProductStackSpec,
    val deployment: String,
    val suiteEnvironment: OidfSuiteEnvironment,
    val configPreprocessor: OidfSuiteConfigPreprocessor,
    val publicBaseUrl: String,
    val productCaPem: String,
    val kmsProviderId: String,
    val walletSigningKeyAlias: String,
    val walletSigningCertificateChain: List<String>,
    val productSslContext: SSLContext,
    val issuerInstanceId: String,
    val verifierInstanceId: String,
    val loginUsername: String,
    val loginPassword: String,
    val verifierSanDns: String,
    val walletInteractionGrpcEndpoint: String,
    val walletUnitGrpcEndpoint: String,
    private val platformUrl: String,
    val tenantId: String,
    private val operatorUsername: String,
    private val operatorPassword: String,
    private val evidenceDirectory: Path,
    private val deploymentProfile: OidfEnterpriseDeploymentProfile?,
    private val deploymentLease: OidfDeploymentLease,
) : AutoCloseable {
    val suite: OidfConformanceSuiteClient = suiteEnvironment.client()

    /** Obtain a fresh tenant-scoped production token without exposing operator credentials. */
    fun tenantRuntimeToken(audiences: Set<String>): String =
        OidfEnterpriseOperatorTokenClient(
            platformUrl = platformUrl,
            tenantId = tenantId,
            operatorUsername = operatorUsername,
            operatorPassword = operatorPassword,
            sslContext = productSslContext,
        ).tenantRuntimeToken(audiences)

    override fun close() {
        val failures = mutableListOf<String>()
        runCatching { deploymentProfile?.exportServiceEvidence() }
            .onFailure { failures += "product evidence export: ${it.stackTraceToString()}" }
        runCatching { suiteEnvironment.exportContainerLogs(evidenceDirectory.resolve("suite-containers")) }
            .onFailure { failures += "suite evidence export: ${it.stackTraceToString()}" }
        runCatching { suiteEnvironment.close() }
            .onFailure { failures += "suite environment close: ${it.stackTraceToString()}" }
        runCatching { deploymentProfile?.close() }
            .onFailure { failures += "deployment profile close: ${it.stackTraceToString()}" }
        runCatching { deploymentLease.close() }
            .onFailure { failures += "deployment lease release: ${it.stackTraceToString()}" }
        check(failures.isEmpty()) { failures.joinToString("\n") }
    }

    companion object {
        fun start(
            spec: OidfProductStackSpec,
            evidenceDirectory: Path,
            lock: OidfSuiteLock = OidfSuiteLock.load(),
        ): OidfProductStack {
            requireFreshEvidenceDirectory(evidenceDirectory)
            Files.createDirectories(evidenceDirectory)
            val tlsCaPath = requiredFileProperty("oidf.sut.tlsCaCert")
            val suiteContextPath = requiredFileProperty("oidf.sut.suiteContext")
            val enterpriseContextPath = requiredFileProperty("oidf.sut.enterpriseContext")
            val credentialsPath = requiredFileProperty("oidf.sut.credentials")
            val infraWorkspace = requiredDirectoryProperty("oidf.sut.infraWorkspace")
            val enterpriseContext = readEnterpriseContext(enterpriseContextPath)
            val deployment = enterpriseContext.deployment
            val composeDeployment = deployment == "enterprise-compose"
            val composeRuntime =
                if (composeDeployment) requiredProperty("oidf.sut.composeRuntime") else null
            val publicBaseUrl = validatedPublicBaseUrl(enterpriseContext.productBaseUrl)
            val platformUrl = validatedPublicBaseUrl(enterpriseContext.platformUrl)
            val tlsCaPem = Files.readString(tlsCaPath)
            val tenantId = enterpriseContext.tenantId
            require(
                !tenantId.equals("platform", ignoreCase = true) &&
                    !enterpriseContext.tenantSlug.equals("platform", ignoreCase = true),
            ) {
                "The restricted platform management tenant cannot host OIDF issuer or verifier instances"
            }
            val kmsProviderId = enterpriseContext.kmsProviderId
            val asInstanceId = enterpriseContext.asInstanceId
            val issuerInstanceId = enterpriseContext.issuerInstanceId
            val verifierInstanceId = enterpriseContext.verifierInstanceId
            val issuerSigningKeyAlias = "oidf-issuer-signing-x5c"
            val verifierSigningKeyAlias = "oidf-verifier-signing-x5c"
            val walletSigningKeyAlias = "oidf-wallet-signing-x5c"
            val credentials = readCredentials(credentialsPath)
            val loginUsername = credentials.testSubjectUsername
            val loginPassword = credentials.testSubjectPassword
            val verifierSanDns = enterpriseContext.verifierSanDns
            val operatorUsername = credentials.operatorUsername
            val operatorPassword = credentials.operatorPassword
            val suitePorts = readSuitePorts(suiteContextPath).also {
                require(it.all.distinct().size == it.all.size) {
                    "OIDF suite context ports must be distinct: ${it.all}"
                }
            }
            val deploymentLease = OidfDeploymentLease.acquire(
                infraWorkspace.resolve("deploy/edk/e2e/build/oidf-conformance/.production-deployment.lock"),
            )

            var suite: OidfSuiteEnvironment? = null
            var deploymentProfile: OidfEnterpriseDeploymentProfile? = null
            try {
                suite =
                    OidfSuiteEnvironment.start(
                        lock = lock,
                        fixedHostPorts = suitePorts,
                        startupEvidenceDirectory = evidenceDirectory.resolve("suite-startup"),
                        additionalTrustedCertificates = mapOf("edk-enterprise-edge" to tlsCaPem),
                        sutHostName = URI.create(publicBaseUrl).host,
                    )
                val preprocessor =
                    OidfSuiteConfigPreprocessor(
                        suiteDir = suite.suiteSourceDirectory,
                        baseUrl = suite.advertisedBaseUrl,
                        localBaseUrl = suite.apiBaseUrl,
                        mtlsBaseUrl = suite.advertisedMtlsBaseUrl,
                    )
                deploymentProfile =
                    if (composeDeployment) {
                        OidfEnterpriseComposeProfile(
                            infraWorkspace = infraWorkspace,
                            composeIngress = requireNotNull(enterpriseContext.composeIngress),
                            composeRuntime = requireNotNull(composeRuntime),
                            suiteContextPath = suiteContextPath,
                            enterpriseContextPath = enterpriseContextPath,
                            credentialsPath = credentialsPath,
                            productProfile = spec.profile,
                            platformUrl = platformUrl,
                            tenantId = tenantId,
                            kmsProviderId = kmsProviderId,
                            asInstanceId = asInstanceId,
                            issuerInstanceId = issuerInstanceId,
                            verifierInstanceId = verifierInstanceId,
                            issuerSigningKeyAlias = issuerSigningKeyAlias,
                            verifierSigningKeyAlias = verifierSigningKeyAlias,
                            productBaseUrl = publicBaseUrl,
                            productTlsCaPath = tlsCaPath,
                            operatorUsername = operatorUsername,
                            operatorPassword = operatorPassword,
                            loginUsername = loginUsername,
                            loginPassword = loginPassword,
                            verifierSanDns = verifierSanDns,
                            suiteEnvironment = suite,
                            configPreprocessor = preprocessor,
                            evidenceDirectory = evidenceDirectory.resolve("enterprise-compose"),
                        )
                    } else {
                        OidfEnterpriseHelmProfile(
                            infraWorkspace = infraWorkspace,
                            namespace = requireNotNull(enterpriseContext.helm).namespace,
                            release = enterpriseContext.helm.release,
                            hostGatewayIp = enterpriseContext.helm.hostGatewayIp,
                            edgeMode = enterpriseContext.helm.edgeMode,
                            suiteContextPath = suiteContextPath,
                            enterpriseContextPath = enterpriseContextPath,
                            credentialsPath = credentialsPath,
                            productProfile = spec.profile,
                            platformUrl = platformUrl,
                            tenantId = tenantId,
                            kmsProviderId = kmsProviderId,
                            asInstanceId = asInstanceId,
                            issuerInstanceId = issuerInstanceId,
                            verifierInstanceId = verifierInstanceId,
                            issuerSigningKeyAlias = issuerSigningKeyAlias,
                            verifierSigningKeyAlias = verifierSigningKeyAlias,
                            productBaseUrl = publicBaseUrl,
                            productTlsCaPath = tlsCaPath,
                            operatorUsername = operatorUsername,
                            operatorPassword = operatorPassword,
                            loginUsername = loginUsername,
                            loginPassword = loginPassword,
                            verifierSanDns = verifierSanDns,
                            suiteEnvironment = suite,
                            configPreprocessor = preprocessor,
                            evidenceDirectory = evidenceDirectory.resolve("enterprise-helm"),
                        )
                    }
                deploymentProfile.apply()
                val signingTrustAnchorPath = deploymentProfile.productSigningTrustAnchorPath
                val signingTrustAnchorPem = Files.readString(signingTrustAnchorPath)
                val walletSigningChainPath = signingTrustAnchorPath.parent.resolve("product-wallet-signing-chain.pem")
                require(walletSigningChainPath.isRegularFile()) {
                    "Provisioning did not export the wallet x5c chain read back from the tenant KMS: $walletSigningChainPath"
                }
                val walletSigningCertificateChain =
                    pemCertificatesAsBase64(Files.readString(walletSigningChainPath))
                require(walletSigningCertificateChain.size >= 2) {
                    "The deployed wallet signer must have a leaf-to-root x5c chain"
                }
                val productSslContext =
                    OidfIssuerAuthorizationBrowser.combinedTrust(
                        tlsCaPem,
                        suite.caCertificatePem,
                    )
                val stack =
                    OidfProductStack(
                        spec = spec,
                        deployment = deployment,
                        suiteEnvironment = suite,
                        configPreprocessor = preprocessor,
                        publicBaseUrl = publicBaseUrl,
                        productCaPem = signingTrustAnchorPem,
                        kmsProviderId = kmsProviderId,
                        walletSigningKeyAlias = walletSigningKeyAlias,
                        walletSigningCertificateChain = walletSigningCertificateChain,
                        productSslContext = productSslContext,
                        issuerInstanceId = issuerInstanceId,
                        verifierInstanceId = verifierInstanceId,
                        loginUsername = loginUsername,
                        loginPassword = loginPassword,
                        verifierSanDns = verifierSanDns,
                        walletInteractionGrpcEndpoint = deploymentProfile.walletInteractionGrpcEndpoint,
                        walletUnitGrpcEndpoint = deploymentProfile.walletUnitGrpcEndpoint,
                        platformUrl = platformUrl,
                        tenantId = tenantId,
                        operatorUsername = operatorUsername,
                        operatorPassword = operatorPassword,
                        evidenceDirectory = evidenceDirectory,
                        deploymentProfile = deploymentProfile,
                        deploymentLease = deploymentLease,
                    )
                stack.verifyPublicDeployment()
                stack.writeDeploymentEvidence(
                    platformUrl = platformUrl,
                    tenantId = tenantId,
                    asInstanceId = asInstanceId,
                    suiteContextPath = suiteContextPath,
                    tlsCaPath = tlsCaPath,
                    signingTrustAnchorPath = signingTrustAnchorPath,
                )
                return stack
            } catch (error: Throwable) {
                runCatching { deploymentProfile?.exportServiceEvidence() }
                runCatching { suite?.exportContainerLogs(evidenceDirectory.resolve("suite-containers")) }
                runCatching { suite?.close() }
                runCatching { deploymentProfile?.close() }
                runCatching { deploymentLease.close() }
                throw error
            }
        }

        private fun requireFreshEvidenceDirectory(evidenceDirectory: Path) {
            if (!Files.exists(evidenceDirectory)) return
            val hasExistingEvidence = Files.list(evidenceDirectory).use { entries -> entries.findAny().isPresent }
            require(!hasExistingEvidence) {
                "OIDF evidence directory already contains files and cannot be reused: $evidenceDirectory"
            }
        }

        private fun requiredProperty(name: String): String =
            checkNotNull(System.getProperty(name)?.trim()?.takeIf(String::isNotEmpty)) {
                "Missing -D$name. Service-role conformance requires a provisioned enterprise Compose or Helm deployment."
            }

        private fun requiredFileProperty(name: String): Path =
            Path(requiredProperty(name)).toAbsolutePath().normalize().also {
                require(it.isRegularFile()) { "$name is not a file: $it" }
            }

        private fun requiredDirectoryProperty(name: String): Path =
            Path(requiredProperty(name)).toAbsolutePath().normalize().also {
                require(Files.isDirectory(it)) { "$name is not a directory: $it" }
            }

        private data class Credentials(
            val operatorUsername: String,
            val operatorPassword: String,
            val testSubjectUsername: String,
            val testSubjectPassword: String,
        )

        private data class HelmDeploymentContext(
            val namespace: String,
            val release: String,
            val hostGatewayIp: String,
            val edgeMode: Boolean,
        )

        private data class EnterpriseContext(
            val deployment: String,
            val platformUrl: String,
            val tenantId: String,
            val tenantSlug: String,
            val kmsProviderId: String,
            val composeIngress: String?,
            val helm: HelmDeploymentContext?,
            val asInstanceId: String,
            val issuerInstanceId: String,
            val verifierInstanceId: String,
            val productBaseUrl: String,
            val verifierSanDns: String,
        )

        private fun readEnterpriseContext(path: Path): EnterpriseContext {
            val root = Json.parseToJsonElement(Files.readString(path)).jsonObject
            require(root["schemaVersion"]?.jsonPrimitive?.content == "2") {
                "Unsupported REST-resolved OIDF enterprise context schema in $path"
            }
            fun required(name: String): String =
                root[name]
                    ?.jsonPrimitive
                    ?.content
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: error("REST-resolved OIDF enterprise context $path has no $name")

            val deployment = required("deployment")
            require(deployment in setOf("enterprise-compose", "enterprise-helm")) {
                "OIDF enterprise context deployment must be enterprise-compose or enterprise-helm, got '$deployment'"
            }
            val composeIngress =
                root["composeIngress"]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotEmpty)
            val helm =
                root["helm"]?.jsonObject?.let { document ->
                    fun requiredHelm(name: String): String =
                        document[name]
                            ?.jsonPrimitive
                            ?.content
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?: error("REST-resolved OIDF enterprise context $path has no helm.$name")
                    HelmDeploymentContext(
                        namespace = requiredHelm("namespace"),
                        release = requiredHelm("release"),
                        hostGatewayIp = requiredHelm("hostGatewayIp"),
                        edgeMode =
                            document["edgeMode"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                                ?: error("REST-resolved OIDF enterprise context $path has no boolean helm.edgeMode"),
                    )
                }
            if (deployment == "enterprise-compose") {
                require(composeIngress != null && helm == null) {
                    "Compose enterprise context must contain composeIngress and no helm metadata"
                }
            } else {
                require(helm != null && composeIngress == null) {
                    "Helm enterprise context must contain helm metadata and no composeIngress"
                }
            }
            return EnterpriseContext(
                deployment = deployment,
                platformUrl = required("platformUrl"),
                tenantId = required("tenantId"),
                tenantSlug = required("tenantSlug"),
                kmsProviderId = required("kmsProviderId"),
                composeIngress = composeIngress,
                helm = helm,
                asInstanceId = required("asInstanceId"),
                issuerInstanceId = required("issuerInstanceId"),
                verifierInstanceId = required("verifierInstanceId"),
                productBaseUrl = required("productBaseUrl"),
                verifierSanDns = required("verifierSanDns"),
            )
        }

        private fun readCredentials(path: Path): Credentials {
            val root = Json.parseToJsonElement(Files.readString(path)).jsonObject
            require(root["schemaVersion"]?.jsonPrimitive?.content == "1") {
                "Unsupported OIDF credentials schema in $path"
            }
            fun required(section: String, name: String): String =
                root[section]
                    ?.jsonObject
                    ?.get(name)
                    ?.jsonPrimitive
                    ?.content
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: error("OIDF credentials file $path has no $section.$name")
            return Credentials(
                operatorUsername = required("platformOperator", "username"),
                operatorPassword = required("platformOperator", "password"),
                testSubjectUsername = required("testSubject", "username"),
                testSubjectPassword = required("testSubject", "password"),
            )
        }

        private fun readSuitePorts(path: Path): OidfSuitePorts {
            val root = Json.parseToJsonElement(Files.readString(path)).jsonObject
            require(root["schemaVersion"]?.jsonPrimitive?.content == "2") {
                "Unsupported OIDF suite context schema in $path"
            }
            val ports = root["ports"]?.jsonObject ?: error("OIDF suite context $path has no ports")
            fun requiredPort(name: String): Int {
                val value = ports[name]?.jsonPrimitive?.content?.toIntOrNull()
                return value?.takeIf { it in 1024..65535 }
                    ?: error("OIDF suite context $path has invalid ports.$name")
            }
            return OidfSuitePorts(
                https = requiredPort("https"),
                mtls = requiredPort("mtls"),
                alternate = requiredPort("alternate"),
            )
        }

        private fun validatedPublicBaseUrl(value: String): String {
            val normalized = value.trimEnd('/')
            val uri = URI.create(normalized)
            require(uri.scheme == "https") { "OIDF product base URL must use verified HTTPS: $normalized" }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "OIDF product base URL must be an HTTPS origin: $normalized"
            }
            require(uri.path.isNullOrEmpty()) { "OIDF product base URL must not contain a path: $normalized" }
            return normalized
        }
    }

    private fun verifyPublicDeployment() {
        val client =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .sslContext(productSslContext)
                .build()
        verifyBootstrap(client, "oid4vci", issuerInstanceId, "issuer")
        verifyBootstrap(client, "oid4vp", verifierInstanceId, "verifier")
    }

    private fun verifyBootstrap(
        client: HttpClient,
        protocol: String,
        instanceId: String,
        role: String,
    ) {
        val encodedInstanceId = URLEncoder.encode(instanceId, StandardCharsets.UTF_8).replace("+", "%20")
        val uri = URI.create("$publicBaseUrl/api/$protocol/v1/testing/instances/$encodedInstanceId/bootstrap")
        val response =
            client.send(
                HttpRequest
                    .newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        require(response.statusCode() == 200) {
            "Enterprise $role testing bootstrap returned ${response.statusCode()} at $uri: ${response.body().take(2_000)}"
        }
        val bootstrap = Json.parseToJsonElement(response.body()).jsonObject
        require(bootstrap["instanceId"]?.jsonPrimitive?.content == instanceId) {
            "Enterprise $role bootstrap did not resolve requested instance '$instanceId': ${response.body().take(2_000)}"
        }
        require(bootstrap["publicBaseUrl"]?.jsonPrimitive?.content?.trimEnd('/') == publicBaseUrl) {
            "Enterprise $role bootstrap publicBaseUrl does not match $publicBaseUrl: ${response.body().take(2_000)}"
        }
    }

    private fun writeDeploymentEvidence(
        platformUrl: String?,
        tenantId: String?,
        asInstanceId: String?,
        suiteContextPath: Path?,
        tlsCaPath: Path,
        signingTrustAnchorPath: Path,
    ) {
        Files.writeString(
            evidenceDirectory.resolve("product-deployment.json"),
            buildJsonObject {
                put("deployment", deployment)
                platformUrl?.let { put("platformUrl", it) }
                put("publicBaseUrl", publicBaseUrl)
                tenantId?.let { put("tenantId", it) }
                asInstanceId?.let { put("asInstanceId", it) }
                put("issuerInstanceId", issuerInstanceId)
                put("verifierInstanceId", verifierInstanceId)
                put("loginUsername", loginUsername)
                put("verifierSanDns", verifierSanDns)
                suiteContextPath?.let { put("suiteContext", it.toString()) }
                put("tlsCaCert", tlsCaPath.toString())
                put("signingTrustAnchorCert", signingTrustAnchorPath.toString())
            }.toString(),
        )
    }
}

/**
 * Cross-process lease for the mutable production deployment and fixed OIDF suite ports.
 *
 * Compose/Helm application and REST provisioning rotate deployment-scoped material. Two JVMs
 * doing that concurrently can make one row authenticate with another row's newly written secret,
 * while both evidence directories appear locally coherent. Fail fast instead of producing
 * unauditable, race-dependent conformance evidence.
 */
internal class OidfDeploymentLease private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    override fun close() {
        try {
            if (lock.isValid) lock.release()
        } finally {
            channel.close()
        }
    }

    companion object {
        fun acquire(path: Path): OidfDeploymentLease {
            Files.createDirectories(path.parent)
            val channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
            )
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                error(
                    "Another OIDF process owns the production deployment lease at $path; " +
                        "do not run Compose/Helm conformance rows concurrently",
                )
            }
            val owner = "pid=${ProcessHandle.current().pid()}\n"
            channel.truncate(0)
            channel.position(0)
            channel.write(ByteBuffer.wrap(owner.encodeToByteArray()))
            channel.force(true)
            return OidfDeploymentLease(channel, lock)
        }
    }
}

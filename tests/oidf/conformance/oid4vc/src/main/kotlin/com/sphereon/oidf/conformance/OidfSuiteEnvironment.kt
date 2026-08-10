/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance

import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Duration
import java.util.Comparator
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

data class OidfSuitePorts(
    val https: Int,
    val mtls: Int,
    val alternate: Int,
) {
    val all: List<Int> = listOf(https, mtls, alternate)
}

/**
 * Ephemeral, pinned OIDF suite stack for local runs and CI.
 *
 * The stack is deliberately tunnel-free: callbacks use the Testcontainers
 * host gateway, and nginx binds the three suite TLS ports on the host. The
 * generated nginx certificate is extracted and trusted explicitly; neither
 * chain validation nor hostname validation is disabled.
 */
class OidfSuiteEnvironment private constructor(
    val lock: OidfSuiteLock,
    val ports: OidfSuitePorts,
    private val network: Network,
    private val mongo: GenericContainer<*>,
    private val server: GenericContainer<*>,
    private val nginx: FixedPortContainer,
    val caCertificatePem: String,
    val sslContext: SSLContext,
    val trustStorePath: Path,
    val suiteSourceDirectory: Path,
    private val tempDirectory: Path,
) : AutoCloseable {
    val apiBaseUrl: String = "https://localhost:${ports.https}"
    val advertisedBaseUrl: String = "https://host.testcontainers.internal:${ports.https}"
    val advertisedMtlsBaseUrl: String = "https://host.testcontainers.internal:${ports.mtls}"
    val advertisedAlternateBaseUrl: String = "https://host.testcontainers.internal:${ports.alternate}"
    val imageIds: Map<String, String> =
        mapOf(
            "server" to checkNotNull(server.containerInfo.imageId),
            "nginx" to checkNotNull(nginx.containerInfo.imageId),
            "mongo" to checkNotNull(mongo.containerInfo.imageId),
        )

    fun client(): OidfConformanceSuiteClient =
        OidfConformanceSuiteClient(
            baseUrl = apiBaseUrl,
            sslContext = sslContext,
            originAliases = mapOf(advertisedBaseUrl to apiBaseUrl),
            browserOrigin = advertisedBaseUrl,
        )

    fun installJvmTrustStore(): JvmTrustStoreScope = JvmTrustStoreScope(trustStorePath)

    fun exportContainerLogs(outputDirectory: Path) {
        writeContainerLogs(outputDirectory, mongo, server, nginx)
    }

    override fun close() {
        val failures = mutableListOf<String>()
        listOf(
            "server" to server,
            "nginx" to nginx,
            "mongo" to mongo,
        ).forEach { (name, container) ->
            runCatching { container.stop() }
                .onFailure { failures += "$name stop: ${it.stackTraceToString()}" }
        }
        runCatching { network.close() }
            .onFailure { failures += "network close: ${it.stackTraceToString()}" }
        runCatching { deleteOwnedTempDirectory(tempDirectory) }
            .onFailure { failures += "temporary directory cleanup: ${it.stackTraceToString()}" }
        check(failures.isEmpty()) { failures.joinToString("\n") }
    }

    companion object {
        private const val NGINX_CERTIFICATE_PATH = "/etc/ssl/certs/nginx-selfsigned.crt"
        private val SUT_HOST_NAME =
            Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+")

        fun start(
            lock: OidfSuiteLock = OidfSuiteLock.load(),
            fixedHostPorts: OidfSuitePorts? = null,
            startupEvidenceDirectory: Path? = null,
            additionalTrustedCertificates: Map<String, String> = emptyMap(),
            sutHostName: String? = null,
        ): OidfSuiteEnvironment {
            sutHostName?.let {
                require(SUT_HOST_NAME.matches(it)) { "SUT hostname must be a DNS hostname: $it" }
            }
            val network = Network.newNetwork()
            val mongo =
                GenericContainer(DockerImageName.parse(lock.mongoImage))
                    .withNetwork(network)
                    .withNetworkAliases("mongodb")
            val nginx =
                FixedPortContainer(DockerImageName.parse(lock.nginxImage))
                    .withNetwork(network)
                    .withNetworkAliases("nginx")
                    .apply {
                        if (fixedHostPorts == null) {
                            withExposedPorts(CONTAINER_HTTPS_PORT, CONTAINER_MTLS_PORT, CONTAINER_ALTERNATE_PORT)
                        } else {
                            withFixedPort(fixedHostPorts.https, CONTAINER_HTTPS_PORT)
                            withFixedPort(fixedHostPorts.mtls, CONTAINER_MTLS_PORT)
                            withFixedPort(fixedHostPorts.alternate, CONTAINER_ALTERNATE_PORT)
                        }
                    }
                    .waitingFor(
                        Wait
                            .forListeningPorts()
                            .withStartupTimeout(Duration.ofMinutes(2)),
                    )
            var tempDirectory: Path? = null
            var server: GenericContainer<*>? = null

            try {
                mongo.start()
                nginx.start()
                val ports =
                    fixedHostPorts ?: OidfSuitePorts(
                        https = nginx.getMappedPort(CONTAINER_HTTPS_PORT),
                        mtls = nginx.getMappedPort(CONTAINER_MTLS_PORT),
                        alternate = nginx.getMappedPort(CONTAINER_ALTERNATE_PORT),
                    )
                Testcontainers.exposeHostPorts(*ports.all.toIntArray())

                val certResult = nginx.execInContainer("cat", NGINX_CERTIFICATE_PATH)
                require(certResult.exitCode == 0) {
                    "Unable to extract OIDF nginx certificate: ${certResult.stderr}"
                }
                val pem = certResult.stdout
                val sslContext = trustOnly(pem)
                val ownedTempDirectory = Files.createTempDirectory("oidf-suite-trust-").toAbsolutePath().normalize()
                tempDirectory = ownedTempDirectory
                val trustStorePath = ownedTempDirectory.resolve("oidf-suite-truststore.p12")
                writeTrustStore(
                    certificates = mapOf("oidf-suite-nginx" to pem) + additionalTrustedCertificates,
                    target = trustStorePath,
                )
                val runningServer =
                    GenericContainer(DockerImageName.parse(lock.serverImage))
                        .withNetwork(network)
                        .withNetworkAliases("server")
                        .apply {
                            // *.localtest.me resolves to container loopback by default.
                            // Preserve the SUT hostname for TLS and Host validation while
                            // routing it to the host-side production Helm Gateway.
                            sutHostName?.let { withExtraHost(it, "host-gateway") }
                        }
                        .withEnv("MONGODB_HOST", "mongodb")
                        .withEnv("BASE_URL", "https://host.testcontainers.internal:${ports.https}")
                        .withEnv("BASE_MTLS_URL", "https://host.testcontainers.internal:${ports.mtls}")
                        .withEnv("OIDC_GITLAB_CLIENTID", "fapi-test-suite-client")
                        .withEnv("OIDC_GITLAB_CLIENTSECRET", "fapi-test-suite-secret")
                        .withEnv("OIDC_GOOGLE_CLIENTID", "fapi-test-suite-client")
                        .withEnv("OIDC_GOOGLE_SECRET", "fapi-test-suite-secret")
                        .withEnv(
                            "JAVA_EXTRA_ARGS",
                            "-Dfintechlabs.devmode=true " +
                                "-Dfintechlabs.external_url_override=https://host.testcontainers.internal:${ports.https} " +
                                "-Djavax.net.ssl.trustStore=$CONTAINER_TRUST_STORE_PATH " +
                                "-Djavax.net.ssl.trustStorePassword=${String(TRUST_STORE_PASSWORD)} " +
                                "-Djavax.net.ssl.trustStoreType=PKCS12",
                        )
                        .withCopyFileToContainer(MountableFile.forHostPath(trustStorePath), CONTAINER_TRUST_STORE_PATH)
                        .waitingFor(
                            Wait
                                .forLogMessage(".*Started Application in.*\\n", 1)
                                .withStartupTimeout(Duration.ofMinutes(5)),
                        )
                server = runningServer
                runningServer.start()

                val suiteSourceDirectory = ownedTempDirectory.resolve("suite-source")
                OidfSuiteConfigPreprocessor.requiredSourceFiles.forEach { relativePath ->
                    val result = runningServer.execInContainer("cat", "/opt/oidf-suite-source/$relativePath")
                    require(result.exitCode == 0) {
                        "Unable to extract locked OIDF suite input $relativePath: ${result.stderr}"
                    }
                    val target = suiteSourceDirectory.resolve(relativePath)
                    Files.createDirectories(target.parent)
                    Files.writeString(target, result.stdout)
                }

                val environment =
                    OidfSuiteEnvironment(
                        lock = lock,
                        ports = ports,
                        network = network,
                        mongo = mongo,
                        server = runningServer,
                        nginx = nginx,
                        caCertificatePem = pem,
                        sslContext = sslContext,
                        trustStorePath = trustStorePath,
                        suiteSourceDirectory = suiteSourceDirectory,
                        tempDirectory = ownedTempDirectory,
                    )
                require(environment.client().healthCheck()) {
                    "OIDF suite did not become API-ready at ${environment.apiBaseUrl}"
                }
                environment.client().requireRequiredOid4vcPlans()
                return environment
            } catch (error: Throwable) {
                startupEvidenceDirectory?.let { directory ->
                    runCatching { writeContainerLogs(directory, mongo, server, nginx) }
                }
                runCatching { nginx.stop() }
                runCatching { server?.stop() }
                runCatching { mongo.stop() }
                runCatching { network.close() }
                tempDirectory?.let { runCatching { deleteOwnedTempDirectory(it) } }
                throw error
            }
        }

        private fun writeContainerLogs(
            outputDirectory: Path,
            mongo: GenericContainer<*>,
            server: GenericContainer<*>?,
            nginx: GenericContainer<*>,
        ) {
            Files.createDirectories(outputDirectory)
            Files.writeString(outputDirectory.resolve("mongodb.log"), runCatching { mongo.logs }.getOrDefault(""))
            Files.writeString(outputDirectory.resolve("server.log"), runCatching { server?.logs.orEmpty() }.getOrDefault(""))
            Files.writeString(outputDirectory.resolve("nginx.log"), runCatching { nginx.logs }.getOrDefault(""))
        }

        private fun certificate(pem: String) =
            CertificateFactory
                .getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(pem.toByteArray(StandardCharsets.US_ASCII)))

        private fun trustOnly(pem: String): SSLContext {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
            keyStore.setCertificateEntry("oidf-suite-nginx", certificate(pem))
            val trustManagers =
                TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    .apply { init(keyStore) }
                    .trustManagers
            return SSLContext.getInstance("TLS").apply { init(null, trustManagers, null) }
        }

        private fun writeTrustStore(
            certificates: Map<String, String>,
            target: Path,
        ) {
            val keyStore = KeyStore.getInstance("PKCS12").apply { load(null, TRUST_STORE_PASSWORD) }
            certificates.forEach { (alias, pem) ->
                require(alias.matches(Regex("[a-z0-9._-]+"))) { "Invalid OIDF trust-store alias: $alias" }
                keyStore.setCertificateEntry(alias, certificate(pem))
            }
            Files.newOutputStream(target).use { keyStore.store(it, TRUST_STORE_PASSWORD) }
        }

        private fun deleteOwnedTempDirectory(directory: Path) {
            val normalized = directory.toAbsolutePath().normalize()
            val systemTemp = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()
            require(normalized.startsWith(systemTemp)) { "Refusing to delete OIDF files outside $systemTemp: $normalized" }
            require(normalized.fileName.toString().startsWith("oidf-suite-trust-")) {
                "Refusing to delete an unrecognized OIDF temporary directory: $normalized"
            }
            if (!Files.exists(normalized)) return
            Files.walk(normalized).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }

        internal val TRUST_STORE_PASSWORD = "changeit".toCharArray()

        private const val CONTAINER_HTTPS_PORT = 8443
        private const val CONTAINER_MTLS_PORT = 8444
        private const val CONTAINER_ALTERNATE_PORT = 8445
        private const val CONTAINER_TRUST_STORE_PATH = "/opt/oidf-trust/oidf-suite-truststore.p12"
    }
}

class JvmTrustStoreScope internal constructor(
    trustStorePath: Path,
) : AutoCloseable {
    private val keys =
        mapOf(
            "javax.net.ssl.trustStore" to trustStorePath.toString(),
            "javax.net.ssl.trustStorePassword" to String(OidfSuiteEnvironment.TRUST_STORE_PASSWORD),
            "javax.net.ssl.trustStoreType" to "PKCS12",
        )
    private val previous = keys.keys.associateWith(System::getProperty)

    init {
        keys.forEach(System::setProperty)
    }

    override fun close() {
        previous.forEach { (key, value) ->
            if (value == null) System.clearProperty(key) else System.setProperty(key, value)
        }
    }
}

private class FixedPortContainer(
    imageName: DockerImageName,
) : GenericContainer<FixedPortContainer>(imageName) {
    fun withFixedPort(
        hostPort: Int,
        containerPort: Int,
    ): FixedPortContainer = apply { addFixedExposedPort(hostPort, containerPort) }
}

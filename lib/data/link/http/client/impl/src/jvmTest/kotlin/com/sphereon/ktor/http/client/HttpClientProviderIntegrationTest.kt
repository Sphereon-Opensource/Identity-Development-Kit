/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.ktor.http.client

import TestCertificateGenerator
import TestCertificateGenerator.toSureCertificate
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyStoreLoaderOpts
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.kms.model.PredefinedKeyStoreTypes
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.kms.keystore.memory.MemoryKeyStoreConfig
import com.sphereon.crypto.kms.keystore.memory.MemoryKeyStoreService
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.ktor.http.client.config.LegacyJvmSslProviderImpl
import com.sphereon.ktor.http.client.config.LegacySslConfig
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.LegacyHttpClientFactory
import com.sphereon.ktor.http.client.provider.LegacyHttpClientOptions
import com.sphereon.ktor.http.client.server.ServerKeyStoreOpts
import com.sphereon.ktor.http.client.server.ServerOpts
import com.sphereon.ktor.http.client.server.ServerTrustStoreOpts
import com.sphereon.ktor.http.client.server.TestServer
import com.sphereon.ktor.http.client.server.startServer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.todo

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpClientProviderIntegrationTest {
    private lateinit var memoryKeyStore: MemoryKeyStoreService
    private lateinit var rootCaKeyPair: KeyPair
    private lateinit var rootCaCert: X509Certificate
    private lateinit var rootCaSureCert: Certificate

    private var server: TestServer? = null
    private var client: HttpClient? = null

    private val storePassword = "storepassword"
    private val privateKeyPassword = "storepassword"

    val app = createJvmHttpClientTestAppGraph(application = this)
    val user = app.userContextManager.getAnonymous()
    val session = user.sessionContextManager.createOrGetFromId("http-client-test")

    @BeforeEach
    fun beforeEach() {
        memoryKeyStore = MemoryKeyStoreService(MemoryKeyStoreConfig(keyVisibility = KeyVisibility.PRIVATE.keyVisibility))

        rootCaKeyPair = TestCertificateGenerator.generateKeyPair(curveNameOrKeySize = "secp256r1")
        rootCaCert =
            TestCertificateGenerator.createSelfSignedCertificate(
                subjectDNStr = "CN=TestRootCA-${Uuid.v4String()}",
                keyPair = rootCaKeyPair,
                sigAlgName = "SHA256withECDSA",
            )
        rootCaSureCert = rootCaCert.toSureCertificate()
    }

    @AfterEach
    fun afterEach() {
        client?.close()
        server?.stop()
    }

    @Test
    @Disabled("mTLS test requires further integration work with new DI-based KMS infrastructure")
    fun `full mTLS handshake using KMS key should succeed`() =
        runTest {
            // --- server setup ---
            val serverKeyPair = TestCertificateGenerator.generateKeyPair(curveNameOrKeySize = "secp256r1")
            val serverCert =
                TestCertificateGenerator.createSignedCertificate(
                    subjectDNStr = "CN=TestServer-${Uuid.v4String()}",
                    subjectPublicKey = serverKeyPair.public,
                    issuerCert = rootCaCert,
                    issuerPrivateKey = rootCaKeyPair.private,
                    sigAlgName = "SHA256withECDSA",
                )
            val serverJks =
                KeyStore.getInstance("JKS").apply {
                    load(null, null)
                    setKeyEntry("serverKey", serverKeyPair.private, privateKeyPassword.toCharArray(), arrayOf(serverCert, rootCaCert))
                }
            val serverKeystoreFile =
                File.createTempFile("srv-keystore", ".jks").apply {
                    deleteOnExit()
                    FileOutputStream(this).use { os ->
                        serverJks.store(os, storePassword.toCharArray())
                    }
                }

            val serverTrustJks =
                KeyStore.getInstance("JKS").apply {
                    load(null, null)
                    setCertificateEntry("rootCa", rootCaCert)
                }
            val serverTrustFile =
                File.createTempFile("srv-trust", ".jks").apply {
                    deleteOnExit()
                    FileOutputStream(this).use { os ->
                        serverTrustJks.store(os, storePassword.toCharArray())
                    }
                }

            val serverOpts =
                ServerOpts(
                    port = 0,
                    keyStoreOpts =
                        ServerKeyStoreOpts(
                            path = serverKeystoreFile.absolutePath,
                            keyStorePassword = storePassword,
                            type = PredefinedKeyStoreTypes.JKS,
                            keyAlias = "serverKey",
                            privateKeyPassword = privateKeyPassword,
                        ),
                    trustStoreOpts =
                        ServerTrustStoreOpts(
                            path = serverTrustFile.absolutePath,
                            trustStorePassword = storePassword,
                            type = PredefinedKeyStoreTypes.JKS,
                        ),
                )
            server = startServer(serverOpts)
            val port = server!!.actualPort()

            // --- client key generation & certificate ---
            val clientAlias = "client-kms-key-${Uuid.v4String()}"
            val execution = session.asCoreApiServiceGraph().serviceExecution
            app as JvmHttpClientTestAppGraph
            val softwareProvider = app.softwareKmsProvider.create(SoftwareKmsProviderConfig(), execution).setPrivateKeyStore(memoryKeyStore)
            val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
            kms.registerProvider(softwareProvider, makeDefaultKms = true)

            kms.generateKeyAsync(alias = clientAlias, use = null, alg = SignatureAlgorithm.ECDSA_SHA256, keyVisibility = KeyVisibility.PRIVATE)
            val keyInfo = kms.getKey(KeyInfo<Jwk>(providerId = kms.defaultProviderId(), alias = clientAlias, keyVisibility = KeyVisibility.PRIVATE))
            val jwk =
                keyInfo.key as? Jwk
                    ?: throw AssertionError("Expected JWK from ManagedKeyInfo")

            assertNotNull(jwk.d)
            assertNotNull(jwk.x)
            assertNotNull(jwk.y)

            val clientCert =
                TestCertificateGenerator.createSignedCertificate(
                    subjectDNStr = "CN=TestClient-${Uuid.v4String()}",
                    subjectPublicKey =
                        run {
                            // reconstruct EC public key from JWK
                            val curveName =
                                when (jwk.crv.toString()) {
                                    "P-256" -> "secp256r1"
                                    "P-384" -> "secp384r1"
                                    "P-521" -> "secp521r1"
                                    else -> jwk.crv.toString()
                                }
                            val params =
                                AlgorithmParameters
                                    .getInstance("EC")
                                    .apply { init(ECGenParameterSpec(curveName)) }
                            val ecSpec = params.getParameterSpec(ECParameterSpec::class.java)
                            val point =
                                ECPoint(
                                    BigInteger(1, jwk.x.toString().decodeFromBase64()),
                                    BigInteger(1, jwk.y.toString().decodeFromBase64()),
                                )
                            KeyFactory.getInstance("EC").generatePublic(
                                ECPublicKeySpec(point, ecSpec),
                            )
                        },
                    issuerCert = rootCaCert,
                    issuerPrivateKey = rootCaKeyPair.private,
                    sigAlgName = "SHA256withECDSA",
                )
            memoryKeyStore.storeCertificateChain(clientAlias, arrayOf(clientCert.toSureCertificate(), rootCaSureCert))

            // --- client trust store ---
            val clientTrustJks =
                KeyStore.getInstance("JKS").apply {
                    load(null, null)
                    setCertificateEntry("rootCa", rootCaCert)
                }
            val clientTrustFile =
                File.createTempFile("cli-trust", ".jks").apply {
                    deleteOnExit()
                    FileOutputStream(this).use { os ->
                        clientTrustJks.store(os, storePassword.toCharArray())
                    }
                }

            // --- build HttpClient with mTLS ---
            val sslOpts =
                LegacySslConfig(
                    certificateAliases = listOf(clientAlias),
                    keyStoreService = memoryKeyStore,
                    certificateStoreService = memoryKeyStore,
                    trustStoreOpts =
                        KeyStoreLoaderOpts(
                            source = KeyStoreLoaderOpts.Source.File(clientTrustFile.absolutePath),
                            keyStorePassword = storePassword,
                            type = PredefinedKeyStoreTypes.JKS.keyStoreType,
                        ),
                )
            val sslConfig = LegacyJvmSslProviderImpl(sslOpts)
            client =
                LegacyHttpClientFactory().createClient(
                    LegacyHttpClientOptions(
                        engine = HttpClientEngineType.OKHTTP,
                        sslConfig = sslConfig,
                        enableContentNegotiation = false,
                    ),
                )

            // --- execute request ---
            val body = client!!.get("https://127.0.0.1:$port/hello").bodyAsText()

            assertEquals("Hello from mTLS Server", body)
        }

    @Test
    fun `mTLS handshake should fail when client certificate is missing`() =
        runTest {
            val serverKeyPair = TestCertificateGenerator.generateKeyPair(curveNameOrKeySize = "secp256r1")
            val serverCert =
                TestCertificateGenerator.createSignedCertificate(
                    subjectDNStr = "CN=TestServer-${Uuid.v4String()}",
                    subjectPublicKey = serverKeyPair.public,
                    issuerCert = rootCaCert,
                    issuerPrivateKey = rootCaKeyPair.private,
                    sigAlgName = "SHA256withECDSA",
                )
            val serverJks =
                KeyStore.getInstance("JKS").apply {
                    load(null, null)
                    setKeyEntry("serverKey", serverKeyPair.private, privateKeyPassword.toCharArray(), arrayOf(serverCert, rootCaCert))
                }
            val serverKeystoreFile =
                File.createTempFile("srv-keystore", ".jks").apply {
                    deleteOnExit()
                    FileOutputStream(this).use { os -> serverJks.store(os, storePassword.toCharArray()) }
                }

            val serverTrustJks =
                KeyStore.getInstance("JKS").apply {
                    load(null, null)
                    setCertificateEntry("rootCa", rootCaCert)
                }
            val serverTrustFile =
                File.createTempFile("srv-trust", ".jks").apply {
                    deleteOnExit()
                    FileOutputStream(this).use { os -> serverTrustJks.store(os, storePassword.toCharArray()) }
                }

            val serverOpts =
                ServerOpts(
                    port = 0,
                    keyStoreOpts =
                        ServerKeyStoreOpts(
                            path = serverKeystoreFile.absolutePath,
                            keyStorePassword = storePassword,
                            type = PredefinedKeyStoreTypes.JKS,
                            keyAlias = "serverKey",
                            privateKeyPassword = privateKeyPassword,
                        ),
                    trustStoreOpts =
                        ServerTrustStoreOpts(
                            path = serverTrustFile.absolutePath,
                            trustStorePassword = storePassword,
                            type = PredefinedKeyStoreTypes.JKS,
                        ),
                )
            server = startServer(serverOpts)
            val port = server!!.actualPort()

            val clientTrustJks =
                KeyStore.getInstance("JKS").apply {
                    load(null, null)
                    setCertificateEntry("rootCa", rootCaCert)
                }
            val clientTrustFile =
                File.createTempFile("cli-trust", ".jks").apply {
                    deleteOnExit()
                    FileOutputStream(this).use { os -> clientTrustJks.store(os, storePassword.toCharArray()) }
                }

            val sslOpts =
                LegacySslConfig(
                    certificateAliases = emptyList(), // No client certificate alias
                    keyStoreService = memoryKeyStore,
                    certificateStoreService = memoryKeyStore,
                    trustStoreOpts =
                        KeyStoreLoaderOpts(
                            source = KeyStoreLoaderOpts.Source.File(clientTrustFile.absolutePath),
                            keyStorePassword = storePassword,
                            type = PredefinedKeyStoreTypes.JKS.keyStoreType,
                        ),
                )
            val sslConfig = LegacyJvmSslProviderImpl(sslOpts)
            client =
                LegacyHttpClientFactory().createClient(
                    LegacyHttpClientOptions(
                        engine = HttpClientEngineType.CIO,
                        sslConfig = sslConfig,
                        enableContentNegotiation = false,
                    ),
                )

            try {
                client!!.get("https://127.0.0.1:$port/hello").bodyAsText()
                throw AssertionError("Request should have failed due to missing client certificate")
            } catch (expected: Exception) {
                assert(
                    expected.message?.contains("handshake", ignoreCase = true) == true ||
                        expected.message?.contains("certificate", ignoreCase = true) == true ||
                        expected is java.io.EOFException || // Could be EOF if server just closes
                        expected is javax.net.ssl.SSLHandshakeException, // Common exception for TLS failures
                ) { "Unexpected exception message: ${expected.message}" }
            }
        }
}

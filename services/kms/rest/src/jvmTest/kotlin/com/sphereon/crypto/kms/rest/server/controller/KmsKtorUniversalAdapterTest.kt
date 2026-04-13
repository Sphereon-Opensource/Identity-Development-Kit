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
 */

package com.sphereon.crypto.kms.rest.server.controller

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.kms.provider.rest.RestClientAuthConfig
import com.sphereon.crypto.kms.provider.rest.RestClientKmsProviderConfig
import com.sphereon.crypto.kms.provider.rest.RestClientKmsProviderImpl
import com.sphereon.crypto.kms.rest.server.TestApiAppGraph
import com.sphereon.crypto.kms.rest.server.createTestApiAppGraph
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test demonstrating the Universal HTTP Adapter pattern with Ktor.
 *
 * This test exercises the FULL HTTP chain:
 * 1. RestClientKmsProvider makes HTTP request to Ktor server
 * 2. KotlinInjectPlugin handles session management
 * 3. installUniversalHttpAdapters routes to HttpAdapterDispatcher
 * 4. HttpAdapterDispatcher dispatches to the correct HttpAdapter
 * 5. HttpAdapter delegates to KMS services
 * 6. KMS services use the software provider for actual key operations
 *
 * This is a real-world test that proves the HTTP adapter architecture works end-to-end.
 */
class KmsKtorUniversalAdapterTest {
    private lateinit var appGraph: TestApiAppGraph
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private lateinit var restClientKmsProvider: RestClientKmsProviderImpl

    @BeforeEach
    fun setUp() {
        // Find an available port
        port = ServerSocket(0).use { it.localPort }

        // Configure a software KMS provider with memory keystore for the server-side
        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "kms.providers.testsoftware.type" to "software",
                "kms.providers.testsoftware.id" to "testsoftware",
                "kms.providers.testsoftware.persistKeysDuringGeneration" to "true",
                "kms.providers.testsoftware.exposePrivateKeysDuringGeneration" to "true",
                "kms.providers.testsoftware.keyStore.type" to "memory",
                "kms.providers.testsoftware.keyStore.id" to "test-memory-keystore",
                "kms.providers.testsoftware.keyStore.keyVisibility" to "private",
                "kms.providers.testsoftware.keyStore.overwriteAlias" to "true",
            ),
        )

        // Create app graph
        appGraph =
            createTestApiAppGraph(
                application = Unit,
                appId = APP_ID,
                profile = PROFILE,
                version = "1.0.0",
            )
        appGraph.userContextManager.destroyAll()

        // Start actual Ktor server
        server =
            embeddedServer(CIO, port = port) {
                install(KotlinInjectPlugin) {
                    this.appGraph = this@KmsKtorUniversalAdapterTest.appGraph
                }
                installUniversalHttpAdapters {
                    verboseLogging = true
                }
            }
        server.start(wait = false)

        // Wait for server to be ready
        runBlocking { delay(500) }

        // Create REST KMS provider pointing to the Ktor server
        val config =
            RestClientKmsProviderConfig(
                id = "testsoftware",
                restKmsUrl = "http://localhost:$port",
                authConfig =
                    RestClientAuthConfig(
                        usePrincipalFromContext = true,
                        useTenantFromContext = true,
                    ),
            )

        // Create user context and session for the REST client
        val userContextInstance =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString(TEST_TENANT_ID),
                DefaultPrincipalInputString(TEST_USER_ID),
            )
        val sessionInstance = userContextInstance.sessionContextManager.createOrGetFromId("test-session")

        restClientKmsProvider =
            RestClientKmsProviderImpl(
                config,
                execution = sessionInstance.asCoreApiServiceGraph().serviceExecution,
            )
    }

    @AfterEach
    fun tearDown() {
        restClientKmsProvider.http.close()
        server.stop(1000, 2000)

        // Clean up properties
        DefaultPrincipalMapPropertySource.deleteProperty("kms.providers.testsoftware.type")
        DefaultPrincipalMapPropertySource.deleteProperty("kms.providers.testsoftware.id")
        DefaultPrincipalMapPropertySource.deleteProperty("kms.providers.testsoftware.persistKeysDuringGeneration")
        DefaultPrincipalMapPropertySource.deleteProperty("kms.providers.testsoftware.exposePrivateKeysDuringGeneration")
        DefaultPrincipalMapPropertySource.deleteProperty("kms.providers.testsoftware.keyStore.type")
        DefaultPrincipalMapPropertySource.deleteProperty("kms.providers.testsoftware.keyStore.id")
        DefaultPrincipalMapPropertySource.deleteProperty("kms.providers.testsoftware.keyStore.keyVisibility")
        DefaultPrincipalMapPropertySource.deleteProperty("kms.providers.testsoftware.keyStore.overwriteAlias")
    }

    @Test
    fun `REST provider can list supported curves through HTTP adapter`() {
        val curves = restClientKmsProvider.supportedCurves()
        assertNotNull(curves)
        assertTrue(curves.isNotEmpty(), "Should return supported curves")
        println("Supported curves: ${curves.joinToString(", ")}")
    }

    @Test
    fun `REST provider can list supported algorithms through HTTP adapter`() {
        val algorithms = restClientKmsProvider.supportedSignatureAlgorithms()
        assertNotNull(algorithms)
        assertTrue(algorithms.isNotEmpty(), "Should return supported algorithms")
        println("Supported algorithms: ${algorithms.joinToString(", ")}")
    }

    @Test
    fun `REST provider can generate key through HTTP adapter`() =
        runTest {
            val keyAlias = "ktor-rest-key-${System.currentTimeMillis()}"

            val managedKeyPair =
                restClientKmsProvider.generateKeyAsync(
                    alias = keyAlias,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )

            assertNotNull(managedKeyPair, "Key should be generated")
            assertEquals(keyAlias, managedKeyPair.alias, "Alias should match")
            assertNotNull(managedKeyPair.kid, "Key should have a KID")
            println("Generated key: alias=${managedKeyPair.alias}, kid=${managedKeyPair.kid}")
        }

    @Test
    fun `REST provider can list keys through HTTP adapter`() =
        runTest {
            // Generate a key first
            val keyAlias = "ktor-list-key-${System.currentTimeMillis()}"
            restClientKmsProvider.generateKeyAsync(
                alias = keyAlias,
                alg = SignatureAlgorithm.ECDSA_SHA256,
            )

            // List keys through REST
            val keys = restClientKmsProvider.listKeys()

            assertNotNull(keys)
            assertTrue(keys.any { it.alias == keyAlias }, "Generated key should appear in list")
            println("Listed ${keys.size} keys")
        }

    @Test
    fun `REST provider can sign and verify through HTTP adapter`() =
        runTest {
            // Generate a key
            val keyAlias = "ktor-sign-key-${System.currentTimeMillis()}"
            val managedKeyPair =
                restClientKmsProvider.generateKeyAsync(
                    alias = keyAlias,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            val keyInfo = keyReference(managedKeyPair, SignatureAlgorithm.ECDSA_SHA256)
            assertNotNull(keyInfo)

            // Sign data through REST
            val data = "Hello from Ktor Universal HTTP Adapter test".encodeToByteArray()
            val signature =
                restClientKmsProvider.createRawSignature(
                    keyInfo = keyInfo,
                    input = data,
                    requireX5Chain = false,
                )
            assertNotNull(signature, "Signature should be created")
            println("Created signature of ${signature.size} bytes")

            // Verify through REST
            val isValid =
                restClientKmsProvider.isValidRawSignature(
                    keyInfo = keyInfo,
                    signature = signature,
                    input = data,
                )
            assertTrue(isValid, "Signature should be valid")

            // Verify with wrong data
            val isInvalid =
                restClientKmsProvider.isValidRawSignature(
                    keyInfo = keyInfo,
                    signature = signature,
                    input = "Tampered data".encodeToByteArray(),
                )
            assertFalse(isInvalid, "Signature should be invalid for wrong data")
        }

    @Test
    fun `REST provider full key lifecycle through HTTP adapter`() =
        runTest {
            val keyAlias = "ktor-lifecycle-${System.currentTimeMillis()}"

            // 1. Generate key
            val managedKeyPair =
                restClientKmsProvider.generateKeyAsync(
                    alias = keyAlias,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(managedKeyPair)
            println("1. Generated key: $keyAlias")

            // 2. List and verify key exists
            var keys = restClientKmsProvider.listKeys()
            assertTrue(keys.any { it.alias == keyAlias }, "Key should exist after generation")
            println("2. Key found in list")

            // 3. Sign data
            val keyInfo = keyReference(managedKeyPair, SignatureAlgorithm.ECDSA_SHA256)
            val data = "Lifecycle test data".encodeToByteArray()
            val signature = restClientKmsProvider.createRawSignature(keyInfo, data, false)
            assertNotNull(signature)
            println("3. Signed data")

            // 4. Verify signature
            val isValid = restClientKmsProvider.isValidRawSignature(keyInfo, input = data, signature = signature)
            assertTrue(isValid)
            println("4. Verified signature")

            // 5. Delete key
            restClientKmsProvider.deleteKey(keyInfo)
            println("5. Deleted key")

            // 6. Verify key is gone
            keys = restClientKmsProvider.listKeys()
            assertFalse(keys.any { it.alias == keyAlias }, "Key should not exist after deletion")
            println("6. Key no longer in list")

            println("Full lifecycle completed successfully!")
        }

    @Test
    fun `multiple algorithms work through HTTP adapter`() =
        runTest {
            val algorithms =
                listOf(
                    SignatureAlgorithm.ECDSA_SHA256,
                    SignatureAlgorithm.ECDSA_SHA384,
                    SignatureAlgorithm.RSA_SHA256,
                )

            for (alg in algorithms) {
                val keyAlias = "ktor-alg-test-${alg.jose?.name ?: alg.cryptoAlgorithm.name}-${System.currentTimeMillis()}"

                val managedKeyPair =
                    restClientKmsProvider.generateKeyAsync(
                        alias = keyAlias,
                        alg = alg,
                    )
                assertNotNull(managedKeyPair, "Key generation should succeed for $alg")

                val keyInfo = keyReference(managedKeyPair, alg)
                val data = "Test data for $alg".encodeToByteArray()

                val signature = restClientKmsProvider.createRawSignature(keyInfo, data, false)
                assertNotNull(signature, "Signing should succeed for $alg")

                val isValid = restClientKmsProvider.isValidRawSignature(keyInfo, input = data, signature = signature)
                assertTrue(isValid, "Verification should succeed for $alg")

                println("Algorithm $alg: OK")
            }
        }

    private fun keyReference(
        keyPair: ManagedKeyPair,
        signatureAlgorithm: SignatureAlgorithm? = null,
    ): KeyInfo<JwkType> =
        KeyInfo(
            kid = keyPair.kid,
            alias = keyPair.alias,
            providerId = keyPair.providerId,
            keyVisibility = KeyVisibility.PRIVATE,
            signatureAlgorithm = signatureAlgorithm,
            keyEncoding = KeyEncoding.JOSE,
        )

    companion object {
        const val TEST_TENANT_ID = "ktor-test-tenant"
        const val TEST_USER_ID = "ktor-test-user"
        const val APP_ID = "kms-ktor-test"
        const val PROFILE = "test"
    }
}

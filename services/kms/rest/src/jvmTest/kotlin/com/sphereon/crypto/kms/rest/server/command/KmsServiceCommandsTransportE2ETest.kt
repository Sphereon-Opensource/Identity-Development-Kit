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

package com.sphereon.crypto.kms.rest.server.command

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.http.describe.HttpMethod
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
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyInput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyInput
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysInput
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.server.TestApiAppGraph
import com.sphereon.crypto.kms.rest.server.createTestApiAppGraph
import com.sphereon.di.session.SessionContext
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E test for KMS Keys ServiceCommands via REST transport.
 *
 * This test verifies the full stack:
 * 1. REST client makes HTTP request
 * 2. Ktor server routes to HTTP adapter (KeysHttpAdapter)
 * 3. HTTP adapter delegates to ServiceCommand implementations
 * 4. ServiceCommand calls KmsRestService
 * 5. Response flows back through the entire stack
 *
 * Uses the in-memory software KMS provider configured via properties.
 */
class KmsServiceCommandsTransportE2ETest {
    private lateinit var appGraph: TestApiAppGraph
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private lateinit var restClient: RestClientKmsProviderImpl

    @BeforeEach
    fun setUp() {
        port = ServerSocket(0).use { it.localPort }

        DefaultPrincipalMapPropertySource.addProperties(
            mapOf(
                "$PROPERTY_PREFIX.type" to "software",
                "$PROPERTY_PREFIX.id" to "testsoftware",
                "$PROPERTY_PREFIX.persistKeysDuringGeneration" to "true",
                "$PROPERTY_PREFIX.exposePrivateKeysDuringGeneration" to "true",
                "$PROPERTY_PREFIX.keyStore.type" to "memory",
                "$PROPERTY_PREFIX.keyStore.id" to "test-memory-keystore",
                "$PROPERTY_PREFIX.keyStore.keyVisibility" to "private",
                "$PROPERTY_PREFIX.keyStore.overwriteAlias" to "true",
            ),
        )

        appGraph =
            createTestApiAppGraph(
                application = Unit,
                appId = "kms-transport-e2e-test",
                profile = "test",
                version = "1.0.0",
            )
        appGraph.userContextManager.destroyAll()

        server =
            embeddedServer(CIO, port = port) {
                install(KotlinInjectPlugin) {
                    this.appGraph = this@KmsServiceCommandsTransportE2ETest.appGraph
                    tenantResolver = FixedTenantResolver("default")
                }
                installUniversalHttpAdapters {
                    verboseLogging = true
                }
            }
        server.start(wait = false)
        runBlocking { delay(500) }

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

        val userContext =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString(TEST_TENANT_ID),
                DefaultPrincipalInputString(TEST_USER_ID),
            )
        val session = userContext.sessionContextManager.createOrGetFromId("service-commands-transport-test")

        restClient =
            RestClientKmsProviderImpl(
                config,
                execution = session.asCoreApiServiceGraph().serviceExecution,
            )
    }

    @AfterEach
    fun tearDown() {
        restClient.http.close()
        server.stop(1000, 2000)
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.type")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.id")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.persistKeysDuringGeneration")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.exposePrivateKeysDuringGeneration")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.keyStore.type")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.keyStore.id")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.keyStore.keyVisibility")
        DefaultPrincipalMapPropertySource.deleteProperty("$PROPERTY_PREFIX.keyStore.overwriteAlias")
    }

    // ==================== Generate Key Transport Test ====================

    @Test
    fun generateKeyViaRestShouldCreateKeyAndReturnResponse() =
        runTest {
            val alias = "transport-generate-test-${System.currentTimeMillis()}"

            val keyPair =
                restClient.generateKeyAsync(
                    alias = alias,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )

            assertNotNull(keyPair, "Generated key pair should not be null")
            assertEquals(alias, keyPair.alias, "Key alias should match")
            assertNotNull(keyPair.kid, "Key should have a KID")
            assertNotNull(keyPair.jose.publicJwk.x, "Public key should have x graph")
        }

    // ==================== List Keys Transport Test ====================

    @Test
    fun listKeysViaRestShouldReturnAllKeys() =
        runTest {
            val alias = "transport-list-test-${System.currentTimeMillis()}"
            restClient.generateKeyAsync(alias = alias, alg = SignatureAlgorithm.ECDSA_SHA256)

            val keys = restClient.listKeys()

            assertNotNull(keys, "Key list should not be null")
            assertTrue(keys.isNotEmpty(), "Should have at least one key")
            assertTrue(keys.any { it.alias == alias }, "Should find the generated key")
        }

    // ==================== Get Key Transport Test ====================

    @Test
    fun getKeyViaRestShouldReturnSpecificKey() =
        runTest {
            val alias = "transport-get-test-${System.currentTimeMillis()}"
            val generatedKey = restClient.generateKeyAsync(alias = alias, alg = SignatureAlgorithm.ECDSA_SHA256)
            assertNotNull(generatedKey)

            val keyInfo = keyReference(generatedKey, SignatureAlgorithm.ECDSA_SHA256)
            val retrievedKey = restClient.getKey(keyInfo)

            assertNotNull(retrievedKey, "Retrieved key should not be null")
            assertEquals(alias, retrievedKey.alias, "Key alias should match")
        }

    // ==================== Delete Key Transport Test ====================

    @Test
    fun deleteKeyViaRestShouldRemoveKey() =
        runTest {
            val alias = "transport-delete-test-${System.currentTimeMillis()}"
            val generatedKey = restClient.generateKeyAsync(alias = alias, alg = SignatureAlgorithm.ECDSA_SHA256)

            val keysBeforeDelete = restClient.listKeys()
            assertTrue(keysBeforeDelete.any { it.alias == alias }, "Key should exist before delete")

            val keyInfo = keyReference(generatedKey, SignatureAlgorithm.ECDSA_SHA256)
            val deleted = restClient.deleteKey(keyInfo)
            assertTrue(deleted, "Delete should return true")

            val keysAfterDelete = restClient.listKeys()
            assertTrue(keysAfterDelete.none { it.alias == alias }, "Key should not exist after delete")
        }

    // ==================== Full CRUD Workflow Test ====================

    @Test
    fun fullCrudWorkflowViaRestTransport() =
        runTest {
            val alias = "transport-crud-test-${System.currentTimeMillis()}"

            // CREATE
            val generatedKey =
                restClient.generateKeyAsync(
                    alias = alias,
                    alg = SignatureAlgorithm.ECDSA_SHA384,
                )
            assertNotNull(generatedKey, "CREATE: Key should be generated")
            assertEquals(alias, generatedKey.alias)

            // READ
            val keyInfo = keyReference(generatedKey, SignatureAlgorithm.ECDSA_SHA384)
            val retrievedKey = restClient.getKey(keyInfo)
            assertNotNull(retrievedKey, "READ: Key should be retrievable")

            // LIST
            val keys = restClient.listKeys()
            assertTrue(keys.any { it.alias == alias }, "LIST: Key should be in list")

            // DELETE
            val deleted = restClient.deleteKey(keyInfo)
            assertTrue(deleted, "DELETE: Should succeed")

            // Verify deletion
            val keysAfterDelete = restClient.listKeys()
            assertTrue(keysAfterDelete.none { it.alias == alias }, "DELETE: Key should be removed from list")
        }

    // ==================== ServiceCommand Metadata Verification ====================

    @Test
    fun serviceCommandMetadataShouldMatchRestEndpoints() {
        // PublicApiCommand httpEndpoint on the command interfaces declares the public REST bindings
        val getKey =
            object : GetKeyServiceCommand {
                override val isEnabled = true
                override val inputTypeToken get() = throw UnsupportedOperationException()
                override val outputTypeToken get() = throw UnsupportedOperationException()

                override suspend fun execute(args: GetKeyInput) = throw UnsupportedOperationException()
            }
        assertEquals(HttpMethod.GET, getKey.httpEndpoint.method)
        assertEquals("/keys/{aliasOrKid}", getKey.httpEndpoint.pathPattern)

        val listKeys =
            object : ListKeysServiceCommand {
                override val isEnabled = true
                override val inputTypeToken get() = throw UnsupportedOperationException()
                override val outputTypeToken get() = throw UnsupportedOperationException()

                override suspend fun execute(args: ListKeysInput) = throw UnsupportedOperationException()
            }
        assertEquals(HttpMethod.GET, listKeys.httpEndpoint.method)
        assertEquals("/keys", listKeys.httpEndpoint.pathPattern)

        val generateKey =
            object : GenerateKeyServiceCommand {
                override val isEnabled = true
                override val inputTypeToken get() = throw UnsupportedOperationException()
                override val outputTypeToken get() = throw UnsupportedOperationException()

                override suspend fun execute(args: GenerateKeyGlobal) = throw UnsupportedOperationException()
            }
        assertEquals(HttpMethod.POST, generateKey.httpEndpoint.method)
        assertEquals("/keys/generate", generateKey.httpEndpoint.pathPattern)

        val deleteKey =
            object : DeleteKeyServiceCommand {
                override val isEnabled = true
                override val inputTypeToken get() = throw UnsupportedOperationException()
                override val outputTypeToken get() = throw UnsupportedOperationException()

                override suspend fun execute(args: DeleteKeyInput) = throw UnsupportedOperationException()
            }
        assertEquals(HttpMethod.DELETE, deleteKey.httpEndpoint.method)
        assertEquals("/keys/{aliasOrKid}", deleteKey.httpEndpoint.pathPattern)
    }

    // ==================== Signature Operations via Transport ====================

    @Test
    fun signatureCreationAndVerificationViaRestTransport() =
        runTest {
            val alias = "transport-sign-test-${System.currentTimeMillis()}"
            val keyPair =
                restClient.generateKeyAsync(
                    alias = alias,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )
            assertNotNull(keyPair)

            val keyInfo = keyReference(keyPair, SignatureAlgorithm.ECDSA_SHA256)

            val testData = "test data for signing".encodeToByteArray()
            val signature =
                restClient.createRawSignature(
                    keyInfo = keyInfo,
                    input = testData,
                    requireX5Chain = false,
                )
            assertNotNull(signature, "Signature should be created")
            assertTrue(signature.isNotEmpty(), "Signature should not be empty")

            val isValid =
                restClient.isValidRawSignature(
                    keyInfo = keyInfo,
                    signature = signature,
                    input = testData,
                )
            assertTrue(isValid, "Signature should be valid")

            val isInvalidForWrongData =
                restClient.isValidRawSignature(
                    keyInfo = keyInfo,
                    signature = signature,
                    input = "wrong data".encodeToByteArray(),
                )
            assertTrue(!isInvalidForWrongData, "Signature should be invalid for wrong data")
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
        const val TEST_TENANT_ID = "service-commands-test-tenant"
        const val TEST_USER_ID = "service-commands-test-user"
        private const val PROPERTY_PREFIX = "kms.providers.testsoftware"
    }
}

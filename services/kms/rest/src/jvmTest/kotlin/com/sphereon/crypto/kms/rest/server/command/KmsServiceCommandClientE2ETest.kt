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
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.crypto.kms.rest.api.client.DeleteKeyServiceCommandClient
import com.sphereon.crypto.kms.rest.api.client.GenerateKeyServiceCommandClient
import com.sphereon.crypto.kms.rest.api.client.GetKeyServiceCommandClient
import com.sphereon.crypto.kms.rest.api.client.HttpServiceCommandTransport
import com.sphereon.crypto.kms.rest.api.client.ListKeysServiceCommandClient
import com.sphereon.crypto.kms.rest.api.client.SessionBoundKmsCommandTransport
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyInput
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyInput
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysInput
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.api.generated.models.JwkUse
import com.sphereon.crypto.kms.rest.api.generated.models.SignatureAlgorithm
import com.sphereon.crypto.kms.rest.server.TestApiAppGraph
import com.sphereon.crypto.kms.rest.server.createTestApiAppGraph
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
 * E2E test for KMS Keys ServiceCommands using the client command pattern.
 *
 * This test demonstrates that **local access and remote access use the same interface**:
 * - Server: [GetKeyServiceCommandImpl] implements [GetKeyServiceCommand]
 * - Client: [GetKeyServiceCommandClient] implements [GetKeyServiceCommand]
 *
 * The test uses:
 * 1. [HttpServiceCommandTransport] - Constructs REST calls from registered command routes
 * 2. Client ServiceCommands - Same interface as server, backed by HTTP transport
 * 3. Ktor embedded server - Real server with software KMS provider
 *
 * Flow: Client ServiceCommand -> HttpServiceCommandTransport -> HTTP -> Server -> ServiceCommandImpl
 */
class KmsServiceCommandClientE2ETest {
    private lateinit var appGraph: TestApiAppGraph
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    private lateinit var transport: HttpServiceCommandTransport
    private lateinit var runtimeTransport: SessionBoundKmsCommandTransport

    private lateinit var getKeyCommand: GetKeyServiceCommand
    private lateinit var listKeysCommand: ListKeysServiceCommand
    private lateinit var generateKeyCommand: GenerateKeyServiceCommand
    private lateinit var deleteKeyCommand: DeleteKeyServiceCommand

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
                appId = "kms-client-e2e-test",
                profile = "test",
                version = "1.0.0",
            )
        appGraph.userContextManager.destroyAll()

        server =
            embeddedServer(CIO, port = port) {
                install(KotlinInjectPlugin) {
                    this.appGraph = this@KmsServiceCommandClientE2ETest.appGraph
                    tenantResolver = FixedTenantResolver("default")
                }
                installUniversalHttpAdapters {
                    verboseLogging = true
                }
            }
        server.start(wait = false)
        runBlocking { delay(500) }

        // Create HTTP transport pointing to test server
        transport =
            HttpServiceCommandTransport(
                baseUrl = "http://localhost:$port",
                commandRoutes =
                    mapOf(
                        GetKeyServiceCommand.COMMAND_ID to ("GET" to "/keys/{aliasOrKid}"),
                        ListKeysServiceCommand.COMMAND_ID to ("GET" to "/keys"),
                        GenerateKeyServiceCommand.COMMAND_ID to ("POST" to "/keys"),
                        DeleteKeyServiceCommand.COMMAND_ID to ("DELETE" to "/keys/{aliasOrKid}"),
                    ),
                tenantHeaderName = "X-Tenant-ID",
                principalHeaderName = "X-User-ID",
            )

        // Bind runtime session authority for auth headers
        val userContext =
            appGraph.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString(TEST_TENANT_ID),
                DefaultPrincipalInputString(TEST_USER_ID),
            )
        val session = userContext.sessionContextManager.createOrGetFromId("command-client-e2e-test")
        runtimeTransport = transport.bindSession(session.sessionContext)

        // Create client ServiceCommands - same interface as server
        getKeyCommand = GetKeyServiceCommandClient(runtimeTransport)
        listKeysCommand = ListKeysServiceCommandClient(runtimeTransport)
        generateKeyCommand = GenerateKeyServiceCommandClient(runtimeTransport)
        deleteKeyCommand = DeleteKeyServiceCommandClient(runtimeTransport)
    }

    @AfterEach
    fun tearDown() {
        transport.close()
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

    // ==================== Same Interface Tests ====================

    @Test
    fun clientGenerateKeyServiceCommandSameAsServer() =
        runTest {
            val input =
                GenerateKeyGlobal(
                    alias = "client-interface-test-${System.currentTimeMillis()}",
                    use = JwkUse.sig,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                )

            val result = generateKeyCommand.execute(input)

            assertTrue(result.isOk, "Generate should succeed via client command")
            assertNotNull(result.value.keyPair)
            assertNotNull(result.value.keyPair.jose)
        }

    @Test
    fun clientListKeysServiceCommandSameAsServer() =
        runTest {
            val generateInput =
                GenerateKeyGlobal(
                    alias = "client-list-test-${System.currentTimeMillis()}",
                )
            generateKeyCommand.execute(generateInput)

            val listInput = ListKeysInput()
            val result = listKeysCommand.execute(listInput)

            assertTrue(result.isOk, "List should succeed via client command")
            assertTrue(result.value.keyInfos.isNotEmpty())
        }

    @Test
    fun clientGetKeyServiceCommandSameAsServer() =
        runTest {
            val alias = "client-get-test-${System.currentTimeMillis()}"
            generateKeyCommand.execute(GenerateKeyGlobal(alias = alias))

            val getInput = GetKeyInput(aliasOrKid = alias)
            val result = getKeyCommand.execute(getInput)

            assertTrue(result.isOk, "Get should succeed via client command")
            assertNotNull(result.value.keyInfo)
        }

    @Test
    fun clientDeleteKeyServiceCommandSameAsServer() =
        runTest {
            val alias = "client-delete-test-${System.currentTimeMillis()}"
            generateKeyCommand.execute(GenerateKeyGlobal(alias = alias))

            val deleteInput = DeleteKeyInput(aliasOrKid = alias)
            val result = deleteKeyCommand.execute(deleteInput)

            assertTrue(result.isOk, "Delete should succeed via client command")
            assertTrue(result.value.deleted)
            assertEquals(alias, result.value.aliasOrKid)
        }

    // ==================== Full CRUD Workflow ====================

    @Test
    fun fullCrudWorkflowUsingClientServiceCommands() =
        runTest {
            val alias = "client-crud-test-${System.currentTimeMillis()}"

            // CREATE
            val createResult =
                generateKeyCommand.execute(
                    GenerateKeyGlobal(
                        alias = alias,
                        use = JwkUse.sig,
                        alg = SignatureAlgorithm.ECDSA_SHA384,
                    ),
                )
            assertTrue(createResult.isOk, "CREATE should succeed")
            val createdKey = createResult.value.keyPair
            assertNotNull(createdKey)

            // READ
            val readResult = getKeyCommand.execute(GetKeyInput(aliasOrKid = alias))
            assertTrue(readResult.isOk, "READ should succeed")
            assertNotNull(readResult.value.keyInfo)

            // LIST
            val listResult = listKeysCommand.execute(ListKeysInput())
            assertTrue(listResult.isOk, "LIST should succeed")
            assertTrue(listResult.value.keyInfos.any { it.alias == alias }, "Key should be in list")

            // DELETE
            val deleteResult = deleteKeyCommand.execute(DeleteKeyInput(aliasOrKid = alias))
            assertTrue(deleteResult.isOk, "DELETE should succeed")
            assertTrue(deleteResult.value.deleted)

            // Verify deletion
            val verifyResult = listKeysCommand.execute(ListKeysInput())
            assertTrue(verifyResult.isOk)
            assertTrue(verifyResult.value.keyInfos.none { it.alias == alias }, "Key should be removed")
        }

    // ==================== Error Handling ====================

    @Test
    fun clientCommandReturnsErrorForNonExistentKey() =
        runTest {
            val result =
                getKeyCommand.execute(GetKeyInput(aliasOrKid = "non-existent-${System.currentTimeMillis()}"))
            assertTrue(result.isErr, "Should return error for non-existent key")
        }

    @Test
    fun clientCommandReturnsErrorForDeleteNonExistentKey() =
        runTest {
            val result =
                deleteKeyCommand.execute(DeleteKeyInput(aliasOrKid = "non-existent-${System.currentTimeMillis()}"))
            assertTrue(result.isErr, "Should return error for deleting non-existent key")
        }

    // ==================== Command Identity Verification ====================

    @Test
    fun clientCommandsHaveCorrectCommandIds() {
        assertEquals(GetKeyServiceCommand.COMMAND_ID, getKeyCommand.commandId)
        assertEquals(ListKeysServiceCommand.COMMAND_ID, listKeysCommand.commandId)
        assertEquals(GenerateKeyServiceCommand.COMMAND_ID, generateKeyCommand.commandId)
        assertEquals(DeleteKeyServiceCommand.COMMAND_ID, deleteKeyCommand.commandId)
    }

    @Test
    fun clientCommandsExposeCorrectPublicApiMetadata() {
        // PublicApiCommand httpEndpoint declares the public REST bindings
        assertEquals(HttpMethod.GET, getKeyCommand.httpEndpoint.method)
        assertEquals("/keys/{aliasOrKid}", getKeyCommand.httpEndpoint.pathPattern)

        assertEquals(HttpMethod.GET, listKeysCommand.httpEndpoint.method)
        assertEquals("/keys", listKeysCommand.httpEndpoint.pathPattern)

        assertEquals(HttpMethod.POST, generateKeyCommand.httpEndpoint.method)
        assertEquals("/keys", generateKeyCommand.httpEndpoint.pathPattern)

        assertEquals(HttpMethod.DELETE, deleteKeyCommand.httpEndpoint.method)
        assertEquals("/keys/{aliasOrKid}", deleteKeyCommand.httpEndpoint.pathPattern)
    }

    companion object {
        const val TEST_TENANT_ID = "command-client-test-tenant"
        const val TEST_USER_ID = "command-client-test-user"
        private const val PROPERTY_PREFIX = "kms.providers.testsoftware"
    }
}

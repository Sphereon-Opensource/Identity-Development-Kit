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
import com.sphereon.crypto.kms.rest.api.generated.models.GenerateKeyGlobal
import com.sphereon.crypto.kms.rest.server.TestApiAppGraph
import com.sphereon.crypto.kms.rest.server.createTestApiAppGraph
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for the Universal HTTP Adapter approach.
 *
 * This test demonstrates that:
 * 1. The `KmsHttpAdapter` (in commonMain) contains ALL routing logic
 * 2. The same HttpAdapter works with Ktor (previously proven with Spring)
 * 3. All routing decisions, parameter handling, error mapping happens in ONE place
 */
class KeysHttpAdapterIntegrationTest {
    private lateinit var appGraph: TestApiAppGraph
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var client: HttpClient
    private var port: Int = 0

    private val jsonParser =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

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
                appId = "kms-adapter-test",
                profile = "test",
                version = "1.0.0",
            )
        appGraph.userContextManager.destroyAll()

        server =
            embeddedServer(CIO, port = port) {
                install(KotlinInjectPlugin) {
                    this.appGraph = this@KeysHttpAdapterIntegrationTest.appGraph
                }
                installUniversalHttpAdapters {
                    verboseLogging = true
                }
            }
        server.start(wait = false)
        runBlocking { delay(500) }

        client =
            HttpClient(io.ktor.client.engine.cio.CIO) {
                install(ContentNegotiation) {
                    json(jsonParser)
                }
            }
    }

    @AfterEach
    fun tearDown() {
        client.close()
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

    @Test
    fun getKeyByAliasOrKidShouldReturnErrorForNonExistent() =
        runTest {
            val response =
                client.get("http://localhost:$port/keys/test-key") {
                    header("X-Tenant-ID", TEST_TENANT_ID)
                    header("X-User-ID", TEST_USER_ID)
                    accept(ContentType.Application.Json)
                }
            assertTrue(
                response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.NotFound,
                "Expected 400 or 404, got ${response.status}",
            )
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["message"], "Error response should have message")
        }

    @Test
    fun listKeysWithProviderIdShouldReturnArray() =
        runTest {
            val response =
                client.get("http://localhost:$port/keys") {
                    header("X-Tenant-ID", TEST_TENANT_ID)
                    header("X-User-ID", TEST_USER_ID)
                    accept(ContentType.Application.Json)
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["keyInfos"], "Response should have keyInfos array")
            assertTrue(body["keyInfos"]!!.jsonArray is kotlinx.serialization.json.JsonArray)
        }

    @Test
    fun generateKeyShouldCreateKeyThroughAdapter() =
        runTest {
            val request = GenerateKeyGlobal(alias = "integration-test-key")

            val response =
                client.post("http://localhost:$port/keys/generate") {
                    header("X-Tenant-ID", TEST_TENANT_ID)
                    header("X-User-ID", TEST_USER_ID)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    accept(ContentType.Application.Json)
                }
            assertEquals(HttpStatusCode.Created, response.status)

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["keyPair"], "Response should have keyPair")

            // Verify Location header
            val location = response.headers["Location"]
            assertNotNull(location, "Should have Location header")
        }

    @Test
    fun deleteKeyShouldRemoveKeyThroughAdapter() =
        runTest {
            // First generate a key
            val generateRequest = GenerateKeyGlobal(alias = "key-to-delete")

            val generateResponse =
                client.post("http://localhost:$port/keys/generate") {
                    header("X-Tenant-ID", TEST_TENANT_ID)
                    header("X-User-ID", TEST_USER_ID)
                    contentType(ContentType.Application.Json)
                    setBody(generateRequest)
                }
            assertEquals(HttpStatusCode.Created, generateResponse.status)

            // Then delete it
            val deleteResponse =
                client.delete("http://localhost:$port/keys/key-to-delete") {
                    header("X-Tenant-ID", TEST_TENANT_ID)
                    header("X-User-ID", TEST_USER_ID)
                }
            assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
        }

    @Test
    fun postKeysWithInvalidJsonShouldReturn400() =
        runTest {
            val response =
                client.post("http://localhost:$port/keys") {
                    header("X-Tenant-ID", TEST_TENANT_ID)
                    header("X-User-ID", TEST_USER_ID)
                    contentType(ContentType.Application.Json)
                    setBody("""{"invalid": "json"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["message"], "Error response should have message")
        }

    @Test
    fun postKeysWithoutBodyShouldReturn400() =
        runTest {
            val response =
                client.post("http://localhost:$port/keys") {
                    header("X-Tenant-ID", TEST_TENANT_ID)
                    header("X-User-ID", TEST_USER_ID)
                    contentType(ContentType.Application.Json)
                    setBody("")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun getUnknownPathShouldReturn404() =
        runTest {
            val response =
                client.get("http://localhost:$port/keys/unknown/nested/path") {
                    header("X-Tenant-ID", TEST_TENANT_ID)
                    header("X-User-ID", TEST_USER_ID)
                }
            assertEquals(HttpStatusCode.NotFound, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(body["message"], "Error response should have message")
        }

    companion object {
        const val TEST_TENANT_ID = "test-tenant-123"
        const val TEST_USER_ID = "test-user-456"
        private const val PROPERTY_PREFIX = "kms.providers.testsoftware"
    }
}

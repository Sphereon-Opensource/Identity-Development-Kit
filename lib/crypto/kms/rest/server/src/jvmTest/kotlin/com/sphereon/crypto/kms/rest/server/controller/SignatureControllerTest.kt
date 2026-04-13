/*
 * Copyright 2025 Sphereon International B.V.
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

import com.sphereon.crypto.kms.rest.server.createTestApiAppComponent
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.crypto.kms.rest.server.TestApiAppComponent
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.Base64
import kotlin.test.assertEquals

class SignatureControllerTest {

    private lateinit var appComponent: TestApiAppComponent
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var client: HttpClient
    private var port: Int = 0

    companion object {
        const val TEST_TENANT_ID = "test-tenant-123"
        const val TEST_USER_ID = "test-user-456"
        private const val PROPERTY_PREFIX = "kms.providers.testsoftware"
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
                "$PROPERTY_PREFIX.keyStore.overwriteAlias" to "true"
            )
        )

        appComponent = createTestApiAppComponent(
            application = Unit,
            appId = "kms-signature-test",
            profile = "test",
            version = "1.0.0"
        )
        appComponent.userContextManager.destroyAll()

        server = embeddedServer(CIO, port = port) {
            install(KotlinInjectPlugin) {
                this.appComponent = this@SignatureControllerTest.appComponent
            }
            installUniversalHttpAdapters {
                verboseLogging = true
            }
        }
        server.start(wait = false)
        runBlocking { delay(500) }

        client = HttpClient(io.ktor.client.engine.cio.CIO)
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
    fun createRawSignature() = runTest {
        // Given
        val generated = generateKey("signature-create-${System.currentTimeMillis()}")
        val payload = createSignaturePayload(generated, "hello".encodeToByteArray())

        // When
        val response = client.post("http://localhost:$port/signatures/raw/create") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        // Then
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun verifyRawSignature() = runTest {
        // Given
        val generated = generateKey("signature-verify-${System.currentTimeMillis()}")
        val input = "hello".encodeToByteArray()
        val signature = createSignature(generated, input)
        val payload = createVerifyPayload(generated, input, signature)

        // When
        val response = client.post("http://localhost:$port/signatures/raw/verify") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        // Then
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private data class GeneratedKey(val alias: String, val kid: String, val providerId: String)

    private suspend fun generateKey(alias: String): GeneratedKey {
        val payload = """
            {
                "alias": "$alias",
                "use": "sig",
                "alg": "ECDSA_SHA256",
                "keyOperations": ["sign"]
            }
        """.trimIndent()

        val response = client.post("http://localhost:$port/keys/generate") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val keyPair = body["keyPair"]!!.jsonObject
        val kid = keyPair["kid"]!!.jsonPrimitive.content
        val resolvedAlias = keyPair["alias"]!!.jsonPrimitive.content
        val providerId = keyPair["providerId"]!!.jsonPrimitive.content
        return GeneratedKey(resolvedAlias, kid, providerId)
    }

    private suspend fun createSignature(generated: GeneratedKey, input: ByteArray): String {
        val payload = createSignaturePayload(generated, input)
        val response = client.post("http://localhost:$port/signatures/raw/create") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return body["signature"]!!.jsonPrimitive.content
    }

    private fun createSignaturePayload(generated: GeneratedKey, input: ByteArray): String {
        val inputB64 = Base64.getEncoder().encodeToString(input)
        return """
            {
                "keyInfo": {
                    "kid": "${generated.kid}",
                    "alias": "${generated.alias}",
                    "providerId": "${generated.providerId}",
                    "signatureAlgorithm": "ECDSA_SHA256",
                    "keyVisibility": "PRIVATE",
                    "keyEncoding": "JOSE",
                    "keyType": "EC"
                },
                "input": "$inputB64"
            }
        """.trimIndent()
    }

    private fun createVerifyPayload(generated: GeneratedKey, input: ByteArray, signature: String): String {
        val inputB64 = Base64.getEncoder().encodeToString(input)
        return """
            {
                "keyInfo": {
                    "kid": "${generated.kid}",
                    "alias": "${generated.alias}",
                    "providerId": "${generated.providerId}",
                    "signatureAlgorithm": "ECDSA_SHA256",
                    "keyVisibility": "PRIVATE",
                    "keyEncoding": "JOSE",
                    "keyType": "EC"
                },
                "input": "$inputB64",
                "signature": "$signature"
            }
        """.trimIndent()
    }
}


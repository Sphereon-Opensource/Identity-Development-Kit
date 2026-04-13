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
import kotlin.test.assertEquals

class ProviderControllerTest {

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
            appId = "kms-provider-test",
            profile = "test",
            version = "1.0.0"
        )
        appComponent.userContextManager.destroyAll()

        server = embeddedServer(CIO, port = port) {
            install(KotlinInjectPlugin) {
                this.appComponent = this@ProviderControllerTest.appComponent
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
    fun listProviders() = runTest {
        val response = client.get("http://localhost:$port/providers") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun getProvider() = runTest {
        val response = client.get("http://localhost:$port/providers/testsoftware") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun listProviderKeys() = runTest {
        val response = client.get("http://localhost:$port/providers/testsoftware/keys") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun storeProviderKey() = runTest {
        val payload = """
            {
                "keyInfo": {
                    "key": {
                        "kty": "EC",
                        "kid": "00-qTBov6GxjPSuMNxnk876cMP0JKjbwl4ZyN_sY2tE",
                        "use": "sig",
                        "key_ops": ["sign"],
                        "crv": "P-256",
                        "x": "HFL67WWh6PYWKOy1mzt9Y2ANs-CWFIyVtouR-Jx_mAM",
                        "y": "9f_1x7fwUuEbEwxSNTYE3jQF-zForWpKkEMpiUp1MNI",
                        "d": "P_4YEyuDj4aA4IVYVku4dm3BoDReFTKVsBwb1utoWCQ"
                    },
                    "kid": "00-qTBov6GxjPSuMNxnk876cMP0JKjbwl4ZyN_sY2tE",
                    "alias": "Gxq2tXSNl_kp8tKHNzIlO7jQDM-aYcgn1aewGW8Yby4",
                    "providerId": "testsoftware",
                    "signatureAlgorithm": "ECDSA_SHA256",
                    "keyVisibility": "PUBLIC",
                    "x5c": ["1", "2"],
                    "keyEncoding": "JOSE",
                    "opts": {"test": "test"},
                    "keyType": "EC"
                }
            }
        """.trimIndent()

        val response = client.post("http://localhost:$port/providers/testsoftware/keys") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun generateProviderKey() = runTest {
        val payload = """
            {
                "use": "sig",
                "alg": "ECDSA_SHA256",
                "keyOperations": ["sign"]
            }
        """.trimIndent()

        val response = client.post("http://localhost:$port/providers/testsoftware/keys/generate") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun getProviderKey() = runTest {
        // First store a key
        val storePayload = """
            {
                "keyInfo": {
                    "key": {
                        "kty": "EC",
                        "kid": "00-qTBov6GxjPSuMNxnk876cMP0JKjbwl4ZyN_sY2tE",
                        "use": "sig",
                        "key_ops": ["sign"],
                        "crv": "P-256",
                        "x": "HFL67WWh6PYWKOy1mzt9Y2ANs-CWFIyVtouR-Jx_mAM",
                        "y": "9f_1x7fwUuEbEwxSNTYE3jQF-zForWpKkEMpiUp1MNI",
                        "d": "P_4YEyuDj4aA4IVYVku4dm3BoDReFTKVsBwb1utoWCQ"
                    },
                    "kid": "00-qTBov6GxjPSuMNxnk876cMP0JKjbwl4ZyN_sY2tE",
                    "alias": "Gxq2tXSNl_kp8tKHNzIlO7jQDM-aYcgn1aewGW8Yby4",
                    "providerId": "testsoftware",
                    "signatureAlgorithm": "ECDSA_SHA256",
                    "keyVisibility": "PUBLIC",
                    "x5c": ["1", "2"],
                    "keyEncoding": "JOSE",
                    "opts": {"test": "test"},
                    "keyType": "EC"
                }
            }
        """.trimIndent()

        val storeResponse = client.post("http://localhost:$port/providers/testsoftware/keys") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            contentType(ContentType.Application.Json)
            setBody(storePayload)
        }
        assertEquals(HttpStatusCode.Created, storeResponse.status)

        val storeBody = Json.parseToJsonElement(storeResponse.bodyAsText()).jsonObject
        val storedKid = storeBody["keyInfo"]!!.jsonObject["kid"]!!.jsonPrimitive.content

        // Then get it
        val getResponse = client.get("http://localhost:$port/providers/testsoftware/keys/$storedKid") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            accept(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
    }

    @Test
    fun deleteProviderKey() = runTest {
        // First store a key
        val storePayload = """
            {
                "keyInfo": {
                    "key": {
                        "kty": "EC",
                        "kid": "00-qTBov6GxjPSuMNxnk876cMP0JKjbwl4ZyN_sY2tE",
                        "use": "sig",
                        "key_ops": ["sign"],
                        "crv": "P-256",
                        "x": "HFL67WWh6PYWKOy1mzt9Y2ANs-CWFIyVtouR-Jx_mAM",
                        "y": "9f_1x7fwUuEbEwxSNTYE3jQF-zForWpKkEMpiUp1MNI",
                        "d": "P_4YEyuDj4aA4IVYVku4dm3BoDReFTKVsBwb1utoWCQ"
                    },
                    "kid": "00-qTBov6GxjPSuMNxnk876cMP0JKjbwl4ZyN_sY2tE",
                    "alias": "Gxq2tXSNl_kp8tKHNzIlO7jQDM-aYcgn1aewGW8Yby4",
                    "providerId": "testsoftware",
                    "signatureAlgorithm": "ECDSA_SHA256",
                    "keyVisibility": "PUBLIC",
                    "x5c": ["1", "2"],
                    "keyEncoding": "JOSE",
                    "opts": {"test": "test"},
                    "keyType": "EC"
                }
            }
        """.trimIndent()

        val storeResponse = client.post("http://localhost:$port/providers/testsoftware/keys") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
            contentType(ContentType.Application.Json)
            setBody(storePayload)
        }
        assertEquals(HttpStatusCode.Created, storeResponse.status)

        val storeBody = Json.parseToJsonElement(storeResponse.bodyAsText()).jsonObject
        val storedKid = storeBody["keyInfo"]!!.jsonObject["kid"]!!.jsonPrimitive.content

        // Then delete it
        val deleteResponse = client.delete("http://localhost:$port/providers/testsoftware/keys/$storedKid") {
            header("X-Tenant-ID", TEST_TENANT_ID)
            header("X-User-ID", TEST_USER_ID)
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }
}

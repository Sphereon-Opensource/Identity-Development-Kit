/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.controller

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.crypto.kms.rest.server.TestApiAppGraph
import com.sphereon.crypto.kms.rest.server.createTestApiAppGraph
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
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

class AdditionalKmsHttpAdaptersIntegrationTest {
    private lateinit var appGraph: TestApiAppGraph
    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var client: HttpClient
    private var port: Int = 0

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
                appId = "kms-additional-adapters-test",
                profile = "test",
                version = "1.0.0",
            )
        appGraph.userContextManager.destroyAll()

        server =
            embeddedServer(CIO, port = port) {
                install(KotlinInjectPlugin) {
                    this.appGraph = this@AdditionalKmsHttpAdaptersIntegrationTest.appGraph
                    tenantResolver = FixedTenantResolver("default")
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
    fun capabilitiesEndpointsShouldRouteThroughHttpAdapters() =
        runTest {
            val allCapabilities =
                client.get("http://localhost:$port/capabilities") {
                    commonHeaders()
                }
            val allCapabilitiesBody = allCapabilities.bodyAsText()
            assertEquals(HttpStatusCode.OK, allCapabilities.status, allCapabilitiesBody)
            val allBody = Json.parseToJsonElement(allCapabilitiesBody).jsonObject
            assertTrue(allBody["providers"]!!.jsonArray.isNotEmpty())

            val providerCapabilities =
                client.get("http://localhost:$port/providers/testsoftware/capabilities") {
                    commonHeaders()
                }
            assertEquals(HttpStatusCode.OK, providerCapabilities.status)
            val providerBody = Json.parseToJsonElement(providerCapabilities.bodyAsText()).jsonObject
            assertEquals("testsoftware", providerBody["providerId"]!!.jsonPrimitive.content)

            val queryPayload =
                """
                {
                    "operation": "ENCRYPT",
                    "contentEncryptionAlgorithm": "A256GCM",
                    "providerType": "software"
                }
                """.trimIndent()
            val queryResponse =
                client.post("http://localhost:$port/providers/query") {
                    commonHeaders()
                    contentType(ContentType.Application.Json)
                    setBody(queryPayload)
                }
            assertEquals(HttpStatusCode.OK, queryResponse.status)
            val queryBody = Json.parseToJsonElement(queryResponse.bodyAsText()).jsonObject
            assertTrue(queryBody["matchCount"]!!.jsonPrimitive.content.toInt() >= 1)

            val bestResponse =
                client.post("http://localhost:$port/providers/query/best") {
                    commonHeaders()
                    contentType(ContentType.Application.Json)
                    setBody(queryPayload)
                }
            assertEquals(HttpStatusCode.OK, bestResponse.status)
            val bestBody = Json.parseToJsonElement(bestResponse.bodyAsText()).jsonObject
            assertNotNull(bestBody["match"]?.jsonObject)
        }

    @Test
    fun encryptionEndpointsShouldEncryptAndDecryptThroughHttpAdapter() =
        runTest {
            importSymmetricKey("http-encryption-key")

            val encryptResponse =
                client.post("http://localhost:$port/encryption/encrypt") {
                    commonHeaders()
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "keyInfo": {
                                "alias": "http-encryption-key",
                                "providerId": "testsoftware"
                            },
                            "plaintext": "$PLAINTEXT_BASE64",
                            "algorithm": "A256GCM"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, encryptResponse.status)
            val encrypted = Json.parseToJsonElement(encryptResponse.bodyAsText()).jsonObject
            val ciphertext = encrypted["ciphertext"]!!.jsonPrimitive.content
            val iv = encrypted["iv"]!!.jsonPrimitive.content
            val authTag = encrypted["authTag"]!!.jsonPrimitive.content
            assertTrue(ciphertext.isNotBlank())
            assertTrue(iv.isNotBlank())
            assertTrue(authTag.isNotBlank())

            val decryptResponse =
                client.post("http://localhost:$port/encryption/decrypt") {
                    commonHeaders()
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "keyInfo": {
                                "alias": "http-encryption-key",
                                "providerId": "testsoftware"
                            },
                            "ciphertext": "$ciphertext",
                            "algorithm": "A256GCM",
                            "iv": "$iv",
                            "authTag": "$authTag"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, decryptResponse.status)
            val decrypted = Json.parseToJsonElement(decryptResponse.bodyAsText()).jsonObject
            assertEquals(PLAINTEXT_BASE64, decrypted["plaintext"]!!.jsonPrimitive.content)
        }

    @Test
    fun certificateEndpointsShouldRouteAndReportUnsupportedProviderStores() =
        runTest {
            val unsupportedDefault =
                client.get("http://localhost:$port/certificates") {
                    commonHeaders()
                }
            assertEquals(HttpStatusCode.BadRequest, unsupportedDefault.status)
            val unsupportedDefaultBody = Json.parseToJsonElement(unsupportedDefault.bodyAsText()).jsonObject
            val unsupportedDefaultMessage = unsupportedDefaultBody["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
            assertTrue(unsupportedDefaultMessage.contains("default key store"))

            val listCertificates =
                client.get("http://localhost:$port/certificates?providerId=testsoftware") {
                    commonHeaders()
                }
            val listCertificatesBody = listCertificates.bodyAsText()
            assertEquals(HttpStatusCode.OK, listCertificates.status, listCertificatesBody)
            val certBody = Json.parseToJsonElement(listCertificatesBody).jsonObject
            assertNotNull(certBody["aliases"]?.jsonArray)

            val listChains =
                client.get("http://localhost:$port/certificate-chains?providerId=testsoftware") {
                    commonHeaders()
                }
            assertEquals(HttpStatusCode.OK, listChains.status)
            val chainBody = Json.parseToJsonElement(listChains.bodyAsText()).jsonObject
            assertNotNull(chainBody["aliases"]?.jsonArray)
        }

    @Test
    fun certificateCsrEndpointShouldGenerateCsrThroughHttpAdapter() =
        runTest {
            val response =
                client.post("http://localhost:$port/certificates/csr") {
                    commonHeaders()
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "subjectKeyInfo": $EC_RESOLVED_KEY_INFO,
                            "subject": {
                                "commonName": "adapter-test.example",
                                "organizationName": "Sphereon"
                            },
                            "serialNumber": 42
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("adapter-test.example", body["commonName"]!!.jsonPrimitive.content)
            assertTrue(body["der"]!!.jsonPrimitive.content.isNotBlank())
        }

    private suspend fun importSymmetricKey(alias: String) {
        val response =
            client.post("http://localhost:$port/keys/import") {
                commonHeaders()
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "keyInfo": {
                            "key": {
                                "kty": "oct",
                                "kid": "$alias",
                                "alg": "A256GCMKW",
                                "key_ops": ["encrypt", "decrypt"],
                                "k": "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE"
                            },
                            "kid": "$alias",
                            "alias": "$alias",
                            "providerId": "testsoftware",
                            "keyVisibility": "PRIVATE",
                            "keyEncoding": "JOSE",
                            "keyType": "OCT"
                        }
                    }
                    """.trimIndent(),
                )
            }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.commonHeaders() {
        header("X-Tenant-ID", TEST_TENANT_ID)
        header("X-User-ID", TEST_USER_ID)
        accept(ContentType.Application.Json)
    }

    companion object {
        private const val TEST_TENANT_ID = "test-tenant-123"
        private const val TEST_USER_ID = "test-user-456"
        private const val PROPERTY_PREFIX = "kms.providers.testsoftware"
        private const val PLAINTEXT_BASE64 = "aGVsbG8ta21z"
        private val EC_RESOLVED_KEY_INFO =
            """
            {
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
                "alias": "csr-adapter-key",
                "providerId": "testsoftware",
                "signatureAlgorithm": "ECDSA_SHA256",
                "keyVisibility": "PRIVATE",
                "keyEncoding": "JOSE",
                "keyType": "EC"
            }
            """.trimIndent()
    }
}

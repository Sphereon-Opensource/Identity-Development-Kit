/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.data.store.blob.okd

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OkdBlobAuthenticationTest {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    @Test
    fun serializedAuthenticationContainsOnlyOpaqueHandles() {
        val auth =
            OkdAuthConfig(
                mode = OkdAuthMode.CLIENT_CREDENTIALS,
                tokenUri = "https://issuer.example.com/token",
                clientId = "okd-client",
                clientSecretId = "sec_0123456789abcdef",
                scopes = listOf("okd:documents"),
            )

        val serialized = json.encodeToString(auth)

        assertTrue(serialized.contains("\"clientSecretId\":\"sec_0123456789abcdef\""))
        assertFalse(serialized.contains("\"clientSecret\":"))
        assertFalse(serialized.contains("\"token\":"))
        assertFalse(serialized.contains("\"authHeader\":"))
        assertFalse(serialized.contains("\${"))
    }

    @Test
    fun strictModesRejectMalformedOrCrossModeCredentialFields() {
        assertFailsWith<IllegalArgumentException> {
            OkdAuthConfig(
                mode = OkdAuthMode.STATIC_TOKEN,
                tokenSecretId = "plaintext-token",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OkdAuthConfig(
                mode = OkdAuthMode.BEARER,
                tokenSecretId = "sec_0123456789abcdef",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OkdAuthConfig(
                mode = OkdAuthMode.CLIENT_CREDENTIALS,
                tokenUri = "https://issuer.example.com/token",
                clientId = "okd-client",
                clientSecretId = "sec_0123456789abcdef",
                tokenSecretId = "sec_abcdef0123456789",
            )
        }
    }

    @Test
    fun legacyPlaintextAndCallerSelectedHeaderAreRejectedByTolerantCodec() {
        assertFailsWith<Exception> {
            json.decodeFromString<OkdAuthConfig>(
                """{"mode":"BEARER","token":"plaintext-token"}""",
            )
        }
        assertFailsWith<Exception> {
            json.decodeFromString<OkdAuthConfig>(
                """
                {
                  "mode": "CLIENT_CREDENTIALS",
                  "tokenUri": "https://issuer.example.com/token",
                  "clientId": "okd-client",
                  "clientSecret": "plaintext-secret"
                }
                """.trimIndent(),
            )
        }
        assertFailsWith<Exception> {
            json.decodeFromString<OkdAuthConfig>(
                """{"mode":"BEARER","authHeader":"X-Credential"}""",
            )
        }
    }

    @Test
    fun staticTokenResolvesOpaqueHandleAtUseTime() =
        runTest {
            var resolvedSecretId: String? = null
            val engine =
                MockEngine { request ->
                    assertEquals("Bearer resolved-token", request.headers[HttpHeaders.Authorization])
                    respond(
                        content = ByteReadChannel("content".encodeToByteArray()),
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                    )
                }
            val store =
                createStaticTokenStore(
                    engine,
                    OpaqueSecretResolver { secretId ->
                        resolvedSecretId = secretId
                        Ok("resolved-token")
                    },
                )

            val result = store.get(BlobInfo(storeId = "okd", path = "document-1"))

            assertTrue(result.isOk)
            assertEquals("sec_0123456789abcdef", resolvedSecretId)
        }

    @Test
    fun credentialFailureStopsBeforeNetworkAndSanitizesError() =
        runTest {
            var networkCalled = false
            val engine =
                MockEngine {
                    networkCalled = true
                    respondError(HttpStatusCode.InternalServerError)
                }
            val store =
                createStaticTokenStore(
                    engine,
                    OpaqueSecretResolver {
                        Err(IdkError.FORBIDDEN_ERROR(message = "provider path and internal credential detail"))
                    },
                )

            val result = store.get(BlobInfo(storeId = "okd", path = "document-1"))

            assertTrue(result.isErr)
            assertFalse(networkCalled)
            assertEquals("OKD credential is unavailable", result.error.message.defaultMessage)
            assertFalse(result.error.message.defaultMessage.contains("provider path"))
        }

    @Test
    fun missingAuthenticatedSessionStopsBearerModeBeforeNetwork() =
        runTest {
            var networkCalled = false
            val engine =
                MockEngine {
                    networkCalled = true
                    respondError(HttpStatusCode.InternalServerError)
                }
            val store =
                OkdBlobStore(
                    config =
                        OkdBlobStoreConfig(
                            baseUrl = "https://dms.example.com/api/v5",
                            auth = OkdAuthConfig(mode = OkdAuthMode.BEARER),
                        ),
                    http = HttpClient(engine),
                    execution = null,
                    opaqueSecretResolver = OpaqueSecretResolver { Ok("unused") },
                )

            val result = store.get(BlobInfo(storeId = "okd", path = "document-1"))

            assertTrue(result.isErr)
            assertFalse(networkCalled)
            assertEquals("Authenticated session token is unavailable", result.error.message.defaultMessage)
        }

    private fun createStaticTokenStore(
        engine: MockEngine,
        opaqueSecretResolver: OpaqueSecretResolver,
    ): OkdBlobStore =
        OkdBlobStore(
            config =
                OkdBlobStoreConfig(
                    baseUrl = "https://dms.example.com/api/v5",
                    auth =
                        OkdAuthConfig(
                            mode = OkdAuthMode.STATIC_TOKEN,
                            tokenSecretId = "sec_0123456789abcdef",
                        ),
                ),
            http = HttpClient(engine),
            execution = null,
            opaqueSecretResolver = opaqueSecretResolver,
        )
}

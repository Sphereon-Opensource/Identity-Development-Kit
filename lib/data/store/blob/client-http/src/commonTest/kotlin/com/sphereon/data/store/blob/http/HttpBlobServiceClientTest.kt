/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.blob.http

import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.command.BlobDeleteOutput
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpBlobServiceClientTest {
    private val jsonCodec =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun createConfig(baseUrl: String = "http://localhost:8080") =
        HttpBlobServiceClientConfig(
            baseUrl = baseUrl,
            defaultStoreId = "test-store",
            auth =
                HttpBlobAuthConfig(
                    mode = HttpBlobAuthMode.STATIC_TOKEN,
                    token = "test-token",
                ),
        )

    private fun createClient(engine: MockEngine): HttpBlobServiceClient {
        val httpClient =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(jsonCodec)
                }
            }
        return HttpBlobServiceClient(
            config = createConfig(),
            http = httpClient,
            execution = null,
        )
    }

    @Test
    fun storeBlobSendsCorrectRequest() =
        runTest {
            val descriptor =
                BlobDescriptor(
                    path = "docs/file.txt",
                    storeId = "test-store",
                    sizeBytes = 13,
                    contentType = "text/plain",
                )
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Put, request.method)
                    assertTrue(request.url.encodedPath.endsWith("/api/blob-stores/test-store/blobs/docs/file.txt"))
                    assertEquals("Bearer test-token", request.headers["Authorization"])
                    respond(jsonCodec.encodeToString(descriptor), headers = jsonHeaders)
                }

            val client = createClient(engine)
            val target =
                BlobInfo(
                    storeId = "test-store",
                    path = "docs/file.txt",
                    tenantId = "tenant-1",
                )
            val result =
                client.storeBlob(
                    target = target,
                    data = "Hello, World!".encodeToByteArray(),
                )

            assertTrue(result.isOk)
            assertEquals("docs/file.txt", result.value.path)
            assertEquals(13L, result.value.sizeBytes)
        }

    @Test
    fun getBlobContentReturnsRawBytes() =
        runTest {
            val content = "Hello, World!".encodeToByteArray()
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertTrue(request.url.encodedPath.endsWith("/content"))
                    respond(
                        content = ByteReadChannel(content),
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("text/plain"),
                                HttpHeaders.ContentDisposition to listOf("attachment; filename=\"file.txt\""),
                            ),
                    )
                }

            val client = createClient(engine)
            val info =
                BlobInfo(
                    storeId = "test-store",
                    path = "docs/file.txt",
                    tenantId = "tenant-1",
                )
            val result = client.getBlob(info = info)

            assertTrue(result.isOk)
            val resolvedBlob = result.value
            assertEquals("Hello, World!", resolvedBlob.data.decodeToString())
            assertEquals("file.txt", resolvedBlob.descriptor.filename)
        }

    @Test
    fun getBlobInfoReturnsStat() =
        runTest {
            val descriptor =
                BlobDescriptor(
                    path = "docs/file.txt",
                    storeId = "test-store",
                    sizeBytes = 42,
                    contentType = "application/pdf",
                )
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertTrue(request.url.encodedPath.endsWith("/stat"))
                    respond(jsonCodec.encodeToString(descriptor), headers = jsonHeaders)
                }

            val client = createClient(engine)
            val info =
                BlobInfo(
                    storeId = "test-store",
                    path = "docs/file.txt",
                    tenantId = "tenant-1",
                )
            val result = client.getBlobInfo(info = info)

            assertTrue(result.isOk)
            assertEquals(42L, result.value.sizeBytes)
        }

    @Test
    fun deleteBlobReturnsResult() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Delete, request.method)
                    respond(jsonCodec.encodeToString(BlobDeleteOutput(deleted = true)), headers = jsonHeaders)
                }

            val client = createClient(engine)
            val info =
                BlobInfo(
                    storeId = "test-store",
                    path = "docs/file.txt",
                    tenantId = "tenant-1",
                )
            val result = client.deleteBlob(info = info)

            assertTrue(result.isOk)
            assertTrue(result.value)
        }

    @Test
    fun deleteNotFoundReturnsFalse() =
        runTest {
            val engine =
                MockEngine {
                    respondError(HttpStatusCode.NotFound)
                }

            val client = createClient(engine)
            val info =
                BlobInfo(
                    storeId = "test-store",
                    path = "missing.txt",
                    tenantId = "tenant-1",
                )
            val result = client.deleteBlob(info = info)

            assertTrue(result.isOk)
            assertEquals(false, result.value)
        }

    @Test
    fun listBlobsWithOptions() =
        runTest {
            val listResult =
                ListResult(
                    descriptors =
                        listOf(
                            BlobDescriptor(path = "a.txt", storeId = "test-store", sizeBytes = 10),
                        ),
                    nextPageToken = "token2",
                )
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertTrue(request.url.encodedPath.endsWith("/api/blob-stores/test-store/blobs"))
                    assertEquals("myprefix", request.url.parameters["prefix"])
                    respond(jsonCodec.encodeToString(listResult), headers = jsonHeaders)
                }

            val client = createClient(engine)
            val info =
                BlobInfo(
                    storeId = "test-store",
                    tenantId = "tenant-1",
                )
            val result =
                client.listBlobs(
                    info = info,
                    options = ListOptions(prefix = "myprefix"),
                )

            assertTrue(result.isOk)
            assertEquals(1, result.value.descriptors.size)
            assertTrue(result.value.hasMore)
        }

    @Test
    fun httpErrorsMappedCorrectly() =
        runTest {
            val engine =
                MockEngine {
                    respondError(HttpStatusCode.Forbidden)
                }

            val client = createClient(engine)
            val info =
                BlobInfo(
                    storeId = "test-store",
                    path = "secret.txt",
                    tenantId = "tenant-1",
                )
            val result = client.getBlob(info = info)

            assertTrue(result.isErr)
            assertTrue(result.error.code?.contains("PERMISSION_DENIED") == true)
        }

    @Test
    fun copyBlobSendsPost() =
        runTest {
            val descriptor =
                BlobDescriptor(
                    path = "docs/copy.txt",
                    storeId = "test-store",
                    sizeBytes = 13,
                )
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertTrue(request.url.encodedPath.endsWith("/copy"))
                    respond(jsonCodec.encodeToString(descriptor), headers = jsonHeaders)
                }

            val client = createClient(engine)
            val source =
                BlobInfo(
                    storeId = "test-store",
                    path = "docs/file.txt",
                    tenantId = "tenant-1",
                )
            val destination =
                BlobInfo(
                    storeId = "test-store",
                    path = "docs/copy.txt",
                    tenantId = "tenant-1",
                )
            val result = client.copyBlob(source = source, destination = destination)

            assertTrue(result.isOk)
        }

    @Test
    fun moveBlobSendsPost() =
        runTest {
            val descriptor =
                BlobDescriptor(
                    path = "docs/moved.txt",
                    storeId = "test-store",
                    sizeBytes = 13,
                )
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertTrue(request.url.encodedPath.endsWith("/move"))
                    respond(jsonCodec.encodeToString(descriptor), headers = jsonHeaders)
                }

            val client = createClient(engine)
            val source =
                BlobInfo(
                    storeId = "test-store",
                    path = "docs/file.txt",
                    tenantId = "tenant-1",
                )
            val destination =
                BlobInfo(
                    storeId = "test-store",
                    path = "docs/moved.txt",
                    tenantId = "tenant-1",
                )
            val result = client.moveBlob(source = source, destination = destination)

            assertTrue(result.isOk)
        }

    @Test
    fun createTempUrlCallsServer() =
        runTest {
            val tempUrlResult =
                com.sphereon.data.store.blob.TempUrlResult(
                    url = "https://cdn.example.com/signed/file.txt?token=abc",
                    expiresAt =
                        kotlin.time.Clock.System
                            .now(),
                    isPublic = true,
                )
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertTrue(request.url.encodedPath.endsWith("/temp-url"))
                    respond(jsonCodec.encodeToString(tempUrlResult), headers = jsonHeaders)
                }

            val client = createClient(engine)
            val info =
                BlobInfo(
                    storeId = "test-store",
                    path = "file.txt",
                    tenantId = "tenant-1",
                )
            val result = client.createTempUrl(info = info)

            assertTrue(result.isOk)
            assertTrue(result.value.url.contains("signed"))
        }

    @Test
    fun createTempUrlReturnsErrorWhenServerDoesNotSupport() =
        runTest {
            val engine =
                MockEngine {
                    respondError(HttpStatusCode.NotFound)
                }
            val client = createClient(engine)
            val info =
                BlobInfo(
                    storeId = "test-store",
                    path = "file.txt",
                    tenantId = "tenant-1",
                )
            val result = client.createTempUrl(info = info)

            assertTrue(result.isErr)
            assertTrue(result.error.code?.contains("NOT_FOUND") == true)
        }
}

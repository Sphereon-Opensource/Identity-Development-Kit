/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.ktor.http.client.provider

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ValidatedEgressHttpFetcherTest {
    private val servers = mutableListOf<HttpServer>()

    @AfterTest
    fun stop() {
        servers.forEach { it.stop(0) }
    }

    private fun server(configure: (HttpServer) -> Unit): Int {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        configure(server)
        server.start()
        servers += server
        return server.address.port
    }

    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)

    /** A fetcher that reaches the local test server: plain http, any port, loopback allowed. Only the behaviour under test is enabled. */
    private fun local(
        maxBytes: Long = 1024,
        timeout: Duration = 5.seconds,
    ) = ValidatedEgressHttpFetcher(
        maxBytes = maxBytes,
        requestTimeout = timeout,
        allowedPorts = null,
        allowedSchemes = setOf("http"),
        addressPolicy = { true },
        resolver = { listOf(InetAddress.getLoopbackAddress()) },
    )

    @Test
    fun nonPublicAddressesAreRefused() {
        val refused =
            listOf(
                "127.0.0.1", "127.1.2.3", "0.0.0.0", "10.0.0.1", "172.16.0.1", "172.31.255.254", "192.168.1.1", "169.254.169.254",
                "100.64.0.1", "198.18.0.1", "224.0.0.1", "239.255.255.255", "255.255.255.255", "240.0.0.1",
                "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254",
                "64:ff9b::7f00:1", "2002:7f00:1::1", "2001:db8::1",
            )
        for (literal in refused) {
            assertFalse(EgressAddressPolicy.isPublic(ip(literal)), "$literal must be refused")
        }
        for (literal in listOf("93.184.216.34", "8.8.8.8", "1.1.1.1", "2606:2800:220:1:248:1893:25c8:1946", "2001:4860:4860::8888")) {
            assertTrue(EgressAddressPolicy.isPublic(ip(literal)), "$literal must be allowed")
        }
    }

    @Test
    fun ipLiteralAndLocalUrlsAreRefusedBeforeAnyConnection() {
        val fetcher = ValidatedEgressHttpFetcher(maxBytes = 1024)
        for (url in listOf(
            "https://localhost/x", "https://127.0.0.1/x", "https://[::1]/x", "https://169.254.169.254/latest/meta-data", "https://10.1.2.3/x",
            "https://[::ffff:127.0.0.1]/x", "https://0.0.0.0/x", "https://224.0.0.1/x", "https://[fc00::1]/x", "https://[fe80::1]/x",
        )) {
            assertFailsWith<EgressFetchException>(url) { runBlocking { fetcher.get(url) } }
        }
    }

    @Test
    fun onlyHttpsWithoutUserinfoOnPort443IsAccepted() {
        val fetcher = ValidatedEgressHttpFetcher(maxBytes = 1024, resolver = { listOf(ip("93.184.216.34")) })
        for (url in listOf("http://example.org/x", "ftp://example.org/x", "file:///etc/passwd", "https://user:pw@example.org/x", "https://example.org:8443/x", "not a url", "https:///x")) {
            assertFailsWith<EgressFetchException>(url) { fetcher.validate(url) }
        }
        fetcher.validate("https://example.org/x")
        fetcher.validate("https://example.org:443/x")
    }

    @Test
    fun aHostNameThatResolvesToAPrivateAddressIsRefused() {
        val fetcher = ValidatedEgressHttpFetcher(maxBytes = 1024, resolver = { listOf(ip("93.184.216.34"), ip("10.0.0.5")) })
        assertFailsWith<EgressFetchException> { runBlocking { fetcher.get("https://mixed.example.org/x") } }
    }

    @Test
    fun aRebindingAnswerAtConnectTimeIsRefusedByTheConnectionResolver() {
        val calls = AtomicInteger()
        val fetcher =
            ValidatedEgressHttpFetcher(
                maxBytes = 1024,
                resolver = { if (calls.getAndIncrement() == 0) listOf(ip("93.184.216.34")) else listOf(ip("127.0.0.1")) },
            )
        val failure = assertFailsWith<EgressFetchException> { runBlocking { fetcher.get("https://rebind.example.org/x") } }
        assertTrue(calls.get() >= 2, "the connection must resolve (and validate) again")
        assertFalse(failure.message.orEmpty().contains("127.0.0.1"))
    }

    @Test
    fun aValidatedResponseIsReturnedByteForByte() {
        val payload = byteArrayOf(0, 1, 2, -1, 65, 66)
        val port =
            server { s ->
                s.createContext("/doc") { ex ->
                    ex.responseHeaders.add("Content-Type", "application/octet-stream")
                    ex.sendResponseHeaders(200, payload.size.toLong())
                    ex.responseBody.use { it.write(payload) }
                }
            }
        val response = runBlocking { local().get("http://localhost:$port/doc") }
        assertContentEquals(payload, response.bytes)
        assertEquals("application/octet-stream", response.contentType)
    }

    @Test
    fun redirectsAreNotFollowed() {
        val followed = AtomicInteger()
        val port =
            server { s ->
                s.createContext("/redirect") { ex ->
                    ex.responseHeaders.add("Location", "/target")
                    ex.sendResponseHeaders(302, -1)
                    ex.close()
                }
                s.createContext("/target") { ex ->
                    followed.incrementAndGet()
                    ex.sendResponseHeaders(200, -1)
                    ex.close()
                }
            }
        val failure = assertFailsWith<EgressFetchException> { runBlocking { local().get("http://localhost:$port/redirect") } }
        assertEquals("HTTP 302", failure.message)
        assertEquals(0, followed.get())
    }

    @Test
    fun aDeclaredOversizeResponseIsRefused() {
        val port =
            server { s ->
                s.createContext("/big") { ex ->
                    ex.sendResponseHeaders(200, 5000)
                    ex.responseBody.use { it.write(ByteArray(5000)) }
                }
            }
        assertFailsWith<EgressFetchException> { runBlocking { local(maxBytes = 1000).get("http://localhost:$port/big") } }
    }

    @Test
    fun aStreamedResponseWithoutContentLengthIsCappedWhileReading() {
        val port =
            server { s ->
                s.createContext("/chunked") { ex ->
                    // length 0 means chunked transfer encoding: no Content-Length is sent
                    ex.sendResponseHeaders(200, 0)
                    ex.responseBody.use { out ->
                        repeat(64) { out.write(ByteArray(1024)) }
                    }
                }
            }
        val failure = assertFailsWith<EgressFetchException> { runBlocking { local(maxBytes = 4096).get("http://localhost:$port/chunked") } }
        assertTrue(failure.message.orEmpty().contains("byte limit"), failure.message)
    }

    @Test
    fun aSlowServerHitsTheRequestTimeout() {
        val port =
            server { s ->
                s.createContext("/slow") { ex ->
                    Thread.sleep(3000)
                    ex.sendResponseHeaders(200, -1)
                    ex.close()
                }
            }
        val started = System.nanoTime()
        assertFailsWith<EgressFetchException> { runBlocking { local(timeout = 500.milliseconds).get("http://localhost:$port/slow") } }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2500, "the request must be cut off by the timeout")
    }

    @Test
    fun errorMessagesNeverCarryTheResponseBody() {
        val port =
            server { s ->
                s.createContext("/err") { ex ->
                    val body = "SECRET-INTERNAL-DETAIL".toByteArray()
                    ex.sendResponseHeaders(500, body.size.toLong())
                    ex.responseBody.use { it.write(body) }
                }
            }
        val failure = assertFailsWith<EgressFetchException> { runBlocking { local().get("http://localhost:$port/err") } }
        assertEquals("HTTP 500", failure.message)
        assertFalse(failure.message.orEmpty().contains("SECRET"))
    }
}

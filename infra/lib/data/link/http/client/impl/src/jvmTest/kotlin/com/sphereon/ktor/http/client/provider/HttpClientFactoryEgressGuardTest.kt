/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.di.context.PrincipalType
import com.sphereon.core.defaults.app.staticMinimalTestAppGraph
import com.sun.net.httpserver.HttpServer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpClientFactoryEgressGuardTest {
    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)

    private fun mappedV6(v4: String): InetAddress {
        val bytes = ByteArray(16)
        bytes[10] = 0xFF.toByte()
        bytes[11] = 0xFF.toByte()
        ip(v4).address.copyInto(bytes, 12)
        return Inet6Address.getByAddress(null, bytes, 0)
    }

    private fun TestScope.newFactory(resolver: (String) -> List<InetAddress>): HttpClientFactoryJvmImpl {
        val app = staticMinimalTestAppGraph(this, "http-client-egress-guard", "test", "1.0.0")
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("http-client-egress-guard-${System.nanoTime()}", principalType = PrincipalType.USER)
        return HttpClientFactoryJvmImpl(
            execution = session.asCoreApiServiceGraph().serviceExecution,
        ).also { it.egressResolver = resolver }
    }

    private fun Throwable.chainText(): String = generateSequence(this) { it.cause }.take(8).joinToString(" | ") { "${it::class.simpleName}: ${it.message}" }

    @Test
    fun hostNamesResolvingToInternalAddressesAreRefusedUnderBlockPrivate() = runTest {
        val cases =
            mapOf(
                "loopback.example.test" to ip("127.0.0.1"),
                "metadata.example.test" to ip("169.254.169.254"),
                "rfc1918.example.test" to ip("10.0.0.5"),
                "v6loopback.example.test" to ip("::1"),
                "mapped.example.test" to mappedV6("10.0.0.5"),
                "mappedloop.example.test" to mappedV6("127.0.0.1"),
                "shared.example.test" to ip("100.64.0.9"),
            )
        val factory = newFactory { host -> listOf(cases.getValue(host)) }
        val client = factory.createClient(HttpClientOptions.createDefault().copy(urlValidation = UrlValidationPolicy.BLOCK_PRIVATE))
        client.use {
            for (host in cases.keys) {
                val failure = assertFailsWith<Throwable>(host) { it.get("http://$host:9/x") }
                assertTrue(failure.chainText().contains("not allowed"), "$host must be refused by the address check: ${failure.chainText()}")
            }
        }
    }

    @Test
    fun aHostNameWithOneInternalAnswerAmongPublicOnesIsRefused() = runTest {
        val factory = newFactory { listOf(ip("93.184.216.34"), ip("10.0.0.5")) }
        val client = factory.createClient(HttpClientOptions.createDefault().copy(urlValidation = UrlValidationPolicy.BLOCK_PRIVATE))
        client.use {
            val failure = assertFailsWith<Throwable> { it.get("http://mixed.example.test:9/x") }
            assertTrue(failure.chainText().contains("not allowed"), failure.chainText())
        }
    }

    @Test
    fun literalInternalUrlsAreRefusedUnderBlockPrivate() = runTest {
        val factory = newFactory { error("literals must not need DNS") }
        val client = factory.createClient(HttpClientOptions.createDefault().copy(urlValidation = UrlValidationPolicy.BLOCK_PRIVATE))
        client.use {
            for (url in listOf("http://127.0.0.1:9/x", "http://169.254.169.254/x", "http://[::1]:9/x", "http://[::ffff:10.0.0.5]:9/x")) {
                assertFailsWith<UrlValidationException>(url) { it.get(url) }
            }
        }
    }

    @Test
    fun internalPoliciesReachInternalAddressesByLiteralAndByResolvedName() = runTest {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.createContext("/ok") { exchange ->
            val body = "internal".encodeToByteArray()
            exchange.sendResponseHeaders(HttpStatusCode.OK.value, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val port = server.address.port
            val factory = newFactory { listOf(ip("127.0.0.1")) }
            val allowPrivate = factory.createClient(HttpClientOptions.createDefault().copy(urlValidation = UrlValidationPolicy.ALLOW_PRIVATE))
            allowPrivate.use { assertEquals("internal", it.get("http://127.0.0.1:$port/ok").bodyAsText()) }
            // A guarded policy that permits loopback resolves and connects to a name that maps to it.
            val guarded = factory.createClient(HttpClientOptions.createDefault().copy(urlValidation = loopbackOriginPolicy))
            guarded.use { assertEquals("internal", it.get("http://svc.example.test:$port/ok").bodyAsText()) }
        } finally {
            server.stop(0)
        }
    }

    private fun redirectingServer(
        target: String,
        finalHits: AtomicInteger,
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.createContext("/start") { exchange ->
            exchange.responseHeaders.add("Location", target)
            exchange.sendResponseHeaders(HttpStatusCode.Found.value, -1)
            exchange.responseBody.use { }
        }
        server.createContext("/final") { exchange ->
            finalHits.incrementAndGet()
            val body = "final".encodeToByteArray()
            exchange.sendResponseHeaders(HttpStatusCode.OK.value, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        return server
    }

    /** Loopback origin allowed (private-network flag off) so a redirect can start somewhere reachable; RFC 1918 stays blocked. */
    private val loopbackOriginPolicy =
        UrlValidationPolicy.BLOCK_PRIVATE.copy(blockPrivateNetworks = false, blockedHosts = emptySet(), blockedHostSuffixes = emptySet())

    @Test
    fun aRedirectToAPrivateLiteralIsRefusedBeforeItIsFollowed() = runTest {
        val finalHits = AtomicInteger()
        val server = redirectingServer("http://10.0.0.5/final", finalHits)
        try {
            val factory = newFactory { listOf(ip("127.0.0.1")) }
            val client = factory.createClient(HttpClientOptions.createDefault().copy(urlValidation = loopbackOriginPolicy))
            client.use {
                assertFailsWith<UrlValidationException> { it.get("http://127.0.0.1:${server.address.port}/start") }
            }
        } finally {
            server.stop(0)
        }
        assertEquals(0, finalHits.get())
    }

    @Test
    fun aRedirectToAHostNameThatResolvesToAPrivateAddressIsRefused() = runTest {
        val finalHits = AtomicInteger()
        val server = redirectingServer("http://rebind.example.test/final", finalHits)
        try {
            val factory = newFactory { host -> if (host == "rebind.example.test") listOf(ip("10.0.0.9")) else listOf(ip("127.0.0.1")) }
            val client = factory.createClient(HttpClientOptions.createDefault().copy(urlValidation = loopbackOriginPolicy))
            client.use {
                val failure = assertFailsWith<Throwable> { it.get("http://127.0.0.1:${server.address.port}/start") }
                assertTrue(failure.chainText().contains("not allowed"), failure.chainText())
            }
        } finally {
            server.stop(0)
        }
        assertEquals(0, finalHits.get())
    }

    @Test
    fun aRedirectToAnAllowedSameOriginTargetIsStillFollowed() = runTest {
        val finalHits = AtomicInteger()
        val server = redirectingServer("/final", finalHits)
        try {
            val factory = newFactory { listOf(ip("127.0.0.1")) }
            val client = factory.createClient(HttpClientOptions.createDefault().copy(urlValidation = loopbackOriginPolicy))
            client.use {
                assertEquals("final", it.get("http://127.0.0.1:${server.address.port}/start").bodyAsText())
            }
        } finally {
            server.stop(0)
        }
        assertEquals(1, finalHits.get())
    }

    @Test
    fun theEngineLiteralInterceptorRefusesInternalLiteralsThatBypassTheResolver() {
        val guard = EgressGuard(addressPolicy = EgressAddressPolicy::isPublic)
        val client = OkHttpClient.Builder().dns(guard.dns).addInterceptor(guard.literalInterceptor).build()
        for (url in listOf("http://127.0.0.1:9/x", "http://[::ffff:7f00:1]:9/x", "http://[::1]:9/x", "http://10.1.2.3:9/x")) {
            assertFailsWith<BlockedEgressAddressException>(url) { client.newCall(Request.Builder().url(url).build()).execute() }
        }
    }
}

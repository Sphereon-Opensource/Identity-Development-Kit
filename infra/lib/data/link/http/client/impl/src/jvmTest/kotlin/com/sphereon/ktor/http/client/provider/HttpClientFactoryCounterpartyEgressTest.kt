/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.di.context.PrincipalType
import com.sphereon.core.defaults.app.staticMinimalTestAppGraph
import io.ktor.client.request.get
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.net.Inet6Address
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The counterparty egress rule on the JVM engine (the engine of the wallet-interaction service, the desktop wallet and,
 * through OkHttp, the Android wallet): every connection is checked against the addresses it resolves to.
 */
class HttpClientFactoryCounterpartyEgressTest {
    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)

    private fun mappedV6(v4: String): InetAddress {
        val bytes = ByteArray(16)
        bytes[10] = 0xFF.toByte()
        bytes[11] = 0xFF.toByte()
        ip(v4).address.copyInto(bytes, 12)
        return Inet6Address.getByAddress(null, bytes, 0)
    }

    private fun TestScope.newFactory(resolver: (String) -> List<InetAddress>): HttpClientFactoryJvmImpl {
        val app = staticMinimalTestAppGraph(this, "http-client-counterparty-egress", "test", "1.0.0")
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId("http-client-counterparty-egress-${System.nanoTime()}", principalType = PrincipalType.USER)
        return HttpClientFactoryJvmImpl(
            execution = session.asCoreApiServiceGraph().serviceExecution,
        ).also { it.egressResolver = resolver }
    }

    private fun Throwable.chainText(): String = generateSequence(this) { it.cause }.take(8).joinToString(" | ") { "${it::class.simpleName}: ${it.message}" }

    @Test
    fun everyBlockedRangeIsClassifiedAsNotPublicAtItsBoundaries() {
        val blocked =
            listOf(
                "0.0.0.0", "0.255.255.255", "10.0.0.0", "10.255.255.255", "100.64.0.0", "100.127.255.255", "127.0.0.0", "127.255.255.255",
                "169.254.0.0", "169.254.255.255", "172.16.0.0", "172.31.255.255", "192.0.0.0", "192.0.0.255", "192.168.0.0", "192.168.255.255",
                "198.18.0.0", "198.19.255.255", "224.0.0.0", "239.255.255.255", "240.0.0.0", "255.255.255.255",
                "::", "::1", "fc00::", "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "fe80::", "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
                "ff00::", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "64:ff9b::7f00:1", "64:ff9b::a00:1", "64:ff9b::c0a8:1", "64:ff9b::a9fe:a9fe",
                "64:ff9b:1::", "64:ff9b:1::1", "64:ff9b:1:ffff:ffff:ffff:ffff:ffff", "::ffff:0:7f00:1", "::ffff:0:a00:5", "::ffff:0:0",
            )
        for (literal in blocked) {
            assertFalse(EgressAddressPolicy.isPublic(ip(literal)), "$literal must not be public")
        }
        for (v4 in listOf("127.0.0.1", "10.0.0.5", "169.254.169.254", "192.168.1.1", "100.64.0.1", "198.18.0.1", "224.0.0.1")) {
            assertFalse(EgressAddressPolicy.isPublic(mappedV6(v4)), "::ffff:$v4 must not be public")
        }
    }

    @Test
    fun publicAddressesNextToTheBlockedRangesAreAllowed() {
        val allowed =
            listOf(
                "1.1.1.1", "8.8.8.8", "9.255.255.255", "11.0.0.0", "100.63.255.255", "100.128.0.0", "126.255.255.255", "128.0.0.0",
                "169.253.255.255", "169.255.0.0", "172.15.255.255", "172.32.0.0", "192.0.1.0", "192.167.255.255", "192.169.0.0",
                "198.17.255.255", "198.20.0.0", "223.255.255.255", "93.184.216.34",
                "2606:4700::1111", "2001:4860:4860::8888", "64:ff9b::808:808", "64:ff9b:2::1", "::ffff:0:808:808",
            )
        for (literal in allowed) {
            assertTrue(EgressAddressPolicy.isPublic(ip(literal)), "$literal must be public")
        }
        assertTrue(EgressAddressPolicy.isPublic(mappedV6("8.8.8.8")))
    }

    @Test
    fun hostNamesResolvingToBlockedAddressesAreRefusedUnderTheCounterpartyRule() = runTest {
        val cases =
            mapOf(
                "loopback.example.test" to ip("127.0.0.1"),
                "metadata.example.test" to ip("169.254.169.254"),
                "rfc1918.example.test" to ip("10.0.0.5"),
                "private16.example.test" to ip("172.16.4.4"),
                "private192.example.test" to ip("192.168.0.10"),
                "shared.example.test" to ip("100.64.0.9"),
                "benchmark.example.test" to ip("198.18.0.9"),
                "ietf.example.test" to ip("192.0.0.9"),
                "multicast.example.test" to ip("224.0.0.9"),
                "v6loopback.example.test" to ip("::1"),
                "v6unique.example.test" to ip("fc00::9"),
                "v6linklocal.example.test" to ip("fe80::9"),
                "v6multicast.example.test" to ip("ff02::9"),
                "mapped.example.test" to mappedV6("10.0.0.5"),
                "mappedloop.example.test" to mappedV6("127.0.0.1"),
                "nat64.example.test" to ip("64:ff9b::7f00:1"),
            )
        val factory = newFactory { host -> listOf(cases.getValue(host)) }
        val client = factory.createClient(HttpClientOptions.createDefault().counterpartyEgress())
        client.use {
            for (host in cases.keys) {
                val failure = assertFailsWith<Throwable>(host) { it.get("https://$host:9/x") }
                assertTrue(failure.chainText().contains("not allowed"), "$host must be refused by the address check: ${failure.chainText()}")
            }
        }
    }

    @Test
    fun aHostNameWithOneBlockedAnswerAmongPublicOnesIsRefused() = runTest {
        val factory = newFactory { listOf(ip("93.184.216.34"), ip("127.0.0.1")) }
        val client = factory.createClient(HttpClientOptions.createDefault().counterpartyEgress())
        client.use {
            val failure = assertFailsWith<Throwable> { it.get("https://mixed.example.test:9/x") }
            assertTrue(failure.chainText().contains("not allowed"), failure.chainText())
        }
    }

    @Test
    fun literalBlockedUrlsAndNonHttpsUrlsAreRefusedWithoutAnyLookup() = runTest {
        val lookups = AtomicInteger()
        val factory = newFactory { lookups.incrementAndGet(); error("literals and refused schemes must not need DNS") }
        val client = factory.createClient(HttpClientOptions.createDefault().counterpartyEgress())
        client.use {
            for (url in listOf(
                "https://127.0.0.1:9/x",
                "https://169.254.169.254/x",
                "https://[::1]:9/x",
                "https://[::ffff:10.0.0.5]:9/x",
                "https://[64:ff9b::7f00:1]/x",
                "https://localhost/x",
                "https://2130706433/x",
                "http://93.184.216.34/x",
                "http://issuer.example.com/x",
                "https://user:secret@issuer.example.com/x",
            )) {
                assertFailsWith<UrlValidationException>(url) { it.get(url) }
            }
        }
        assertTrue(lookups.get() == 0, "no DNS lookup may happen for a refused URL")
    }

    @Test
    fun theRuleCannotBeLoosenedThroughAdditionalConfigOrAnotherPolicyOnTheSameOptions() = runTest {
        val factory = newFactory { listOf(ip("127.0.0.1")) }
        val loosened =
            HttpClientOptions
                .createDefault()
                .copy(urlValidation = UrlValidationPolicy.ALLOW_PRIVATE, additionalConfig = { followRedirects = true })
                .counterpartyEgress()
        factory.createClient(loosened).use {
            val failure = assertFailsWith<Throwable> { it.get("https://loopback.example.test:9/x") }
            assertTrue(failure.chainText().contains("not allowed"), failure.chainText())
        }
    }

    @Test
    fun aSystemProxyDoesNotLetAGuardedClientSkipTheAddressCheck() = runTest {
        val previous = ProxySelector.getDefault()
        ProxySelector.setDefault(
            object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> = listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.test", 3128)))

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
            },
        )
        try {
            val lookups = mutableListOf<String>()
            val factory = newFactory { host -> lookups += host; listOf(ip("10.0.0.5")) }
            factory.createClient(HttpClientOptions.createDefault().counterpartyEgress()).use {
                val failure = assertFailsWith<Throwable> { it.get("https://target.example.test:9/x") }
                assertTrue(failure.chainText().contains("not allowed"), failure.chainText())
            }
            // With a proxy in the path the resolver would be asked for the proxy and the target would never be checked.
            assertEquals(listOf("target.example.test"), lookups.distinct())
        } finally {
            ProxySelector.setDefault(previous)
        }
    }
}

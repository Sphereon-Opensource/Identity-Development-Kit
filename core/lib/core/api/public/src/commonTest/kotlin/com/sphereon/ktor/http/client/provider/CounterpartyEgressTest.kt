/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.ktor.http.client.provider

import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CounterpartyEgressTest {
    private val policy = UrlValidationPolicy.COUNTERPARTY_EGRESS

    private fun assertRefused(url: String) {
        assertFailsWith<UrlValidationException>(url) { policy.validate(Url(url)) }
    }

    private fun assertAllowed(url: String) {
        policy.validate(Url(url))
    }

    @Test
    fun ipv4RangesAreRefusedAtTheirBoundaries() {
        for (address in listOf(
            "0.0.0.0",
            "0.1.2.3",
            "10.0.0.1",
            "10.255.255.255",
            "100.64.0.1",
            "100.127.255.255",
            "127.0.0.1",
            "127.255.255.254",
            "169.254.0.1",
            "169.254.169.254",
            "172.16.0.1",
            "172.31.255.255",
            "192.0.0.1",
            "192.0.0.255",
            "192.168.0.1",
            "192.168.255.255",
            "198.18.0.1",
            "198.19.255.255",
            "224.0.0.1",
            "239.255.255.255",
            "240.0.0.1",
            "255.255.255.255",
        )) {
            assertRefused("https://$address/x")
        }
    }

    @Test
    fun publicIpv4AddressesNextToBlockedRangesAreAllowed() {
        for (address in listOf(
            "1.1.1.1",
            "8.8.8.8",
            "9.255.255.255",
            "11.0.0.1",
            "93.184.216.34",
            "100.63.255.255",
            "100.128.0.1",
            "126.255.255.255",
            "128.0.0.1",
            "169.253.255.255",
            "169.255.0.1",
            "172.15.255.255",
            "172.32.0.1",
            "192.0.1.1",
            "192.167.255.255",
            "192.169.0.1",
            "198.17.255.255",
            "198.20.0.1",
            "223.255.255.255",
        )) {
            assertAllowed("https://$address/x")
        }
    }

    @Test
    fun ipv6RangesAndEmbeddedIpv4AreRefused() {
        for (address in listOf(
            "[::]",
            "[::1]",
            "[0:0:0:0:0:0:0:1]",
            "[fc00::1]",
            "[fdff:ffff::1]",
            "[fe80::1]",
            "[febf::1]",
            "[fec0::1]",
            "[ff00::1]",
            "[ff02::1]",
            "[::ffff:127.0.0.1]",
            "[::ffff:7f00:1]",
            "[::ffff:10.0.0.5]",
            "[::ffff:a00:5]",
            "[::ffff:169.254.169.254]",
            "[0:0:0:0:0:ffff:7f00:1]",
            "[::127.0.0.1]",
            "[::a00:5]",
            "[64:ff9b::7f00:1]",
            "[64:ff9b::10.0.0.1]",
            "[64:ff9b::a9fe:a9fe]",
            "[2002:7f00:1::1]",
            "[2002:a00:5::1]",
            "[64:ff9b:1::1]",
            "[64:ff9b:1:ffff:ffff:ffff:ffff:ffff]",
            "[::ffff:0:0]",
            "[::ffff:0:7f00:1]",
            "[::ffff:0:a00:5]",
        )) {
            assertRefused("https://$address/x")
        }
    }

    @Test
    fun publicIpv6AndEmbeddedPublicIpv4AreAllowed() {
        for (address in listOf(
            "[2001:4860:4860::8888]",
            "[2606:4700::1111]",
            "[::ffff:8.8.8.8]",
            "[::ffff:808:808]",
            "[64:ff9b::808:808]",
            "[2002:808:808::1]",
            "[::ffff:0:808:808]",
            "[64:ff9b:2::1]",
        )) {
            assertAllowed("https://$address/x")
        }
    }

    @Test
    fun nonCanonicalIpv4LiteralsAreRefused() {
        for (host in listOf("2130706433", "0x7f000001", "0177.0.0.1", "0x7f.0.0.1", "167772161")) {
            assertRefused("https://$host/x")
        }
    }

    @Test
    fun localHostNamesAreRefused() {
        for (host in listOf("localhost", "localhost.", "LOCALHOST", "app.localhost", "service.internal", "printer.local", "metadata.google.internal")) {
            assertRefused("https://$host/x")
        }
        assertAllowed("https://issuer.example.com/.well-known/openid-credential-issuer")
        assertAllowed("https://tenant-a.wihv.nk.sphereon.com:8443/oid4vci/tenant-a")
    }

    @Test
    fun onlyHttpsWithoutUserinfoIsAllowed() {
        assertRefused("http://issuer.example.com/x")
        assertRefused("ftp://issuer.example.com/x")
        assertRefused("https://user:secret@issuer.example.com/x")
        assertRefused("https://user@issuer.example.com/x")
        assertAllowed("https://issuer.example.com/x")
    }

    @Test
    fun counterpartyEgressReplacesAnyWeakerPolicyAndNeverAcceptsAnOptOut() {
        val weakened = HttpClientOptions.createDefault().copy(urlValidation = UrlValidationPolicy.ALLOW_PRIVATE)
        assertEquals(UrlValidationPolicy.COUNTERPARTY_EGRESS, weakened.counterpartyEgress().urlValidation)
        assertEquals(UrlValidationPolicy.COUNTERPARTY_EGRESS, HttpClientOptions.createDefault().copy(urlValidation = UrlValidationPolicy.NONE).counterpartyEgress().urlValidation)
        assertEquals(UrlValidationPolicy.COUNTERPARTY_EGRESS, HttpClientOptions.createDefault().counterpartyEgress().urlValidation)
    }

    @Test
    fun theCallContextDecidesWhetherASharedComponentIsHeldToTheRule() = runTest {
        val shared = HttpClientOptions.createDefault().copy(urlValidation = UrlValidationPolicy.ALLOW_PRIVATE)
        assertFalse(isCounterpartyEgress())
        assertEquals(UrlValidationPolicy.ALLOW_PRIVATE, shared.counterpartyEgressWhenHolder().urlValidation)
        withCounterpartyEgress {
            assertTrue(isCounterpartyEgress())
            assertEquals(UrlValidationPolicy.COUNTERPARTY_EGRESS, shared.counterpartyEgressWhenHolder().urlValidation)
            // Work started from inside a holder call inherits the mark.
            assertTrue(coroutineScope { async { isCounterpartyEgress() }.await() })
        }
        assertFalse(isCounterpartyEgress())
        assertEquals(UrlValidationPolicy.ALLOW_PRIVATE, shared.counterpartyEgressWhenHolder().urlValidation)
    }

    @Test
    fun aRefusedDestinationIsRecognisedThroughItsCauses() {
        assertTrue(UrlValidationException("refused").isEgressRefusal())
        assertTrue(IllegalStateException("wrapped", UrlValidationException("refused")).isEgressRefusal())
        val refusal = object : RuntimeException("refused"), EgressRefusal {}
        assertTrue(IllegalStateException("wrapped", refusal).isEgressRefusal())
        assertFalse(IllegalStateException("connection reset").isEgressRefusal())
    }

    @Test
    fun resolvedAddressesAreCheckedAgainstTheAddressFlagsWithoutNamingTheAddress() {
        policy.validateResolvedAddress("93.184.216.34")
        policy.validateResolvedAddress("2606:4700::1111")
        for (address in listOf("127.0.0.1", "10.1.2.3", "169.254.169.254", "::1", "fe80::1", "::ffff:10.0.0.1", "64:ff9b:1::1", "::ffff:0:7f00:1")) {
            val failure = assertFailsWith<UrlValidationException>(address) { policy.validateResolvedAddress(address) }
            assertFalse(failure.message.orEmpty().contains(address), "the refusal must not name the address")
        }
        UrlValidationPolicy.ALLOW_PRIVATE.validateResolvedAddress("10.1.2.3")
    }

    @Test
    fun enginesThatCannotCheckResolvedAddressesRefuseTheRule() {
        assertFailsWith<UrlValidationException> {
            HttpClientOptions.createDefault().counterpartyEgress().requireResolvedAddressEnforcementSupport("browser")
        }
        HttpClientOptions.createDefault().requireResolvedAddressEnforcementSupport("browser")
        HttpClientOptions.createDefault().copy(urlValidation = UrlValidationPolicy.BLOCK_PRIVATE).requireResolvedAddressEnforcementSupport("browser")
    }
}

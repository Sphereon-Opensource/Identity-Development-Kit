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

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResolvedAddressGuardTest {
    private val policy = UrlValidationPolicy.COUNTERPARTY_EGRESS

    @Test
    fun getaddrinfoReturnsNumericAddressesTheCounterpartyRuleRefusesForLocalhost() {
        val addresses = resolveHostAddresses("localhost")
        assertTrue(addresses.isNotEmpty(), "localhost must resolve")
        for (address in addresses) {
            assertFailsWith<UrlValidationException>(address) { policy.validateResolvedAddress(address) }
        }
    }

    @Test
    fun numericHostsResolveToThemselves() {
        assertEquals(listOf("127.0.0.1"), resolveHostAddresses("127.0.0.1").distinct())
        assertTrue(resolveHostAddresses("::1").any { it.startsWith("::1") })
    }

    @Test
    fun anUnresolvableHostYieldsNoAddresses() {
        assertTrue(resolveHostAddresses("does-not-exist.invalid").isEmpty())
    }

    @Test
    fun theGuardRefusesAHostThatResolvesToABlockedAddressBeforeAnyRequestIsSent() = runTest {
        val seen = mutableListOf<String>()
        val client = HttpClient(Darwin)
        client.installResolvedAddressGuard(policy) { host ->
            seen += host
            listOf("127.0.0.1")
        }
        try {
            assertFailsWith<UrlValidationException> { client.get("https://svc.example.test/x") }
        } finally {
            client.close()
        }
        assertEquals(listOf("svc.example.test"), seen)
    }

    @Test
    fun theGuardRefusesAHostWithOneBlockedAnswerAmongPublicOnes() = runTest {
        val client = HttpClient(Darwin)
        client.installResolvedAddressGuard(policy) { listOf("93.184.216.34", "10.0.0.5") }
        try {
            assertFailsWith<UrlValidationException> { client.get("https://mixed.example.test/x") }
        } finally {
            client.close()
        }
    }

    @Test
    fun theGuardRefusesAHostThatDoesNotResolve() = runTest {
        val client = HttpClient(Darwin)
        client.installResolvedAddressGuard(policy) { emptyList() }
        try {
            assertFailsWith<UrlValidationException> { client.get("https://gone.example.test/x") }
        } finally {
            client.close()
        }
    }

    @Test
    fun policiesThatBlockNoRangeInstallNoResolvedAddressCheck() {
        assertTrue(policy.blocksAddressRanges())
        assertTrue(!UrlValidationPolicy.ALLOW_PRIVATE.blocksAddressRanges())
    }
}

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
import kotlin.test.Test
import kotlin.test.assertFailsWith

class UrlValidationPolicyTest {
    @Test
    fun blockPrivateRefusesLiteralInternalHostsIncludingMappedIpv6AndTrailingDot() {
        for (url in listOf(
            "http://127.0.0.1/x",
            "http://169.254.169.254/latest/meta-data",
            "http://10.0.0.5/x",
            "http://[::1]/x",
            "http://[::ffff:10.0.0.5]/x",
            "http://[::ffff:7f00:1]/x",
            "http://localhost./x",
            "http://metadata.google.internal./x",
        )) {
            assertFailsWith<UrlValidationException>(url) { UrlValidationPolicy.BLOCK_PRIVATE.validate(Url(url)) }
        }
    }

    @Test
    fun allowPrivateAcceptsInternalAddressesButStillRefusesMetadataAndUserinfo() {
        for (url in listOf("http://127.0.0.1:8080/x", "http://10.0.0.5/x", "https://172.20.0.3/x", "http://[::1]/x", "http://vdx-edge/x")) {
            UrlValidationPolicy.ALLOW_PRIVATE.validate(Url(url))
        }
        for (url in listOf("http://169.254.169.254/x", "http://metadata.google.internal/x", "http://user:pw@10.0.0.5/x")) {
            assertFailsWith<UrlValidationException>(url) { UrlValidationPolicy.ALLOW_PRIVATE.validate(Url(url)) }
        }
    }
}

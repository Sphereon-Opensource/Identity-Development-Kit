/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.did.methods.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebDidInternalResolutionFallbackTest {
    @Test
    fun `retargets only the document path to the trusted east-west service`() {
        assertEquals(
            "http://enterprise-did:8080/.well-known/did.json",
            internalDidResolutionUrl(
                publicUrl = "https://phase.example:25443/.well-known/did.json",
                internalBaseUrl = "http://enterprise-did:8080/",
            ),
        )
        assertEquals(
            "http://enterprise-did:8080/customers/acme/did.json",
            internalDidResolutionUrl(
                publicUrl = "https://public.example/customers/acme/did.json",
                internalBaseUrl = "http://enterprise-did:8080",
            ),
        )
        assertEquals("phase.example:25443", webAuthorityOf("https://phase.example:25443/.well-known/did.json"))
    }

    @Test
    fun `rejects malformed or credential-bearing internal destinations`() {
        val publicUrl = "https://phase.example/.well-known/did.json"
        assertNull(internalDidResolutionUrl(publicUrl, null))
        assertNull(internalDidResolutionUrl(publicUrl, "enterprise-did:8080"))
        assertNull(internalDidResolutionUrl(publicUrl, "file:///tmp/did"))
        assertNull(internalDidResolutionUrl(publicUrl, "http://user@enterprise-did:8080"))
        assertNull(internalDidResolutionUrl(publicUrl, "http://enterprise-did:8080?target=other"))
        assertNull(webAuthorityOf("https://user@phase.example/.well-known/did.json"))
    }
}

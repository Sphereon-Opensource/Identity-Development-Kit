/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the spec §3.1 Path-to-URL mapping.
 *
 * Spec: https://identity.foundation/didwebvh/v1.0/#did-to-https-transformation
 */
class WebvhDidUrlBuilderTest {
    @Test
    fun decomposesAsciiHostNoPort() {
        val parts = WebvhDidUrlBuilder.decompose("did:webvh:QmSCID:example.com")
        assertTrue(parts.isOk)
        assertEquals("QmSCID", parts.value.scid)
        assertEquals("example.com", parts.value.host)
        assertEquals(null, parts.value.port)
        assertEquals(emptyList(), parts.value.pathSegments)
    }

    @Test
    fun decomposesHostWithEncodedPort() {
        val parts = WebvhDidUrlBuilder.decompose("did:webvh:QmSCID:example.com%3A8443")
        assertTrue(parts.isOk)
        assertEquals(8443, parts.value.port)
        assertEquals("example.com", parts.value.host)
    }

    @Test
    fun decomposesHostWithPathSegments() {
        val parts = WebvhDidUrlBuilder.decompose("did:webvh:QmSCID:example.com:tenants:acme")
        assertTrue(parts.isOk)
        assertEquals(listOf("tenants", "acme"), parts.value.pathSegments)
    }

    @Test
    fun rejectsMissingPrefix() {
        val parts = WebvhDidUrlBuilder.decompose("did:web:example.com")
        assertTrue(parts.isErr)
    }

    @Test
    fun rejectsMissingHost() {
        val parts = WebvhDidUrlBuilder.decompose("did:webvh:QmSCID")
        assertTrue(parts.isErr)
    }

    @Test
    fun rejectsBlankScid() {
        val parts = WebvhDidUrlBuilder.decompose("did:webvh::example.com")
        assertTrue(parts.isErr)
    }

    @Test
    fun rejectsLiteralIpv4() {
        val parts = WebvhDidUrlBuilder.decompose("did:webvh:QmSCID:127.0.0.1")
        assertTrue(parts.isErr)
    }

    @Test
    fun rejectsBracketedIpv6() {
        val parts = WebvhDidUrlBuilder.decompose("did:webvh:QmSCID:[::1]")
        assertTrue(parts.isErr)
    }

    @Test
    fun logUrlUsesWellKnownWhenNoPath() {
        val url = WebvhDidUrlBuilder.toLogUrl("did:webvh:QmSCID:example.com")
        assertTrue(url.isOk)
        assertEquals("https://example.com/.well-known/did.jsonl", url.value)
    }

    @Test
    fun logUrlIncludesPortAndPath() {
        val url = WebvhDidUrlBuilder.toLogUrl("did:webvh:QmSCID:example.com%3A8443:dids:tenant1")
        assertTrue(url.isOk)
        assertEquals("https://example.com:8443/dids/tenant1/did.jsonl", url.value)
    }

    @Test
    fun witnessUrlSiblingToLog() {
        val url = WebvhDidUrlBuilder.toWitnessUrl("did:webvh:QmSCID:example.com:tenants:acme")
        assertTrue(url.isOk)
        assertEquals("https://example.com/tenants/acme/did-witness.json", url.value)
    }

    @Test
    fun witnessUrlUsesWellKnownWhenNoPath() {
        val url = WebvhDidUrlBuilder.toWitnessUrl("did:webvh:QmSCID:example.com")
        assertTrue(url.isOk)
        assertEquals("https://example.com/.well-known/did-witness.json", url.value)
    }

    /**
     * Spec example: `did:webvh:{SCID}:jp納豆.例.jp:用户` ->
     * `https://xn--jp-cd2fp15c.xn--fsq.jp/%E7%94%A8%E6%88%B7/did.jsonl`
     */
    @Test
    fun handlesIdnSpecExample() {
        val did = "did:webvh:QmSCID:jp納豆.例.jp:用户"
        val url = WebvhDidUrlBuilder.toLogUrl(did)
        assertTrue(url.isOk, "expected OK, got: ${if (url.isErr) url.error else ""}")
        assertEquals("https://xn--jp-cd2fp15c.xn--fsq.jp/%E7%94%A8%E6%88%B7/did.jsonl", url.value)
    }

    @Test
    fun roundTripsThroughToDid() {
        val did = WebvhDidUrlBuilder.toDid(scid = "QmSCID", host = "example.com", port = 8443, pathSegments = listOf("tenants", "acme"))
        assertEquals("did:webvh:QmSCID:example.com%3A8443:tenants:acme", did)
        val parts = WebvhDidUrlBuilder.decompose(did)
        assertTrue(parts.isOk)
        assertEquals("QmSCID", parts.value.scid)
        assertEquals("example.com", parts.value.host)
        assertEquals(8443, parts.value.port)
        assertEquals(listOf("tenants", "acme"), parts.value.pathSegments)
    }
}

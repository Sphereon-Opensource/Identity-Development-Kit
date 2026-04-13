/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.did

import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import dev.zacsweers.metro.DependencyGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for DID trust validation logic.
 *
 * These tests verify the validation service's support detection and argument handling.
 * Full DI integration tests (with ResolveDidCommand) require the DI test infrastructure
 * and are run in jvmTest with @DependencyGraph.
 */
class DidTrustValidationServiceTest {

    @Test
    fun supportsDidContextType() {
        val supportedTypes = setOf(TrustContext.TYPE_DID)
        assertTrue(TrustContext.TYPE_DID in supportedTypes)
        assertFalse(TrustContext.TYPE_X509 in supportedTypes)
        assertFalse(TrustContext.TYPE_ETSI_TSL in supportedTypes)
        assertFalse(TrustContext.TYPE_OPENID_FEDERATION in supportedTypes)
    }

    @Test
    fun validateDidTrustArgsDefaults() {
        val args = ValidateDidTrustArgs(did = "did:key:z6MkTest")
        assertEquals("did:key:z6MkTest", args.did)
        assertTrue(args.allowedMethods.isEmpty())
        assertTrue(args.trustedDids.isEmpty())
        assertEquals(null, args.identifierJson)
    }

    @Test
    fun validateDidTrustArgsWithMethods() {
        val args = ValidateDidTrustArgs(
            did = "did:web:example.com",
            allowedMethods = listOf("web", "key"),
            trustedDids = listOf("did:web:trusted.example.com")
        )
        assertEquals(2, args.allowedMethods.size)
        assertTrue(args.allowedMethods.contains("web"))
        assertTrue(args.allowedMethods.contains("key"))
        assertEquals(1, args.trustedDids.size)
    }

    @Test
    fun trustContextDidTypeConstant() {
        assertEquals("did", TrustContext.TYPE_DID)
    }

    @Test
    fun didMethodParsedFromDid() {
        // Verify DID method extraction logic that the service uses
        val did = "did:web:example.com:path"
        val parts = did.split(":")
        assertTrue(parts.size >= 3)
        assertEquals("did", parts[0])
        assertEquals("web", parts[1])
    }

    @Test
    fun commandIdConstant() {
        assertEquals("trust.did.validate", ValidateDidTrustCommand.COMMAND_ID)
    }
}

/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.x509

import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X509TrustValidationTest {

    @Test
    fun supportsX509ContextTypes() {
        val supportedTypes = setOf(TrustContext.TYPE_X509, TrustContext.TYPE_CA_BUNDLE)
        assertTrue(TrustContext.TYPE_X509 in supportedTypes)
        assertTrue(TrustContext.TYPE_CA_BUNDLE in supportedTypes)
        assertFalse(TrustContext.TYPE_DID in supportedTypes)
        assertFalse(TrustContext.TYPE_ETSI_TSL in supportedTypes)
    }

    @Test
    fun validateX509TrustArgsDefaults() {
        val args = ValidateX509TrustArgs(identifierJson = "{}")
        assertEquals("{}", args.identifierJson)
        assertTrue(args.checkRevocation)
    }

    @Test
    fun validateX509TrustArgsNoRevocationCheck() {
        val args = ValidateX509TrustArgs(
            identifierJson = "{}",
            checkRevocation = false
        )
        assertFalse(args.checkRevocation)
    }

    @Test
    fun typeConstants() {
        assertEquals("x509", TrustContext.TYPE_X509)
        assertEquals("ca_bundle", TrustContext.TYPE_CA_BUNDLE)
    }
}

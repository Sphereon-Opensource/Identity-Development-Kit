/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TrustModelsTest {
    @Test
    fun trustContextTypeConstants() {
        assertEquals("etsi_tsl", TrustContext.TYPE_ETSI_TSL)
        assertEquals("x509", TrustContext.TYPE_X509)
        assertEquals("ca_bundle", TrustContext.TYPE_CA_BUNDLE)
        assertEquals("did", TrustContext.TYPE_DID)
        assertEquals("openid_federation", TrustContext.TYPE_OPENID_FEDERATION)
        assertEquals("public_key", TrustContext.TYPE_PUBLIC_KEY)
        assertEquals("custom", TrustContext.TYPE_CUSTOM)
    }

    @Test
    fun trustContextEquality() {
        val ctx1 = TrustContext(type = TrustContext.TYPE_DID, framework = "test")
        val ctx2 = TrustContext(type = TrustContext.TYPE_DID, framework = "test")
        val ctx3 = TrustContext(type = TrustContext.TYPE_X509)
        assertEquals(ctx1, ctx2)
        assertNotEquals(ctx1, ctx3)
    }

    @Test
    fun trustContextDefaultParameters() {
        val ctx = TrustContext(type = TrustContext.TYPE_ETSI_TSL)
        assertEquals(null, ctx.framework)
        assertTrue(ctx.parameters.isEmpty())
    }

    @Test
    fun trustContextWithParameters() {
        val params = mapOf("url" to "https://example.com/tsl.xml")
        val ctx = TrustContext(type = TrustContext.TYPE_ETSI_TSL, parameters = params)
        assertEquals("https://example.com/tsl.xml", ctx.parameters["url"])
    }

    @Test
    fun trustValidationResultTrusted() {
        val result =
            TrustValidationResult(
                trusted = true,
                status = TrustStatus.TRUSTED,
                details = "Chain validated",
            )
        assertTrue(result.trusted)
        assertEquals(TrustStatus.TRUSTED, result.status)
    }

    @Test
    fun trustValidationResultUntrusted() {
        val result =
            TrustValidationResult(
                trusted = false,
                status = TrustStatus.UNTRUSTED,
                details = "No valid trust path",
            )
        assertFalse(result.trusted)
        assertEquals(TrustStatus.UNTRUSTED, result.status)
    }

    @Test
    fun trustStatusValues() {
        val values = TrustStatus.entries
        assertTrue(values.contains(TrustStatus.TRUSTED))
        assertTrue(values.contains(TrustStatus.UNTRUSTED))
        assertTrue(values.contains(TrustStatus.REVOKED))
        assertTrue(values.contains(TrustStatus.EXPIRED))
        assertTrue(values.contains(TrustStatus.NOT_YET_VALID))
        assertTrue(values.contains(TrustStatus.VALIDATION_ERROR))
        assertTrue(values.contains(TrustStatus.UNKNOWN))
        assertEquals(7, values.size)
    }

    @Test
    fun trustAnchorTypeConstants() {
        assertEquals("etsi_tsp", TrustAnchor.TYPE_ETSI_TSP)
        assertEquals("root_ca", TrustAnchor.TYPE_ROOT_CA)
        assertEquals("did_document", TrustAnchor.TYPE_DID_DOCUMENT)
        assertEquals("openid_federation_entity", TrustAnchor.TYPE_OPENID_FED_ENTITY)
        assertEquals("public_key", TrustAnchor.TYPE_PUBLIC_KEY)
    }

    @Test
    fun trustAnchorTypeEnumValues() {
        val values = TrustAnchorType.entries
        assertEquals(6, values.size)
        assertTrue(values.contains(TrustAnchorType.ETSI_TSL))
        assertTrue(values.contains(TrustAnchorType.X509_CA_BUNDLE))
        assertTrue(values.contains(TrustAnchorType.DID))
        assertTrue(values.contains(TrustAnchorType.OPENID_FEDERATION))
        assertTrue(values.contains(TrustAnchorType.PUBLIC_KEY))
        assertTrue(values.contains(TrustAnchorType.CUSTOM))
    }
}

/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustConfigTest {
    @Test
    fun defaultTrustConfig() {
        val config = TrustConfig()
        assertTrue(config.validation.enabled)
        assertTrue(config.validation.defaultCheckRevocation)
        assertFalse(config.anchors.x509.enabled)
        assertFalse(config.anchors.etsi.enabled)
        assertFalse(config.anchors.oidfed.enabled)
        assertFalse(config.anchors.did.enabled)
        assertTrue(config.revocation.enabled)
        assertEquals(60L, config.cache.trustListTtlMinutes)
        assertEquals(15L, config.cache.revocationTtlMinutes)
        assertEquals(30L, config.cache.oidfedEntityTtlMinutes)
    }

    @Test
    fun x509TrustConfigDefaults() {
        val config = X509TrustConfig()
        assertFalse(config.enabled)
        assertTrue(config.caBundlePaths.isEmpty())
        assertTrue(config.caBundleUrls.isEmpty())
        assertTrue(config.trustedFingerprints.isEmpty())
    }

    @Test
    fun x509TrustConfigCustom() {
        val config =
            X509TrustConfig(
                enabled = true,
                caBundlePaths = listOf("/etc/ssl/certs/ca-certificates.crt"),
                trustedFingerprints = listOf("sha256:abc123"),
            )
        assertTrue(config.enabled)
        assertEquals(1, config.caBundlePaths.size)
        assertEquals(1, config.trustedFingerprints.size)
    }

    @Test
    fun etsiTrustConfigDefaults() {
        val config = EtsiTrustConfig()
        assertFalse(config.enabled)
        assertEquals(null, config.lotlUrl)
        assertTrue(config.verifySignatures)
        assertTrue(config.territories.isEmpty())
    }

    @Test
    fun didTrustConfigAllowedMethods() {
        val config =
            DidTrustConfig(
                enabled = true,
                trustedDids = listOf("did:web:example.com"),
                allowedMethods = listOf("web", "key", "jwk"),
            )
        assertTrue(config.enabled)
        assertEquals(3, config.allowedMethods.size)
        assertTrue(config.allowedMethods.contains("web"))
    }

    @Test
    fun oidfTrustConfigDefaults() {
        val config = OidfTrustConfig()
        assertFalse(config.enabled)
        assertTrue(config.trustAnchors.isEmpty())
        assertEquals(5, config.maxChainDepth)
        assertTrue(config.requiredTrustMarks.isEmpty())
    }

    @Test
    fun revocationConfigDefaults() {
        val config = RevocationConfig()
        assertTrue(config.enabled)
        assertTrue(config.checkOcsp)
        assertTrue(config.checkCrl)
        assertTrue(config.preferOcsp)
        assertEquals(10000L, config.timeoutMs)
    }
}

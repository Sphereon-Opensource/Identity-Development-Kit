/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class TrustListUrlPolicyTest {
    @Test
    fun secureDefaultsRejectNonHttpsAndInternalTargets() {
        assertFailsWith<TrustListResolutionException> {
            TrustListUrlPolicy.validate("http://example.com/trust-list.xml")
        }
        assertFailsWith<TrustListResolutionException> {
            TrustListUrlPolicy.validate("https://127.0.0.1/trust-list.xml")
        }
        assertFailsWith<TrustListResolutionException> {
            TrustListUrlPolicy.validate("https://10.0.0.7/trust-list.xml")
        }
        assertFailsWith<TrustListResolutionException> {
            TrustListUrlPolicy.validate("https://metadata.google.internal/computeMetadata/v1/")
        }
        assertFailsWith<TrustListResolutionException> {
            TrustListUrlPolicy.validate("https://user:password@example.com/trust-list.xml")
        }
    }

    @Test
    fun normalizedKeyOnlyNormalizesSchemeHostAndDefaultPort() {
        assertEquals(
            "https://example.com/trust-list.xml?format=xml",
            TrustListUrlPolicy.normalizedCacheKey("HTTPS://EXAMPLE.COM:443/trust-list.xml?format=xml"),
        )
    }

    @Test
    fun normalizedKeyDropsFragmentsFromTheRequestAndCacheIdentity() {
        assertEquals(
            "https://example.com/trust-list.xml?format=xml",
            TrustListUrlPolicy.normalizedCacheKey("HTTPS://EXAMPLE.COM:443/trust-list.xml?format=xml#secret-fragment"),
        )
    }

    @Test
    fun malformedUrlExposesOnlyTheStableMalformedUrlReason() {
        val failure =
            assertFailsWith<TrustListResolutionException> {
                TrustListUrlPolicy.validate("https://[not-an-ip")
            }

        assertEquals(com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_URL_MALFORMED, failure.reasonCode)
        assertNotNull(failure.message)
        assertFalse(failure.message!!.contains("not-an-ip"))
    }

    @Test
    fun relativeRedirectsResolveAgainstTheCurrentCanonicalUri() {
        assertEquals(
            "https://example.com/next.xml?format=xml",
            TrustListUrlPolicy.resolveRedirect(
                currentUri = "HTTPS://EXAMPLE.COM:443/path/current.xml#ignored",
                location = "../next.xml?format=xml#also-ignored",
                requireHttps = true,
            ),
        )
    }

    @Test
    fun missingRedirectLocationHasAStableReasonWithoutEchoingTheResponse() {
        val failure =
            assertFailsWith<TrustListResolutionException> {
                TrustListUrlPolicy.resolveRedirect(
                    currentUri = "https://example.com/current.xml",
                    location = null,
                    requireHttps = true,
                )
            }

        assertEquals(com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_MISSING, failure.reasonCode)
        assertFalse(failure.message!!.contains("example.com"))
    }

    @Test
    fun httpsRedirectDowngradeHasAStableReason() {
        val failure =
            assertFailsWith<TrustListResolutionException> {
                TrustListUrlPolicy.resolveRedirect(
                    currentUri = "https://example.com/current.xml",
                    location = "http://example.com/next.xml",
                    requireHttps = true,
                )
            }

        assertEquals(com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_DOWNGRADE, failure.reasonCode)
    }

    @Test
    fun malformedRedirectLocationHasAStableReason() {
        val failure =
            assertFailsWith<TrustListResolutionException> {
                TrustListUrlPolicy.resolveRedirect(
                    currentUri = "https://example.com/current.xml",
                    location = "https://[malformed",
                    requireHttps = true,
                )
            }

        assertEquals(com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_REDIRECT_MALFORMED, failure.reasonCode)
    }

    @Test
    fun payloadLimitRejectsOversizedTrustLists() {
        val failure = assertFailsWith<TrustListResolutionException> {
            TrustListPayloadPolicy.enforce(ByteArray(4), maxBodyBytes = 3)
        }
        assertEquals(com.sphereon.trust.core.TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE, failure.reasonCode)
    }
}

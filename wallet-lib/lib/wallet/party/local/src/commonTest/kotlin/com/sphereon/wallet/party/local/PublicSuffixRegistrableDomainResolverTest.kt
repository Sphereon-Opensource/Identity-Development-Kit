/* Copyright 2026 Sphereon International B.V. */

package com.sphereon.wallet.party.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicSuffixRegistrableDomainResolverTest {
    @Test
    fun `resolves sibling subdomains to the same registrable domain`() {
        assertEquals("example.com", PublicSuffixRegistrableDomainResolver.registrableDomain("issuer.example.com"))
        assertEquals("example.com", PublicSuffixRegistrableDomainResolver.registrableDomain("verifier.example.com"))
    }

    @Test
    fun `respects multi-label public suffixes`() {
        assertEquals("alpha.co.uk", PublicSuffixRegistrableDomainResolver.registrableDomain("issuer.alpha.co.uk"))
        assertEquals("beta.co.uk", PublicSuffixRegistrableDomainResolver.registrableDomain("verifier.beta.co.uk"))
    }

    @Test
    fun `respects private suffixes`() {
        assertEquals("alpha.github.io", PublicSuffixRegistrableDomainResolver.registrableDomain("issuer.alpha.github.io"))
        assertEquals("beta.github.io", PublicSuffixRegistrableDomainResolver.registrableDomain("verifier.beta.github.io"))
    }

    @Test
    fun `implements wildcard and exception rules`() {
        assertNull(PublicSuffixRegistrableDomainResolver.registrableDomain("a.ck"))
        assertEquals("www.ck", PublicSuffixRegistrableDomainResolver.registrableDomain("www.ck"))
        assertEquals("www.ck", PublicSuffixRegistrableDomainResolver.registrableDomain("service.www.ck"))
    }
}

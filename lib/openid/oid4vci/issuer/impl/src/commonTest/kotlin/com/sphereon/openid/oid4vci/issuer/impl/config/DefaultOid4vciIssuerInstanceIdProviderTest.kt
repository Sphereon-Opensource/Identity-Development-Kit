package com.sphereon.openid.oid4vci.issuer.impl.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultOid4vciIssuerInstanceIdProviderTest {
    @Test
    fun setterAcceptsAndPropagatesOnlyExactCanonicalUuid() {
        val provider = DefaultOid4vciIssuerInstanceIdProvider()
        val canonical = "00000000-0000-4000-8000-000000000041"
        provider.setCurrentInstanceId(canonical)
        assertEquals(canonical, provider.currentInstanceId())

        assertFailsWith<IllegalArgumentException> { provider.setCurrentInstanceId("default") }
        assertFailsWith<IllegalArgumentException> { provider.setCurrentInstanceId(" $canonical") }
        assertEquals(canonical, provider.currentInstanceId(), "a rejected selector must not mutate active routing")
    }
}

/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.oauth2.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CanonicalAuthorizationServerIssuerTest {
    @Test
    fun canonicalizesIssuerInTheNeutralCommonContract() {
        assertEquals(
            "https://xn--bcher-kva.example/auth",
            CanonicalAuthorizationServerIssuer.canonicalize("HTTPS://BÜCHER.Example:443/auth/"),
        )
    }

    @Test
    fun rejectsNonCanonicalIssuerSpelling() {
        assertFailsWith<IllegalArgumentException> {
            CanonicalAuthorizationServerIssuer.validate("HTTPS://example.test:443/")
        }
    }
}

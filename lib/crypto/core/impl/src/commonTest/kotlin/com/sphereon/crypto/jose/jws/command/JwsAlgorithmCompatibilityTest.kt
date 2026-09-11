/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.crypto.jose.jws.command

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.Jwk
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class JwsAlgorithmCompatibilityTest {
    private val ecPublic = Jwk(
        kty = JwaKeyType.EC,
        crv = JwaCurve.P_256,
        x = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        y = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE",
        alg = JwaAlgorithm.ES256,
        use = "sig",
        key_ops = arrayOf(JoseKeyOperations.VERIFY),
    )

    @Test
    fun managedKidLookupDoesNotCarryProtectedHeaderAlgorithmHint() {
        val lookup = managedKidLookup("managed-kid")

        assertEquals("managed-kid", lookup.kid)
        assertNull(lookup.signatureAlgorithm)
    }

    @Test
    fun `resolved configured JWK requires exact algorithm and curve`() {
        val resolved = ResolvedKeyInfo.fromKey(ecPublic)
        assertNull(resolved.jwsAlgorithmCompatibilityFailure("ES256"))
        assertContains(
            assertNotNull(resolved.jwsAlgorithmCompatibilityFailure("ES384")),
            "does not match resolved JWK alg",
        )
    }

    @Test
    fun `resolved managed metadata algorithm is enforced without JVM key material`() {
        val managed = KeyInfo<KeyType>(
            kid = "managed",
            providerId = "provider",
            keyType = com.sphereon.crypto.core.generic.KeyTypeMapping.EC,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            keyVisibility = KeyVisibility.PUBLIC,
        )
        assertNull(managed.jwsAlgorithmCompatibilityFailure("ES256"))
        assertContains(
            assertNotNull(managed.jwsAlgorithmCompatibilityFailure("ES384")),
            "does not match resolved signature algorithm",
        )
    }

    @Test
    fun `use and key operations cannot authorize a signing-only or encryption key`() {
        val encryption = ecPublic.copy(use = "enc")
        assertContains(
            assertNotNull(ResolvedKeyInfo.fromKey(encryption).jwsAlgorithmCompatibilityFailure("ES256")),
            "use must be 'sig'",
        )
        val signingOnly = ecPublic.copy(key_ops = arrayOf(JoseKeyOperations.SIGN))
        assertContains(
            assertNotNull(ResolvedKeyInfo.fromKey(signingOnly).jwsAlgorithmCompatibilityFailure("ES256")),
            "key_ops must include 'verify'",
        )
    }
}

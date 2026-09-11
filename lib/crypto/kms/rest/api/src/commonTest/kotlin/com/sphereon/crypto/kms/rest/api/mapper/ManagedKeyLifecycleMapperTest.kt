/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.api.mapper

import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManagedKeyLifecycleMapperTest {
    private val providerKey =
        ManagedKeyInfo.fromKeyInfo(
            KeyInfo(
                key =
                    Jwk(
                        kty = JwaKeyType.EC,
                        kid = "provider-kid",
                        crv = JwaCurve.P_256,
                        x = "public-x",
                        y = "public-y",
                        d = "private-d",
                        x5c = arrayOf("leaf", "issuer"),
                        x5t = "sha1-thumbprint",
                        x5u = "https://provider.invalid/certificate",
                        x5t_S256 = "sha256-thumbprint",
                    ),
                alias = "provider-alias",
                providerId = "provider-1",
                x5c = arrayOf("leaf", "issuer"),
            ),
        )

    @Test
    fun persistedReferenceEnrichesProviderKeyLifecycleMetadata() {
        val reference =
            ManagedKeyReference(
                alias = "provider-alias",
                kid = "provider-kid",
                providerId = "provider-1",
                origin = Origin.EXTERNAL,
                controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
            )

        val rest = providerKey.toRest(reference)

        assertEquals("external", rest.origin?.value)
        assertEquals("externally_managed", rest.controlMode?.value)
        assertEquals(listOf("leaf", "issuer"), rest.key.x5c?.toList())
        assertEquals("sha1-thumbprint", rest.key.x5t)
        assertEquals("sha256-thumbprint", rest.key.x5tHashS256)
        assertNull(rest.key.d)
        assertNull(rest.key.p)
        assertNull(rest.key.q)
        assertNull(rest.key.dp)
        assertNull(rest.key.dq)
        assertNull(rest.key.qi)
        assertNull(rest.key.k)
        assertNull(rest.key.x5u)
    }

    @Test
    fun providerInventoryWithoutPersistedReferenceDoesNotFabricateLifecycleMetadata() {
        val rest = providerKey.toRest()

        assertNull(rest.origin)
        assertNull(rest.controlMode)
    }
}

/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.command

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.crypto.resolution.managed.ManagedOptsKid
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwsSigningHeaderTest {
    @Test
    fun keyInfoIdentifierPutsKidAndTypOnHeader() {
        val header =
            asSigningProtectedHeader(
                typ = "at+jwt",
                signingIdentifier =
                    ManagedOptsKeyInfo(
                        identifier =
                            KeyInfo<KeyType>(
                                alias = "sts-id-token-signing",
                                kid = "sts-id-token-signing-1",
                                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                            ),
                    ),
            )
        assertEquals("at+jwt", header["typ"]?.jsonPrimitive?.contentOrNull)
        assertEquals("sts-id-token-signing-1", header["kid"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun kidIdentifierPutsKidOnHeader() {
        val header = asSigningProtectedHeader("JWT", ManagedOptsKid(identifier = "pinned-kid"))
        assertEquals("JWT", header["typ"]?.jsonPrimitive?.contentOrNull)
        assertEquals("pinned-kid", header["kid"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun aliasOnlyIdentifierOmitsKid() {
        val header = asSigningProtectedHeader("JWT", ManagedOptsAlias(identifier = "sts-id-token-signing"))
        assertEquals("JWT", header["typ"]?.jsonPrimitive?.contentOrNull)
        assertNull(header["kid"])
    }
}

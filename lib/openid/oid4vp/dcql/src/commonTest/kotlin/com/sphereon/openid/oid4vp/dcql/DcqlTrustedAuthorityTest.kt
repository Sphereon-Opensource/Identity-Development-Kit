/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */
package com.sphereon.openid.oid4vp.dcql

import io.konform.validation.Valid
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** OpenID4VP 1.0 Final Section 6.1.1 trusted-authority wire contract. */
class DcqlTrustedAuthorityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun finalTrustedAuthorityIdentifiersRoundTrip() {
        val authorities =
            listOf(
                DcqlTrustedAuthority(type = "aki", values = listOf("s9tIpPmhxdiuNkHMEWNpYim8S8Y")),
                DcqlTrustedAuthority(type = "etsi_tl", values = listOf("https://lotl.example.com")),
                DcqlTrustedAuthority(type = "openid_federation", values = listOf("https://trust.example.com")),
            )

        authorities.forEach { authority ->
            assertEquals(authority, json.decodeFromString<DcqlTrustedAuthority>(json.encodeToString(authority)))
        }
        assertEquals(setOf("aki", "etsi_tl", "openid_federation"), DcqlTrustedAuthority.VALID_TYPES)
    }

    @Test
    fun rejectsNonFinalTypeIdentifiersAndEmptyValues() {
        assertFailsWith<IllegalArgumentException> {
            DcqlTrustedAuthority(type = "authority-key-id", values = listOf("value"))
        }
        assertFailsWith<IllegalArgumentException> {
            DcqlTrustedAuthority(type = "aki", values = emptyList())
        }
    }

    @Test
    fun validatesEtsiAndFederationIdentifiersAsHttpsUrls() {
        assertFailsWith<IllegalArgumentException> {
            DcqlTrustedAuthority(type = "etsi_tl", values = listOf("http://lotl.example.com"))
        }
        assertFailsWith<IllegalArgumentException> {
            DcqlTrustedAuthority(type = "openid_federation", values = listOf("urn:example:trust"))
        }
        assertTrue(
            validateDcqlTrustedAuthority(
                DcqlTrustedAuthority(type = "etsi_tl", values = listOf("https://lotl.example.com")),
            ) is Valid,
        )
    }

    @Test
    fun duplicateTypesAreNormalizedWithoutChangingFinalTypeIdentifiers() {
        val credential =
            DcqlCredentialQuery(
                id = "pid",
                format = "dc+sd-jwt",
                meta = sdJwtVcMeta("urn:eudi:pid:1"),
                trusted_authorities =
                    listOf(
                        DcqlTrustedAuthority(type = "aki", values = listOf("one")),
                        DcqlTrustedAuthority(type = "aki", values = listOf("two", "one")),
                    ),
            )

        assertEquals(
            listOf(DcqlTrustedAuthority(type = "aki", values = listOf("one", "two"))),
            credential.normalizedTrustedAuthorities,
        )
    }
}

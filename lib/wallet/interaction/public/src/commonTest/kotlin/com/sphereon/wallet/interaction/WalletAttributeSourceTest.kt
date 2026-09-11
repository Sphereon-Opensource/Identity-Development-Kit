/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletAttributeSourceTest {
    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        }

    @Test
    fun `an attested kind without a named authority cannot be constructed`() {
        attestedKinds().forEach { kind ->
            assertFailsWith<IllegalArgumentException> { WalletAttributeSource(kind = kind) }
            assertFailsWith<IllegalArgumentException> { WalletAttributeSource(kind = kind, authority = null) }
            assertFailsWith<IllegalArgumentException> { WalletAttributeSource(kind = kind, authority = "") }
            assertFailsWith<IllegalArgumentException> { WalletAttributeSource(kind = kind, authority = "   ") }
        }
    }

    @Test
    fun `every attested kind with a named authority satisfies the producer invariant`() {
        val kinds = attestedKinds()
        assertEquals(
            setOf(
                WalletAttributeSourceKind.SUPERIOR_ATTESTED,
                WalletAttributeSourceKind.SUPERIOR_POLICY_PINNED,
                WalletAttributeSourceKind.ACCESS_CERTIFICATE,
                WalletAttributeSourceKind.REGISTRAR_REGISTERED,
                WalletAttributeSourceKind.QUALIFIED_SUPERVISED,
            ),
            kinds.toSet(),
            "a new attested kind must be constructed here, not discovered by an empty emitted-set scan",
        )
        kinds.forEach { kind ->
            val source = WalletAttributeSource(kind = kind, authority = "Named Authority")
            assertEquals(kind, source.kind)
            assertEquals("Named Authority", source.authority)
            assertFalse(source.authority.isNullOrBlank())
        }
    }

    @Test
    fun `SELF_ASSERTED omits authority`() {
        val source = WalletAttributeSource(kind = WalletAttributeSourceKind.SELF_ASSERTED)
        assertNull(source.authority)
        assertNull(source.authorityIdentifier)
        val encoded = json.encodeToString(source)
        assertEquals("""{"kind":"SELF_ASSERTED"}""", encoded)
        assertFalse(encoded.contains("authority"))
    }

    @Test
    fun `SUPERIOR_ATTESTED with a named authority can be constructed`() {
        val source =
            WalletAttributeSource(
                kind = WalletAttributeSourceKind.SUPERIOR_ATTESTED,
                authority = "Example Trust Anchor",
                authorityIdentifier = "https://ta.example",
            )
        assertEquals("Example Trust Anchor", source.authority)
        assertEquals("https://ta.example", source.authorityIdentifier)
        val encoded = json.encodeToString(source)
        assertTrue(encoded.contains("\"authority\":\"Example Trust Anchor\""))
    }

    @Test
    fun `display metadata name is self-asserted and identifier fallback has no source`() {
        val fromMetadata = selfAssertedDisplayNameSource("Example Issuer")
        assertNotNull(fromMetadata)
        assertEquals(WalletAttributeSourceKind.SELF_ASSERTED, fromMetadata.kind)
        assertNull(fromMetadata.authority)
        assertNull(selfAssertedDisplayNameSource(null))
        assertNull(selfAssertedDisplayNameSource(""))
        assertNull(selfAssertedDisplayNameSource("   "))
    }

    @Test
    fun `a DCR contact is self-asserted and client_name is not a legal name`() {
        val detail =
            counterpartyDetailFromDcrMetadata(
                buildJsonObject {
                    put("client_name", JsonPrimitive("Acme Relying Party"))
                    put("client_uri", JsonPrimitive("https://rp.example"))
                    put("policy_uri", JsonPrimitive("https://rp.example/privacy"))
                    put("contacts", JsonArray(listOf(JsonPrimitive("rp@example.com"))))
                },
            )
        assertNotNull(detail)
        assertEquals("rp@example.com", detail.contactEmail?.value)
        assertEquals(WalletAttributeSourceKind.SELF_ASSERTED, detail.contactEmail?.source?.kind)
        assertNull(detail.contactEmail?.source?.authority)
        assertEquals("https://rp.example", detail.websiteUri?.value)
        assertEquals(WalletAttributeSourceKind.SELF_ASSERTED, detail.websiteUri?.source?.kind)
        assertEquals("https://rp.example/privacy", detail.privacyPolicyUri?.value)
        assertEquals(WalletAttributeSourceKind.SELF_ASSERTED, detail.privacyPolicyUri?.source?.kind)
        assertNull(detail.legalName, "client_name is display metadata, not a registered legal name")
        assertNull(detail.contactPhone)
        assertNull(detail.jurisdiction)
    }

    @Test
    fun `DCR contacts without an email do not invent a phone`() {
        val detail =
            counterpartyDetailFromDcrMetadata(
                buildJsonObject {
                    put("contacts", JsonArray(listOf(JsonPrimitive("not-an-email"))))
                },
            )
        assertNull(detail)
    }

    private fun attestedKinds(): List<WalletAttributeSourceKind> =
        WalletAttributeSourceKind.entries.filter { it != WalletAttributeSourceKind.SELF_ASSERTED }
}

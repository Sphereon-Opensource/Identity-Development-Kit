/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WalletRegisteredPurposeTest {
    private val registrar =
        WalletAttributeSource(
            kind = WalletAttributeSourceKind.ACCESS_CERTIFICATE,
            authority = "NL Relying Party Registrar",
            authorityIdentifier = "https://registrar.example.nl",
        )

    @Test
    fun `a registered intended use keeps its identifier and every localisation`() {
        val purpose =
            WalletRegisteredPurpose(
                intendedUseIdentifier = "urn:registrar:nl:intended-use:age-check",
                purpose =
                    listOf(
                        WalletMultiLangString(lang = "en", value = "To verify that you are over 18"),
                        WalletMultiLangString(lang = "nl", value = "Om te controleren of u ouder bent dan 18"),
                    ),
                source = registrar,
            )

        assertEquals("urn:registrar:nl:intended-use:age-check", purpose.intendedUseIdentifier)
        assertEquals("To verify that you are over 18", purpose.purpose.forLang("en"))
        assertEquals("Om te controleren of u ouder bent dan 18", purpose.purpose.forLang("NL"))
        assertNull(purpose.purpose.forLang("de"), "a language the registrar did not record is absent, never machine translated")
        assertEquals("NL Relying Party Registrar", purpose.source.authority)
    }

    @Test
    fun `a self-asserted purpose cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> {
            WalletRegisteredPurpose(
                intendedUseIdentifier = "urn:example:we-said-so",
                purpose = listOf(WalletMultiLangString(lang = "en", value = "Because we would like to know")),
                source = WalletAttributeSource(kind = WalletAttributeSourceKind.SELF_ASSERTED),
            )
        }
    }

    @Test
    fun `an intended use without a purpose or an identifier is refused`() {
        assertFailsWith<IllegalArgumentException> {
            WalletRegisteredPurpose(
                intendedUseIdentifier = "urn:registrar:nl:intended-use:age-check",
                purpose = emptyList(),
                source = registrar,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WalletRegisteredPurpose(
                intendedUseIdentifier = " ",
                purpose = listOf(WalletMultiLangString(lang = "en", value = "To verify that you are over 18")),
                source = registrar,
            )
        }
    }

    @Test
    fun `a localisation with no language or no text is refused`() {
        assertFailsWith<IllegalArgumentException> { WalletMultiLangString(lang = "", value = "To open a bank account") }
        assertFailsWith<IllegalArgumentException> { WalletMultiLangString(lang = "en", value = "  ") }
    }

    @Test
    fun `an unregistered purpose stays absent on both selection levels`() {
        val requirement = WalletCredentialRequirement(id = "pid")
        val request = WalletCredentialSelectionRequest(requirements = listOf(requirement), satisfiable = true)

        assertNull(requirement.purpose, "a request whose certificate registers no purpose must not grow one")
        assertNull(request.purpose)
    }
}

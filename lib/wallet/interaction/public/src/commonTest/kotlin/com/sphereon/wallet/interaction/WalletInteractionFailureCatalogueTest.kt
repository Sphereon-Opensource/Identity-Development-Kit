/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletInteractionFailureCatalogueTest {
    private val dottedIdentifier = Regex("""[A-Za-z][A-Za-z0-9_]*\.[A-Za-z][A-Za-z0-9_.]*""")

    @Test
    fun `prefers the backend message over everything`() {
        val resolved =
            resolveFailureMessage(
                disposition = WalletFailureDisposition.TERMINAL,
                message = "Backend said so.",
                code = WalletInteractionFailureCodes.OID4VP_REQUEST_URI_EXPIRED,
            )
        assertEquals("Backend said so.", resolved)
    }

    @Test
    fun `treats an empty message as absent rather than rendering blank prose`() {
        val resolved =
            resolveFailureMessage(
                disposition = WalletFailureDisposition.TERMINAL,
                message = "",
                code = WalletInteractionFailureCodes.OID4VP_REQUEST_URI_EXPIRED,
            )
        assertEquals(
            WalletInteractionFailureCatalogue.messageForCode(WalletInteractionFailureCodes.OID4VP_REQUEST_URI_EXPIRED),
            resolved,
        )
    }

    @Test
    fun `distinguishes codes that share a message key`() {
        val expired =
            resolveFailureMessage(
                disposition = WalletFailureDisposition.TERMINAL,
                code = WalletInteractionFailureCodes.OID4VP_REQUEST_URI_EXPIRED,
            )
        val fetchFailed =
            resolveFailureMessage(
                disposition = WalletFailureDisposition.REPEATABLE,
                code = WalletInteractionFailureCodes.OID4VP_REQUEST_URI_FETCH_FAILED,
            )
        assertNotEquals(expired, fetchFailed)
    }

    @Test
    fun `falls back to the disposition for a code without a catalogue entry`() {
        val resolved =
            resolveFailureMessage(
                disposition = WalletFailureDisposition.REPEATABLE,
                code = WalletInteractionFailureCodes.OID4VCI_REFRESH_NONCE_REQUEST_FAILED,
            )
        assertEquals(
            WalletInteractionFailureCatalogue.messageForDisposition(WalletFailureDisposition.REPEATABLE),
            resolved,
        )
    }

    @Test
    fun `classified errors do not populate message from the catalogue`() {
        val error = classifiedWalletInteractionError(WalletInteractionFailureCodes.OID4VP_REQUEST_URI_EXPIRED)
        assertNull(error.message)
        assertEquals(
            WalletInteractionFailureCatalogue.messageForCode(WalletInteractionFailureCodes.OID4VP_REQUEST_URI_EXPIRED),
            resolveFailureMessage(error),
        )
    }

    @Test
    fun `unknown locale falls back to the default locale`() {
        val dutch =
            resolveFailureMessage(
                disposition = WalletFailureDisposition.TERMINAL,
                code = WalletInteractionFailureCodes.OID4VP_REQUEST_URI_EXPIRED,
                locale = "nl-NL",
            )
        val english =
            resolveFailureMessage(
                disposition = WalletFailureDisposition.TERMINAL,
                code = WalletInteractionFailureCodes.OID4VP_REQUEST_URI_EXPIRED,
                locale = WalletInteractionFailureCatalogue.DEFAULT_LOCALE,
            )
        assertEquals(english, dutch)
    }

    @Test
    fun `every catalogue entry is a non-empty string in the default locale`() {
        val dispositions = WalletInteractionFailureCatalogue.byDisposition()
        assertEquals(
            setOf(
                WalletFailureDisposition.TERMINAL,
                WalletFailureDisposition.REPEATABLE,
                WalletFailureDisposition.RESUMABLE,
            ),
            dispositions.keys,
        )
        dispositions.values.forEach { text ->
            assertTrue(text.isNotBlank(), "disposition copy must be non-empty")
        }
        val byCode = WalletInteractionFailureCatalogue.byCode()
        assertTrue(byCode.isNotEmpty(), "the by-code map is small but must not be empty")
        byCode.forEach { (code, text) ->
            assertTrue(code.isNotBlank())
            assertTrue(text.isNotBlank(), "$code must resolve to a non-empty string")
        }
    }

    @Test
    fun `no catalogue entry contains a code, a key, or a dotted identifier`() {
        val byCode = WalletInteractionFailureCatalogue.byCode()
        val entries = WalletInteractionFailureCatalogue.byDisposition().values + byCode.values
        entries.forEach { text ->
            assertTrue('\u2014' !in text, "copy must not contain an em-dash")
            assertTrue(!text.contains("vouched", ignoreCase = true))
            assertTrue(!text.contains("wallet.interaction."))
            assertTrue(dottedIdentifier.find(text) == null, "copy contains a dotted identifier: $text")
            byCode.keys.forEach { code ->
                assertTrue(code !in text, "copy contains code $code: $text")
            }
        }
        assertTrue(byCode.keys.none { it.startsWith("wallet.interaction.error.") })
    }

    @Test
    fun `never renders a code or a key for an unknown code`() {
        for (disposition in WalletFailureDisposition.entries) {
            val resolved = resolveFailureMessage(disposition = disposition, code = "oid4vci.some_internal_thing")
            assertTrue(dottedIdentifier.find(resolved) == null, resolved)
            assertTrue(!resolved.contains("wallet.interaction."))
        }
    }
}

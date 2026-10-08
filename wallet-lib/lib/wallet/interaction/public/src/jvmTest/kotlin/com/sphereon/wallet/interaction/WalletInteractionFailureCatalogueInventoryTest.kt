/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalogue is keyed on `code` and is deliberately smaller than the 120 emitted codes. Internal
 * executor failures a holder cannot distinguish fall back to the disposition sentence.
 */
class WalletInteractionFailureCatalogueInventoryTest {
    @Test
    fun `every byCode entry is an emitted failure code`() {
        val unknown = WalletInteractionFailureCatalogue.byCode().keys.filterNot { it in emittedFailureCodes() }
        assertEquals(emptyList(), unknown, "catalogue keys that are not engine codes: $unknown")
    }

    @Test
    fun `omitted codes fall back to disposition rather than a unique sentence`() {
        val emitted = emittedFailureCodes()
        val listed = emitted.filter { WalletInteractionFailureCatalogue.byCode().containsKey(it) }
        val omitted = emitted.filterNot { WalletInteractionFailureCatalogue.byCode().containsKey(it) }
        assertEquals(emitted.size, listed.size + omitted.size)
        assertTrue(listed.isNotEmpty(), "some codes must have holder-specific copy")
        assertTrue(omitted.size > listed.size, "most codes deliberately fall back to disposition")
        for (code in omitted) {
            val disposition = DISPOSITIONS.getValue(code)
            val resolved = resolveFailureMessage(disposition = disposition, code = code)
            assertEquals(
                WalletInteractionFailureCatalogue.messageForDisposition(disposition),
                resolved,
                "$code should not have a unique sentence",
            )
        }
    }
}

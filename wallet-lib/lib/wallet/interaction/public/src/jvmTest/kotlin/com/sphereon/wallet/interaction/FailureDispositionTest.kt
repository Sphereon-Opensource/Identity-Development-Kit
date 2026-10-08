/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every failure code the engine can emit has a deliberately chosen disposition.
 *
 * The renderer draws one action per disposition, so a code that arrives with the wrong one sends a
 * holder to retry a spent single-use artefact, or offers Start over on an exchange the backend could
 * still have continued. When this fails because a new code was added, classify it in the table
 * above rather than adding it to this list to make the build green.
 *
 * [emittedFailureCodes] is JVM reflection over [WalletInteractionFailureCodes] public String
 * constants. There is no sealed failure type; a hand-maintained duplicate list would let a new
 * const val pass this test without a disposition.
 */
class FailureDispositionTest {
    @Test
    fun `every emitted failure code has a classification`() {
        val unclassified = emittedFailureCodes().filterNot { DISPOSITIONS.containsKey(it) }
        assertEquals(emptyList(), unclassified, "failure codes with no disposition: $unclassified")
    }

    @Test
    fun `spent single-use artefacts are terminal`() {
        for (code in SINGLE_USE_SPENT_CODES) {
            assertEquals(WalletFailureDisposition.TERMINAL, DISPOSITIONS.getValue(code), "$code must not invite a retry")
        }
    }
}

/**
 * Public static String fields on [WalletInteractionFailureCodes]. Adding a const val is enough
 * for this list to grow; classify it in [DISPOSITIONS] or the first test fails.
 */
fun emittedFailureCodes(): List<String> =
    WalletInteractionFailureCodes::class.java.declaredFields
        .filter { field ->
            Modifier.isPublic(field.modifiers) &&
                Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java
        }
        .map { field -> field.get(null) as String }
        .sorted()

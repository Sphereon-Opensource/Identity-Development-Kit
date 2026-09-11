/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Iterates `authorization-handoff-uri-vectors.json` (generated into [AuthorizationHandoffUriVectors]).
 * A vector the implementation disagrees with fails. An unknown `expected` value fails rather than
 * being skipped. Null is a Kotlin `String?` and cannot appear as a JSON `uri`.
 */
class OpenableAuthorizationUriTest {
    @Test
    fun `shared vectors agree with openableAuthorizationUri`() {
        val vectors = AuthorizationHandoffUriVectors.all
        assertTrue(vectors.isNotEmpty(), "authorization-handoff-uri-vectors.json must not be empty")
        for (vector in vectors) {
            val admitted = openableAuthorizationUri(vector.uri)
            when (vector.expected) {
                "admit" ->
                    assertEquals(vector.uri, admitted, vector.why)
                "refuse" ->
                    assertNull(admitted, vector.why)
                else ->
                    error("unknown expected '${vector.expected}' for uri=${vector.uri}: ${vector.why}")
            }
        }
    }

    @Test
    fun `refuses a null value which the json file cannot represent`() {
        assertNull(openableAuthorizationUri(null))
    }
}

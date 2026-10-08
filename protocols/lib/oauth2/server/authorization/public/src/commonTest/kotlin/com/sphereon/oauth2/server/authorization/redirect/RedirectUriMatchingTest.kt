/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.redirect

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedirectUriMatchingTest {
    private val registered = listOf("https://app.example.test/callback")

    @Test
    fun exactMatchIsAccepted() {
        assertTrue(RedirectUriMatching.matches("https://app.example.test/callback", registered))
    }

    @Test
    fun additionalQueryParametersAreAccepted() {
        assertTrue(RedirectUriMatching.matches("https://app.example.test/callback?state=abc", registered))
    }

    @Test
    fun schemeAndHostAreComparedCaseInsensitively() {
        assertTrue(RedirectUriMatching.matches("HTTPS://APP.EXAMPLE.TEST/callback", registered))
    }

    @Test
    fun pathIsComparedCaseSensitively() {
        assertFalse(RedirectUriMatching.matches("https://app.example.test/CallBack", registered))
    }

    @Test
    fun aDifferentHostIsRejected() {
        assertFalse(RedirectUriMatching.matches("https://evil.example.test/callback", registered))
    }

    @Test
    fun aHostThatMerelyEndsWithTheRegisteredHostIsRejected() {
        assertFalse(RedirectUriMatching.matches("https://app.example.test.evil.test/callback", registered))
    }

    @Test
    fun aDifferentSchemeIsRejected() {
        assertFalse(RedirectUriMatching.matches("http://app.example.test/callback", registered))
    }

    @Test
    fun aDifferentPathIsRejected() {
        assertFalse(RedirectUriMatching.matches("https://app.example.test/other", registered))
    }

    @Test
    fun aRegisteredEntryCarryingAQueryOnlyMatchesExactly() {
        val withQuery = listOf("https://app.example.test/callback?tenant=acme")
        assertTrue(RedirectUriMatching.matches("https://app.example.test/callback?tenant=acme", withQuery))
        assertFalse(RedirectUriMatching.matches("https://app.example.test/callback?tenant=evil", withQuery))
        assertFalse(RedirectUriMatching.matches("https://app.example.test/callback", withQuery))
    }

    @Test
    fun noRegisteredEntriesMeansNoRedirect() {
        assertFalse(RedirectUriMatching.matches("https://app.example.test/callback", emptyList()))
    }

    @Test
    fun aRelativeOrMalformedTargetIsRejected() {
        assertFalse(RedirectUriMatching.matches("/callback", registered))
        assertFalse(RedirectUriMatching.matches("app.example.test/callback", registered))
        assertFalse(RedirectUriMatching.matches("", registered))
    }

    @Test
    fun javascriptAndDataTargetsAreRejected() {
        assertFalse(RedirectUriMatching.matches("javascript://app.example.test/callback", registered))
        assertFalse(RedirectUriMatching.matches("data://app.example.test/callback", registered))
    }
}

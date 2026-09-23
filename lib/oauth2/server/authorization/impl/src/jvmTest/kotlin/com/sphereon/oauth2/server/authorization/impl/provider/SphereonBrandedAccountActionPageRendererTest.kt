/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.oauth2.server.authorization.provider.AccountActionPageContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SphereonBrandedAccountActionPageRendererTest {
    private val renderer = SphereonBrandedAccountActionPageRenderer()

    private suspend fun html(showWebAuthn: Boolean = true): String {
        val result =
            renderer.render(
                AccountActionPageContext(
                    asInstanceId = "default",
                    tenantId = "acme",
                    cspNonce = "n0nce",
                    loginPath = "/as/acme/login",
                    showWebAuthn = showWebAuthn,
                    organizationName = "Acme",
                ),
            )
        assertTrue(result.isOk, "render must succeed")
        return result.value.html
    }

    private fun lineWith(markup: String, needle: String): String =
        markup.lines().firstOrNull { it.contains(needle) }
            ?: error("markup has no line containing '$needle'")

    @Test
    fun `hidden cannot be defeated by an author rule`() = runTest {
        assertTrue(html().contains("[hidden]{display:none !important}"))
    }

    @Test
    fun `the retry activation check wording is gone`() = runTest {
        assertFalse(html().contains("Retry activation check"))
    }

    @Test
    fun `the password field is not forced in markup`() = runTest {
        val passwordInput = lineWith(html(), """id="password"""")
        assertFalse(
            passwordInput.contains(" required"),
            "whether a password is required is decided by the resolve response, not at render time",
        )
    }

    @Test
    fun `passkey enrollment asks for a discoverable credential`() = runTest {
        val markup = html()
        assertTrue(markup.contains("""residentKey: "required""""))
        assertFalse(markup.contains("""residentKey: "preferred""""))
    }

    @Test
    fun `passkey is never labelled with the placeholder operator name`() = runTest {
        assertFalse(html().contains(""""Operator""""))
    }

    @Test
    fun `base64url conversion targets plus and slash, not backslashes`() = runTest {
        val markup = html()
        assertTrue(markup.contains("""replace(/\+/g, "-")"""))
        assertTrue(markup.contains("""replace(/\//g, "_")"""))
        assertFalse(
            markup.contains("""replace(/\\+/g"""),
            "an escaped backslash here makes the helper throw ReferenceError and silently kills passkey enrollment",
        )
    }

    @Test
    fun `both credential paths are offered when webauthn is enabled`() = runTest {
        val markup = html(showWebAuthn = true)
        assertTrue(markup.contains("""id="choose-passkey""""))
        assertTrue(markup.contains("""id="choose-password""""))
    }

    @Test
    fun `no passkey controls when webauthn is disabled`() = runTest {
        val markup = html(showWebAuthn = false)
        assertFalse(markup.contains("""id="choose-passkey""""))
        assertFalse(markup.contains("""id="also-passkey""""))
    }

    @Test
    fun `copy switches on the action kind`() = runTest {
        val markup = html()
        assertTrue(markup.contains("identity.password-change"))
        assertTrue(markup.contains("Set a new password"))
        assertFalse(markup.contains("Activate your account"))
    }

    @Test
    fun `every inline block carries the csp nonce`() = runTest {
        val markup = html()
        val blocks = Regex("""<(style|script)(\s[^>]*)?>""").findAll(markup).toList()
        assertTrue(blocks.isNotEmpty(), "the page must carry inline blocks")
        assertTrue(
            blocks.all { it.value.contains("""nonce="n0nce"""") },
            "an inline block without the nonce is blocked by the strict CSP",
        )
    }

    @Test
    fun `the action token never reaches a url`() = runTest {
        val markup = html()
        assertTrue(markup.contains("location.hash"))
        assertFalse(markup.contains("?token="))
        assertFalse(markup.contains("&token="))
    }
}

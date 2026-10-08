/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.provider

/**
 * Holds the symmetric secret used to seal the login form's CSRF token. Pluggable so that a
 * production deployment can override the default in-memory provider with one backed by the
 * configured [com.sphereon.core.api.conf.SecretProvider] (so multi-instance deployments share
 * a key and the form survives a 302 to a peer).
 *
 * Multi-instance deployments MUST bind a shared secret because each instance's default key is
 * independent.
 */
interface LoginCsrfKeyProvider {
    /** Return the current HMAC key bytes. Must be at least 16 bytes; 32 strongly recommended. */
    fun keyBytes(): ByteArray
}

/**
 * Mints + verifies the `(tab_id, session_code)` CSRF tuple bound to a `session_id` on the AS
 * login form. Defends against capability-URL leak: an attacker who learns
 * `/login?session_id=<sid>` from a referrer log or browser history cannot fabricate a valid
 * `session_code` without the deployment's CSRF HMAC key.
 *
 * Wire shape on the rendered form:
 *  - hidden `session_id` : opaque pending-auth-session id, already public.
 *  - hidden `tab_id`     : CSPRNG random, freshly minted per render.
 *  - hidden `session_code` : `BASE64URL(HMAC-SHA-256(key, sid || "|" || tab_id))`.
 *
 * The cookie binding (separate cookie holding the tab_id) is enforced at the HTTP layer; this
 * contract is the cryptographic core.
 */
interface LoginCsrfTokenizer {
    /**
     * Mint a fresh `(tab_id, session_code)` for [sessionId]. The caller embeds both in the
     * rendered form's hidden inputs and writes the tab_id to a `oidc_login_csrf` cookie so
     * the POST handler can confirm both halves were present for the same browser session.
     */
    suspend fun mint(sessionId: String): LoginCsrfToken

    /**
     * Verify the `(tab_id, session_code)` submitted with [sessionId]. Returns true iff the
     * recomputed code matches under a constant-time compare.
     */
    suspend fun verify(
        sessionId: String,
        tabId: String,
        sessionCode: String,
    ): Boolean
}

/**
 * Output of [LoginCsrfTokenizer.mint]. Both fields are wire-visible in the rendered form;
 * the [tabId] is also written to the `oidc_login_csrf` cookie for the cookie-binding half
 * of the defense.
 */
data class LoginCsrfToken(
    val tabId: String,
    val sessionCode: String,
)

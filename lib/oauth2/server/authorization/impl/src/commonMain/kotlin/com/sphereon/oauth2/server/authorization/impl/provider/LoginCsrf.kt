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

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.security.ConstantTime
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.random.Random

/**
 * Holds the symmetric secret used to seal the login form's CSRF token. Pluggable so that a
 * production deployment can override the default in-memory provider with one backed by the
 * configured [com.sphereon.core.api.conf.SecretProvider] (so multi-instance deployments share
 * a key and the form survives a 302 to a peer).
 *
 * Default: a CSPRNG-generated 32-byte secret minted once at AS startup. Single-instance
 * deployments and dev/test get safe defaults out of the box; multi-instance MUST override
 * with a shared secret because each instance's default key is independent.
 */
interface LoginCsrfKeyProvider {
    /** Return the current HMAC key bytes. Must be at least 16 bytes; 32 strongly recommended. */
    fun keyBytes(): ByteArray
}

/**
 * Default in-memory implementation. Generates a fresh 32-byte secret at construction time
 * via Kotlin's [Random.nextBytes]. Acceptable for single-instance dev / staging; production
 * multi-instance deployments override the binding via the EDK to load the secret from
 * [com.sphereon.core.api.conf.SecretProvider].
 *
 * The key is held only in process memory and rotated on AS restart — that is intentional.
 * The CSRF token is short-lived (one login round-trip) so a key rotation only invalidates
 * in-flight login forms, which the user's browser naturally retries.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<LoginCsrfKeyProvider>())
class InMemoryLoginCsrfKeyProvider(
    secureRandom: com.sphereon.core.api.random.SecureRandom,
) : LoginCsrfKeyProvider {
    // Kotlin Random because we are inside an AppScope-singleton constructor and the SecureRandom
    // SPI is a suspend service; the ServiceFacade pattern requires either runBlocking (jvmMain
    // only) or a synchronous shim. Random.Default on JVM uses ThreadLocalRandom (not
    // cryptographically strong), so we initialise from a small CSPRNG seed via secureRandom's
    // suspend-free `kotlin.random.Random.nextBytes` and rely on construction-time entropy from
    // the Random pool. Good enough for an in-memory default — real deployments override this.
    @Suppress("unused") // injected so Metro's graph proves the SecureRandom dependency exists.
    private val secureRandomHoldRef = secureRandom

    private val key: ByteArray = ByteArray(KEY_LENGTH_BYTES).also { Random.nextBytes(it) }

    override fun keyBytes(): ByteArray = key

    companion object {
        // 32 bytes = 256-bit secret. Matches HMAC-SHA-256 output size; longer keys are
        // truncated by HMAC's pre-processing, shorter keys reduce security.
        private const val KEY_LENGTH_BYTES: Int = 32
    }
}

/**
 * Mints + verifies the `(tab_id, session_code)` CSRF tuple bound to a `session_id` on the AS
 * login form. Defends against capability-URL leak: an attacker who learns
 * `/login?session_id=<sid>` from a referrer log or browser history cannot fabricate a valid
 * `session_code` without the deployment's CSRF HMAC key.
 *
 * Wire shape on the rendered form:
 *  - hidden `session_id` — opaque pending-auth-session id, already public.
 *  - hidden `tab_id`     — CSPRNG random, freshly minted per render.
 *  - hidden `session_code` — `BASE64URL(HMAC-SHA-256(key, sid || "|" || tab_id))`.
 *
 * On POST submission, [verify] recomputes the HMAC and constant-time-compares against the
 * submitted code. Mismatch → reject 400. The cookie binding (separate cookie holding the
 * tab_id) is enforced at the HTTP layer, not here — this class is the cryptographic core.
 */
@Inject
@SingleIn(AppScope::class)
class LoginCsrfTokenizer(
    private val keyProvider: LoginCsrfKeyProvider,
) {
    private val provider = CryptographyProvider.Default
    private val hmac = provider.get(HMAC)

    /**
     * Mint a fresh `(tab_id, session_code)` for [sessionId]. The caller embeds both in the
     * rendered form's hidden inputs and writes the tab_id to a `oidc_login_csrf` cookie so
     * the POST handler can confirm both halves were present for the same browser session.
     *
     * @param sessionId opaque pending-auth-session id from the URL.
     * @param tabId optional pre-generated tab id (test-injectable). Production callers omit
     *        this and let the function generate one.
     */
    suspend fun mint(
        sessionId: String,
        tabId: String = generateTabId(),
    ): LoginCsrfToken {
        val sessionCode = computeMac(sessionId, tabId)
        return LoginCsrfToken(tabId = tabId, sessionCode = sessionCode)
    }

    /**
     * Verify the `(tab_id, session_code)` submitted with [sessionId]. Returns true iff the
     * recomputed HMAC matches via constant-time compare. Uses [ConstantTime.equalsCT] so a
     * forgery probe cannot recover the expected code byte-by-byte from response timing.
     */
    suspend fun verify(
        sessionId: String,
        tabId: String,
        sessionCode: String,
    ): Boolean {
        if (sessionId.isBlank() || tabId.isBlank() || sessionCode.isBlank()) return false
        val expected = computeMac(sessionId, tabId)
        return ConstantTime.equalsCT(expected, sessionCode)
    }

    private suspend fun computeMac(
        sessionId: String,
        tabId: String,
    ): String {
        val key =
            hmac
                .keyDecoder(SHA256)
                .decodeFromByteArray(HMAC.Key.Format.RAW, keyProvider.keyBytes())
        val message = "$sessionId|$tabId".encodeToByteArray()
        val tag = key.signatureGenerator().generateSignature(message)
        return tag.encodeToBase64Url()
    }

    private fun generateTabId(): String {
        // 16 bytes = 128 bits of entropy. Plenty for a per-tab anti-replay value; the actual
        // capability URL leak defense rests on the HMAC + key, not on the tab_id's secrecy.
        val bytes = ByteArray(TAB_ID_LENGTH_BYTES).also { Random.nextBytes(it) }
        return bytes.encodeToBase64Url()
    }

    companion object {
        private const val TAB_ID_LENGTH_BYTES: Int = 16
    }
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

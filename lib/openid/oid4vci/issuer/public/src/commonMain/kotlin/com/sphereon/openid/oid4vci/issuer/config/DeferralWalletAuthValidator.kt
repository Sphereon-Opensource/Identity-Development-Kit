/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.config

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.credential.issuance.pipeline.CredentialClaimsBinding
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig

/**
 * §6.5 wallet-auth invariant for deferral policies.
 *
 * A credential whose `deferralPolicy.enabled = true` and `maxDeferralSeconds` exceeds the AS's
 * `accessTokenLifetimeSeconds` MUST have one of:
 *
 * - refresh tokens enabled (`"refresh_token" in grantTypesEnabled`) with
 *   `refreshTokenLifetimeSeconds >= maxDeferralSeconds`, OR
 * - a deferral-scoped access-token fallback wired (the issuer's
 *   `MintDeferralScopedTokenCommand` is bound on the runtime DI graph), enabled here via
 *   `deferralScopedTokenFallbackEnabled`. When this remediation is chosen the configured
 *   token TTL ([deferralScopedTokenTtlSeconds]) MUST itself be `>= maxDeferralSeconds`,
 *   otherwise the minted token expires before the wallet can complete its last poll.
 *
 * Without either remediation the wallet's access token expires before the deferral resolves
 * and the holder is permanently stranded — the issuance pipeline runs to completion server-side
 * but the wallet cannot present a valid token to `/deferred_credential`.
 *
 * This validator runs at config-load (operator startup) so misconfigurations fail loud before
 * any wallet attempts a poll.
 */
@JsExportCompat
object DeferralWalletAuthValidator {
    /**
     * Config key operators flip to acknowledge that the runtime DI graph wires
     * `MintDeferralScopedTokenCommand`. The validator does not introspect the DI graph itself
     * — that's an `AppScope` concern resolved at session start — so the operator's `true`
     * here is taken as proof that the binding is present.
     */
    const val CONFIG_KEY_DEFERRAL_TOKEN_FALLBACK_ENABLED: String =
        "oid4vci.issuer.deferral-scoped-token.enabled"

    /**
     * Config key operators set to size the minted deferral-scoped access token's lifetime.
     * Read by `HandleCredentialRequestCommandImpl.resolveDeferralTokenTtlSeconds`; the
     * validator cross-checks this value against every deferrable binding's
     * `maxDeferralSeconds` so a too-short TTL fails-fast at startup rather than stranding
     * wallets at poll time.
     */
    const val CONFIG_KEY_DEFERRAL_SCOPED_TOKEN_TTL_SECONDS: String =
        "oid4vci.issuer.deferral-scoped-token.default-ttl-seconds"

    private const val REFRESH_TOKEN_GRANT = "refresh_token"

    /**
     * Validates the deferral-policy ↔ wallet-auth invariant for `claimsBindings` against
     * `asConfig`. Returns [Ok] when every deferrable binding has a remediation, or [Err]
     * naming the first binding that violates the invariant.
     *
     * @param claimsBindings every credential's pipeline binding (carrying the deferral policy).
     * @param asConfig the OAuth2 server configuration the issuer's wallet flows authenticate
     *   against — supplies `accessTokenLifetimeSeconds`, `refreshTokenLifetimeSeconds`, and
     *   `grantTypesEnabled`.
     * @param deferralScopedTokenFallbackEnabled `true` when the deployment wires a
     *   `MintDeferralScopedTokenCommand` on the runtime graph (operator opts in by setting
     *   `oid4vci.issuer.deferral-scoped-token.enabled = true` and providing a server signing
     *   identifier). When `true`, the validator treats every deferrable config as remediated
     *   regardless of refresh-token settings, but additionally requires
     *   [deferralScopedTokenTtlSeconds] to be `>= maxDeferralSeconds`.
     * @param deferralScopedTokenTtlSeconds the minted token's TTL in seconds, as resolved from
     *   `oid4vci.issuer.deferral-scoped-token.default-ttl-seconds`. Only consulted when
     *   [deferralScopedTokenFallbackEnabled] is `true` AND refresh tokens do not cover the
     *   binding on their own. Pass the same default the issuer uses (`604800`, 7 days) when
     *   the operator did not override the key, so the validator sees what the runtime sees.
     */
    fun validate(
        claimsBindings: Iterable<CredentialClaimsBinding>,
        asConfig: OAuth2ServerInstanceConfig,
        deferralScopedTokenFallbackEnabled: Boolean,
        deferralScopedTokenTtlSeconds: Long? = null,
    ): IdkResult<Unit, IdkError> {
        val firstViolation =
            claimsBindings.firstOrNull { binding ->
                violatesInvariant(
                    binding,
                    asConfig,
                    deferralScopedTokenFallbackEnabled,
                    deferralScopedTokenTtlSeconds,
                )
            } ?: return Ok(Unit)
        val policy = firstViolation.deferralPolicy
        return Err(
            IdkError.INVALID_STATE(
                message =
                    buildViolationMessage(
                        bindingId = firstViolation.id,
                        maxDeferralSeconds = policy.maxDeferralSeconds,
                        asConfig = asConfig,
                        fallbackEnabled = deferralScopedTokenFallbackEnabled,
                        deferralScopedTokenTtlSeconds = deferralScopedTokenTtlSeconds,
                    ),
            ),
        )
    }

    private fun violatesInvariant(
        binding: CredentialClaimsBinding,
        asConfig: OAuth2ServerInstanceConfig,
        deferralScopedTokenFallbackEnabled: Boolean,
        deferralScopedTokenTtlSeconds: Long?,
    ): Boolean {
        val policy = binding.deferralPolicy
        val invariantApplies =
            policy.enabled && policy.maxDeferralSeconds > asConfig.accessTokenLifetimeSeconds
        if (!invariantApplies) return false

        val refreshOk =
            REFRESH_TOKEN_GRANT in asConfig.grantTypesEnabled &&
                asConfig.refreshTokenLifetimeSeconds >= policy.maxDeferralSeconds
        // Fallback opted in: the minted token's TTL must itself cover the deferral window,
        // otherwise the wallet still loses authentication before the policy expires.
        val fallbackOk =
            deferralScopedTokenFallbackEnabled &&
                deferralScopedTokenTtlSeconds != null &&
                deferralScopedTokenTtlSeconds >= policy.maxDeferralSeconds
        return !(refreshOk || fallbackOk)
    }

    private fun buildViolationMessage(
        bindingId: String,
        maxDeferralSeconds: Long,
        asConfig: OAuth2ServerInstanceConfig,
        fallbackEnabled: Boolean,
        deferralScopedTokenTtlSeconds: Long?,
    ): String {
        val base =
            "Credential configuration '$bindingId' has deferralPolicy.maxDeferralSeconds=$maxDeferralSeconds " +
                "exceeding AS accessTokenLifetimeSeconds=${asConfig.accessTokenLifetimeSeconds}, but " +
                "the refresh-token grant (grantTypesEnabled=${asConfig.grantTypesEnabled}, " +
                "refreshTokenLifetimeSeconds=${asConfig.refreshTokenLifetimeSeconds}) does not cover the window."
        return if (fallbackEnabled) {
            "$base The deferral-scoped access-token fallback is enabled but its configured TTL " +
                "(${deferralScopedTokenTtlSeconds ?: "unset"}s) is shorter than maxDeferralSeconds=$maxDeferralSeconds. " +
                "Raise '$CONFIG_KEY_DEFERRAL_SCOPED_TOKEN_TTL_SECONDS' to at least $maxDeferralSeconds."
        } else {
            "$base Neither remediation is in place: either add 'refresh_token' to grantTypesEnabled with " +
                "refreshTokenLifetimeSeconds >= $maxDeferralSeconds, or set " +
                "'$CONFIG_KEY_DEFERRAL_TOKEN_FALLBACK_ENABLED = true', wire MintDeferralScopedTokenCommand, " +
                "and configure '$CONFIG_KEY_DEFERRAL_SCOPED_TOKEN_TTL_SECONDS >= $maxDeferralSeconds'."
        }
    }
}

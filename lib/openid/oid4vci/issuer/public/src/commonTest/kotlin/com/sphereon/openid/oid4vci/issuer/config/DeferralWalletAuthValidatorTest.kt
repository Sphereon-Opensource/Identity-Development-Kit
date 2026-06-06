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

import com.sphereon.credential.issuance.pipeline.CredentialClaimsBinding
import com.sphereon.credential.issuance.pipeline.DeferralPolicy
import com.sphereon.credential.issuance.pipeline.SemanticAttributeSetRef
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §6.5 wallet-auth invariant tests. The validator MUST flag a deferrable credential whose
 * deferral window outlives the AS access token unless either refresh tokens (with sufficient
 * lifetime) or the deferral-scoped-token fallback is enabled.
 */
class DeferralWalletAuthValidatorTest {
    private val accessTokenLifetimeSeconds = 3600 // 1h, the default
    private val tenMinuteDeferral = 600L
    private val twoDayDeferral = 2L * 24 * 3600

    private fun binding(
        id: String,
        policy: DeferralPolicy,
    ): CredentialClaimsBinding =
        CredentialClaimsBinding(
            id = id,
            semanticAttributeSetRef = SemanticAttributeSetRef(bundleId = "test"),
            deferralPolicy = policy,
        )

    private fun asConfig(
        grantTypes: Set<String> = setOf("authorization_code", "client_credentials", "refresh_token"),
        refreshTokenLifetimeSeconds: Int = 86400,
        accessTokenLifetime: Int = accessTokenLifetimeSeconds,
    ): OAuth2ServerInstanceConfig =
        OAuth2ServerInstanceConfig(
            accessTokenLifetimeSeconds = accessTokenLifetime,
            refreshTokenLifetimeSeconds = refreshTokenLifetimeSeconds,
            grantTypesEnabled = grantTypes,
        )

    @Test
    fun deferralWithinAccessTokenLifetimeIsAlwaysOk() {
        // Deferral fits inside the original token, so no remediation needed.
        val result =
            DeferralWalletAuthValidator.validate(
                claimsBindings = listOf(binding("pid", DeferralPolicy(enabled = true, maxDeferralSeconds = tenMinuteDeferral))),
                asConfig = asConfig(grantTypes = setOf("authorization_code"), refreshTokenLifetimeSeconds = 0),
                deferralScopedTokenFallbackEnabled = false,
            )
        assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
    }

    @Test
    fun deferrablePolicyWithRefreshTokenAndSufficientLifetimeIsOk() {
        val result =
            DeferralWalletAuthValidator.validate(
                claimsBindings = listOf(binding("pid", DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral))),
                asConfig =
                    asConfig(
                        grantTypes = setOf("authorization_code", "refresh_token"),
                        refreshTokenLifetimeSeconds = (twoDayDeferral + 1000).toInt(),
                    ),
                deferralScopedTokenFallbackEnabled = false,
            )
        assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
    }

    @Test
    fun deferrablePolicyWithRefreshTokenButInsufficientLifetimeIsNotEnoughOnItsOwn() {
        // Refresh token enabled but its own lifetime is shorter than the deferral window.
        val result =
            DeferralWalletAuthValidator.validate(
                claimsBindings = listOf(binding("pid", DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral))),
                asConfig =
                    asConfig(
                        grantTypes = setOf("authorization_code", "refresh_token"),
                        refreshTokenLifetimeSeconds = (twoDayDeferral - 1000).toInt(),
                    ),
                deferralScopedTokenFallbackEnabled = false,
            )
        assertTrue(result.isErr, "expected Err for insufficient refresh-token lifetime")
        assertEquals("INVALID_STATE", result.error.code)
        assertTrue(
            result.error.message.defaultMessage
                .contains("pid"),
            "error must name the offending credential binding id",
        )
    }

    @Test
    fun deferrablePolicyWithFallbackEnabledAndSufficientTokenTtlIsOk() {
        // The operator opted into the deferral-scoped-token fallback AND sized the token TTL to
        // cover the deferral window; refresh tokens irrelevant.
        val result =
            DeferralWalletAuthValidator.validate(
                claimsBindings = listOf(binding("pid", DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral))),
                asConfig = asConfig(grantTypes = setOf("authorization_code"), refreshTokenLifetimeSeconds = 0),
                deferralScopedTokenFallbackEnabled = true,
                deferralScopedTokenTtlSeconds = twoDayDeferral,
            )
        assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
    }

    @Test
    fun deferrablePolicyWithNoRefreshAndNoFallbackErrs() {
        val result =
            DeferralWalletAuthValidator.validate(
                claimsBindings = listOf(binding("pid", DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral))),
                asConfig = asConfig(grantTypes = setOf("authorization_code"), refreshTokenLifetimeSeconds = 0),
                deferralScopedTokenFallbackEnabled = false,
            )
        assertTrue(result.isErr, "expected Err for missing remediation")
        assertEquals("INVALID_STATE", result.error.code)
    }

    @Test
    fun disabledDeferralPolicyIsAlwaysOk() {
        // A disabled deferral policy never triggers the invariant check.
        val result =
            DeferralWalletAuthValidator.validate(
                claimsBindings =
                    listOf(
                        binding(
                            "pid",
                            DeferralPolicy(enabled = false, maxDeferralSeconds = twoDayDeferral),
                        ),
                    ),
                asConfig = asConfig(grantTypes = setOf("authorization_code"), refreshTokenLifetimeSeconds = 0),
                deferralScopedTokenFallbackEnabled = false,
            )
        assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
    }

    @Test
    fun deferrablePolicyWithFallbackEnabledButShortTokenTtlIsRejected() {
        // Fallback opted in, but the configured TTL is shorter than maxDeferralSeconds — the
        // minted token still expires before the wallet's last poll, so the validator must flag
        // this at config-load instead of stranding wallets at runtime. The deferral window must
        // exceed the AS access-token lifetime, otherwise the §6.5 path doesn't fire at all.
        val ttl = twoDayDeferral - 1000L
        val result =
            DeferralWalletAuthValidator.validate(
                claimsBindings = listOf(binding("pid", DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral))),
                asConfig = asConfig(grantTypes = setOf("authorization_code"), refreshTokenLifetimeSeconds = 0),
                deferralScopedTokenFallbackEnabled = true,
                deferralScopedTokenTtlSeconds = ttl,
            )
        assertTrue(result.isErr, "expected Err for too-short deferral-scoped token TTL")
        assertEquals("INVALID_STATE", result.error.code)
        val message = result.error.message.defaultMessage
        assertTrue(message.contains(ttl.toString()), "error must report the configured TTL: $message")
        assertTrue(
            message.contains(twoDayDeferral.toString()),
            "error must report the maxDeferralSeconds threshold: $message",
        )
    }

    @Test
    fun deferrablePolicyWithFallbackEnabledAndExactlyMatchingTokenTtlIsOk() {
        // TTL == maxDeferralSeconds is the boundary case the §6.5 invariant explicitly allows.
        val result =
            DeferralWalletAuthValidator.validate(
                claimsBindings = listOf(binding("pid", DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral))),
                asConfig = asConfig(grantTypes = setOf("authorization_code"), refreshTokenLifetimeSeconds = 0),
                deferralScopedTokenFallbackEnabled = true,
                deferralScopedTokenTtlSeconds = twoDayDeferral,
            )
        assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
    }

    @Test
    fun deferrablePolicyWithFallbackEnabledButUnsetTokenTtlIsRejected() {
        // Operator forgot to size the TTL; default-null must NOT be silently accepted as
        // sufficient because the validator cannot verify what the runtime will read.
        val result =
            DeferralWalletAuthValidator.validate(
                claimsBindings = listOf(binding("pid", DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral))),
                asConfig = asConfig(grantTypes = setOf("authorization_code"), refreshTokenLifetimeSeconds = 0),
                deferralScopedTokenFallbackEnabled = true,
                deferralScopedTokenTtlSeconds = null,
            )
        assertTrue(result.isErr, "expected Err when TTL is not supplied with fallback enabled")
        assertEquals("INVALID_STATE", result.error.code)
        assertTrue(
            result.error.message.defaultMessage
                .contains("unset"),
            "error must signal that the TTL config is unset",
        )
    }

    @Test
    fun firstViolatingBindingStopsValidation() {
        val result =
            DeferralWalletAuthValidator.validate(
                claimsBindings =
                    listOf(
                        binding("first", DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral)),
                        binding("second", DeferralPolicy(enabled = true, maxDeferralSeconds = twoDayDeferral)),
                    ),
                asConfig = asConfig(grantTypes = setOf("authorization_code"), refreshTokenLifetimeSeconds = 0),
                deferralScopedTokenFallbackEnabled = false,
            )
        assertTrue(result.isErr)
        assertTrue(
            result.error.message.defaultMessage
                .contains("first"),
            "error must name the FIRST offending binding, not the last",
        )
    }
}

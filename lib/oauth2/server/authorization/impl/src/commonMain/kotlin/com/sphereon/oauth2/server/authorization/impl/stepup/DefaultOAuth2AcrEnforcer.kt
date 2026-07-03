/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.stepup

import com.sphereon.core.api.service.AuthAssuranceLevel
import com.sphereon.oauth2.server.authorization.stepup.OAuth2AcrEnforcementResult
import com.sphereon.oauth2.server.authorization.stepup.OAuth2AcrEnforcer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * IDK default [OAuth2AcrEnforcer]. Accepts the canonical NIST
 * [AuthAssuranceLevel.acr] URNs used by command contracts and the legacy SAML 2.0
 * PasswordProtectedTransport / MobileTwoFactorContract / SmartcardPKI vocabulary used by older
 * EDK step-up deployments. Deployments that speak a different ACR vocabulary (eIDAS LoA URIs,
 * custom enterprise URIs, OIDC trust-profile URLs) override the binding via DI.
 *
 * Pure / stateless / AppScope. Caller supplies `nowEpochSeconds` for testability.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<OAuth2AcrEnforcer>())
class DefaultOAuth2AcrEnforcer : OAuth2AcrEnforcer {
    override fun enforce(
        requestedAcrValues: List<String>?,
        currentAcr: String?,
        currentAal: AuthAssuranceLevel,
        currentAmr: Set<String>,
        authTimeEpochSeconds: Long?,
        maxAge: Long?,
        nowEpochSeconds: Long,
    ): OAuth2AcrEnforcementResult {
        val nonEmpty = requestedAcrValues?.filter { it.isNotBlank() }.orEmpty()

        // Stale-auth check first (independent of ACR comparison): an over-strong achieved
        // authentication can still be too old. Missing authTime + non-null maxAge fails
        // because we cannot prove freshness.
        if (maxAge != null) {
            if (authTimeEpochSeconds == null || nowEpochSeconds - authTimeEpochSeconds > maxAge) {
                val targetAal = strongestRequestedAal(nonEmpty) ?: currentAal
                return OAuth2AcrEnforcementResult.Insufficient(
                    requiredAal = targetAal,
                    currentAal = currentAal,
                    acrValues = nonEmpty.firstOrNull() ?: acrFor(targetAal),
                    maxAge = maxAge,
                    reason = OAuth2AcrEnforcementResult.Insufficient.Reason.STALE_AUTH,
                )
            }
        }

        // No ACR demand → grant whatever the authenticator surfaced.
        if (nonEmpty.isEmpty()) {
            return OAuth2AcrEnforcementResult.Sufficient(grantedAcr = currentAcr)
        }

        // OIDC Core §3.1.2.1 — acr_values is voluntary; OP SHOULD echo a requested ACR when
        // satisfied. First requested ACR whose AAL is ≤ currentAal wins (priority order).
        val matched =
            nonEmpty.firstOrNull { acr ->
                val requiredAal = aalFor(acr) ?: return@firstOrNull false
                currentAal.ordinal >= requiredAal.ordinal
            }
        if (matched != null) {
            return OAuth2AcrEnforcementResult.Sufficient(grantedAcr = matched)
        }

        // Nothing matched. Demand the strongest requested AAL; surface the first requested
        // ACR as the canonical challenge URI (priority order). Unmappable ACRs fall back to
        // AAL1 so the caller still gets a deterministic challenge.
        val targetAal = strongestRequestedAal(nonEmpty) ?: AuthAssuranceLevel.AAL1
        return OAuth2AcrEnforcementResult.Insufficient(
            requiredAal = targetAal,
            currentAal = currentAal,
            acrValues = nonEmpty.first(),
            maxAge = maxAge,
            reason = OAuth2AcrEnforcementResult.Insufficient.Reason.INSUFFICIENT_ACR,
        )
    }

    private fun strongestRequestedAal(requested: List<String>): AuthAssuranceLevel? = requested.mapNotNull { aalFor(it) }.maxByOrNull { it.ordinal }

    private fun aalFor(acr: String): AuthAssuranceLevel? =
        AuthAssuranceLevel.entries.firstOrNull { it.acr == acr }
            ?: when (acr) {
                SAML_PASSWORD_PROTECTED_TRANSPORT -> AuthAssuranceLevel.AAL1
                SAML_MOBILE_TWO_FACTOR_CONTRACT -> AuthAssuranceLevel.AAL2
                SAML_SMARTCARD_PKI -> AuthAssuranceLevel.AAL3
                else -> null
            }

    private fun acrFor(aal: AuthAssuranceLevel): String = aal.acr

    companion object {
        const val SAML_PASSWORD_PROTECTED_TRANSPORT: String =
            "urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport"
        const val SAML_MOBILE_TWO_FACTOR_CONTRACT: String =
            "urn:oasis:names:tc:SAML:2.0:ac:classes:MobileTwoFactorContract"
        const val SAML_SMARTCARD_PKI: String =
            "urn:oasis:names:tc:SAML:2.0:ac:classes:SmartcardPKI"
    }
}

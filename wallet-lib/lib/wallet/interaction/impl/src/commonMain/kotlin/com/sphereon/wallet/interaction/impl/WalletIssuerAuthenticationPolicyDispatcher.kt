/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletIssuerAuthenticationPolicy
import com.sphereon.wallet.interaction.WalletIssuerAuthenticationPolicyProvider
import com.sphereon.wallet.interaction.WalletProtocol

/** Dispatches exact issuer policies contributed by independently owned deployments. */
class WalletIssuerAuthenticationPolicyDispatcher(
    providers: Set<WalletIssuerAuthenticationPolicyProvider>,
) : WalletIssuerAuthenticationPolicyProvider {
    private val providers = providers.toList()

    override suspend fun policyFor(
        issuer: String,
        protocol: WalletProtocol,
    ): WalletIssuerAuthenticationPolicy? {
        val matches = providers.mapNotNull { it.policyFor(issuer, protocol) }
        return matches.singleOrNull()
    }
}

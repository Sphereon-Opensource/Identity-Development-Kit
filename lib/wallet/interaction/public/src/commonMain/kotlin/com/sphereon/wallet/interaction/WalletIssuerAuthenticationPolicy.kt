/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.wallet.interaction

import com.sphereon.crypto.core.jose.JwkType

/** Deployment-owned admission policy for issuer authentication material. */
data class WalletIssuerAuthenticationPolicy(
    val issuer: String,
    val protocol: WalletProtocol,
    val plans: List<WalletIssuerAuthenticationResolutionPlan>,
) {
    init {
        require(issuer.isNotBlank()) { "wallet_issuer_authentication_policy_issuer_blank" }
        require(plans.isNotEmpty()) { "wallet_issuer_authentication_policy_plans_empty" }
    }
}

/** Exact issuer/protocol lookup used before any external identifier resolution. */
fun interface WalletIssuerAuthenticationPolicyProvider {
    suspend fun policyFor(issuer: String, protocol: WalletProtocol): WalletIssuerAuthenticationPolicy?
}

/**
 * A resolver input explicitly admitted by deployment policy. The protocol adapters never construct
 * these plans from JWT headers or credential content.
 */
sealed interface WalletIssuerAuthenticationResolutionPlan {
    val issuer: String

    data class PinnedJwk(
        override val issuer: String,
        val jwk: JwkType,
        val reference: String = "pinned-jwk",
    ) : WalletIssuerAuthenticationResolutionPlan {
        init {
            require(issuer.isNotBlank()) { "wallet_issuer_authentication_plan_issuer_blank" }
            require(reference.isNotBlank()) { "wallet_issuer_authentication_plan_reference_blank" }
        }
    }

    data class PinnedJwks(
        override val issuer: String,
        val jwks: List<JwkType>,
        val reference: String = "pinned-jwks",
    ) : WalletIssuerAuthenticationResolutionPlan {
        init {
            require(issuer.isNotBlank()) { "wallet_issuer_authentication_plan_issuer_blank" }
            require(jwks.isNotEmpty()) { "wallet_issuer_authentication_plan_keys_empty" }
            require(reference.isNotBlank()) { "wallet_issuer_authentication_plan_reference_blank" }
        }
    }

    data class HttpsJwks(
        override val issuer: String,
        val url: String,
        val requestedKid: String? = null,
        val reference: String = url,
    ) : WalletIssuerAuthenticationResolutionPlan {
        init {
            require(issuer.isNotBlank()) { "wallet_issuer_authentication_plan_issuer_blank" }
            require(url.isNotBlank()) { "wallet_issuer_authentication_plan_url_blank" }
            require(reference.isNotBlank()) { "wallet_issuer_authentication_plan_reference_blank" }
        }
    }

    data class X5c(
        override val issuer: String,
        val chain: List<String>,
        val verify: Boolean,
        val trustAnchors: List<String>,
        val issuerBinding: String,
        val keyId: String? = null,
        val verificationTime: String? = null,
        val reference: String = "x5c",
    ) : WalletIssuerAuthenticationResolutionPlan {
        init {
            require(issuer.isNotBlank()) { "wallet_issuer_authentication_plan_issuer_blank" }
            require(chain.isNotEmpty()) { "wallet_issuer_authentication_plan_x5c_empty" }
            require(reference.isNotBlank()) { "wallet_issuer_authentication_plan_reference_blank" }
        }
    }
}

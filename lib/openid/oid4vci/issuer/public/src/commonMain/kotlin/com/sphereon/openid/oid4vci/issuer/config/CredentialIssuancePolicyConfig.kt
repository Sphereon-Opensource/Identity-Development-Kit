/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.openid.oid4vci.issuer.config

import kotlinx.serialization.Serializable

/**
 * Per-credential-configuration issuance policy.
 *
 * Loaded from ConfigService under the namespace:
 *   `sphereon.oid4vci.issuer.credentials.<credentialConfigurationId>`
 *
 * Properties (all have safe defaults so an unconfigured credential works out of the box):
 *
 * | Config key (relative to namespace) | Property | Default |
 * |---|---|---|
 * | `iae.enabled` | [iaeEnabled] | `false` |
 * | `iae.interaction-type` | [iaeInteractionType] | `urn:openid:dcp:iae:openid4vp_presentation` |
 * | `iae.dcql-query-id` | [iaeDcqlQueryId] | `null` |
 * | `grants.pre-authorized-code.allowed` | [preAuthorizedCodeAllowed] | `true` |
 * | `grants.pre-authorized-code.tx-code-required` | [txCodeRequired] | `false` |
 * | `grants.authorization-code.allowed` | [authorizationCodeAllowed] | `true` |
 * | `nonce.ttl-seconds` | [nonceTtlSeconds] | `300` |
 * | `deferred.retry-interval-seconds` | [deferredRetryIntervalSeconds] | `5` |
 * | `encryption.response-required` | [encryptionResponseRequired] | `false` |
 */
@Serializable
data class CredentialIssuancePolicyConfig(
    /** Whether Identity Assurance Evidence (IAE) is required for this credential type. */
    val iaeEnabled: Boolean = false,
    /**
     * IAE interaction type URN. Defaults to OpenID4VP presentation.
     * Must be one of the URNs defined in OID4VCI 1.1 Section 6.1.
     */
    val iaeInteractionType: String = "urn:openid:dcp:iae:openid4vp_presentation",
    /**
     * Optional DCQL query ID to use when constructing the IAE VP request.
     * When null, the verifier service uses a minimal default query.
     */
    val iaeDcqlQueryId: String? = null,
    /** Whether the pre-authorized_code grant is permitted for this credential type. */
    val preAuthorizedCodeAllowed: Boolean = true,
    /** Whether a transaction code (tx_code) is required for the pre-authorized_code grant. */
    val txCodeRequired: Boolean = false,
    /** Whether the authorization_code grant is permitted for this credential type. */
    val authorizationCodeAllowed: Boolean = true,
    /** Lifetime of c_nonce values issued for this credential type, in seconds. */
    val nonceTtlSeconds: Long = 300,
    /** Retry interval hint returned in deferred credential responses, in seconds. */
    val deferredRetryIntervalSeconds: Int = 5,
    /** Whether credential response encryption is required from the wallet. */
    val encryptionResponseRequired: Boolean = false,
) {
    companion object {
        /** Config namespace prefix. Append `.<credentialConfigurationId>` to form the full prefix. */
        const val CONFIG_NAMESPACE = "oid4vci.issuer.credentials"
    }
}

/**
 * Resolves [CredentialIssuancePolicyConfig] for a given credential configuration ID.
 *
 * Implementations read policy from ConfigService; callers fall back to defaults when no
 * implementation is present (the interface is injected as optional).
 */
interface CredentialIssuancePolicyResolver {
    /**
     * Resolves the issuance policy for [credentialConfigurationId].
     *
     * Never throws — returns a config with all defaults when no properties are configured.
     */
    suspend fun resolve(credentialConfigurationId: String): CredentialIssuancePolicyConfig
}

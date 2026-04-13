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

import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialRequestEncryption
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode

/**
 * Configuration provider for the OID4VCI issuer.
 *
 * Supplies credential configurations and issuer display properties.
 * In Phase 8, this will be backed by the credential-design store.
 */
interface Oid4vciIssuerConfigProvider {
    val issuerIdentifier: String
    val credentialConfigurations: Map<String, CredentialConfigurationSupported>
    val authorizationServers: List<String>?
    val display: List<DisplayProperties>?

    /**
     * Optional signing key for producing signed issuer metadata (OID4VCI 1.1 Section 13.2).
     *
     * When set, the well-known metadata endpoint:
     * - Returns a signed JWT when the client sends `Accept: application/jwt`
     * - Populates the `signed_metadata` field on JSON responses
     *
     * When null, JWT responses return 406 Not Acceptable and JSON responses omit `signed_metadata`.
     */
    val signingKey: ManagedIdentifierOptsOrResult?
        get() = null

    /**
     * OID4VCI 1.1 top-level credential response encryption metadata.
     *
     * Declares what encryption algorithms the issuer supports for encrypting credential responses.
     * When null, the metadata omits the `credential_response_encryption` field.
     */
    val credentialResponseEncryption: MetadataCredentialResponseEncryption?
        get() = null

    /**
     * OID4VCI 1.1 credential request encryption metadata.
     *
     * Declares the issuer's encryption key and supported algorithms for receiving encrypted requests.
     * When null, the metadata omits the `credential_request_encryption` field.
     */
    val credentialRequestEncryption: MetadataCredentialRequestEncryption?
        get() = null

    /**
     * OID4VCI 1.1 batch credential issuance metadata.
     *
     * Declares the maximum batch size for batch credential issuance.
     * When null, the metadata omits the `batch_credential_issuance` field.
     */
    val batchCredentialIssuance: BatchCredentialIssuance?
        get() = null

    /**
     * Per-credential signing configuration (key alias, key reference mode, cert chain path).
     *
     * Keyed by credential configuration ID. Used at issuance time to resolve the signing key
     * and populate the JWT protected header with the appropriate identifier (kid, x5c, etc.).
     */
    val credentialSigningConfigs: Map<String, CredentialSigningConfig>
        get() = emptyMap()

    /**
     * All KMS aliases this issuer actively signs with — union of per-credential signing
     * keys and the top-level issuer-metadata signing key (when configured). Falls back
     * to the credential configuration ID when a credential has no explicit alias.
     *
     * Consumed by the SD-JWT VC Issuer Metadata endpoint (`/.well-known/jwt-vc-issuer`)
     * to build the published JWKS.
     */
    val signingKeyAliases: Set<String>
        get() {
            val perCredential =
                credentialConfigurations.keys.map { configId ->
                    credentialSigningConfigs[configId]?.signingKeyAlias ?: configId
                }
            return (perCredential + listOfNotNull(metadataSigningKeyAlias))
                .filter { it.isNotBlank() }
                .toSet()
        }

    /**
     * Alias of the key used to sign issuer metadata (OID4VCI §13.2.4), when configured.
     * Separate from [signingKey] because the bridge to KMS-alias-based resolution happens
     * in the provider implementation. Null when metadata signing isn't enabled.
     */
    val metadataSigningKeyAlias: String?
        get() = null

    /**
     * Issuer-wide clock-skew tolerance, in seconds. The `iat` claim (and `nbf`, when
     * emitted) on issued credentials is shifted backward by this many seconds from
     * the issuer's wall-clock. This accommodates wallets whose clocks are slightly
     * ahead — without the skew they would reject a just-issued credential as "not
     * yet valid".
     *
     * YAML: `sphereon.oid4vci.issuer.issuance-clock-skew-in-seconds` (kebab-case).
     * Default: 60.
     */
    val issuanceClockSkewInSeconds: Long
        get() = 60L
}

/**
 * Signing configuration for a single credential type.
 *
 * @property signingKeyAlias KMS key alias for credential signing. When null, defaults to the credential configuration ID.
 * @property signingKeyMode Key reference mode determining how the signing key is identified in the JWT header.
 * @property signingCertChainPath Optional PEM file path for X.509 certificate chain (fallback when KMS key has no x5c).
 */
data class CredentialSigningConfig(
    val signingKeyAlias: String? = null,
    val signingKeyMode: SigningKeyMode = SigningKeyMode.None,
    val signingCertChainPath: String? = null,
    /**
     * Validity window of issued credentials of this configuration, expressed in days.
     * When non-null the format handler emits an `exp` claim at `iat + days × 86400`.
     * When null (the default) no `exp` claim is emitted — per SD-JWT VC §3.2.2 and
     * RFC 7519 §4.1.4, absence of `exp` means the credential has no expiration.
     *
     * YAML: `sphereon.oid4vci.issuer.credentials.[<id>].expiration-in-days` (kebab-case).
     */
    val expirationInDays: Int? = null,
)

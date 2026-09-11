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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.BatchCredentialIssuance
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialRequestEncryption
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialResponseEncryption
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.statuslist.StatusListBinding
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Configuration provider for the OID4VCI issuer.
 *
 * Supplies credential configurations and issuer display properties.
 * In Phase 8, this will be backed by the credential-design store.
 */
@JsExportCompat
interface Oid4vciIssuerConfigProvider {
    val issuerIdentifier: String

    /**
     * Optional protocol version configured for this issuer. If not configured, the issuer defaults
     * to OID4VCI 1.0. The version drives derived format shapes, including the SD-JWT VC type
     * metadata draft paired with that OID4VCI version.
     */
    val oid4vciSpecVersion: Oid4vciSpecVersion
        get() = Oid4vciSpecVersion.V1_0

    /**
     * Resolved protocol profile that controls wire shapes derived by this issuer.
     *
     * OID4VCI 1.0 Final normatively references SD-JWT VC draft-ietf-oauth-sd-jwt-vc-11,
     * so VCT metadata produced under the default profile uses that draft's `lang` fields,
     * `claims[].sd`, and simple rendering shape. New OID4VCI / SD-JWT VC revisions must be
     * added as explicit profiles before changing emitted metadata.
     */
    val specProfile: Oid4vciIssuerSpecProfile
        get() = Oid4vciIssuerSpecProfile.forVersion(oid4vciSpecVersion)

    /**
     * Hook called by the metadata endpoint (and any other read-path that needs a fully-built
     * view) **before** reading [credentialConfigurations]. The default is a no-op so existing
     * providers that never needed a build step require no change. Providers that build their
     * configuration lazily (e.g. [HybridOid4vciIssuerConfigProvider]) override this to trigger
     * the build exactly once per session.
     */
    @JsExportIgnoreCompat
    suspend fun prepare() {}

    @JsExportIgnoreCompat
    val credentialConfigurations: Map<String, CredentialConfigurationSupported>
    /** Stable authorization-server override configured for one credential configuration. */
    @OptIn(ExperimentalUuidApi::class)
    @JsExportIgnoreCompat
    fun credentialAuthorizationServerId(credentialConfigurationId: String): Uuid? = null
    @JsExportIgnoreCompat
    fun credentialAuthorizationServerAllowedGrants(credentialConfigurationId: String): Set<com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationGrant>? = null

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
     * Suspending because a deployment that manages key material centrally resolves the key from its
     * own server-side binding (see
     * [com.sphereon.openid.oid4vci.issuer.spi.IssuerKeyNameResolver]) rather than from a
     * configuration value.
     */
    @JsExportIgnoreCompat
    suspend fun signingKey(): ManagedIdentifierOptsOrResult? = null

    /**
     * OID4VCI 1.1 top-level credential response encryption metadata.
     *
     * Declares what encryption algorithms the issuer supports for encrypting credential responses.
     * When null, the metadata omits the `credential_response_encryption` field.
     */
    val credentialResponseEncryption: MetadataCredentialResponseEncryption?
        get() = null

    /**
     * OID4VCI 1.0 §11.2.4 credential request encryption metadata.
     *
     * Declares the issuer's encryption key and supported algorithms for receiving encrypted
     * requests. The `jwks` field on the returned object is allowed to be a placeholder
     * (`{"keys": []}`); the metadata builder resolves [credentialRequestDecryptionKey] from
     * KMS at runtime and replaces the placeholder with the real public JWK so we never have
     * to store key material in YAML / git.
     *
     * When null, the metadata omits the `credential_request_encryption` field entirely.
     */
    val credentialRequestEncryption: MetadataCredentialRequestEncryption?
        get() = null

    /**
     * KMS-managed identifier for the issuer's request-decryption keypair (ECDH-ES P-256). The
     * public half is published in `credential_request_encryption.jwks` (with
     * `kid = keyInfo.kid`) so wallets can encrypt credential-request bodies to it; the private
     * half stays in the KMS and is resolved by `kid` when an encrypted credential request
     * arrives.
     *
     * Mirrors the [signingKey] pattern: the config layer returns unresolved opts; consumers
     * resolve them at use time via `MultiManagedIdentifierService` because the resolution is
     * suspending and can fail. Returns null when the issuer doesn't advertise request
     * encryption, and also when a deployment that manages key material centrally has no usable
     * binding for the request-decryption key — the field is then omitted from metadata and an
     * encrypted request is refused, identically for every unusable binding shape.
     */
    @JsExportIgnoreCompat
    suspend fun credentialRequestDecryptionKey(): ManagedIdentifierOptsOrResult? = null

    /**
     * OID4VCI 1.1 batch credential issuance metadata.
     *
     * Declares the maximum batch size for batch credential issuance.
     * When null, the metadata omits the `batch_credential_issuance` field.
     */
    val batchCredentialIssuance: BatchCredentialIssuance?
        get() = null

    /**
     * Optional key-storage-status refresh period advertised in Credential Issuer metadata when
     * production key-attestation evidence is expected. The value is seconds and maps to
     * `preferred_key_storage_status_period`.
     */
    val preferredKeyStorageStatusPeriodSeconds: Int?
        get() = null

    /**
     * Per-credential signing configuration (key alias, key reference mode, resolved x5c chain).
     *
     * Keyed by credential configuration ID. Used at issuance time to resolve the signing key
     * and populate the JWT protected header with the appropriate identifier (kid, x5c, etc.).
     */
    @JsExportIgnoreCompat
    suspend fun credentialSigningConfigs(): Map<String, CredentialSigningConfig> = emptyMap()

    /**
     * Per-credential binding to a status list defined in the standalone, protocol-neutral
     * `statuslists` namespace (see `StatusListDefinitionsProvider`) — status lists are NOT an OID4VCI
     * concept. When present (and a `CredentialStatusEnricher` is on the classpath), issuance allocates
     * a status entry and embeds the status claim into that credential before signing. Keyed by
     * credential configuration ID; the value's `statusListCorrelationId` references the hosted list.
     */
    @JsExportIgnoreCompat
    val statusListBindings: Map<String, StatusListBinding>
        get() = emptyMap()

    /**
     * Fail-closed binding resolution for issuance. A credential configuration bound to a status
     * list must never issue without its status claim — a credential issued without one can never
     * be revoked. So unlike [statusListBindings] (which only exposes resolvable bindings), this
     * distinguishes the three cases the issuance path must handle:
     * - `Ok(null)` — the configuration declares no status list; issuance proceeds without a
     *   status claim.
     * - `Ok(binding)` — the binding resolved; issuance must embed the status claim before signing.
     * - `Err` — the configuration declares a status list but the binding cannot be resolved
     *   (definitions source missing, unknown list id); issuance must abort.
     */
    @JsExportIgnoreCompat
    fun statusListBindingFor(credentialConfigId: String): IdkResult<StatusListBinding?, IdkError> = Ok(statusListBindings[credentialConfigId])

    /** Suspend-aware resolution used by issuance when definitions may be backed by persistence. */
    @JsExportIgnoreCompat
    suspend fun statusListBindingForIssuance(credentialConfigId: String): IdkResult<StatusListBinding?, IdkError> =
        statusListBindingFor(credentialConfigId)

    /**
     * Per-credential, per-proof-carrier trust configuration for OID4VCI 1.0 §7.2 key
     * attestations. The outer key is the credential configuration ID; the inner key is the
     * `proof_type` value (`"jwt"`, `"attestation"`, …) — same nesting level as the
     * `key_attestations_required` policy in `proof_types_supported`, so the operator
     * configures policy and trust together inside one block per proof carrier.
     *
     * Each entry carries:
     * The verifier material is selected from the persisted Trust Domain attachment and is never
     * read from YAML, filesystem paths, or a global fallback store.
     *
     * Missing persisted attachment or verifier material fails closed.
     */
    @JsExportIgnoreCompat
    suspend fun walletProviderTrustFor(
        args: ResolveWalletProviderTrustArgs,
    ): IdkResult<ResolvedWalletProviderTrust, IdkError> =
        com.sphereon.core.api.Err(
            IdkError.fromString(
                code = "wallet_provider_trust_not_resolved",
                message = "Wallet-provider trust is not resolved for credential configuration '${args.credentialConfigurationId}'",
            ),
        )

    /**
     * All KMS key names this issuer actively signs with — union of the resolved per-credential
     * signing keys and the issuer-metadata signing key.
     *
     * Consumed by the SD-JWT VC Issuer Metadata endpoint (`/.well-known/jwt-vc-issuer`) to build the
     * published JWKS. A credential configuration whose key does not resolve contributes nothing: the
     * configuration id is caller-visible, so publishing it as a key name would advertise, and let a
     * signer select, whatever key material happened to sit at that name.
     */
    @JsExportIgnoreCompat
    suspend fun signingKeyNames(): Set<String> {
        val signingConfigs = credentialSigningConfigs()
        val perCredential = credentialConfigurations.keys.mapNotNull { configId -> signingConfigs[configId]?.signingKeyAlias }
        return (perCredential + listOfNotNull(metadataSigningKeyName()))
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Name of the key used to sign issuer metadata (OID4VCI §13.2.4), and the default the issuer's
     * credentials sign with. Separate from [signingKey] because the bridge to KMS name-based
     * resolution happens in the provider implementation.
     *
     * Null when no usable key is available. A deployment that manages key material centrally
     * resolves this from its own server-side binding (see
     * [com.sphereon.openid.oid4vci.issuer.spi.IssuerKeyNameResolver]); the name is
     * process-internal and must never reach a DTO or a REST response.
     */
    @JsExportIgnoreCompat
    suspend fun metadataSigningKeyName(): String? = null

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

    /**
     * Governs the credential endpoint's §6.1 completeness gate when a completeness verdict
     * reports missing required claims and the binding carries no explicit deferral policy of its
     * own (see [com.sphereon.openid.oid4vci.issuer.lifecycle.Oid4vciCompletenessLifecycleResult.missingRequiredClaims]).
     *
     * - [MissingRequiredClaimsPolicy.REJECT] (the default) fails the credential request with
     *   `invalid_credential_request` — a credential is never issued with a required claim absent.
     * - [MissingRequiredClaimsPolicy.DEFER] instead routes the request down the same deferred
     *   (`transaction_id`) response path used when a binding's own deferral policy is enabled.
     *
     * YAML: `sphereon.oid4vci.issuer.missing-required-claims` (kebab-case). Default: `reject`.
     */
    val missingRequiredClaims: MissingRequiredClaimsPolicy
        get() = MissingRequiredClaimsPolicy.REJECT
}

/**
 * Policy for the credential endpoint's completeness gate when required claims are missing and
 * the binding carries no explicit deferral policy. See [Oid4vciIssuerConfigProvider.missingRequiredClaims].
 */
@JsExportCompat
enum class MissingRequiredClaimsPolicy {
    REJECT,
    DEFER,
}

@JsExportCompat
enum class Oid4vciSpecVersion(
    val value: String,
) {
    V1_0("1.0"),
    V1_1("1.1");

    companion object {
        fun parse(value: String?): Oid4vciSpecVersion =
            when (value?.trim()?.lowercase()) {
                null, "", "1", "1.0", "v1.0", "oid4vci-1.0" -> V1_0
                else -> throw IllegalArgumentException("Unsupported OID4VCI issuer spec version '$value'")
            }
    }
}

@JsExportCompat
@Serializable
enum class Oid4vciIssuerSpecProfile(
    val version: Oid4vciSpecVersion,
    val oid4vciSpec: String,
    val sdJwtVcSpec: SdJwtVcSpecProfile,
    /** OID4VCI 1.0 Final requires `alg`; the pinned 1.1 draft permits JWK-derived agreement. */
    val credentialResponseEncryptionAlgorithmRequired: Boolean,
    /** `credential_response_encryption.zip` is introduced by the pinned 1.1 draft. */
    val credentialResponseEncryptionCompressionAllowed: Boolean,
) {
    OID4VCI_1_0_FINAL(
        version = Oid4vciSpecVersion.V1_0,
        oid4vciSpec = "openid-4-verifiable-credential-issuance-1_0-final",
        sdJwtVcSpec = SdJwtVcSpecProfile.DRAFT_11,
        credentialResponseEncryptionAlgorithmRequired = true,
        credentialResponseEncryptionCompressionAllowed = false,
    ),
    OID4VCI_1_1_DRAFT_2A1F0513(
        version = Oid4vciSpecVersion.V1_1,
        oid4vciSpec = "openid-4-verifiable-credential-issuance-1_1-2a1f0513",
        sdJwtVcSpec = SdJwtVcSpecProfile.DRAFT_11,
        credentialResponseEncryptionAlgorithmRequired = false,
        credentialResponseEncryptionCompressionAllowed = true,
    );

    companion object {
        fun forVersion(version: Oid4vciSpecVersion): Oid4vciIssuerSpecProfile = entries.single { it.version == version }

        fun parse(value: String): Oid4vciIssuerSpecProfile =
            entries.singleOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unsupported OID4VCI issuer profile '$value'")
    }
}

@JsExportCompat
enum class SdJwtVcSpecProfile(
    val draft: String,
) {
    DRAFT_11("draft-ietf-oauth-sd-jwt-vc-11"),
}

/**
 * Signing configuration for a single credential type.
 *
 * @property signingKeyAlias Server-resolved KMS key name this credential signs under. Null means the
 *   key is unavailable and issuance of this credential is refused; nothing is derived from the
 *   credential configuration id and no key is created. The name is process-internal and must never
 *   reach a DTO or a REST response.
 * @property signingKeyMode Key reference mode determining how the signing key is identified in the JWT header.
 * @property signingX5c Optional resolved X.509 certificate chain in RFC 7517 x5c encoding. The
 *   strings are standard-base64 DER certificates, as required by JOSE/COSE; PEM and DER parsing
 *   is performed by the common certificate utilities before this boundary.
 * @property dataIntegrityCryptosuite Explicit cryptosuite used for `ldp_vc` Data Integrity issuance.
 */
@JsExportCompat
data class CredentialSigningConfig(
    val signingKeyAlias: String? = null,
    val signingKeyMode: SigningKeyMode = SigningKeyMode.None,
    /** Exact assertionMethod verification-method URI selected for this key. */
    val signingVerificationMethodId: String? = null,
    val signingX5c: Array<String>? = null,
    val dataIntegrityCryptosuite: String? = null,
    /**
     * Validity window of issued credentials of this configuration, expressed in days.
     * When non-null, handlers derive an absent semantic end from the data-validity start plus
     * `days * 86400`. VCDM 1.1 emits that end as both `expirationDate` and NumericDate `exp`;
     * VCDM 2.0 retains `validUntil` in the VC payload while `exp` remains a separate
     * signature-lifetime policy, currently aligned to the same configured duration.
     * When null (the default) no `exp` claim is emitted, so no signature expiration is configured.
     *
     * YAML: `sphereon.oid4vci.issuer.credentials.[<id>].expiration-in-days` (kebab-case).
     */
    val expirationInDays: Int? = null,
)

/**
 * Trust configuration for OID4VCI 1.0 §7.2 key-attestation JWTs scoped to a single
 * credential configuration.
 *
 * The verifier resolves the attester key in this priority order:
 * 1. `x5c` header → validate chain via `X509TrustValidationService` against the union
 *    of [x509TrustAnchorPaths] and the globally loaded `lib/trust/x509` anchors.
 * 2. `kid` / `jwk` header → match against typed persisted JWK verifier material.
 *
 * Missing persisted trust material is never inferred or replaced by a process-wide store.
 *
 * This material is never sourced from configuration files.
 */

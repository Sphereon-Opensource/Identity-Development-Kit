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

package com.sphereon.openid.oid4vp.verifier.requesturi

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.qualifyDidJarVerificationMethodId
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Handler for request_uri fetches.
 *
 * Generates signed JARs on-demand from stored session data.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RequestUriHandler", exact = true)
interface RequestUriHandler {
    suspend fun handleGet(requestUriPath: String): IdkResult<RequestUriResponse, IdkError>

    suspend fun handlePost(
        requestUriPath: String,
        walletMetadata: String? = null,
        walletNonce: String? = null,
    ): IdkResult<RequestUriResponse, IdkError>
}

/**
 * Configuration for JAR signing when serving request_uri.
 *
 * Not exported to JS as it contains non-exportable crypto key types.
 */

interface RequestObjectSigningConfig {
    suspend fun resolveSigningKey(): KeyInfoType<*>

    val audience: String
    val expirationSeconds: Long

    /**
     * Whether JAR signing is enabled. When false, the request object is returned
     * as plain JSON (application/json) instead of a signed JWT (application/oauth-authz-req+jwt).
     * Defaults to true for backwards compatibility.
     */
    val enabled: Boolean get() = false

    /**
     * Whether the signed Request Object should include a JWT `iss` claim. Defaults to
     * `false`.
     *
     * OID4VP 1.0 §5.6: `client_id` is required; `iss` MAY be present for JAR interop
     * but **wallets MUST ignore `iss`**. The §5.9.3 DID example omits `iss`. When this
     * flag is enabled for RFC 9101 peers that still require `iss`, the value is the
     * binding's [VerifierSignerBinding.bareIdentifier] (e.g. bare DID), never a
     * substitute for `client_id` identity.
     */
    val includeIss: Boolean get() = false

    /**
     * How the verifier self-identifies in the signed JAR. Per OID4VP 1.0 §5.9.3,
     * **`client_id` with its Client Identifier Prefix is the sole client identity /
     * authentication path** for request-object verification — not JOSE `kid`, not
     * JWT `iss`, and not credential-issuer claims.
     *
     * The binding drives:
     *   - outer / JAR `client_id` (prefixed, e.g. `decentralized_identifier:did:jwk:...`)
     *   - JOSE header material required by that prefix (`kid` = absolute DID URL for
     *     `decentralized_identifier`; `x5c` for `x509_*`)
     *   - optional JAR `iss` (bare identifier only; wallets must ignore it)
     *
     * The optional [scheme] parameter requests a specific Client Identifier Prefix
     * (e.g. did:jwk vs x509_hash). When null, implementations use the deployment
     * default. Implementations MAY share one key + cert across bindings (JOSE header
     * switches between `kid` and `x5c`).
     *
     * When signing is enabled and binding resolution fails, implementations MUST
     * throw — never silently emit an HTTPS / `redirect_uri` `client_id`.
     *
     * Default: null.
     */
    suspend fun resolveSignerBinding(scheme: ClientIdScheme? = null): VerifierSignerBinding? = null

    companion object {
        /**
         * Creates a disabled signing config that returns request objects as plain JSON.
         * Use this when JAR signing is not required (e.g., development, or wallets
         * that accept unsigned request objects).
         */
        fun disabled(expirationSeconds: Long = 300): RequestObjectSigningConfig = DisabledRequestObjectSigningConfig(expirationSeconds)
    }
}

/**
 * A [RequestObjectSigningConfig] that disables JAR signing.
 * The signing key and audience are not used.
 */

private class DisabledRequestObjectSigningConfig(
    override val expirationSeconds: Long,
) : RequestObjectSigningConfig {
    override val enabled: Boolean = false

    override suspend fun resolveSigningKey(): KeyInfoType<*> = throw UnsupportedOperationException("JAR signing is disabled")

    override val audience: String get() = throw UnsupportedOperationException("JAR signing is disabled")
}

/**
 * How the verifier authenticates the signed JAR to the wallet per OID4VP 1.0 §5.9.3.
 *
 * Each variant determines: the OID4VP Client Identifier Prefix (the string before `:`
 * in `client_id`), the identifier that follows, and which JOSE header the signer
 * attaches to the JAR (`kid` vs `x5c`).
 */
sealed class VerifierSignerBinding {
    /** Prefix + identifier combined, e.g. `decentralized_identifier:did:jwk:eyJr...`. */
    abstract val clientId: String

    /** Corresponding [ClientIdScheme] enum. */
    abstract val scheme: ClientIdScheme

    /**
     * The bare identifier without the §5.9.3 prefix (e.g. `did:jwk:...` rather than
     * `decentralized_identifier:did:jwk:...`, or the DNS name rather than
     * `x509_san_dns:<dnsName>`). Used when resolving DID Documents / cert SANs and as
     * the optional JAR `iss` value. Wallet authentication still keys off [clientId].
     */
    abstract val bareIdentifier: String

    /**
     * §5.9.3 `decentralized_identifier`: `client_id` is `decentralized_identifier:<did>`;
     * the request MUST be signed with a key from that DID Document. JOSE `kid` identifies the
     * verification method — preferably the absolute DID URL (spec example: `did:example:123#1`),
     * but a document-relative fragment (`#1`) is also accepted and qualified against the DID
     * from `client_id` on both mint and verify.
     *
     * @param did the bare DID (no fragment), e.g. `did:jwk:eyJr...`
     * @param verificationMethodId absolute DID URL or relative `#fragment` used as JOSE `kid`
     */
    data class Did(
        val did: String,
        val verificationMethodId: String,
    ) : VerifierSignerBinding() {
        init {
            require(qualifyDidJarVerificationMethodId(did, verificationMethodId) != null) {
                "OID4VP decentralized_identifier JAR kid must be an absolute DID URL rooted in '$did' " +
                    "or a relative fragment '#…' (got '$verificationMethodId')"
            }
        }

        override val clientId: String = "${ClientIdScheme.DECENTRALIZED_IDENTIFIER.prefix}:$did"
        override val scheme: ClientIdScheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER
        override val bareIdentifier: String = did

        /** Absolute verification-method DID URL (relative kids qualified against [did]). */
        val absoluteVerificationMethodId: String =
            qualifyDidJarVerificationMethodId(did, verificationMethodId)!!
    }

    /**
     * §5.9.3 `x509_san_dns`: the original identifier MUST be a DNS name that matches a
     * dNSName SAN entry in the leaf X.509 certificate; JAR is signed with that cert's key
     * and the chain goes in the `x5c` JOSE header.
     *
     * @param dnsName DNS name (matches a leaf-cert SAN entry)
     * @param certificateChain base64-encoded DER certs (leaf first), emitted as `x5c`
     */
    data class X509SanDns(
        val dnsName: String,
        val certificateChain: List<String>,
    ) : VerifierSignerBinding() {
        override val clientId: String = "${ClientIdScheme.X509_SAN_DNS.prefix}:$dnsName"
        override val scheme: ClientIdScheme = ClientIdScheme.X509_SAN_DNS
        override val bareIdentifier: String = dnsName
    }

    /**
     * §5.9.3 `x509_hash`: the original identifier MUST be the base64url SHA-256 hash of the
     * DER-encoded leaf certificate. Signer and `x5c` emission identical to [X509SanDns].
     *
     * @param certificateHash base64url SHA-256 of the leaf cert's DER bytes
     * @param certificateChain base64-encoded DER certs (leaf first), emitted as `x5c`
     */
    data class X509Hash(
        val certificateHash: String,
        val certificateChain: List<String>,
    ) : VerifierSignerBinding() {
        override val clientId: String = "${ClientIdScheme.X509_HASH.prefix}:$certificateHash"
        override val scheme: ClientIdScheme = ClientIdScheme.X509_HASH
        override val bareIdentifier: String = certificateHash
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("RequestUriResponse", exact = true)
@JsExportCompat
@Serializable
data class RequestUriResponse(
    val signedJar: String,
    val contentType: String = CONTENT_TYPE_JAR,
    val sessionId: String,
    val expiresAt: Long,
) {
    companion object {
        /**
         * Per OID4VP §5.10.1 and RFC 9101 §3.2, the signed request object response MUST use
         * `application/oauth-authz-req+jwt`. Wallets that reject this value are non-compliant.
         */
        const val CONTENT_TYPE_JAR = "application/oauth-authz-req+jwt"
        const val CONTENT_TYPE_JSON = "application/json"
    }
}

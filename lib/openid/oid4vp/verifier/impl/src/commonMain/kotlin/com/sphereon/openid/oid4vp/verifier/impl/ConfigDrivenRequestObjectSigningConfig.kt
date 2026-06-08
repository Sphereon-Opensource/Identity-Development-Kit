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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierService
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestObjectSigningConfig>())
class ConfigDrivenRequestObjectSigningConfig(
    private val execution: SessionExecution,
    private val managedIdentifierService: ManagedIdentifierService,
    private val kms: KeyManagerService,
    private val didProviderRegistry: DidProviderRegistry,
) : RequestObjectSigningConfig {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    override val enabled: Boolean
        get() = configService.getProperty("$NAMESPACE.signing.enabled", Boolean::class) ?: false

    override val includeIss: Boolean
        get() = configService.getProperty("$NAMESPACE.signing.include-iss", Boolean::class) ?: false

    override val audience: String
        get() = configService.getPropertyAsString("$NAMESPACE.signing.audience") ?: ""

    override val expirationSeconds: Long
        get() =
            configService.getProperty("$NAMESPACE.signing.expiration.seconds", Long::class)
                ?: DEFAULT_EXPIRATION_SECONDS

    override suspend fun resolveSigningKey(): KeyInfoType<*> {
        val alias = requireKeyAlias()
        ensureSigningKey(alias)
        val opts = ManagedOptsAlias(identifier = alias)
        val result = managedIdentifierService.resolve(opts)
        check(result.isOk) { "Failed to resolve signing key '$alias': ${result.error}" }
        @Suppress("UNCHECKED_CAST")
        return result.value as KeyInfoType<KeyType>
    }

    /**
     * Get-or-generate the signing key under [alias]. The DID/x509 binding is derived
     * deterministically from this key's public JWK, and the request_uri JAR is signed
     * with it on every fetch — so create-time client_id substitution
     * (CreateAuthorizationRequestCommandImpl) and fetch-time signing (RequestUriHandlerImpl)
     * resolve to the SAME identifier only if the key is stable. Generating once and reusing
     * by alias guarantees that.
     *
     * Generated with ES256 (P-256) which is what the verifier advertises in client_metadata
     * (`sd-jwt_alg_values`/`kb-jwt_alg_values` = ES256) and what did:jwk wallets resolve.
     * A no-op when the alias already exists.
     */
    private suspend fun ensureSigningKey(alias: String) {
        val existing = kms.getKeyResult(KeyInfo<Nothing>(alias = alias))
        if (existing.isOk && existing.value.key != null) {
            return
        }
        val generated =
            kms.generateKeyResult(
                // Pin the provider explicitly. With KMS routed to a remote crypto service the
                // alg-only lookup (getKmsBySignatureAlgorithm) can miss because the remote
                // registry advertises its providers by id, not by a queryable alg set over the
                // wire — pass the configured provider id so the software KMS is selected directly.
                providerId = configService.getPropertyAsString("$NAMESPACE.signing.providerId") ?: DEFAULT_PROVIDER_ID,
                alias = alias,
                alg = SignatureAlgorithm.ECDSA_SHA256,
                keyVisibility = KeyVisibility.PRIVATE,
            )
        check(generated.isOk) {
            "Request-object signing is enabled but the signing key '$alias' could not be " +
                "resolved or generated in the KMS: ${generated.error.message.defaultMessage}"
        }
    }

    /**
     * Produce the verifier's client-identity binding per OID4VP 1.0 §5.9.3.
     *
     * - Returns null when signing is disabled.
     * - When [scheme] is non-null, build the binding for that scheme regardless of the
     *   verifier-side default. Same KMS alias / cert / key serves all schemes — only the
     *   prefix and JOSE header differ. This lets callers (the universal command, the
     *   demo's verifier UI) pick a binding per request.
     * - When [scheme] is null, fall back to the deployment-configured default mode at
     *   `$NAMESPACE.signing.mode` (legacy behaviour).
     *
     * When signing is enabled, this MUST succeed — any configuration error throws (no
     * silent fallback to the HTTPS client_id, which §5.9.3 forbids signing under anyway).
     */
    override suspend fun resolveSignerBinding(scheme: ClientIdScheme?): VerifierSignerBinding? {
        if (!enabled) return null
        val alias = requireKeyAlias()
        return when (scheme) {
            ClientIdScheme.DECENTRALIZED_IDENTIFIER -> {
                // Default did method to `jwk` if the caller didn't pin one via config.
                val configuredMode = configService.getPropertyAsString("$NAMESPACE.signing.mode")
                val didMethod =
                    if (configuredMode != null && configuredMode.startsWith("did:")) {
                        configuredMode.removePrefix("did:")
                    } else {
                        "jwk"
                    }
                buildDidBinding(alias, method = didMethod)
            }

            ClientIdScheme.X509_SAN_DNS -> {
                buildX509SanDnsBinding(alias)
            }

            ClientIdScheme.X509_HASH -> {
                buildX509HashBinding(alias)
            }

            null -> {
                // No scheme requested — use the deployment-configured default.
                val mode = configService.getPropertyAsString("$NAMESPACE.signing.mode") ?: DEFAULT_MODE
                when {
                    mode.startsWith("did:") -> buildDidBinding(alias, method = mode.removePrefix("did:"))
                    mode == "x509_san_dns" -> buildX509SanDnsBinding(alias)
                    mode == "x509_hash" -> buildX509HashBinding(alias)
                    else -> error("Unsupported $NAMESPACE.signing.mode='$mode' (expected did:<method>, x509_san_dns, or x509_hash)")
                }
            }

            else -> {
                error(
                    "Unsupported client_id_scheme '$scheme' for verifier JAR signing. " +
                        "Supported: decentralized_identifier (did:jwk), x509_san_dns, x509_hash.",
                )
            }
        }
    }

    /**
     * Build a DID-based signer binding. The verification-method URL used as the JOSE
     * `kid` is resolved with this priority:
     *
     *   1. `$NAMESPACE.signing.verification-method-id` — full DID URL (absolute override).
     *   2. `$NAMESPACE.signing.verification-method-fragment` — fragment only, prepended
     *      with `#` and appended to the resolved DID (e.g. `fragment: 0` on `did:jwk:abc`
     *      → `did:jwk:abc#0`). Useful for did:jwk where the fragment is always `0`.
     *   3. The first verification method returned by the DID provider whose purpose set
     *      includes `authentication`.
     *
     * No `#0` fallback — that assumption is only valid for did:jwk, and even there the
     * caller should opt in explicitly via config. If neither a config override nor a
     * provider-supplied VM exists, we fail loudly so deployments never emit a
     * fabricated kid.
     */
    private suspend fun buildDidBinding(
        alias: String,
        method: String
    ): VerifierSignerBinding.Did {
        val jwk = loadJwk(alias)
        val provider =
            didProviderRegistry.getProvider(method)
                ?: error("No DID provider registered for method '$method' — cannot derive verifier client_id")
        // did:web / did:webvh bind the key to a document hosted at a host; the DID is not derived
        // from the key, so the host must be supplied. It is the host of the verifier's public base URL.
        val domain =
            if (method in WEB_RESOLVED_METHODS) {
                resolveDidWebDomain()
                    ?: error(
                        "did:$method signing requires a host: set '$DID_WEB_DOMAIN_KEY' or a valid absolute '${EXTERNAL_BASE_URL_KEYS.first()}'.",
                    )
            } else {
                null
            }
        val createResult =
            provider
                .create(
                    DidCreateOptions(
                        method = method,
                        publicKeyJwk = jwk.toPublicKey(),
                        domain = domain,
                        // The verifier's request-object signing key authenticates the verifier — the
                        // published did:web document MUST list it under the authentication relationship.
                        purposes = if (method in WEB_RESOLVED_METHODS) listOf(VerificationPurpose.AUTHENTICATION) else null,
                        // Descriptive, per-key fragment (e.g. did:web:host#oid4vp-verifier-signing) so the
                        // verifier's VM is distinct in the shared hosted document.
                        verificationMethodId = if (method in WEB_RESOLVED_METHODS) alias else null,
                    ),
                ).getOrElse { error("Failed to derive did:$method from signing key '$alias': ${it.message.defaultMessage}") }

        val absoluteOverride = configService.getPropertyAsString("$NAMESPACE.signing.verification-method-id")
        val fragmentOverride = configService.getPropertyAsString("$NAMESPACE.signing.verification-method-fragment")

        val vmId =
            when {
                absoluteOverride != null -> {
                    require(absoluteOverride.startsWith(createResult.did)) {
                        "$NAMESPACE.signing.verification-method-id='$absoluteOverride' must reference the DID '${createResult.did}' derived from signing key '$alias'"
                    }
                    absoluteOverride
                }

                fragmentOverride != null -> {
                    "${createResult.did}#${fragmentOverride.removePrefix("#")}"
                }

                else -> {
                    createResult.verificationMethodsByPurpose[VerificationPurpose.AUTHENTICATION]
                        ?.firstOrNull()
                        ?.id
                        // did:jwk encodes the key in the DID itself; per the did:jwk method spec
                        // the single verification method is always `<did>#0`. That is not a
                        // fabricated fragment — it is method-defined and unambiguous, so default
                        // to it for did:jwk specifically (other methods still fail loudly below).
                        ?: if (method == "jwk") {
                            "${createResult.did}#0"
                        } else {
                            error(
                                "DID provider for method '$method' returned no `authentication` verification method for signing key '$alias'. " +
                                    "Configure $NAMESPACE.signing.verification-method-id (full DID URL) or " +
                                    "$NAMESPACE.signing.verification-method-fragment (fragment only) to pin one explicitly — " +
                                    "we won't fabricate a fragment (no universal '#0' default).",
                            )
                        }
                }
            }
        return VerifierSignerBinding.Did(did = createResult.did, verificationMethodId = vmId)
    }

    private suspend fun buildX509SanDnsBinding(alias: String): VerifierSignerBinding.X509SanDns {
        val chain = loadX5cChain(alias)
        val dnsName =
            configService.getPropertyAsString("$NAMESPACE.signing.sanDnsName")
                ?: error(
                    "x509_san_dns signing requires $NAMESPACE.signing.sanDnsName (must match a " +
                        "dNSName SAN entry in the leaf certificate per OID4VP §5.9.3).",
                )
        return VerifierSignerBinding.X509SanDns(dnsName = dnsName, certificateChain = chain)
    }

    /**
     * Build the `x509_hash:` binding per OID4VP 1.0 §5.9.3 / 1.1 §5.9.3 / HAIP 1.0 §5.
     *
     * Spec verbatim (OID4VP 1.0 §5.9.3, line 616):
     *
     *   "the original Client Identifier (the part without the `x509_hash:` prefix) MUST be a
     *    hash and match the hash of the leaf certificate passed with the request. … The value
     *    of `x509_hash` is the base64url-encoded value of the SHA-256 hash of the DER-encoded
     *    X.509 certificate."
     *
     * The hash is therefore a deterministic function of the leaf cert — derive it on the fly
     * from the same `x5c[0]` bytes the JAR will carry. Any pinned-config approach risks drift
     * (regenerated keystore, swapped cert) that the conformance test catches as
     * `ExtractAndValidateX509HashClientId: Mismatch between Client ID … and the calculated
     * x509 hash`.
     *
     * `x5c` entries are base64-encoded DER per RFC 7515 §4.1.6 (regular base64 with padding,
     * not base64url) — decode that and SHA-256 the resulting DER bytes.
     */
    private suspend fun buildX509HashBinding(alias: String): VerifierSignerBinding.X509Hash {
        val chain = loadX5cChain(alias)
        val leafBase64 =
            chain.firstOrNull()
                ?: error(
                    "Signing key '$alias' resolved an empty x5c chain — x509_hash signing requires the " +
                        "leaf certificate as the first element of the chain.",
                )
        val leafDer = leafBase64.decodeFrom(Encoding.BASE64)
        val certHash = hash(leafDer, DigestAlg.SHA256).encodeToBase64Url()
        return VerifierSignerBinding.X509Hash(certificateHash = certHash, certificateChain = chain)
    }

    private suspend fun loadJwk(alias: String): Jwk {
        ensureSigningKey(alias)
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = alias))
        check(keyResult.isOk) { "Failed to load signing key '$alias' from KMS: ${keyResult.error}" }
        val managedKey = keyResult.value.key
        return (managedKey?.key as? Jwk)
            ?: error("Signing key '$alias' is not a JWK — cannot use for verifier JAR signing")
    }

    private suspend fun loadX5cChain(alias: String): List<String> {
        val jwk = loadJwk(alias)
        return jwk.x5c?.toList()
            ?: error(
                "Signing key '$alias' has no x5c chain — x509_san_dns/x509_hash signing requires " +
                    "the KMS-stored JWK to carry the leaf certificate chain.",
            )
    }

    private fun requireKeyAlias(): String =
        configService.getPropertyAsString("$NAMESPACE.signing.keyAlias")
            ?: error("Request URI signing is enabled but no key alias configured at $NAMESPACE.signing.keyAlias")

    /**
     * Resolve the did:web/webvh host: an explicit override, else the host of the verifier's public
     * base URL (the HTTPS URL the example is started with).
     */
    private fun resolveDidWebDomain(): String? {
        configService.getPropertyAsString(DID_WEB_DOMAIN_KEY)?.takeIf { it.isNotBlank() }?.let { return hostOf(it) }
        for (key in EXTERNAL_BASE_URL_KEYS) {
            configService.getPropertyAsString(key)?.takeIf { it.isNotBlank() }?.let { return hostOf(it) }
        }
        return null
    }

    companion object {
        private const val NAMESPACE = "oid4vp.verifier.request-object"
        private val WEB_RESOLVED_METHODS = setOf("web", "webvh")

        // The verifier's public base URL lives under `oid4vp.universal` in this deployment; accept the
        // `oid4vp.verifier` form too for robustness.
        private val EXTERNAL_BASE_URL_KEYS = listOf("oid4vp.universal.external-base-url", "oid4vp.verifier.external-base-url")
        private const val DID_WEB_DOMAIN_KEY = "oid4vp.verifier.request-object.signing.did-web-domain"

        /** Host (authority without scheme/port/path) of an absolute http(s) URL, or null. */
        private fun hostOf(url: String): String? {
            val authority =
                url
                    .substringAfter("://", "")
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
            val host = authority.substringBefore('@').substringBefore(':')
            return host.takeIf { it.isNotBlank() }
        }

        private const val DEFAULT_EXPIRATION_SECONDS = 300L
        private const val DEFAULT_MODE = "did:jwk"
        private const val DEFAULT_PROVIDER_ID = "default"
    }
}

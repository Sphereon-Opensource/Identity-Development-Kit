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

package com.sphereon.openid.oid4vp.verifier.impl.config

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.x509.certificateFromBase64Der
import com.sphereon.crypto.resolution.managed.ManagedIdentifierService
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding
import com.sphereon.openid.oid4vp.verifier.spi.VerifierSigningKeyNameResolver
import dev.zacsweers.metro.Provider

/**
 * Config-backed [RequestObjectSigningConfig] whose entire keyspace is rooted at a runtime-supplied
 * verifier namespace.
 *
 * All property reads route through [namespaceProvider], which returns the active verifier config
 * root — either the singular `oid4vp.verifier` (single-verifier config-only deploy) or the
 * per-instance `oid4vp.verifiers.<id>` (multi-verifier routing selected at request time). The
 * request-object signing keyspace then nests under `${root}.request-object.*`.
 *
 * The two concrete providers differ ONLY in what they pass as [namespaceProvider]:
 *  - [ConfigDrivenOid4vpVerifierConfigProvider] pins it to the singular root.
 *  - [RegistryBackedOid4vpVerifierConfigProvider] computes it from the resolved instance id,
 *    falling back to the singular root when no instance is set.
 *
 * The supplier is evaluated PER READ (every property getter calls it again), not cached at
 * construction, so a holder populated mid-session is honoured.
 */
abstract class AbstractConfigOid4vpVerifierConfigProvider(
    private val execution: SessionExecution,
    private val managedIdentifierService: ManagedIdentifierService,
    private val kms: KeyManagerService,
    private val didProviderRegistry: DidProviderRegistry,
    private val namespaceProvider: () -> String,
    /**
     * Active verifier instance id, evaluated per read like [namespaceProvider]. It identifies the
     * binding a bound [VerifierSigningKeyNameResolver] resolves against; it never itself names a key.
     */
    private val instanceIdProvider: () -> String? = { null },
    /**
     * Bound by deployments that manage signing material centrally. While bound it is the only source
     * of the signing key name: `signing.keyAlias` and `signing.providerId` are not read at all, and a
     * null answer refuses the signing outright.
     */
    private val signingKeyNameResolver: Provider<VerifierSigningKeyNameResolver>? = null,
) : RequestObjectSigningConfig {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    /** Active verifier config root, e.g. `oid4vp.verifier` or `oid4vp.verifiers.<id>`. */
    private val verifierNamespace: String
        get() = namespaceProvider()

    /** Active request-object signing keyspace root, nested under the verifier namespace. */
    private val signingNamespace: String
        get() = "$verifierNamespace.request-object"

    override val enabled: Boolean
        get() = configService.getProperty("$signingNamespace.signing.enabled", Boolean::class) ?: false

    override val includeIss: Boolean
        get() = configService.getProperty("$signingNamespace.signing.include-iss", Boolean::class) ?: false

    override val audience: String
        get() = configService.getPropertyAsString("$signingNamespace.signing.audience") ?: ""

    override val expirationSeconds: Long
        get() =
            configService.getProperty("$signingNamespace.signing.expiration.seconds", Long::class)
                ?: DEFAULT_EXPIRATION_SECONDS

    /**
     * Resolve the request-object signing key.
     *
     * The key must already exist. Nothing is created here: a signing key the verifier's published
     * `client_id` is derived from is provisioned durably, out of band, and a request that finds none
     * is refused rather than served under a freshly minted identity.
     */
    override suspend fun resolveSigningKey(): KeyInfoType<*> {
        val keyName = requireSigningKeyName()
        val result = managedIdentifierService.resolve(ManagedOptsAlias(identifier = keyName))
        check(result.isOk) { SIGNING_KEY_UNAVAILABLE }
        @Suppress("UNCHECKED_CAST")
        return result.value as KeyInfoType<KeyType>
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
     *   `$signingNamespace.signing.mode` (legacy behaviour).
     *
     * When signing is enabled, this MUST succeed — any configuration error throws (no
     * silent fallback to the HTTPS client_id, which §5.9.3 forbids signing under anyway).
     */
    override suspend fun resolveSignerBinding(scheme: ClientIdScheme?): VerifierSignerBinding? {
        if (!enabled) return null
        val keyName = requireSigningKeyName()
        return when (scheme) {
            ClientIdScheme.DECENTRALIZED_IDENTIFIER -> {
                // Default did method to `jwk` if the caller didn't pin one via config.
                val configuredMode = configService.getPropertyAsString("$signingNamespace.signing.mode")
                val didMethod =
                    if (configuredMode != null && configuredMode.startsWith("did:")) {
                        configuredMode.removePrefix("did:")
                    } else {
                        "jwk"
                    }
                buildDidBinding(keyName, method = didMethod)
            }

            ClientIdScheme.X509_SAN_DNS -> {
                buildX509SanDnsBinding(keyName)
            }

            ClientIdScheme.X509_HASH -> {
                buildX509HashBinding(keyName)
            }

            null -> {
                // No scheme requested — use the deployment-configured default.
                val mode = configService.getPropertyAsString("$signingNamespace.signing.mode") ?: DEFAULT_MODE
                when {
                    mode.startsWith("did:") -> buildDidBinding(keyName, method = mode.removePrefix("did:"))
                    mode == "x509_san_dns" -> buildX509SanDnsBinding(keyName)
                    mode == "x509_hash" -> buildX509HashBinding(keyName)
                    else -> error("Unsupported $signingNamespace.signing.mode='$mode' (expected did:<method>, x509_san_dns, or x509_hash)")
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
     *   1. `$signingNamespace.signing.verification-method-id` — full DID URL (absolute override).
     *   2. `$signingNamespace.signing.verification-method-fragment` — fragment only, prepended
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
        keyName: String,
        method: String,
    ): VerifierSignerBinding.Did {
        val jwk = loadJwk(keyName)
        val provider =
            didProviderRegistry.getProvider(method)
                ?: error("No DID provider registered for method '$method' — cannot derive verifier client_id")
        // did:web / did:webvh bind the key to a document hosted at a host; the DID is not derived
        // from the key, so the host must be supplied. It is the host of the verifier's public base URL.
        val domain =
            if (method in WEB_RESOLVED_METHODS) {
                resolveDidWebDomain()
                    ?: error(
                        "did:$method signing requires a host: set '${didWebDomainKey()}' to a host or absolute URL, " +
                            "or set a valid absolute verifier external base URL.",
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
                        verificationMethodId = if (method in WEB_RESOLVED_METHODS) keyName else null,
                    ),
                ).getOrElse { error("Failed to derive did:$method from the verifier signing key: ${it.message.defaultMessage}") }

        val absoluteOverride = configService.getPropertyAsString("$signingNamespace.signing.verification-method-id")
        val fragmentOverride = configService.getPropertyAsString("$signingNamespace.signing.verification-method-fragment")

        val vmId =
            when {
                absoluteOverride != null -> {
                    requireAbsoluteVerificationMethodIdForDid(createResult.did, absoluteOverride)
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
                                "DID provider for method '$method' returned no `authentication` verification method for the verifier signing key. " +
                                    "Configure $signingNamespace.signing.verification-method-id (full DID URL) or " +
                                    "$signingNamespace.signing.verification-method-fragment (fragment only) to pin one explicitly — " +
                                    "we won't fabricate a fragment (no universal '#0' default).",
                            )
                        }
                }
            }
        return VerifierSignerBinding.Did(
            did = createResult.did,
            verificationMethodId = requireAbsoluteVerificationMethodIdForDid(createResult.did, vmId),
        )
    }

    private suspend fun buildX509SanDnsBinding(keyName: String): VerifierSignerBinding.X509SanDns {
        val chain = loadX5cChain(keyName)
        val dnsName =
            configService.getPropertyAsString("$signingNamespace.signing.sanDnsName")
                ?: error(
                    "x509_san_dns signing requires $signingNamespace.signing.sanDnsName (must match a " +
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
    private suspend fun buildX509HashBinding(keyName: String): VerifierSignerBinding.X509Hash {
        val chain = loadX5cChain(keyName)
        val leafBase64 =
            chain.firstOrNull()
                ?: error(
                    "The verifier signing key resolved an empty x5c chain — x509_hash signing requires the " +
                        "leaf certificate as the first element of the chain.",
                )
        val leafDer = leafBase64.decodeFrom(Encoding.BASE64)
        val certHash = hash(leafDer, DigestAlg.SHA256).encodeToBase64Url()
        return VerifierSignerBinding.X509Hash(certificateHash = certHash, certificateChain = chain)
    }

    /**
     * Load the signing key's stored JWK. The key must already be present; a miss refuses with the
     * uniform message and no key is created.
     */
    private suspend fun loadJwk(keyName: String): Jwk {
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = keyName))
        check(keyResult.isOk) { SIGNING_KEY_UNAVAILABLE }
        val managedKey = keyResult.value.key
        return (managedKey?.key as? Jwk) ?: error(SIGNING_KEY_UNAVAILABLE)
    }

    private suspend fun loadX5cChain(keyName: String): List<String> {
        val jwk = loadJwk(keyName)
        val storedChain =
            jwk.x5c?.toList()
                ?: error(
                    "The verifier signing key has no x5c chain — x509_san_dns/x509_hash signing requires " +
                        "the KMS-stored JWK to carry the leaf certificate chain.",
                )
        return requestObjectX5cChain(storedChain)
    }

    /**
     * The name of the key this verifier signs request objects under.
     *
     * A bound [VerifierSigningKeyNameResolver] is the sole source while it is bound: the resolver
     * derives the name from the deployment's own binding for the active tenant and verifier
     * instance, and `signing.keyAlias` / `signing.providerId` are not consulted at all. Without one,
     * the deployment's own configured alias is used. Either way nothing is generated and every
     * unusable outcome ends at the same refusal.
     */
    private suspend fun requireSigningKeyName(): String = resolveSigningKeyName() ?: error(SIGNING_KEY_UNAVAILABLE)

    private suspend fun resolveSigningKeyName(): String? {
        val resolver =
            signingKeyNameResolver?.invoke()
                ?: return configService.getPropertyAsString("$signingNamespace.signing.keyAlias")?.takeIf { it.isNotBlank() }
        val tenantId = execution.tenantId.takeIf { it.isNotBlank() } ?: return null
        val instanceId = instanceIdProvider()?.takeIf { it.isNotBlank() } ?: return null
        return resolver.resolveRequestObjectSigningKeyName(tenantId, instanceId)?.takeIf { it.isNotBlank() }
    }

    /** Instance-relative did:web host override key, nested under the active signing namespace. */
    private fun didWebDomainKey(): String = "$signingNamespace.signing.did-web-domain"

    /**
     * Resolve the did:web/webvh host: an explicit (instance-relative) override, else the host of the
     * verifier's public base URL (the HTTPS URL the example is started with).
     *
     * The verifier external base URL is read from the active verifier namespace
     * (`${verifierNamespace}.external-base-url`); the deployment-wide `oid4vp.universal.external-base-url`
     * is also accepted as a global fallback (it is not per-instance).
     */
    private fun resolveDidWebDomain(): String? {
        configService.getPropertyAsString(didWebDomainKey())?.takeIf { it.isNotBlank() }?.let { return verifierHostOf(it) }
        for (key in externalBaseUrlKeys()) {
            configService.getPropertyAsString(key)?.takeIf { it.isNotBlank() }?.let { return verifierHostOf(it) }
        }
        return null
    }

    /**
     * Verifier external base URL keys in resolution order: instance-relative first
     * (`${verifierNamespace}.external-base-url`), then the deployment-wide universal key
     * (`oid4vp.universal.external-base-url`, NOT per-instance).
     */
    private fun externalBaseUrlKeys(): List<String> =
        listOf(
            "$verifierNamespace.external-base-url",
            UNIVERSAL_EXTERNAL_BASE_URL_KEY,
        )

    companion object {
        private val WEB_RESOLVED_METHODS = setOf("web", "webvh")

        // The verifier's public base URL also lives under `oid4vp.universal` in this deployment; this
        // is a single deployment-wide value, NOT per-verifier-instance, so it stays a fixed key.
        private const val UNIVERSAL_EXTERNAL_BASE_URL_KEY = "oid4vp.universal.external-base-url"

        private const val DEFAULT_EXPIRATION_SECONDS = 300L
        private const val DEFAULT_MODE = "did:jwk"

        /**
         * Single refusal for every reason the request-object signing key cannot be used: no binding,
         * a detached or cross-tenant one, an inactive one, an unmapped one, and a key that is absent
         * from the KMS or is not a JWK. Stating one message keeps the refusal from becoming a
         * discovery oracle over which verifiers hold which key material.
         */
        internal const val SIGNING_KEY_UNAVAILABLE = "The verifier request-object signing key is unavailable"
    }
}

/**
 * Build the JOSE `x5c` value for a signed authorization request.
 *
 * KMS storage keeps the complete validation chain, including the root CA. RFC 7515 `x5c` values
 * sent by the verifier contain the leaf and any intermediates, but not the trust anchor. A
 * terminal self-issued certificate is the stored root and is therefore omitted when a leaf is
 * also present. A single-certificate chain is preserved because its only entry is the leaf.
 */
internal fun requestObjectX5cChain(
    storedChain: List<String>,
    terminalIsSelfIssued: (String) -> Boolean = { encodedCertificate ->
        val certificate = certificateFromBase64Der(encodedCertificate)
        certificate.issuerDN == certificate.subjectDN
    },
): List<String> {
    require(storedChain.isNotEmpty()) { "Request-object signing requires a non-empty x5c chain" }
    return if (storedChain.size > 1 && terminalIsSelfIssued(storedChain.last())) {
        storedChain.dropLast(1)
    } else {
        storedChain
    }
}

/**
 * Authority portion of a verifier domain setting, accepting either a bare authority or an absolute
 * URL. A non-default port is part of a did:web authority and must be retained so the DID provider
 * can encode it as `%3A<port>`.
 */
internal fun verifierHostOf(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val authority =
        (if (trimmed.contains("://")) trimmed.substringAfter("://") else trimmed)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    if ('@' in authority) return null
    return authority.takeIf { it.isNotBlank() }
}

internal fun requireAbsoluteVerificationMethodIdForDid(
    did: String,
    verificationMethodId: String,
): String {
    require(
        did.startsWith("did:") &&
            verificationMethodId.startsWith("$did#") &&
            verificationMethodId.length > did.length + 1 &&
            verificationMethodId.substringBefore('#') == did
    ) {
        "Verifier request-object kid must be a full assertionMethod id rooted in issuer DID '$did'"
    }
    return verificationMethodId
}

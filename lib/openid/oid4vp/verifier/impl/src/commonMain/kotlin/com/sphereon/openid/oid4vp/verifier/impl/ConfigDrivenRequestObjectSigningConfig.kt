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

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierService
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidCreateOptions
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.did.models.VerificationPurpose
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
        val opts = ManagedOptsAlias(identifier = alias)
        val result = managedIdentifierService.resolve(opts)
        check(result.isOk) { "Failed to resolve signing key '$alias': ${result.error}" }
        @Suppress("UNCHECKED_CAST")
        return result.value as KeyInfoType<KeyType>
    }

    /**
     * Produce the verifier's client-identity binding per OID4VP 1.0 §5.9.3. Returns null
     * only when signing is disabled. When signing is enabled, this MUST succeed — any
     * configuration error throws (no silent fallback to the HTTPS client_id, which §5.9.3
     * forbids signing under anyway).
     *
     * Mode selection via `$NAMESPACE.signing.mode`:
     *   - `did:jwk` / `did:key` / …   → [VerifierSignerBinding.Did]
     *   - `x509_san_dns`              → [VerifierSignerBinding.X509SanDns]
     *   - `x509_hash`                 → [VerifierSignerBinding.X509Hash]
     *   - (absent) → defaults to `did:jwk`, matching historical demo behaviour.
     */
    override suspend fun resolveSignerBinding(): VerifierSignerBinding? {
        if (!enabled) return null
        val alias = requireKeyAlias()
        val mode = configService.getPropertyAsString("$NAMESPACE.signing.mode") ?: DEFAULT_MODE
        return when {
            mode.startsWith("did:") -> buildDidBinding(alias, method = mode.removePrefix("did:"))
            mode == "x509_san_dns" -> buildX509SanDnsBinding(alias)
            mode == "x509_hash" -> buildX509HashBinding(alias)
            else -> error("Unsupported $NAMESPACE.signing.mode='$mode' (expected did:<method>, x509_san_dns, or x509_hash)")
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
        val createResult =
            provider
                .create(DidCreateOptions(method = method, publicKeyJwk = jwk.toPublicKey()))
                .getOrElse { error("Failed to derive did:$method from signing key '$alias': ${it.message.defaultMessage}") }

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
                        ?: error(
                            "DID provider for method '$method' returned no `authentication` verification method for signing key '$alias'. " +
                                "Configure $NAMESPACE.signing.verification-method-id (full DID URL) or " +
                                "$NAMESPACE.signing.verification-method-fragment (fragment only) to pin one explicitly — " +
                                "we won't fabricate a fragment (no universal '#0' default).",
                        )
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

    private suspend fun buildX509HashBinding(alias: String): VerifierSignerBinding.X509Hash {
        val chain = loadX5cChain(alias)
        val providedHash =
            configService.getPropertyAsString("$NAMESPACE.signing.certHash")
                ?: error(
                    "x509_hash signing requires $NAMESPACE.signing.certHash " +
                        "(base64url SHA-256 of the DER-encoded leaf certificate per OID4VP §5.9.3). " +
                        "We don't derive this on the fly — the hash is a trust-anchor identifier that " +
                        "must be pinned in configuration.",
                )
        return VerifierSignerBinding.X509Hash(certificateHash = providedHash, certificateChain = chain)
    }

    private suspend fun loadJwk(alias: String): Jwk {
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

    companion object {
        private const val NAMESPACE = "oid4vp.verifier.request-object"
        private const val DEFAULT_EXPIRATION_SECONDS = 300L
        private const val DEFAULT_MODE = "did:jwk"
    }
}

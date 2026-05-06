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
 *
 */

package com.sphereon.crypto.core.cose

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.toException
import com.sphereon.crypto.core.CoseCryptoCallbackCoroutines
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.DefaultCallbacks
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.defaultCreateMac0
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.VerifySignatureResult
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.interop.toCertificateDto
import com.sphereon.crypto.core.interop.x509CertificateFromDer
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.Scoped

/**
 * Marker interface for CoseCryptoCallbackCoroutines to enable injection.
 * This avoids generic type issues with kotlin-inject.
 */
interface CoseCryptoCallbackCoroutinesMarker : CoseCryptoCallbackCoroutines

/**
 * Automatic registration adapter for COSE crypto callbacks.
 *
 * This class is automatically discovered and initialized when the SessionScope is created,
 * ensuring that COSE crypto callbacks are available even when using legacy DefaultCallbacks directly.
 *
 * Uses Provider pattern to break circular dependencies.
 *
 * @param keyManagerServiceProvider Lazy provider for KeyManagerService to avoid circular dependency
 * @param rawSignatureServiceProvider Lazy provider for SimpleSignatureService
 * @param publicKeyResolverServiceProvider Lazy provider for KeyResolverService
 */

@Suppress("TooGenericExceptionCaught")
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CoseCryptoCallbackCoroutinesMarker>())
@ContributesBinding(SessionScope::class, binding = binding<CoseCryptoCallbackCoroutines>())
class CoseCryptoProviderToCallbackAdapter(
    private val keyManagerServiceProvider: dev.zacsweers.metro.Provider<KeyManagerService>,
    private val rawSignatureServiceProvider: dev.zacsweers.metro.Provider<SimpleSignatureService>? = null,
    private val publicKeyResolverServiceProvider: dev.zacsweers.metro.Provider<KeyResolverService>? = null,
    private val coseHeaderCborCodec: CoseHeaderCborCodec = CoseHeaderCborCodecImpl(),
) : CoseCryptoCallbackCoroutinesMarker,
    Scoped {
    private val keyManagerService: KeyManagerService? by lazy {
        try {
            keyManagerServiceProvider()
        } catch (_: Exception) {
            null
        }
    }

    private val rawSignatureService: SimpleSignatureService? by lazy {
        try {
            rawSignatureServiceProvider?.invoke()
        } catch (_: Exception) {
            null
        }
    }

    private val publicKeyResolverService: KeyResolverService? by lazy {
        try {
            publicKeyResolverServiceProvider?.invoke()
        } catch (_: Exception) {
            null
        }
    }

    init {
        // Note: We cannot validate providers in init block as that would trigger the circular dependency.
        // Validation is deferred to actual usage time via the lazy properties and assertion methods.
    }

    override fun onEnterScope(scope: Scope) {
        // Register with DefaultCallbacks for backward compatibility
        DefaultCallbacks.setCoseCryptoDefault(this)
    }

    private fun assertedSignatureProvider(
        alg: SignatureAlgorithm? = null,
        kms: String? = null,
    ): SimpleSignatureService =
        (keyManagerService?.getProvider(providerId = kms, alg = alg) ?: rawSignatureService)
            ?: throw PKIException("No signature provider available. Either keyManager or rawSignatureService must be provided.")

    private fun assertedPublicKeyProvider(keyInfo: KeyInfoType<*>): KeyResolverService {
        val kms = keyManagerService
        val keyType = keyInfo.keyType
        // If the key has a provider ID, check if that KMS provider also implements KeyResolverService
        // and supports the key type
        val providerAsResolver =
            keyInfo.providerId?.let { providerId ->
                try {
                    val provider = kms?.getProviderById(providerId)
                    val resolver = provider as? KeyResolverService
                    // Check if the resolver supports the key type
                    if (resolver != null && keyType != null && resolver.allSupportedKeyTypes().contains(keyType)) {
                        resolver
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        // If the provider is a suitable resolver, use it; otherwise find a resolver by keyType
        return providerAsResolver ?: kms?.getResolverByKeyTypeOrIdentifier(
            keyType = keyType,
            resolverId = null,
        ) ?: publicKeyResolverService ?: throw PKIException("Could not deduce key resolver from key info, default resolver, or provided resolver")
    }

    /**
     * Signs the provided input data using the specified key and algorithm.
     *
     * @param input The data to be signed, along with the key and algorithm information.
     * @return The generated signature as a ByteArray.
     */
    override suspend fun sign(
        input: ToBeSignedCbor,
        requireX5Chain: Boolean?,
    ): ByteArray {
        val keyInfo = input.keyInfo
        val alg = keyInfo.signatureAlgorithm ?: input.alg
        return assertedSignatureProvider(alg = alg, kms = keyInfo.providerId).createRawSignature(keyInfo, input.value, requireX5Chain == true)
    }

    /**
     * Verifies a COSE_Sign1 message using the provided key information.
     *
     * @param input The COSE_Sign1 message to verify.
     * @param keyInfo The key information used for verification.
     * @return The result of the signature verification.
     */
    override suspend fun verify1(
        input: CoseSign1<*>,
        keyInfo: KeyInfoType<*>?,
        requireX5Chain: Boolean?,
    ): VerifySignatureResultType<CoseKeyType> {
        // RFC 9052 §3.1: x5chain is a header parameter that may appear in EITHER the protected
        // OR unprotected header of a COSE_Sign1. ISO 18013-5 §9.1.2.4 specifically allows mdoc
        // IssuerAuth to carry x5chain in the protected header (and conformance test mdocs do).
        // Check both — protected first, since that's where the issuer's authenticated chain
        // belongs when integrity-protected (RFC 9052 §1.4 "the protected header is integrity
        // protected by the signature").
        val chain =
            input.protectedHeader.x5chain?.value
                ?: input.unprotectedHeader?.x5chain?.value
        var chainKeyInfo: ResolvedKeyInfoType<Jwk>? = null
        if (chain?.isNotEmpty() == true && keyInfo == null) {
            val cert = x509CertificateFromDer(chain.first().value)
            val jwk = Jwk.from(cert.toCertificateDto().getPublicKeyJwk(x5c = chain.map { it.encodeValueTo(Encoding.BASE64) }.toTypedArray()))
            chainKeyInfo = ResolvedKeyInfo.fromKey(jwk)
        }
        require(chainKeyInfo != null || keyInfo != null) { "No key info supplied for verify1" }
        val resolvedKeyInfo = chainKeyInfo ?: this.resolvePublicKey(keyInfo!!)
        val key = resolvedKeyInfo.key
        // Algorithm precedence per RFC 9052 §4.4 ("Signing and Verification Process"):
        //   the COSE_Sign1 protected header `alg` is the authoritative algorithm. The COSE
        //   key may carry an `alg` constraint (RFC 9052 §7.1) but it isn't always set —
        //   ISO 18013-5 mdoc DeviceKeys typically omit it and rely on the COSE_Sign1 header.
        // Order of attempts:
        //   1. Resolved keyInfo's signatureAlgorithm (caller hint).
        //   2. The key's own alg (when present, e.g. JWK with alg).
        //   3. The COSE_Sign1 protected header alg (RFC 9052 authoritative source).
        val alg =
            resolvedKeyInfo.signatureAlgorithm
                ?: key.getSignatureAlgorithm()
                ?: input.protectedHeader.alg?.let { SignatureAlgorithm.fromCose(it) }
                ?: throw IllegalArgumentException(
                    "No alg was supplied for key (and the COSE_Sign1 protected header carried none).",
                )
        require(input.payload?.value !== null) { "Null payload supplied to verify signature" }

        val recalculatedToBeSignedCbor =
            createToBeSignedCbor(
                protectedHeader = input.protectedHeader,
                payload = checkNotNull(input.payload) { "Null payload supplied to verify signature" },
                keyInfo = resolvedKeyInfo,
                alg = alg,
                headerCodec = coseHeaderCborCodec,
            ).getOrElse { throw it.toException() }
        val validSig =
            assertedSignatureProvider(alg = alg, kms = resolvedKeyInfo.providerId).isValidRawSignature(
                resolvedKeyInfo,
                input = recalculatedToBeSignedCbor.value,
                signature = input.signature.value,
            )
        return VerifySignatureResult(
            keyInfo = CoseJoseKeyMappingService.toCoseKeyInfo(resolvedKeyInfo),
            error = !validSig,
            critical = !validSig,
            message =
                if (validSig) {
                    "Signature valid"
                } else {
                    "Signature invalid"
                },
            name = com.sphereon.crypto.core.CryptoConst.COSE_LITERAL,
        )
    }

    override suspend fun mac0(
        input: CoseMac0InputCbor,
        sharedSecret: ByteArray,
        alg: SignatureAlgorithm,
    ) = defaultCreateMac0(input, sharedSecret, alg)

    /**
     * Resolves a public key based on the provided key information.
     *
     * @param keyInfo Contains information about the key to be resolved, possibly including the key identifier (kid), key type, and other optional parameters.
     * @return The resolved public key as an instance of Key.
     */
    override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(keyInfo: KeyInfoType<KeyType>): ResolvedKeyInfoType<KeyType> {
        // Note: When only a kid is supplied, the first matching provider is used.
        // For multi-provider scenarios, include providerId in keyInfo for deterministic resolution.
        return assertedPublicKeyProvider(keyInfo = keyInfo).resolvePublicKey(
            keyInfo = keyInfo,
        ) // We call this method from the verification, so let's not verify ourselves as well!
    }
}

@ContributesTo(SessionScope::class)
interface CoseCryptoProviderToCallbackAdapterScopedModule {
    @Provides
    @IntoSet
    @ForScope(SessionScope::class)
    fun provideScoped(impl: CoseCryptoCallbackCoroutinesMarker): Scoped = impl as Scoped
}

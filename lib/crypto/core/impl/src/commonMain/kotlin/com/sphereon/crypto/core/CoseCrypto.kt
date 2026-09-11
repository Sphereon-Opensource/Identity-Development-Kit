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

package com.sphereon.crypto.core

import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.encodeToBase64Array
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.toException
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseHeaderCborCodec
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.cose.CoseMac0Cbor
import com.sphereon.crypto.core.cose.CoseMac0InputCbor
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.crypto.core.cose.CoseSign1Input
import com.sphereon.crypto.core.cose.ToBeSignedCbor
import com.sphereon.crypto.core.cose.createToBeMacedCbor
import com.sphereon.crypto.core.cose.createToBeSignedCbor
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.VerifySignatureResult
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.crypto.core.generic.toCoseAlgorithm
import com.sphereon.crypto.core.generic.toCoseCurve
import com.sphereon.crypto.core.generic.toJoseSignatureAlgorithm
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.interop.toDerEcdhPrivateKey
import com.sphereon.crypto.core.interop.toDerEcdhPublicKey
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.di.session.SessionScope
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The main entry point for COSE signature creation/validation, delegating to a platform specific callback implemented by external developers
 */

abstract class AbstractCoseCryptoService(
    protected open var platformCallback: CoseCryptoCallbackCoroutines? = null,
    val provider: CryptographyProvider? = CryptographyProvider.Default,
    private val coseHeaderCborCodec: CoseHeaderCborCodec =
        com.sphereon.crypto.core.cose
            .CoseHeaderCborCodecImpl(),
) : HasPlatformCallback<CoseCryptoCallbackCoroutines>,
    CoseCryptoService {
    private var disabled = false

    /**
     * Result of preSign1 operation with guaranteed non-null headers.
     */
    protected data class PreSign1Result(
        val input: CoseSign1Input,
        val protectedHeader: CoseHeaderCbor,
        val unprotectedHeader: CoseHeaderCbor,
        val toSign: ToBeSignedCbor,
        val keyInfo: KeyInfoType<CoseKeyType>,
    )

    override fun isEnabled(): Boolean = !this.disabled

    override fun disable() =
        apply {
            this.disabled = true
        }

    override fun enable() =
        apply {
            this.disabled = false
        }

    override fun hasPlatform(): Boolean = this.platformCallback !== null || DefaultCallbacks.hasCoseCryptoDefault()

    override fun platform(): CoseCryptoCallbackCoroutines = this.platformCallback ?: DefaultCallbacks.coseCrypto()

    override fun setPlatform(platform: CoseCryptoCallbackCoroutines): HasPlatformCallback<CoseCryptoCallbackCoroutines> =
        apply {
            this.platformCallback = platform
        }

    protected fun assertEnabled() {
        check(isEnabled()) { "COSE session is disabled; cannot sign" }
        check(hasPlatform()) { "COSE have not been initialized. Please register your CoseCallback implementation, or register a default implementation" }
    }

    protected suspend fun preSign1(
        input: CoseSign1Input,
        keyInfo: KeyInfoType<*>?,
        requireX5Chain: Boolean,
    ): PreSign1Result {
        assertEnabled()
        val (unprotectedHeader, protectedHeader, cborKeyInfo) =
            verifyAndAmendKeyInfo(
                protectedHeader = input.protectedHeader,
                unprotectedHeader = input.unprotectedHeader,
                keyInfo = keyInfo,
                requireX5Chain = requireX5Chain,
            )
        // verifyAndAmendKeyInfo guarantees alg is set in protectedHeader via buildFinalHeaders
        val alg = checkNotNull(protectedHeader.alg) { "Algorithm resolution failed for key: ${cborKeyInfo.key}" }
        val signatureAlgorithm =
            SignatureAlgorithm.tryFromCoseForKey(alg, cborKeyInfo).getOrElse { throw it.toException() }
        val coseSign1 = input.copy(protectedHeader = protectedHeader, unprotectedHeader = unprotectedHeader)
        val originalKeyInfo = keyInfo
        val signingKeyInfo =
            if (
                originalKeyInfo != null &&
                originalKeyInfo.key == null &&
                originalKeyInfo.alias != null
            ) {
                KeyInfo<KeyType>(
                    kid = originalKeyInfo.kid,
                    opts = originalKeyInfo.opts,
                    keyVisibility = originalKeyInfo.keyVisibility,
                    signatureAlgorithm = originalKeyInfo.signatureAlgorithm ?: signatureAlgorithm,
                    x5c = originalKeyInfo.x5c,
                    alias = originalKeyInfo.alias,
                    providerId = originalKeyInfo.providerId,
                    keyType = originalKeyInfo.keyType,
                    keyEncoding = originalKeyInfo.keyEncoding,
                    noCache = originalKeyInfo.noCache,
                )
            } else {
                cborKeyInfo
            }
        val toSign =
            createToBeSignedCbor(
                protectedHeader = protectedHeader,
                payload = coseSign1.payload,
                keyInfo = signingKeyInfo,
                alg = signatureAlgorithm,
                headerCodec = coseHeaderCborCodec,
            ).getOrElse { throw it.toException() }
        return PreSign1Result(
            input = coseSign1,
            protectedHeader = protectedHeader,
            unprotectedHeader = unprotectedHeader,
            toSign = toSign,
            keyInfo = cborKeyInfo,
        )
    }

    protected fun <CborType : Any> postSign1(
        preSignResult: PreSign1Result,
        signature: ByteArray,
    ): CoseSign1Result<CborType> {
        val coseSign1 =
            CoseSign1<CborType>(
                protectedHeader = preSignResult.protectedHeader,
                unprotectedHeader = preSignResult.unprotectedHeader,
                signature = signature.toCborByteString(),
                payload = preSignResult.input.payload,
            )
        return CoseSign1Result(coseSign1 = coseSign1, keyInfo = preSignResult.keyInfo, input = preSignResult.input)
    }

    /**
     * Resolves the signature algorithm from available sources.
     * Priority: protectedHeader > unprotectedHeader > keyInfo > default (ES256 for EC keys)
     */
    private fun resolveSignatureAlgorithm(
        protectedHeader: CoseHeaderCbor?,
        unprotectedHeader: CoseHeaderCbor?,
        keyInfo: KeyInfoType<*>?,
    ): CoseAlgorithm? {
        // Priority chain: protected header > unprotected header > keyInfo > default
        return protectedHeader?.alg
            ?: unprotectedHeader?.alg
            ?: keyInfo?.signatureAlgorithm?.cose
            ?: defaultAlgorithmForKeyType(keyInfo?.key?.getKeyType())
    }

    private fun defaultAlgorithmForKeyType(keyType: KeyTypeMapping?): CoseAlgorithm? {
        val effectiveKeyType = keyType?.cose ?: KeyTypeMapping.EC.cose
        return if (effectiveKeyType == CoseKeyTypeEnum.EC2) {
            SignatureAlgorithm.ECDSA_SHA256.cose
        } else {
            null
        }
    }

    /**
     * Resolves the key ID from available sources.
     * Priority: keyInfo > protectedHeader > unprotectedHeader
     */
    private fun resolveKid(
        protectedHeader: CoseHeaderCbor?,
        unprotectedHeader: CoseHeaderCbor?,
        keyInfo: KeyInfoType<*>?,
    ): String? =
        keyInfo?.kid
            ?: protectedHeader?.kid?.encodeValueTo(Encoding.UTF8)
            ?: unprotectedHeader?.kid?.encodeValueTo(Encoding.UTF8)

    /**
     * Constructs a KeyInfo from an x5chain (certificate chain) in the COSE header.
     * This is used when no keyInfo is provided but the header contains an x5chain.
     * Supports both EC and RSA key types.
     *
     * @param x5chain The certificate chain from COSE headers
     * @param sigAlg The signature algorithm to use
     * @param kid Optional key ID to include in the resulting KeyInfo
     * @return KeyInfo constructed from the certificate's public key
     */
    protected fun buildKeyInfoFromX5Chain(
        x5chain: com.sphereon.cbor.CborArray<com.sphereon.cbor.CborByteString>,
        sigAlg: CoseAlgorithm,
        kid: String?,
    ): KeyInfo<CoseKeyType> {
        // All CoseAlgorithm values have keyType defined
        val algKeyType = sigAlg.keyType!!

        val leafCertDER = x5chain.value[0].value
        val cert = certificateFromDer(leafCertDER)
        val x5c = x5chain.encodeToBase64Array(false)
        val jwk = cert.getPublicKeyJwk(alg = sigAlg.toJoseSignatureAlgorithm(), x5c = x5c, generateKid = false)

        return buildKeyInfoFromJwk(
            jwk = jwk,
            x5chain = x5chain,
            x5c = x5c,
            sigAlg = sigAlg,
            algKeyType = algKeyType,
            kid = kid,
        )
    }

    /**
     * Constructs a KeyInfo from a JWK and x5chain.
     * Extracted for testability with custom JWK configurations.
     *
     * @param jwk The JWK containing key material
     * @param x5chain The certificate chain
     * @param x5c Base64-encoded certificate chain
     * @param sigAlg The signature algorithm (provides curve fallback for EC)
     * @param algKeyType The algorithm's key type (EC2, RSA, etc.)
     * @param kid Optional key ID
     * @return KeyInfo constructed from the JWK
     */
    protected fun buildKeyInfoFromJwk(
        jwk: com.sphereon.crypto.core.jose.JwkType,
        x5chain: com.sphereon.cbor.CborArray<com.sphereon.cbor.CborByteString>,
        x5c: Array<String>,
        sigAlg: CoseAlgorithm,
        algKeyType: CoseKeyTypeEnum,
        kid: String?,
    ): KeyInfo<CoseKeyType> {
        val signatureAlgorithm =
            SignatureAlgorithm.tryFromCoseForKey(sigAlg, KeyInfo(key = jwk)).getOrElse { throw it.toException() }

        // Build CoseKey with appropriate parameters based on key type
        val isEcKey = algKeyType == CoseKeyTypeEnum.EC2
        val isRsaKey = algKeyType == CoseKeyTypeEnum.RSA
        val coseKey =
            CoseKey(
                x5chain = x5chain,
                kty = com.sphereon.cbor.CborUInt(algKeyType.value.toLong()),
                kid = kid?.toCborByteString(Encoding.UTF8),
                alg =
                    jwk.alg
                        ?.toCoseAlgorithm()
                        ?.value
                        ?.let { CborUInt(value = it) },
                generateKid = false,
                // EC parameters - EC algorithms always have a curve by definition
                crv =
                    if (isEcKey) {
                        CborUInt(sigAlg.curve!!.value.toLong())
                    } else {
                        null
                    },
                x =
                    if (isEcKey) {
                        jwk.x?.toCborByteString(Encoding.BASE64URL)
                    } else {
                        null
                    },
                y =
                    if (isEcKey) {
                        jwk.y?.toCborByteString(Encoding.BASE64URL)
                    } else {
                        null
                    },
                d =
                    if (isEcKey) {
                        jwk.d?.toCborByteString(Encoding.BASE64URL)
                    } else {
                        null
                    },
                // RSA parameters
                n =
                    if (isRsaKey) {
                        jwk.n?.toCborByteString(Encoding.BASE64URL)
                    } else {
                        null
                    },
                rsaE =
                    if (isRsaKey) {
                        jwk.e?.toCborByteString(Encoding.BASE64URL)
                    } else {
                        null
                    },
            )

        return KeyInfo(
            key = coseKey,
            x5c = x5c,
            signatureAlgorithm = signatureAlgorithm,
            kid = kid,
            keyType = KeyTypeMapping.fromCose(algKeyType),
        )
    }

    /**
     * Builds the final headers with algorithm and x5chain properly set.
     */
    private fun buildFinalHeaders(
        protectedHeader: CoseHeaderCbor?,
        unprotectedHeader: CoseHeaderCbor?,
        sigAlg: CoseAlgorithm?,
        x5chain: com.sphereon.cbor.CborArray<com.sphereon.cbor.CborByteString>?,
    ): Pair<CoseHeaderCbor, CoseHeaderCbor> {
        // Build unprotected header with x5chain (copy if exists, create new if not)
        val finalUnprotectedHeader = (unprotectedHeader ?: CoseHeaderCbor()).copy(x5chain = x5chain)

        // Build protected header with algorithm (ensure alg is set)
        val baseProtectedHeader = protectedHeader ?: CoseHeaderCbor()
        val finalProtectedHeader =
            if (baseProtectedHeader.alg == null && sigAlg != null) {
                baseProtectedHeader.copy(alg = sigAlg)
            } else {
                baseProtectedHeader
            }

        return Pair(finalUnprotectedHeader, finalProtectedHeader)
    }

    protected suspend fun verifyAndAmendKeyInfo(
        protectedHeader: CoseHeaderCbor? = null,
        unprotectedHeader: CoseHeaderCbor? = null,
        keyInfo: KeyInfoType<*>? = null,
        requireX5Chain: Boolean = true,
    ): Triple<CoseHeaderCbor, CoseHeaderCbor, ResolvedKeyInfoType<CoseKeyType>> {
        // Step 1: Resolve x5chain from headers
        var x5chain = protectedHeader?.x5chain ?: unprotectedHeader?.x5chain

        // Step 2: Resolve algorithm and kid
        val sigAlg = resolveSignatureAlgorithm(protectedHeader, unprotectedHeader, keyInfo)
        val kid = resolveKid(protectedHeader, unprotectedHeader, keyInfo)

        // Step 3: Determine keyInfo - either use provided or build from x5chain
        val resolvedKeyInfo: KeyInfoType<*> =
            when {
                keyInfo != null -> keyInfo
                x5chain != null && sigAlg != null -> buildKeyInfoFromX5Chain(x5chain, sigAlg, kid)
                else -> throw IllegalStateException("No key info provided and no x5chain in headers to construct one from")
            }

        // Step 4: Resolve the actual key and retain the resolver's public metadata.
        // The original keyInfo may be an alias-only signing selector with no kid; do
        // not use that selector as the returned public-key metadata.
        val resolvedPublicKeyInfo =
            if (resolvedKeyInfo.key == null) {
                this.resolvePublicCborKey(resolvedKeyInfo)
            } else {
                null
            }
        val key: CoseKeyType =
            resolvedPublicKeyInfo?.key
                ?: CoseJoseKeyMappingService.toCoseKey(checkNotNull(resolvedKeyInfo.key))

        // Step 5: Update x5chain from key if not already set
        if (x5chain == null) {
            x5chain = key.x5chain
        }

        // Step 6: Validate x5chain requirement
        if (requireX5Chain && x5chain == null) {
            throw IllegalArgumentException(
                "No x5c or x5chain could be found in header or resolved key. " +
                    "keyinfo x5c: ${resolvedKeyInfo.x5c}, header: $protectedHeader",
            )
        }

        // Step 7: Build final headers
        val (finalUnprotectedHeader, finalProtectedHeader) =
            buildFinalHeaders(
                protectedHeader,
                unprotectedHeader,
                sigAlg,
                x5chain,
            )

        val finalKeyInfo =
            resolvedPublicKeyInfo
                ?: CoseJoseKeyMappingService.toResolvedCoseKeyInfo(
                    CoseJoseKeyMappingService.toResolvedKeyInfo(resolvedKeyInfo, key),
                )

        return Triple(
            finalUnprotectedHeader,
            finalProtectedHeader,
            finalKeyInfo,
        )
    }

    open suspend fun resolvePublicCborKey(keyInfo: KeyInfoType<*>): ResolvedKeyInfoType<CoseKeyType> {
        val info = resolvePublicKey(keyInfo)
        return CoseJoseKeyMappingService.toResolvedCoseKeyInfo(info)
    }

    override suspend fun <CborType : Any> sign1(
        input: CoseSign1Input,
        keyInfo: KeyInfoType<*>?,
        requireX5Chain: Boolean?,
    ): CoseSign1Result<CborType> {
        val preSignResult = this.preSign1(input, keyInfo, requireX5Chain == true)
        val signature = platform().sign(preSignResult.toSign, requireX5Chain)
        return this.postSign1(preSignResult, signature)
    }

    override suspend fun verify1(
        input: CoseSign1<*>,
        keyInfo: KeyInfoType<*>?,
        requireX5Chain: Boolean?,
    ): VerifySignatureResultType<CoseKeyType> {
        val (_, _, info) =
            verifyAndAmendKeyInfo(
                protectedHeader = input.protectedHeader,
                unprotectedHeader = input.unprotectedHeader,
                keyInfo = keyInfo,
                requireX5Chain = requireX5Chain == true,
            )
        try {
            this.assertEnabled()
        } catch (e: IllegalStateException) {
            return VerifySignatureResult(
                keyInfo = info,
                name = CryptoConst.COSE_LITERAL,
                message = "COSE signing/verification has been disabled or not callback has been registered! ${e.message}",
                detailMessage = e.message,
                error = this.isEnabled(),
                critical = this.isEnabled(),
            )
        }

        return platform().verify1(input = input, keyInfo = info, requireX5Chain = requireX5Chain == true)
    }

    override suspend fun mac0(
        input: CoseMac0InputCbor,
        sharedSecret: ByteArray,
        alg: SignatureAlgorithm,
    ): CoseMac0Result = platform().mac0(input = input, sharedSecret = sharedSecret, alg = alg)

    override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(keyInfo: KeyInfoType<KeyType>) = platform().resolvePublicKey(keyInfo)
}

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CoseCryptoService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoseCryptoServiceImpl", exact = true)
class CoseCryptoServiceImpl private constructor(
    coseHeaderCborCodec: CoseHeaderCborCodec,
    platformCallback: CoseCryptoCallbackCoroutines?,
) : AbstractCoseCryptoService(platformCallback = platformCallback, coseHeaderCborCodec = coseHeaderCborCodec) {
    @Inject
    constructor(
        platformCallback: CoseCryptoCallbackCoroutines,
        coseHeaderCborCodec: CoseHeaderCborCodec =
            com.sphereon.crypto.core.cose
                .CoseHeaderCborCodecImpl(),
    ) : this(coseHeaderCborCodec, platformCallback)

    constructor(
        coseHeaderCborCodec: CoseHeaderCborCodec =
            com.sphereon.crypto.core.cose
                .CoseHeaderCborCodecImpl(),
    ) : this(coseHeaderCborCodec, null)
}

suspend fun defaultCreateMac0(
    input: CoseMac0InputCbor,
    sharedSecret: ByteArray,
    alg: SignatureAlgorithm = input.protectedHeader.alg?.let { SignatureAlgorithm.fromCose(it) } ?: SignatureAlgorithm.HMAC_SHA256,
    provider: CryptographyProvider? = CryptographyProvider.Default,
    coseHeaderCborCodec: CoseHeaderCborCodec =
        com.sphereon.crypto.core.cose
            .CoseHeaderCborCodecImpl(),
): CoseMac0Result {
    // Since this lib will mostly be used in the context of Mdl/Mdocs where the mac0 is ECKA-DH (Diffie-Hellman) with SHA-256, we provide a default
    // We expose the provider as optional, as it can be set later on services. But in reallity it is required for this call!
    requireNotNull(provider) { "Crypto provider needs to be set for the default Mac0 implementation" }
    val digest = alg.digestAlgorithm?.toCryptoGraphicAlgorithm()
    checkNotNull(digest) {
        """Signature (Digest) algorithm $alg not supported or provided. Supported algorithms: ${
            arrayOf(SignatureAlgorithm.HMAC_SHA256, SignatureAlgorithm.HMAC_SHA384, SignatureAlgorithm.HMAC_SHA512).joinToString(", ")
        }"""
    }
    val protectedHeader = input.protectedHeader.copy(alg = alg.cose)
    val inputWithHeader = input.copy(protectedHeader = protectedHeader)
    val payload =
        inputWithHeader.payload ?: inputWithHeader.detachedPayload
            ?: throw IllegalStateException("Payload or detached payload is required")
    val toBeMaced =
        createToBeMacedCbor(
            protectedHeader = protectedHeader,
            payload = payload,
            externalAad = inputWithHeader.externalAad,
            headerCodec = coseHeaderCborCodec,
        ).getOrElse { throw it.toException() }
    val tag =
        provider
            .get(HMAC)
            .keyDecoder(digest)
            .decodeFromByteArray(HMAC.Key.Format.RAW, sharedSecret)
            .signatureGenerator()
            .generateSignature(toBeMaced.value)
    val coseMac0 =
        CoseMac0Cbor(
            tag = tag.toCborByteString(),
            protectedHeader = inputWithHeader.protectedHeader ?: CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
            unprotectedHeader = inputWithHeader.unprotectedHeader,
            payload = inputWithHeader.payload?.toCborByteString(),
        )
    return CoseMac0Result(input = inputWithHeader, coseMac0 = coseMac0)
}

suspend fun defaultCreateMac0UsingKeys(
    provider: CryptographyProvider? = null,
    input: CoseMac0InputCbor,
    selfPrivateKey: ResolvedKeyInfoType<*>,
    otherPublicKey: ResolvedKeyInfoType<*>,
    alg: SignatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
    info: String = "EMacKey",
    salt: ByteArray = byteArrayOf(),
    macCallback: suspend (
        provider: CryptographyProvider?,
        input: CoseMac0InputCbor,
        sharedSecret: ByteArray,
        alg: SignatureAlgorithm,
    ) -> CoseMac0Result,
): CoseMac0Result {
    // Since this lib will mostly be used in the context of Mdl/Mdocs where the mac0 is ECKA-DH (Diffie-Hellman) with SHA-256, we provide a default
    requireNotNull(provider) { "Crypto provider needs to be set for the default Mac0 implementation" }
    val selfRawPrivateKey = toDerEcdhPrivateKey(provider = provider, selfPrivateKey)
    val otherRawPublicKey = toDerEcdhPublicKey(provider = provider, otherPublicKey)
    val sharedSecret = selfRawPrivateKey.sharedSecretGenerator().generateSharedSecretToByteArray(otherRawPublicKey)
    val emacKey =
        provider
            .get(HKDF)
            .secretDerivation(digest = SHA256, outputSize = 32.bytes, salt = salt, info = info.decodeFrom(Encoding.UTF8))
            .deriveSecretToByteArray(sharedSecret)
    return macCallback(provider, input, emacKey, alg)
}

/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.mdoc

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.encodeToCborByteArray
import com.sphereon.cbor.toCborByteString
import com.sphereon.cbor.toCborItem
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.CoseCryptoServiceImpl
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.CoseSign1Result
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.cose.CoseSign1Input
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.defaultCreateMac0
import com.sphereon.crypto.core.defaultCreateMac0UsingKeys
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.data.device.DeviceAuth
import com.sphereon.mdoc.data.device.DeviceAuthentication
import com.sphereon.mdoc.data.device.DeviceMac
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceSigned
import com.sphereon.mdoc.data.device.DeviceSignedItems
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedNameSpaces
import com.sphereon.mdoc.data.device.MacKeys
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import dev.whyoleg.cryptography.CryptographyProvider
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodec
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName
import kotlinx.coroutines.CancellationException

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<MdocSignService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocSignServiceImpl", exact = true)
class MdocSignServiceImpl(
    val coseCryptoService: CoseCryptoServiceImpl,
    val execution: SessionExecution,
    val mobileSecurityObjectCborCodec: MobileSecurityObjectCborCodec,
    val sessionTranscriptCborCodec: SessionTranscriptCborCodec,
) : MdocSignService {
    val log = execution.log.logManager.withTag("MdocSignService")

    override suspend fun issuerSignMso(
        mso: MobileSecurityObject,
        issuerKeyInfo: ManagedKeyInfoType<*>,
        signatureAlgorithm: SignatureAlgorithm?,
        unprotectedHeader: CoseHeaderCbor?,
        protectedHeader: CoseHeaderCbor?,
        requireDeviceX5Chain: Boolean,
    ): CoseSign1Result<MobileSecurityObject> {
        // Just an assertion it is present
        getSuppliedOrMSODerivedCborKeyInfo(mso = mso)

        val cborIssuerSignKeyInfo: ManagedKeyInfoType<CoseKeyType> =
            ManagedKeyInfo(
                alias = issuerKeyInfo.alias,
                providerId = issuerKeyInfo.providerId,
                resolvedKeyInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(issuerKeyInfo),
            )

        val alg = (
            signatureAlgorithm ?: issuerKeyInfo.signatureAlgorithm ?: cborIssuerSignKeyInfo.key.alg?.let {
                SignatureAlgorithm.Companion.fromCose(CoseAlgorithm.Companion.fromValue(toIntExact(it.value, "COSE alg")))
            }
        )

        val protected = CoseHeaderCbor.Companion.copyOrInit(protectedHeader, alg = alg?.cose)
        val unprotected = CoseHeaderCbor.Companion.copyOrInit(unprotectedHeader, alg = null)
        val kidVal = cborIssuerSignKeyInfo.kid ?: cborIssuerSignKeyInfo.key.kid
        val kid =
            if (kidVal is String) {
                kidVal.toCborByteString(Encoding.BASE64URL)
            } else if (kidVal is CborByteString) {
                kidVal
            } else {
                null
            }
        val x5cStr = cborIssuerSignKeyInfo.key.getX509CertificateChain()
        if (unprotected.x5chain == null && x5cStr != null) {
            unprotected.x5chain = x5cStr.encodeToCborByteArray(Encoding.BASE64) // Base64 not base64url for x5c!
        }
        if (kid !== null) {
            require(protected.kid == null || kid === protected.kid) { "Mismatch between key info kid ${cborIssuerSignKeyInfo.kid} and key kid $kid" }
            protected.kid = kid
        }
        requireNotNull(unprotected.x5chain) { "x5chain in unprotected header is required for issuer signing according to 9.1.2.4" }

        val input =
            CoseSign1Input
                .Builder()
                .withPayload(mobileSecurityObjectCborCodec.encode(mso).getOrThrow())
                .withEncodePayloadAsDataItem(true)
                .withProtectedHeader(protected)
                .withUnprotectedHeader(unprotected)
                .build()
        val signResult =
            coseCryptoService.sign1<MobileSecurityObject>(
                input = input,
                keyInfo = cborIssuerSignKeyInfo,
                requireX5Chain = requireDeviceX5Chain,
            )
        return signResult
    }

    override suspend fun issuerSignIssuerSigned(
        mso: MobileSecurityObject,
        issuerSignedNameSpaces: IssuerSignedNameSpaces,
        issuerKeyInfo: ManagedKeyInfoType<*>,
        signatureAlgorithm: SignatureAlgorithm?,
        unprotectedHeader: CoseHeaderCbor?,
        protectedHeader: CoseHeaderCbor?,
        requireDeviceX5Chain: Boolean,
    ): IssuerSigned {
        val signResult = issuerSignMso(mso, issuerKeyInfo, signatureAlgorithm, unprotectedHeader, protectedHeader, requireDeviceX5Chain)
        return IssuerSigned(nameSpaces = issuerSignedNameSpaces, issuerAuth = signResult.coseSign1, original = null)
    }

    override suspend fun issuerSignDocument(
        mso: MobileSecurityObject,
        issuerSignedNameSpaces: IssuerSignedNameSpaces,
        issuerKeyInfo: ManagedKeyInfoType<*>,
        signatureAlgorithm: SignatureAlgorithm?,
        unprotectedHeader: CoseHeaderCbor?,
        protectedHeader: CoseHeaderCbor?,
        requireDeviceX5Chain: Boolean,
    ): Document =
        Document(
            docType = mso.docType,
            issuerSigned =
                issuerSignIssuerSigned(
                    mso,
                    issuerSignedNameSpaces,
                    issuerKeyInfo,
                    signatureAlgorithm,
                    unprotectedHeader,
                    protectedHeader,
                    requireDeviceX5Chain,
                ),
            deviceSigned = null,
            original = null,
        )

    override suspend fun deviceSignDocument(
        request: DocRequest,
        document: Document,
        deviceAuthentication: DeviceAuthentication,
        deviceKeyInfo: KeyInfoType<*>?,
        unprotectedHeader: CoseHeaderCbor?,
        protectedHeader: CoseHeaderCbor?,
        requireDeviceX5Chain: Boolean,
        macKeys: MacKeys?,
    ): Document {
        log.info("Device signing document: ${document.docType}")
        if (request.itemsRequest.docType != document.docType) {
            log.error("Document request docType ${request.itemsRequest.docType} does not match document docType ${document.docType}")
            throw IllegalArgumentException("Document request docType ${request.itemsRequest.docType} does not match document docType ${document.docType}")
        }
        val mso = decodeMso(document)
        val keyInfo = getDeviceSigningKeyInfo(keyInfo = deviceKeyInfo, mso = mso)
        log.info("Using keyInfo: $keyInfo")

        // DeviceRequest.macKeys is propagated by the request/response processor as an explicit
        // parameter.  Its presence selects ISO mdoc MAC authentication; an empty list is treated
        // as no capability advertisement and retains the legacy signature path.
        macKeys?.takeIf { it.isNotEmpty() }?.let { advertisedMacKeys ->
            val resolvedDeviceKeyInfo =
                keyInfo as? ResolvedKeyInfoType<CoseKeyType>
                    ?: throw IllegalArgumentException(
                        "Cannot create mdoc deviceMac: managed device keys cannot be used without private key material.",
                    )
            return createMacAuthenticatedDocument(
                request = request,
                document = document,
                deviceAuthentication = deviceAuthentication,
                deviceKeyInfo = resolvedDeviceKeyInfo,
                advertisedMacKeys = advertisedMacKeys,
            )
        }

        var signatureAlgorithm =
            keyInfo.signatureAlgorithm ?: keyInfo.key?.alg?.let {
                SignatureAlgorithm.Companion.fromCose(CoseAlgorithm.Companion.fromValue(toIntExact(it.value, "COSE alg")))
            }
        val alg = protectedHeader?.alg
        if (alg !== null) {
            signatureAlgorithm = SignatureAlgorithm.Companion.fromCose(alg)
        }
        log.debug("Signature algorithm: $signatureAlgorithm")

        log.info("Before sign doc MSO: $mso")

        // 18013-5 "9.1.3.6" requires the alg as the only element in the protected header

        val protected = CoseHeaderCbor.Companion.copyOrInit(protectedHeader, alg = signatureAlgorithm?.cose)
        /*val kidVal = keyInfo.kid ?: keyInfo.key.kid
        val kid = if (kidVal is String) kidVal.toCborByteString(Encoding.BASE64URL) else if (kidVal is CborByteString) kidVal else null
        val x5cStr = keyInfo.key.getX509CertificateChain()
        if (protected.x5chain == null && x5cStr != null) {
            protected.x5chain = x5cStr.encodeToCborByteArray(Encoding.BASE64) // Base64 not base64url for x5c!
        }
        if (kid !== null) {
            if (protected.kid != null && kid !== protected.kid) {
                throw IllegalArgumentException("Mismatch between key info kid ${keyInfo.kid} and key kid $kid")
            }
            protected.kid = kid
        }*/

        val input =
            CoseSign1Input
                .Builder()
                .withPayload(encodeDeviceAuthentication(deviceAuthentication))
                .withEncodePayloadAsDataItem(true)
                .withProtectedHeader(protected)
                .withUnprotectedHeader(unprotectedHeader)
                .build()

        log.debug("Input: ${input.payload}")
        val signResult =
            coseCryptoService.sign1<DeviceAuthentication>(
                input = input,
                keyInfo = keyInfo,
                requireX5Chain = requireDeviceX5Chain,
            )
        log.debug("Signresult: $signResult")
        val deviceSignature = signResult.coseSign1.detachedPayloadCopy()
        log.info("Device signature: $deviceSignature")
        return Document(
            docType = request.itemsRequest.docType,
            deviceSigned =
                DeviceSigned(
                    nameSpaces = deviceAuthentication.deviceNamespaces,
                    deviceAuth = DeviceAuth(deviceSignature = deviceSignature, original = null),
                    original = null,
                ),
            issuerSigned = document.limitDisclosures(request),
            original = null,
        ).also { log.info("Device signed document done: ${it.docType}") }
    }

    /**
     * Creates the ISO detached COSE_Mac0 form using the reader-advertised MAC keys.
     *
     * The device private key remains in the resolved key information and is never exported or
     * logged.  The generic crypto-core helper performs ECDH and HKDF with the ISO EMacKey info
     * value; this class only supplies the mdoc DeviceAuthentication payload and envelope.
     */
    private suspend fun createMacAuthenticatedDocument(
        request: DocRequest,
        document: Document,
        deviceAuthentication: DeviceAuthentication,
        deviceKeyInfo: ResolvedKeyInfoType<CoseKeyType>,
        advertisedMacKeys: MacKeys,
    ): Document {
        require(deviceKeyInfo.key.d != null) {
            "Cannot create mdoc deviceMac: the selected device key does not contain private key material."
        }

        val detachedPayload =
            CborEncodedItem<Any>(encodeDeviceAuthentication(deviceAuthentication)).value.toBstr().value
        var lastFailure: IllegalArgumentException? = null

        for ((index, readerKey) in advertisedMacKeys.withIndex()) {
            if (readerKey.d != null) {
                lastFailure = IllegalArgumentException("advertised macKeys[$index] must be a public key")
                continue
            }
            val attempt =
                tryCreateMacAuthenticatedDocumentForReader(
                    request = request,
                    document = document,
                    deviceAuthentication = deviceAuthentication,
                    deviceKeyInfo = deviceKeyInfo,
                    readerKey = readerKey,
                    detachedPayload = detachedPayload,
                    index = index,
                )
            val signedDocument = attempt.getOrNull()
            if (signedDocument != null) {
                return signedDocument
            }
            lastFailure = attempt.exceptionOrNull() as? IllegalArgumentException
        }

        throw IllegalArgumentException(
            "Cannot create mdoc deviceMac: no compatible reader MAC key was advertised.",
            lastFailure,
        )
    }

    /**
     * Performs one reader-key MAC attempt in its own suspend frame.
     *
     * Keeping cancellation rethrow and retryable argument handling out of the reader loop avoids
     * a GraalVM Native Image exception-frame merge failure in this suspend function.
     */
    private suspend fun tryCreateMacAuthenticatedDocumentForReader(
        request: DocRequest,
        document: Document,
        deviceAuthentication: DeviceAuthentication,
        deviceKeyInfo: ResolvedKeyInfoType<CoseKeyType>,
        readerKey: CoseKeyType,
        detachedPayload: ByteArray,
        index: Int,
    ): Result<Document> =
        try {
            val macResult =
                defaultCreateMac0UsingKeys(
                    provider = CryptographyProvider.Default,
                    input =
                        com.sphereon.crypto.core.cose.CoseMac0InputCbor(
                            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.HMAC256_256),
                            detachedPayload = detachedPayload,
                        ),
                    selfPrivateKey = deviceKeyInfo,
                    otherPublicKey = ResolvedKeyInfo(key = readerKey),
                    alg = SignatureAlgorithm.HMAC_SHA256,
                    info = "EMacKey",
                    salt = byteArrayOf(),
                ) { provider, input, sharedSecret, alg ->
                    defaultCreateMac0(
                        input = input,
                        sharedSecret = sharedSecret,
                        alg = alg,
                        provider = provider,
                    )
                }

            Result.success(
                Document(
                    docType = request.itemsRequest.docType,
                    deviceSigned =
                        com.sphereon.mdoc.data.device.DeviceSigned(
                            nameSpaces = deviceAuthentication.deviceNamespaces,
                            deviceAuth =
                                DeviceAuth(
                                    deviceMac = DeviceMac.fromCoseMac0(macResult.coseMac0.detachedPayloadCopy()),
                                    original = null,
                                ),
                            original = null,
                        ),
                    issuerSigned = document.limitDisclosures(request),
                    original = null,
                ).also { log.info("Device signed document with COSE_Mac0 using reader MAC key index $index") },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }

    private fun decodeMso(document: Document): MobileSecurityObject {
        val payload =
            document.issuerSigned.issuerAuth.payload
                ?.value
                ?: throw IllegalArgumentException("Payload is null for MSO, that is not allowed")
        return mobileSecurityObjectCborCodec.decode(payload).getOrThrow().value
    }

    private fun encodeDeviceAuthentication(value: DeviceAuthentication): ByteArray {
        value.original?.let { return it }

        val sessionTranscriptItem: CborItem<*> =
            com.sphereon.cbor.Cbor
                .tryDecode(
                    sessionTranscriptCborCodec.encode(value.sessionTranscript).getOrThrow(),
                ).getOrThrow()

        return com.sphereon.cbor.Cbor.encode(
            CborArray(
                mutableListOf(
                    CborString("DeviceAuthentication"),
                    sessionTranscriptItem,
                    CborString(value.docType.toString()),
                    CborEncodedItem<CborMap<CborString, CborMap<CborString, CborItem<*>>>>(
                        com.sphereon.cbor.Cbor
                            .encode(encodeDeviceNameSpaces(value.deviceNamespaces)),
                    ),
                ),
            ),
        )
    }

    private fun encodeDeviceNameSpaces(value: DeviceNameSpaces): CborMap<CborString, CborMap<CborString, CborItem<*>>> =
        CborMap(
            value.value.entries
                .associate { (nameSpace, items) ->
                    CborString(nameSpace.toString()) to encodeDeviceSignedItems(items)
                }.toMutableMap(),
        )

    private fun encodeDeviceSignedItems(value: DeviceSignedItems): CborMap<CborString, CborItem<*>> =
        CborMap(
            value.value.entries
                .associate { (identifier, elementValue) ->
                    CborString(identifier.toString()) to elementValue.toCborItem()
                }.toMutableMap(),
        )

    companion object Utils {
        @JsStatic
        fun getSuppliedOrMSODerivedCborKeyInfo(
            keyInfo: KeyInfoType<*>? = null,
            mso: MobileSecurityObject? = null,
            lookupManaged: Boolean = false,
        ): ResolvedKeyInfoType<CoseKeyType> {
            val msoInfo: ResolvedKeyInfoType<CoseKey>? =
                mso?.deviceKeyInfo?.deviceKey?.let {
                    ResolvedKeyInfo(
                        key = it,
                        kid = it.kid?.encodeValueTo(Encoding.UTF8) ?: keyInfo?.kid,
                        signatureAlgorithm =
                            it.alg?.let { alg -> SignatureAlgorithm.Companion.fromCose(CoseAlgorithm.Companion.fromValue(toIntExact(alg.value, "COSE alg"))) }
                                ?: keyInfo?.signatureAlgorithm,
                    )
                }
            // The above object already takes passed in sig algo into account as fallback
            val signatureAlgorithm =
                msoInfo?.signatureAlgorithm ?: keyInfo?.signatureAlgorithm ?: keyInfo?.key?.getSignatureAlgorithm()
            val key = (keyInfo?.key?.let { CoseJoseKeyMappingService.toCoseKey(it) } ?: msoInfo?.key)
            require(key != null) { "No key information provided and it could not be derived from the Mobile Security Object" }
            val kty =
                signatureAlgorithm?.cose?.keyType?.let { CborUInt(it.value.toLong()) }
                    ?: key.kty
                    ?: throw IllegalArgumentException("kty could not be resolved for Cbor Key!")
            val crv = key.crv ?: signatureAlgorithm?.cose?.curve?.let { CborUInt(it.value.toLong()) }
            val resolvedKey = key.copy(kid = key.kid ?: msoInfo?.kid?.toCborByteString() ?: keyInfo?.kid?.toCborByteString(), kty = kty, crv = crv)

            if (keyInfo != null) {
                return CoseJoseKeyMappingService.toResolvedCoseKeyInfo(CoseJoseKeyMappingService.toResolvedKeyInfo(keyInfo, resolvedKey)).copy(
                    signatureAlgorithm = signatureAlgorithm,
                    kid = keyInfo.kid ?: msoInfo?.kid,
                    keyType = KeyTypeMapping.Companion.fromCose(CoseKeyTypeEnum.Companion.fromValue(kty.value)),
                    x5c = resolvedKey.getX509CertificateChain() ?: keyInfo.x5c,
                )
            } else {
                require(msoInfo != null) { "No key information provided and it could not be derived from the Mobile Security Object" }
            }
            if (lookupManaged && msoInfo.alias === null) { // No-op
            }
            return requireNotNull(msoInfo) { "No key information provided and it could not be derived from the Mobile Security Object" }
        }

        /**
         * Resolves the key used for ISO DeviceAuth while preserving the MSO holder binding.
         *
         * A COSE `kid` is an opaque byte string on the wire. The IDK's established COSE/JWK
         * mapping represents that byte string as raw UTF-8 text in [KeyInfoType.kid]. Managed
         * signing therefore carries the KMS alias and that exact text selector, but never the
         * public-only MSO key as inline signing material.
         */
        @JsStatic
        fun getDeviceSigningKeyInfo(
            keyInfo: KeyInfoType<*>? = null,
            mso: MobileSecurityObject,
        ): KeyInfoType<CoseKeyType> {
            val msoKey = mso.deviceKeyInfo.deviceKey
            val msoKid = msoKey.kid?.encodeValueTo(Encoding.UTF8)
            val resolved = getSuppliedOrMSODerivedCborKeyInfo(keyInfo = keyInfo, mso = mso)

            require(publicKeyMaterialMatches(resolved.key, msoKey)) {
                "Supplied mdoc device key does not match Mobile Security Object device key"
            }

            val suppliedKid = keyInfo?.kid ?: keyInfo?.key?.getKeyId(false)
            if (suppliedKid != null && msoKid != null) {
                require(suppliedKid == msoKid) {
                    "Device signing key kid '$suppliedKid' does not match Mobile Security Object device key kid '$msoKid'"
                }
            }

            val managedSigning =
                keyInfo != null &&
                    keyInfo.key?.d == null &&
                    (keyInfo.alias != null || keyInfo.key == null)
            if (!managedSigning) {
                return resolved
            }

            require(!resolved.alias.isNullOrBlank()) {
                "Managed mdoc device signing requires a non-blank key alias"
            }
            require(msoKid != null) {
                "Managed mdoc device signing requires a kid in the Mobile Security Object device key"
            }

            return KeyInfo(
                kid = msoKid,
                key = null,
                opts = resolved.opts,
                keyVisibility = KeyVisibility.PRIVATE,
                signatureAlgorithm = resolved.signatureAlgorithm ?: msoKey.getSignatureAlgorithm(),
                x5c = resolved.x5c,
                alias = resolved.alias,
                providerId = resolved.providerId,
                keyType = resolved.keyType ?: msoKey.getKeyType(),
                keyEncoding = resolved.keyEncoding,
                noCache = resolved.noCache,
            )
        }

        private fun publicKeyMaterialMatches(
            supplied: CoseKeyType,
            authenticated: CoseKeyType,
        ): Boolean {
            if (supplied.kty != authenticated.kty) {
                return false
            }
            return when (CoseKeyTypeEnum.fromValue(authenticated.kty.value)) {
                CoseKeyTypeEnum.EC2 ->
                    supplied.crv == authenticated.crv &&
                        supplied.x == authenticated.x &&
                        supplied.y == authenticated.y
                CoseKeyTypeEnum.OKP -> supplied.crv == authenticated.crv && supplied.x == authenticated.x
                CoseKeyTypeEnum.RSA -> supplied.n == authenticated.n && supplied.rsaE == authenticated.rsaE
                CoseKeyTypeEnum.Symmetric,
                CoseKeyTypeEnum.Reserved,
                -> false
            }
        }
    }
}

private fun toIntExact(
    value: Long,
    field: String,
): Int {
    require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$field is outside the Int range" }
    return value.toInt()
}

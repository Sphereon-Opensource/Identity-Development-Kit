package com.sphereon.mdoc

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.encodeToCborByteArray
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.CoseCryptoServiceImpl
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.CoseSign1Result
import com.sphereon.crypto.core.KeyInfoType
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
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.data.device.DeviceAuth
import com.sphereon.mdoc.data.device.DeviceAuthentication
import com.sphereon.mdoc.data.device.DeviceSigned
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedNameSpaces
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<MdocSignService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocSignServiceImpl", exact = true)
class MdocSignServiceImpl(val coseCryptoService: CoseCryptoServiceImpl, val execution: SessionExecution) : MdocSignService {

    val log = execution.log.logManager.withTag("MdocSignService")
    companion object Utils {
        @JsStatic
        fun getSuppliedOrMSODerivedCborKeyInfo(
            keyInfo: KeyInfoType<*>? = null,
            mso: MobileSecurityObject? = null,
            lookupManaged: Boolean = false
        ): ResolvedKeyInfoType<CoseKeyType> {

            val msoInfo: ResolvedKeyInfoType<CoseKey>? = mso?.deviceKeyInfo?.deviceKey?.let {
                ResolvedKeyInfo(
                    key = it,
                    kid = it.kid?.encodeValueTo(Encoding.BASE64URL) ?: keyInfo?.kid,
                    signatureAlgorithm = it.alg?.let { alg -> SignatureAlgorithm.Companion.fromCose(CoseAlgorithm.Companion.fromValue(alg.value.toInt())) }
                        ?: keyInfo?.signatureAlgorithm,
                )
            }
            val signatureAlgorithm = msoInfo?.signatureAlgorithm ?: keyInfo?.signatureAlgorithm ?: keyInfo?.key?.getSignatureAlgorithm() // The above object already takes passed in sig algo into account as fallback
            val key = (keyInfo?.key?.let{ CoseJoseKeyMappingService.toCoseKey(it)} ?: msoInfo?.key)
            if (key == null) {
                throw IllegalArgumentException("No key information provided and it could not be derived from the Mobile Security Object")
            }
            val kty = signatureAlgorithm?.cose?.keyType?.toCbor() ?: key.kty ?: throw IllegalArgumentException("kty could not be resolved for Cbor Key!")
            val crv = key.crv ?: signatureAlgorithm?.cose?.curve?.toCbor()
            val resolvedKey = key.copy(kid = key.kid ?: msoInfo?.kid?.toCborByteString() ?: keyInfo?.kid?.toCborByteString(), kty = kty, crv = crv )

            if (keyInfo != null) {
                return CoseJoseKeyMappingService.toResolvedCoseKeyInfo(CoseJoseKeyMappingService.toResolvedKeyInfo(keyInfo, resolvedKey)).copy(
                    signatureAlgorithm = signatureAlgorithm,
                    kid = keyInfo.kid ?: msoInfo?.kid,
                    keyType = KeyTypeMapping.Companion.fromCose(CoseKeyTypeEnum.Companion.fromValue(kty.value)),
                    x5c = resolvedKey.getX509CertificateChain() ?: keyInfo.x5c,
                )
            } else if (msoInfo == null) {
                throw IllegalArgumentException("No key information provided and it could not be derived from the Mobile Security Object")
            }
            if (lookupManaged && msoInfo.alias === null) {

            }
            return msoInfo
        }
    }

    override suspend fun issuerSignMso(
        mso: MobileSecurityObject,
        issuerKeyInfo: ManagedKeyInfoType<*>,
        signatureAlgorithm: SignatureAlgorithm?,
        unprotectedHeader: CoseHeaderCbor?,
        protectedHeader: CoseHeaderCbor?,
        requireDeviceX5Chain: Boolean
    ): CoseSign1Result<MobileSecurityObject> {

        // Just an assertion it is present
        getSuppliedOrMSODerivedCborKeyInfo(mso = mso)

        val cborIssuerSignKeyInfo: ManagedKeyInfoType<CoseKeyType> = ManagedKeyInfo(
            alias = issuerKeyInfo.alias,
            providerId = issuerKeyInfo.providerId,
            resolvedKeyInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(issuerKeyInfo)
        )

        val alg = (signatureAlgorithm ?: issuerKeyInfo.signatureAlgorithm ?: cborIssuerSignKeyInfo.key.alg?.let {
            SignatureAlgorithm.Companion.fromCose(CoseAlgorithm.Companion.fromValue(it.value.toInt()))
        })


        val protected = CoseHeaderCbor.Companion.copyOrInit(protectedHeader, alg = alg?.cose)
        val unprotected = CoseHeaderCbor.Companion.copyOrInit(unprotectedHeader, alg = null)
        val kidVal = cborIssuerSignKeyInfo.kid ?: cborIssuerSignKeyInfo.key.kid
        val kid = if (kidVal is String) kidVal.toCborByteString(Encoding.BASE64URL) else if (kidVal is CborByteString) kidVal else null
        val x5cStr = cborIssuerSignKeyInfo.key.getX509CertificateChain()
        if (unprotected.x5chain == null && x5cStr != null) {
            unprotected.x5chain = x5cStr.encodeToCborByteArray(Encoding.BASE64) // Base64 not base64url for x5c!
        }
        if (kid !== null) {
            if (protected.kid != null && kid !== protected.kid) {
                throw IllegalArgumentException("Mismatch between key info kid ${cborIssuerSignKeyInfo.kid} and key kid $kid")
            }
            protected.kid = kid
        }
        requireNotNull(unprotected.x5chain) {"x5chain in unprotected header is required for issuer signing according to 9.1.2.4"}

        val input = CoseSign1Input.Builder()
            .withPayload(mso)
            .withEncodePayloadAsDataItem(true)
            .withProtectedHeader(protected)
            .withUnprotectedHeader(unprotected)
            .build()
        val signResult = coseCryptoService.sign1<MobileSecurityObject>(
            input = input,
            keyInfo = cborIssuerSignKeyInfo,
            requireX5Chain = requireDeviceX5Chain
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
        requireDeviceX5Chain: Boolean
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
        requireDeviceX5Chain: Boolean
    ): Document {
        return Document(
            docType = mso.docType,
            issuerSigned = issuerSignIssuerSigned(
                mso,
                issuerSignedNameSpaces,
                issuerKeyInfo,
                signatureAlgorithm,
                unprotectedHeader,
                protectedHeader,
                requireDeviceX5Chain
            ),
            deviceSigned = null,
            original = null
        )
    }

    override suspend fun deviceSignDocument(
        request: DocRequest,
        document: Document,
        deviceAuthentication: DeviceAuthentication,
        deviceKeyInfo: KeyInfoType<*>?,
        unprotectedHeader: CoseHeaderCbor?,
        protectedHeader: CoseHeaderCbor?,
        requireDeviceX5Chain: Boolean
    ): Document {
        log.info("Device signing document: ${document.docType}")
        if (request.itemsRequest.docType != document.docType) {
            log.error("Document request docType ${request.itemsRequest.docType} does not match document docType ${document.docType}")
            throw IllegalArgumentException("Document request docType ${request.itemsRequest.docType} does not match document docType ${document.docType}")
        }
        val keyInfo = getSuppliedOrMSODerivedCborKeyInfo(keyInfo = deviceKeyInfo, mso = document.MSO)
        log.info("Using keyInfo: $keyInfo")
        var signatureAlgorithm = keyInfo.signatureAlgorithm ?: keyInfo.key.alg?.let {
            SignatureAlgorithm.Companion.fromCose(CoseAlgorithm.Companion.fromValue(it.value.toInt()))
        }
        val alg = protectedHeader?.alg
        if (alg !== null) {
            signatureAlgorithm = SignatureAlgorithm.Companion.fromCose(alg)
        }
        log.debug("Signature algorithm: $signatureAlgorithm")

        log.info("Before sign doc MSO: ${document.MSO}")

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

        val input = CoseSign1Input.Builder()
            .withPayload(deviceAuthentication)
            .withEncodePayloadAsDataItem(true)
            .withProtectedHeader(protected)
            .withUnprotectedHeader(unprotectedHeader)
            .build()

        log.debug("Input: ${input.payload}")
        val signResult = coseCryptoService.sign1<DeviceAuthentication>(
            input = input,
            keyInfo = keyInfo,
            requireX5Chain = requireDeviceX5Chain
        )
        log.debug("Signresult: $signResult")
        val deviceSignature = signResult.coseSign1.detachedPayloadCopy()
        log.info("Device signature: $deviceSignature")
        return Document(
            docType = request.itemsRequest.docType,
            deviceSigned = DeviceSigned(
                nameSpaces = deviceAuthentication.deviceNamespaces,
                deviceAuth = DeviceAuth(deviceSignature = deviceSignature, original = null),
                original = null
            ),
            issuerSigned = document.limitDisclosures(request),
            original = null
        ).also { log.info("Device signed document done: ${it.docType}")}
    }
}
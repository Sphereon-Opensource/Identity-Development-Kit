/*
 * © 2025 Sphereon International B.V.
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

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborNull
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.toCborByteString
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.cbor.json.JsonView
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.js.JsStatic

@JsExportCompat
data class CoseSign1Input(
    // This value is required in the eventual to be signed object, but can be filled using key info
    val protectedHeader: CoseHeaderCbor? = null,

    val unprotectedHeader: CoseHeaderCbor? = null,

    val payload: CborByteString,
) : CborStructure<CoseSign1Input, CborArray<CborItem<*>>>(
    CDDL.list
) {



    companion object {
        @JsStatic
        @JsName("fromCborItem")
        fun fromCborItem(a: CborArray<CborItem<*>>): CoseSign1Input {
            val protectedHeaderBytes: CborByteString = a.required(0) // required. See above notice why the property is optional above
            val unprotectedHeaders = a.optional<CborMap<NumberLabel, CborItem<*>>>(1)
//            val payloadAvailable = a.value[2].value != null
            return CoseSign1Input(
                CoseHeaderCbor.fromCborItem(protectedHeaderBytes.cborDecodeValue()),
                unprotectedHeaders?.let { CoseHeaderCbor.fromCborItem(it) },
                a.required(2),
//                if (payloadAvailable) a.required(2) else null,
            )
        }

        @JsStatic
        @JsName("decodeCbor")
        fun decodeCbor(encoded: ByteArray) =
            fromCborItem(cborSerializer.decode(encoded))
    }

    fun toSignature1Structure() = CoseSignatureStructureCbor(
        structure = SigStructure.Signature1.toCbor(),
        externalAad = CborByteString(byteArrayOf()),
        bodyProtected = if (protectedHeader !== null) CborByteString(protectedHeader.encodeCbor()) else CborByteString(byteArrayOf()), // throw in case no header?
        payload = payload
    )

    @JsName("toBeSignedCbor")
    fun toBeSignedCbor(keyInfo: KeyInfoType<*>, alg: SignatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256) =
        ToBeSignedCbor(value = toSignature1Structure().encodeCbor(), keyInfo = keyInfo, alg = alg)

    @JsName("toBeSignedJson")
    fun toBeSignedJson(keyInfo: KeyInfoType<*>, alg: SignatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256) =
        ToBeSignedJson(base64UrlValue = toSignature1Structure().encodeCbor().encodeTo(Encoding.BASE64URL), keyInfo = keyInfo, alg = alg)

    class Builder(
        private var protectedHeader: CoseHeaderCbor? = CoseHeaderCbor(),
        private var unprotectedHeader: CoseHeaderCbor? = null,
        private var payload: CborStructure<*, *>? = null,
        private var encodePayloadAsDataItem: Boolean = false,
    ) {

        fun withProtectedHeader(protectedHeader: CoseHeaderCbor) = apply { this.protectedHeader = protectedHeader }
        fun withUnprotectedHeader(unprotectedHeader: CoseHeaderCbor?) = apply { this.unprotectedHeader = unprotectedHeader }
        fun withPayload(payload: CborStructure<*, *>) = apply { this.payload = payload }

        fun withEncodePayloadAsDataItem(encodePayloadAsDataItem: Boolean = true) = apply { this.encodePayloadAsDataItem = encodePayloadAsDataItem }

        fun build(): CoseSign1Input {
            val content = if (encodePayloadAsDataItem) {
                payload?.let { CborEncodedItem.fromData(it).encodeCbor().toCborByteString().also { println("SIGNING Encoded payload: ${it.encodeCbor().encodeToHex()}")} }
            } else {
                payload?.encodeCbor()?.toCborByteString().also { println("SIGNING NON Encoded payload: ${it?.encodeCbor()?.encodeToHex()}") }
            }
            if (content === null) {
                throw IllegalArgumentException("Payload is required")
            }

            return CoseSign1Input(
                payload = content,
                unprotectedHeader = unprotectedHeader,
                protectedHeader = protectedHeader
            ).also { println("COSE SIGN1 Input cbor: ${it.encodeCbor().encodeToHex()}") }
        }
    }


    override fun cborBuilder(): CborBuilder<CoseSign1Input> {
        return CborArray.builder(this)
            .add(protectedHeader?.toCborStructure())
            .add(unprotectedHeader?.toCborStructure())
            .add(payload ?: CborNull())
            .end()
    }

    override fun toString(): String {
        return "CoseSign1InputCbor(protectedHeader=$protectedHeader, unprotectedHeader=$unprotectedHeader, payload=$payload)"
    }

}


@JsExportCompat
data class CoseSign1<CborType : Any>(


    val protectedHeader: CoseHeaderCbor,

    val unprotectedHeader: CoseHeaderCbor?,

    val payload: CborByteString?,

    val signature: CborByteString,

    ) : CborStructure<CoseSign1<CborType>, CborArray<CborItem<*>>>(CDDL.list) {
    /* fun cborDecodePayload(convertFunction: (arg: CborItem<*>) -> CborType): CborType? {
         val result = payload?.value?.let { cborSerializer.decode<CborEncodedItem<CborItem<*>>>(it) }?.decodedValue
         return result?.let { convertFunction.invoke(it) }
     }
 */
    fun toSignature1Structure() = CoseSignatureStructureCbor(
        structure = SigStructure.Signature1.toCbor(),
        externalAad = CborByteString(byteArrayOf()),
        bodyProtected = CborByteString(protectedHeader.encodeCbor()),
        payload = payload ?: throw IllegalArgumentException("No payload present")
    )

    @JsName("toBeSignedCbor")
    fun toBeSignedCbor(keyInfo: KeyInfoType<*>, alg: SignatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256) =
        ToBeSignedCbor(value = toSignature1Structure().encodeCbor(), keyInfo = keyInfo, alg = alg)

    @JsName("toBeSignedJson")
    fun toBeSignedJson(keyInfo: KeyInfoType<*>, alg: SignatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256) =
        ToBeSignedJson(base64UrlValue = toSignature1Structure().encodeCbor().encodeTo(Encoding.BASE64URL), keyInfo = keyInfo, alg = alg)

    fun detachedPayloadCopy(): CoseSign1<CborType> {
        return this.copy(payload = null)
    }

    companion object {
        @JsStatic
        @JsName("fromCborItem")
        fun <CborType : Any> fromCborItem(a: CborArray<CborItem<*>>): CoseSign1<CborType> {
            val protectedHeaderBytes: CborByteString = a.required(0)
            val unprotectedHeaders = a.optional<CborMap<NumberLabel, CborItem<*>>>(1)
            val payloadAvailable = a.value[2].value != null
            return CoseSign1(
                CoseHeaderCbor.fromCborItem(protectedHeaderBytes.cborDecodeValue()),
                unprotectedHeaders?.let { CoseHeaderCbor.fromCborItem(it) },
                if (payloadAvailable) a.required(2) else null,
                a.required(3)
            )
        }

        @JsStatic
        @JsName("decodeCbor")
        fun <CborType : Any> decodeCbor(encoded: ByteArray) =
            fromCborItem<CborType>(cborSerializer.decode(encoded))
    }

    override fun cborBuilder(): CborBuilder<CoseSign1<CborType>> {
        return CborArray.builder(this).add(CborByteString(protectedHeader.encodeCbor()))
            .add(unprotectedHeader?.toCborStructure() ?: CoseHeaderCbor().toCborStructure()).add(payload ?: CborNull())
            .add(signature)
            .end()
    }


    override fun hashCode(): Int {
        var result = protectedHeader.hashCode()
        result = 31 * result + (unprotectedHeader?.hashCode() ?: 0)
        result = 31 * result + (payload?.hashCode() ?: 0)
        result = 31 * result + signature.hashCode()
        return result
    }

    override fun toString(): String {
        return "CoseSign1Cbor(protectedHeader=$protectedHeader, unprotectedHeader=$unprotectedHeader, payload=$payload, signature=$signature)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CoseSign1<*>

        if (protectedHeader != other.protectedHeader) return false
        if (unprotectedHeader != other.unprotectedHeader) return false
        if (payload != other.payload) return false
        if (signature != other.signature) return false

        return true
    }


}

typealias COSE_Sign1<CborType> = CoseSign1<CborType>


@JsExportCompat
sealed class SigStructure(val value: String) {
    object Signature : SigStructure("Signature")
    object Signature1 : SigStructure("Signature1")
    object CounterSignature : SigStructure("CounterSignature")

    fun toCbor() = CborString(value)

    companion object {
        @JsStatic
        val asList = listOf(Signature, Signature1, CounterSignature)

        @JsStatic
        @JsName("fromValue")
        fun fromValue(value: String) = asList.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Unknown signature $value")
    }
}

@JsExportCompat
data class CoseSignatureStructureCbor(
    val structure: CborString = CborString(SigStructure.Signature1.value),
    val bodyProtected: CborByteString,
    val signProtected: CborByteString? = null,
    val externalAad: CborByteString = CborByteString(byteArrayOf()),
    val payload: CborByteString,
) : CborStructure<CoseSignatureStructureCbor, CborArray<CborItem<*>>>(CDDL.list) {
    override fun cborBuilder(): CborBuilder<CoseSignatureStructureCbor> =
        CborArray.builder(this).addRequired(structure).addRequired(bodyProtected).add(signProtected).addRequired(externalAad)
            .addRequired(payload)
            .end()



    @JsName("toBeSigned")
    fun toBeSigned(keyInfo: KeyInfoType<*>, alg: SignatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256) =
        ToBeSignedCbor(value = toCborStructure().encodeCbor(), keyInfo = keyInfo, alg = alg)

    @JsName("toBeSignedJson")
    fun toBeSignedJson(keyInfo: KeyInfoType<*>, alg: SignatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256) =
        ToBeSignedJson(base64UrlValue = toCborStructure().encodeCbor().encodeTo(Encoding.HEX), keyInfo = keyInfo, alg = alg)

    override fun toString(): String {
        return "CoseSignatureStructureCbor(structure=$structure, bodyProtected=$bodyProtected, signProtected=$signProtected, externalAad=$externalAad, payload=$payload)"
    }


    companion object {
        @JsStatic
        @JsName("fromCborItem")
        fun fromCborItem(a: CborArray<CborItem<*>>): CoseSignatureStructureCbor {

            if (a.value.size == 4) {
                return CoseSignatureStructureCbor(
                    structure = a.required(0),
                    bodyProtected = a.required(1),
                    externalAad = a.required(2),
                    payload = a.required(3)
                )
            }
            return CoseSignatureStructureCbor(
                structure = a.required(0),
                bodyProtected = a.required(1),
                signProtected = a.optional(2),
                externalAad = a.required(3),
                payload = a.required(4)
            )
        }

        @JsStatic
        fun decodeCbor(encoded: ByteArray) =
            fromCborItem(cborSerializer.decode(encoded))
    }
}

@JsExportCompat
data class ToBeSignedJson(
    val base64UrlValue: String,
    val keyInfo: KeyInfoType<*>,
    val alg: SignatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256,
) : JsonView() {
    override fun toJsonString() = cryptoJsonSerializer.encodeToString(this)
    override fun toCbor() =
        ToBeSignedCbor(base64UrlValue.decodeFromBase64Url(), keyInfo = keyInfo, alg = alg)

}

@JsExportCompat
data class ToBeSignedCbor(
    val value: ByteArray,
    val keyInfo: KeyInfoType<*>,
    val alg: SignatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256,
) :
    CborStructure<ToBeSignedCbor, CborByteString>(CDDL.bstr) {
    override fun cborBuilder(): CborBuilder<ToBeSignedCbor> = CborBuilder(CborByteString(value), this)
}

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
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborNull
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.js.JsStatic

@JsExportCompat
data class CoseMac0InputCbor(
    val protectedHeader: CoseHeaderCbor = CoseHeaderCbor(),

    val unprotectedHeader: CoseHeaderCbor? = null,

    val externalAad: ByteArray = byteArrayOf(),

    val payload: ByteArray? = null,

    val detachedPayload: ByteArray? = null,
) : CborStructure<CoseMac0InputCbor, CborArray<CborItem<*>>>(
    CDDL.list
) {

    fun toMac0Structure() = CoseMacStructureCbor(
        context = CborString(MacContext.Mac0.value),
        protected = CborByteString(protectedHeader.encodeCbor()),
        externalAad = CborByteString(externalAad),
        payload = CborByteString(payload ?: detachedPayload ?: throw IllegalStateException("Payload or detached payload is required")),
    )

    /*@JsName("toBeMacedCbor")
    fun toBeMacedCbor(sharedSecret: ByteArray, alg: SignatureAlgorithm = SignatureAlgorithm.HMAC_SHA256) =
        ToBeMacedCbor(macStructureBytes = toMac0Structure().cborEncode(), secret = sharedSecret, alg = alg, input = this)


    @JsName("toBeMacedJson")
    fun toBeMacedJson(sharedSecretAsBase64Url: String, alg: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256) = ToBeMacedJson(
        macStructureAsBase64Url = toMac0Structure().cborEncode().encodeToBase64Url(),
        secretAsBase64Url = sharedSecretAsBase64Url,
        alg = alg,
        input = toJson()
    )
*/
    class Builder(
        private var protectedHeader: CoseHeaderCbor = CoseHeaderCbor(),
        private var unprotectedHeader: CoseHeaderCbor? = null,
        private var payload: ByteArray? = null,
        private var detachedPayload: ByteArray? = null,
        private var externalAad: ByteArray? = null,
    ) {

        fun withProtectedHeader(protectedHeader: CoseHeaderCbor) = apply { this.protectedHeader = protectedHeader }
        fun withUnprotectedHeader(unprotectedHeader: CoseHeaderCbor) = apply { this.unprotectedHeader = unprotectedHeader }
        fun withPayloadFromStructure(payload: CborStructure<*, *>) = apply { this.payload = payload.encodeCbor() }
        fun withPayload(payload: ByteArray) = apply { this.payload = payload }
        fun withDetachedPayload(detachedPayload: ByteArray) = apply { this.detachedPayload = detachedPayload }
        fun withExternalAad(externalAad: ByteArray) = apply { this.externalAad = externalAad }

        fun build(): CoseMac0InputCbor {
            return CoseMac0InputCbor(
                payload = payload,
                detachedPayload = detachedPayload,
                protectedHeader = protectedHeader,
                unprotectedHeader = unprotectedHeader,
                externalAad = externalAad ?: byteArrayOf()
            )
        }
    }


    override fun cborBuilder(): CborBuilder<CoseMac0InputCbor> {
        throw NotImplementedError("CoseMac0Input Cbor encoding is not needed/possible at present")
    }


}


@JsExportCompat
data class CoseMac0Cbor(
    val protectedHeader: CoseHeaderCbor,

    val unprotectedHeader: CoseHeaderCbor?,

    val payload: CborByteString? = null,

    val tag: CborByteString,

    ) : CborStructure<CoseMac0Cbor, CborArray<CborItem<*>>>(CDDL.list) {

    fun toMac0Structure(detachedContent: ByteArray? = null) = CoseMacStructureCbor(
        context = CborString(MacContext.Mac0.value),
        externalAad = CborByteString(byteArrayOf()),
        protected = CborByteString(protectedHeader.encodeCbor()),
        payload = payload ?: detachedContent?.let { CborByteString(it) } ?: throw IllegalArgumentException("No payload present"))

    /*@JsName("toBeMacedCbor")
    fun toBeMacedCbor(sharedSecret: ByteArray, alg: SignatureAlgorithm = SignatureAlgorithm.HMAC_SHA256, input: CoseMac0InputCbor) = ToBeMacedCbor(
        macStructureBytes = toMac0Structure(detachedContent = input.detachedPayload).cborEncode(),
        secret = sharedSecret,
        alg = alg,
        input = toMac0Input()
    )

    @JsName("toBeMacedJson")
    fun toBeMacedJson(sharedSecret: ByteArray, alg: SignatureAlgorithm = SignatureAlgorithm.HMAC_SHA256, detachedContent: ByteArray? = null) =
        ToBeMacedJson(
            macStructureAsBase64Url = toMac0Structure(detachedContent = detachedContent).cborEncode().encodeToBase64Url(),
            secretAsBase64Url = sharedSecret.encodeToBase64Url(),
            alg = alg,
            input = toMac0Input().toJson()
        )
*/
    fun detachedPayloadCopy(): CoseMac0Cbor {
        return this.copy(payload = null)
    }

    companion object {
        @JsStatic
        @JsName("fromCborItem")
        fun fromCborItem(a: CborArray<CborItem<*>>): CoseMac0Cbor {
            val protectedHeaderBytes: CborByteString = a.required(0)
            val unprotectedHeaders = a.optional<CborMap<NumberLabel, CborItem<*>>>(1)
            val payloadAvailable = a.value[2].value != null
            return CoseMac0Cbor(
                CoseHeaderCbor.fromCborItem(protectedHeaderBytes.cborDecodeValue()),
                unprotectedHeaders?.let { CoseHeaderCbor.fromCborItem(it) },
                if (payloadAvailable) a.required(2) else null,
                a.required(3)
            )
        }

        @JsStatic
        @JsName("decodeCbor")
        fun decodeCbor(encoded: ByteArray) = fromCborItem(cborSerializer.decode(encoded))
    }

    override fun cborBuilder(): CborBuilder<CoseMac0Cbor> {
        return CborArray.builder(this).add(CborByteString(protectedHeader.encodeCbor())).add(unprotectedHeader?.toCborStructure())
            .add(payload ?: CborNull()).add(tag).end()
    }


    override fun hashCode(): Int {
        var result = protectedHeader.hashCode()
        result = 31 * result + (unprotectedHeader?.hashCode() ?: 0)
        result = 31 * result + (payload?.hashCode() ?: 0)
        result = 31 * result + tag.hashCode()
        return result
    }

    override fun toString(): String {
        return "CoseMac0Cbor(protectedHeader=$protectedHeader, unprotectedHeader=$unprotectedHeader, payload=$payload, signature=$tag)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CoseMac0Cbor

        if (protectedHeader != other.protectedHeader) return false
        if (unprotectedHeader != other.unprotectedHeader) return false
        if (payload != other.payload) return false
        if (tag != other.tag) return false

        return true
    }


}

typealias COSE_Mac0 = CoseMac0Cbor


@JsExportCompat
sealed class MacContext(val value: String) {
    data object Mac : MacContext("MAC")
    data object Mac0 : MacContext("MAC0")

    fun toCbor() = CborString(value)

    companion object {
        @JsStatic
        val asList = listOf(Mac, Mac0)

        @JsStatic
        @JsName("fromValue")
        fun fromValue(value: String) = asList.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Unknown signature $value")
    }
}

@JsExportCompat
data class CoseMacStructureCbor(
    val context: CborString = CborString(MacContext.Mac0.value),
    val protected: CborByteString = CborByteString(byteArrayOf()),
    val externalAad: CborByteString = CborByteString(byteArrayOf()),
    val payload: CborByteString,
) : CborStructure<CoseMacStructureCbor, CborArray<CborItem<*>>>(CDDL.list) {
    override fun cborBuilder(): CborBuilder<CoseMacStructureCbor> =
        CborArray.builder(this).addRequired(CborString(context.value)).addRequired(protected).addRequired(externalAad).addRequired(payload)
            .end()

    @JsName("toBeMaced")
    fun toBeMaced(): CborByteString = CborByteString(this.encodeCbor())

    companion object {
        @JsName("fromCborItem")
        @JsStatic
        fun fromCborItem(a: CborArray<CborItem<*>>): CoseMacStructureCbor {
            return CoseMacStructureCbor(
                context = a.required(0), protected = a.required(1), externalAad = a.required(2), payload = a.required(3)
            )
        }

        @JsStatic
        fun decodeCbor(encoded: ByteArray) = fromCborItem(cborSerializer.decode(encoded))
    }
}

/*
@JsExportCompat
data class ToBeMacedJson(
    val input: CoseMac0InputJson,
    val macStructureAsBase64Url: String,
    val secretAsBase64Url: String,
    val alg: SignatureAlgorithm = SignatureAlgorithm.HMAC_SHA256
) : JsonView() {
    override fun toJsonString() = cryptoJsonSerializer.encodeToString(this)
    override fun toCbor() =
        ToBeMacedCbor(
            macStructureBytes = macStructureAsBase64Url.decodeFromBase64Url(),
            secret = secretAsBase64Url.decodeFromBase64Url(),
            alg = alg,
            input = input.toCbor()
        )

    fun coseMacStructure() = CoseMacStructureCbor.fromCborItem(cborSerializer.decode(macStructureAsBase64Url.decodeFromBase64Url())).toJson()

}
*/

/*
@JsExportCompat
data class ToBeMacedCbor(
    val input: CoseMac0InputCbor,
    val macStructureBytes: ByteArray,
    val secret: ByteArray,
    val alg: SignatureAlgorithm = SignatureAlgorithm.HMAC_SHA256
) :
    CborStructure<ToBeMacedCbor, ToBeMacedJson, CborByteString>(CDDL.bstr) {
    override fun cborBuilder(): CborBuilder<ToBeMacedCbor> = CborBuilder(CborByteString(macStructureBytes), this)

    fun coseMacStructure() = CoseMacStructureCbor.fromCborItem(cborSerializer.decode(macStructureBytes))


    override fun toJson() =
        ToBeMacedJson(
            macStructureAsBase64Url = macStructureBytes.encodeToBase64Url(),
            secretAsBase64Url = secret.encodeToBase64Url(),
            alg = alg,
            input = input.toJson()
        )

}
*/

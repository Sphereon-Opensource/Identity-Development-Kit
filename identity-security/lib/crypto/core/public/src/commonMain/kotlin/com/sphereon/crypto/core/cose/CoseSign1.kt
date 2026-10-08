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

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborString
import com.sphereon.cbor.json.JsonView
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@JsExportCompat
data class
CoseSign1Input
    @JvmOverloads
    constructor(
        // This value is required in the eventual to be signed object, but can be filled using key info
        val protectedHeader: CoseHeaderCbor? = null,
        val unprotectedHeader: CoseHeaderCbor? = null,
        val payload: CborByteString,
    ) {
        class Builder(
            private var protectedHeader: CoseHeaderCbor? = CoseHeaderCbor(),
            private var unprotectedHeader: CoseHeaderCbor? = null,
            private var payload: ByteArray? = null,
            private var encodePayloadAsDataItem: Boolean = false,
        ) {
            fun withProtectedHeader(protectedHeader: CoseHeaderCbor) = apply { this.protectedHeader = protectedHeader }

            fun withUnprotectedHeader(unprotectedHeader: CoseHeaderCbor?) = apply { this.unprotectedHeader = unprotectedHeader }

            fun withPayload(payload: ByteArray) = apply { this.payload = payload }

            fun withEncodePayloadAsDataItem(encodePayloadAsDataItem: Boolean = true) = apply { this.encodePayloadAsDataItem = encodePayloadAsDataItem }

            fun build(): CoseSign1Input {
                val content =
                    if (encodePayloadAsDataItem) {
                        payload?.let { CborEncodedItem(it, it).value.toBstr() }
                    } else {
                        payload?.toCborByteString()
                    }
                require(content !== null) { "Payload is required" }

                return CoseSign1Input(
                    payload = content,
                    unprotectedHeader = unprotectedHeader,
                    protectedHeader = protectedHeader,
                )
            }
        }

        override fun toString(): String = "CoseSign1InputCbor(protectedHeader=$protectedHeader, unprotectedHeader=$unprotectedHeader, payload=$payload)"
    }

@JsExportCompat
data class CoseSign1<CborType : Any>(
    val protectedHeader: CoseHeaderCbor,
    val unprotectedHeader: CoseHeaderCbor?,
    val payload: CborByteString?,
    val signature: CborByteString,
) {
    /* fun cborDecodePayload(convertFunction: (arg: CborItem<*>) -> CborType): CborType? {
         val result = payload?.value?.let { Cbor.decode<CborEncodedItem<CborItem<*>>>(it) }?.decodedValue
         return result?.let { convertFunction.invoke(it) }
     }
     */
    fun detachedPayloadCopy(): CoseSign1<CborType> = this.copy(payload = null)

    override fun hashCode(): Int {
        var result = protectedHeader.hashCode()
        result = 31 * result + (unprotectedHeader?.hashCode() ?: 0)
        result = 31 * result + (payload?.hashCode() ?: 0)
        result = 31 * result + signature.hashCode()
        return result
    }

    override fun toString(): String = "CoseSign1Cbor(protectedHeader=$protectedHeader, unprotectedHeader=$unprotectedHeader, payload=$payload, signature=$signature)"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as CoseSign1<*>

        if (protectedHeader != other.protectedHeader) {
            return false
        }
        if (unprotectedHeader != other.unprotectedHeader) {
            return false
        }
        if (payload != other.payload) {
            return false
        }
        if (signature != other.signature) {
            return false
        }

        return true
    }
}

typealias COSE_Sign1<CborType> = CoseSign1<CborType>

@JsExportCompat
sealed class SigStructure(
    val value: String,
) {
    object Signature : SigStructure("Signature")

    object Signature1 : SigStructure("Signature1")

    object CounterSignature : SigStructure("CounterSignature")

    companion object {
        @JsStatic
        @JvmStatic
        val asList = listOf(Signature, Signature1, CounterSignature)

        @JsStatic
        @JsName("fromValue")
        @JvmStatic
        fun fromValue(value: String) = asList.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Unknown signature $value")
    }
}

@JsExportCompat
data class
CoseSignatureStructureCbor
    @JvmOverloads
    constructor(
        val structure: CborString = CborString(SigStructure.Signature1.value),
        val bodyProtected: CborByteString,
        val signProtected: CborByteString? = null,
        val externalAad: CborByteString = CborByteString(byteArrayOf()),
        val payload: CborByteString,
    ) {
        override fun toString(): String = "CoseSignatureStructureCbor(structure=$structure, bodyProtected=$bodyProtected, signProtected=$signProtected, externalAad=$externalAad, payload=$payload)"
    }

@JsExportCompat
data class
ToBeSignedJson
    @JvmOverloads
    constructor(
        val base64UrlValue: String,
        val keyInfo: KeyInfoType<*>,
        val alg: SignatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256,
    ) : JsonView() {
        override fun toJsonString() = cryptoJsonSerializer.encodeToString(this)

        override fun toCbor() = ToBeSignedCbor(base64UrlValue.decodeFromBase64Url(), keyInfo = keyInfo, alg = alg)
    }

@JsExportCompat
data class
ToBeSignedCbor
    @JvmOverloads
    constructor(
        val value: ByteArray,
        val keyInfo: KeyInfoType<*>,
        val alg: SignatureAlgorithm = keyInfo.signatureAlgorithm ?: SignatureAlgorithm.ECDSA_SHA256,
    )

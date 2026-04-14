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
import com.sphereon.cbor.CborString
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@JsExportCompat
data class
CoseMac0InputCbor
    @JvmOverloads
    constructor(
        val protectedHeader: CoseHeaderCbor = CoseHeaderCbor(),
        val unprotectedHeader: CoseHeaderCbor? = null,
        val externalAad: ByteArray = byteArrayOf(),
        val payload: ByteArray? = null,
        val detachedPayload: ByteArray? = null,
    ) {
        companion object

        class Builder(
            private var protectedHeader: CoseHeaderCbor = CoseHeaderCbor(),
            private var unprotectedHeader: CoseHeaderCbor? = null,
            private var payload: ByteArray? = null,
            private var detachedPayload: ByteArray? = null,
            private var externalAad: ByteArray? = null,
        ) {
            fun withProtectedHeader(protectedHeader: CoseHeaderCbor) = apply { this.protectedHeader = protectedHeader }

            fun withUnprotectedHeader(unprotectedHeader: CoseHeaderCbor) = apply { this.unprotectedHeader = unprotectedHeader }

            fun withPayload(payload: ByteArray) = apply { this.payload = payload }

            fun withDetachedPayload(detachedPayload: ByteArray) = apply { this.detachedPayload = detachedPayload }

            fun withExternalAad(externalAad: ByteArray) = apply { this.externalAad = externalAad }

            fun build(): CoseMac0InputCbor =
                CoseMac0InputCbor(
                    payload = payload,
                    detachedPayload = detachedPayload,
                    protectedHeader = protectedHeader,
                    unprotectedHeader = unprotectedHeader,
                    externalAad = externalAad ?: byteArrayOf(),
                )
        }
    }

@JsExportCompat
data class
CoseMac0Cbor
    @JvmOverloads
    constructor(
        val protectedHeader: CoseHeaderCbor,
        val unprotectedHeader: CoseHeaderCbor?,
        val payload: CborByteString? = null,
        val tag: CborByteString,
    ) {
        fun detachedPayloadCopy(): CoseMac0Cbor = this.copy(payload = null)

        override fun hashCode(): Int {
            var result = protectedHeader.hashCode()
            result = 31 * result + (unprotectedHeader?.hashCode() ?: 0)
            result = 31 * result + (payload?.hashCode() ?: 0)
            result = 31 * result + tag.hashCode()
            return result
        }

        override fun toString(): String = "CoseMac0Cbor(protectedHeader=$protectedHeader, unprotectedHeader=$unprotectedHeader, payload=$payload, signature=$tag)"

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as CoseMac0Cbor

            if (protectedHeader != other.protectedHeader) {
                return false
            }
            if (unprotectedHeader != other.unprotectedHeader) {
                return false
            }
            if (payload != other.payload) {
                return false
            }
            if (tag != other.tag) {
                return false
            }

            return true
        }
    }

typealias COSE_Mac0 = CoseMac0Cbor

@JsExportCompat
sealed class MacContext(
    val value: String,
) {
    data object Mac : MacContext("MAC")

    data object Mac0 : MacContext("MAC0")

    companion object {
        @JsStatic
        @JvmStatic
        val asList = listOf(Mac, Mac0)

        @JsStatic
        @JsName("fromValue")
        @JvmStatic
        fun fromValue(value: String) = asList.firstOrNull { it.value == value } ?: throw IllegalArgumentException("Unknown signature $value")
    }
}

@JsExportCompat
data class
CoseMacStructureCbor
    @JvmOverloads
    constructor(
        val context: CborString = CborString(MacContext.Mac0.value),
        val protected: CborByteString = CborByteString(byteArrayOf()),
        val externalAad: CborByteString = CborByteString(byteArrayOf()),
        val payload: CborByteString,
    )

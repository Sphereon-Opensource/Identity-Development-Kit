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

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborString
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.encodeToBase64Array
import com.sphereon.cbor.encodeToCborByteArray
import com.sphereon.cbor.json.JsonView
import com.sphereon.cbor.toCborByteString
import com.sphereon.cbor.toCborString
import com.sphereon.cbor.toCborStringArray
import com.sphereon.cbor.toStringArray
import com.sphereon.core.api.Encoding
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Represents a COSE (CBOR Object Signing and Encryption) header in JSON format.
 *
 * This data class provides various common COSE header parameters as specified in RFC 8152.
 * The parameters are defined to describe the cryptographic algorithm, critical fields,
 * content type, key identifier, initialization vector, partial initialization vector,
 * and x.509 certificate chain.
 *
 * @property alg The COSE algorithm parameter.
 * @property crit An array of critical headers.
 * @property contentType The content type of the payload.
 * @property kid The key identifier for the key used.
 * @property iv Initialization vector for encryption.
 * @property partialIv Partial initialization vector.
 * @property x5chain An array representing the x.509 certificate chain.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoseHeaderJson", exact = true)
@JsExportCompat
@Serializable
data class
CoseHeaderJson
    @JvmOverloads
    constructor(
        /**
         * 3.1.  Common COSE Headers Parameters are listed below
         *
         * See https://www.rfc-editor.org/rfc/rfc8152 Table 2
         *
         */
        val alg: CoseAlgorithm? = null,
        val crit: Array<String>? = null,
        val contentType: String? = null,
        val kid: String? = null,
        val iv: String? = null,
        val partialIv: String? = null,
        val x5chain: Array<String>? = null,
        /** COSE `typ` (RFC 9596, label 16): the media type of the complete COSE object, e.g. a CWT type. */
        val typ: String? = null,
    ) : JsonView() {
        /**
         * Converts the current instance of `CoseHeaderJson` to a JSON string.
         *
         * This method uses `cryptoJsonSerializer` to serialize the current
         * instance to its JSON representation.
         *
         * @return A JSON string representation of the current `CoseHeaderJson` object.
         */
        override fun toJsonString() = cryptoJsonSerializer.encodeToString(this)

        /**
         * Converts the current CoseHeader object to its CBOR (Concise Binary Object Representation) format.
         *
         * @return An instance of CoseHeaderCbor that represents the CBOR-encoded header information.
         */
        override fun toCbor(): CoseHeaderCbor =
            CoseHeaderCbor(
                alg = alg,
                crit = crit?.toCborStringArray(),
                contentType = contentType?.toCborString(),
                kid = kid?.toCborByteString(Encoding.UTF8),
                partialIv = partialIv?.toCborByteString(Encoding.UTF8),
                iv = iv?.toCborByteString(Encoding.UTF8),
                x5chain = x5chain?.encodeToCborByteArray(Encoding.BASE64), // base64 not url
                typ = typ?.toCborString(),
            )

        // TODO: To JOSE
    }

/**
 * Represents a COSE header encoded in CBOR format.
 *
 * @property alg The algorithm identifier.
 * @property crit Critical headers that must be understood.
 * @property contentType A string describing the content type.
 * @property kid The key identifier.
 * @property iv Initialization vector for cipher operations.
 * @property partialIv Partial initialization vector for cipher operations.
 * @property x5chain Certificate chain.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoseHeaderCbor", exact = true)
@JsExportCompat
data class
CoseHeaderCbor
    @JvmOverloads
    constructor(
        /**
         * 3.1.  Common COSE Headers Parameters are listed below
         *
         * See https://www.rfc-editor.org/rfc/rfc8152 Table 2
         *
         */

        val alg: CoseAlgorithm? = null,
        val crit: CborArray<CborString>? = null,
        val contentType: CborString? = null,
        var kid: CborByteString? = null,
        val iv: CborByteString? = null,
        val partialIv: CborByteString? = null,
        var x5chain: CborArray<CborByteString>? = null,
        /** COSE `typ` (RFC 9596, label 16): the media type of the complete COSE object, e.g. a CWT type. */
        var typ: CborString? = null,
    ) {
        /**
         * Converts the COSE header to a JSON representation.
         *
         * @return A CoseHeaderJson object containing the JSON representation of the COSE header.
         */
        @OptIn(ExperimentalStdlibApi::class)
        fun toJson(): CoseHeaderJson =
            CoseHeaderJson(
                alg = alg,
                crit = crit?.toStringArray(),
                contentType = contentType?.toString(),
                kid = kid?.encodeValueTo(Encoding.UTF8),
                iv = iv?.encodeValueTo(Encoding.UTF8),
                partialIv = partialIv?.encodeValueTo(Encoding.UTF8),
                x5chain = x5chain?.encodeToBase64Array(),
                typ = typ?.toString(),
            )

        /**
         * Checks if all the properties (x5chain, alg, partialIv, kid, iv, crit, contentType) are null.
         *
         * @return true if all properties are null; false otherwise.
         */
        fun isEmpty(): Boolean =
            this.x5chain == null && this.alg == null && this.partialIv == null && this.kid == null && this.iv == null && this.crit == null && this.contentType == null && this.typ == null

        /**
         * Checks if this CoseHeaderCbor object is equal to another object.
         * Two CoseHeaderCbor objects are considered equal if all their
         * corresponding fields (alg, crit, contentType, kid, iv, partialIv, x5chain) are equal.
         *
         * @param other the object to compare with this CoseHeaderCbor object.
         * @return true if the specified object is equal to this CoseHeaderCbor object, false otherwise.
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is CoseHeaderCbor) {
                return false
            }

            if (alg != other.alg) {
                return false
            }
            if (crit != other.crit) {
                return false
            }
            if (contentType != other.contentType) {
                return false
            }
            if (kid != other.kid) {
                return false
            }
            if (iv != other.iv) {
                return false
            }
            if (partialIv != other.partialIv) {
                return false
            }
            if (x5chain != other.x5chain) {
                return false
            }
            if (typ != other.typ) {
                return false
            }

            return true
        }

        /**
         * Generates a hash code value for this object based on its fields.
         *
         * @return an integer hash code value representing this object.
         */
        override fun hashCode(): Int {
            var result = alg?.hashCode() ?: 0
            result = 31 * result + (crit?.hashCode() ?: 0)
            result = 31 * result + (contentType?.hashCode() ?: 0)
            result = 31 * result + (kid?.hashCode() ?: 0)
            result = 31 * result + (iv?.hashCode() ?: 0)
            result = 31 * result + (partialIv?.hashCode() ?: 0)
            result = 31 * result + (x5chain?.hashCode() ?: 0)
            result = 31 * result + (typ?.hashCode() ?: 0)
            return result
        }

        /**
         * Returns a string representation of the `CoseHeaderCbor` instance.
         *
         * @return a string that includes the values of `alg`, `crit`, `contentType`, `kid`, `iv`, `partialIv`, and `x5chain`
         */
        override fun toString(): String = "CoseHeaderCbor(alg=$alg, crit=$crit, contentType=$contentType, kid=$kid, iv=$iv, partialIv=$partialIv, x5chain=$x5chain, typ=$typ)"

        /**
         * Utility object containing static properties and methods for handling COSE headers.
         */
        companion object {
            @JvmStatic
            val ALG = NumberLabel(1)

            @JvmStatic
            val CRIT = NumberLabel(2)

            @JvmStatic
            val CONTENT_TYPE = NumberLabel(3)

            @JvmStatic
            val KID = NumberLabel(4)

            @JvmStatic
            val IV = NumberLabel(5)

            @JvmStatic
            val PARTIAL_IV = NumberLabel(6)

            @JvmStatic
            val X5CHAIN = NumberLabel(33)

            @JvmStatic
            val TYP = NumberLabel(16)

            /**
             * Copies the given `CoseHeaderCbor` object or initializes a new one if the provided object is null.
             *
             * @param other The `CoseHeaderCbor` object to copy. If null, a new `CoseHeaderCbor` object is initialized.
             * @return A new `CoseHeaderCbor` object, either a copy of the provided object or a newly created one.
             */
            @JvmStatic
            fun copyOrInit(
                other: CoseHeaderCbor?,
                alg: CoseAlgorithm? = null,
            ) = if (other === null) {
                CoseHeaderCbor(alg = alg)
            } else {
                other.copy(alg = alg)
            }
        }
    }

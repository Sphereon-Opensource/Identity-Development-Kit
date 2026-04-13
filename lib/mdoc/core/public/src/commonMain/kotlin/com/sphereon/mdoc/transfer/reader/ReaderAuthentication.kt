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

package com.sphereon.mdoc.transfer.reader

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborNil
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.dsl.cborArrayBuilder
import com.sphereon.cbor.toCborByteString
import com.sphereon.cbor.toCborItem
import com.sphereon.cbor.toCborString
import com.sphereon.core.compat.Uuid
import com.sphereon.core.api.encodeToHex
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.oid4vp.oid4vpHandoverFromClientIdAndResponseUri
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderAuthentication", exact = true)
data class ReaderAuthentication(
    val sessionTranscript: SessionTranscript,
    val itemsRequestBytes: CborEncodedItem<DeviceItemsRequest>,
) : CborStructure<ReaderAuthentication, CborArray<CborItem<*>>>(CDDL.list) {
    override fun cborBuilder(): CborBuilder<ReaderAuthentication> = cborArrayBuilder(this) {
        add(READER_AUTHENTICATION)
        add(sessionTranscript.toCborStructure())
        add(itemsRequestBytes.encodeCbor())
    }

    companion object Decoder : HasFromCbor<CborArray<CborItem<*>>, ReaderAuthentication> {
        @JsStatic
        val READER_AUTHENTICATION = CborString("ReaderAuthentication")

        override fun fromCborStructure(structure: CborArray<CborItem<*>>): ReaderAuthentication {
            if (structure.required<CborString>(0) != READER_AUTHENTICATION) {
                throw IllegalArgumentException("'ReaderAuthentication' element cannot be null")
            }
            val itemsRequestBytes = structure.required<CborEncodedItem<CborMap<StringLabel, CborItem<*>>>>(2)
            return ReaderAuthentication(
                sessionTranscript = SessionTranscript.Decoder.fromCborStructure(structure.required(1)),
                itemsRequestBytes.copy(DeviceItemsRequest.Decoder.fromCborStructure(itemsRequestBytes.data()))
            )
        }

        override fun decodeCbor(bytes: ByteArray) = fromCborStructure(cborSerializer.decode(bytes))
    }
}

typealias ReaderAuthenticationBytes = CborEncodedItem<ReaderAuthentication>
typealias ReaderAuth = CoseSign1<ReaderAuthenticationBytes>

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionTranscript", exact = true)
data class SessionTranscript(
    val deviceEngagement: CborEncodedItem<DeviceEngagement>? = null,
    val eReaderKey: CborEncodedItem<out CoseKeyType>? = null,
    val handover: Handover<*, CborItem<*>>,
    override val original: ByteArray?,
) : CborStructure<SessionTranscript, CborArray<CborItem<*>>>(CDDL.list) {
    override fun cborBuilder(): CborBuilder<SessionTranscript> = cborArrayBuilder(this) {
        add(deviceEngagement?.toCborItem())
        add(eReaderKey)
        add(handover)
    }

    fun toCborEncodedItem(): CborEncodedItem<SessionTranscript> {
        return CborEncodedItem(CborByteString(this.encodeCbor()))
    }

    @Suppress("UNCHECKED_CAST")
    companion object Decoder : HasFromCborWithOriginal<CborArray<CborItem<*>>, SessionTranscript> {
        const val DEVICE_ENGAGEMENT = 0
        const val ENGAGEMENT_READER_KEY = 1
        const val HANDOVER = 2

        fun fromEncodedCborStructure(encodedItem: CborEncodedItem<CborArray<CborItem<*>>>): SessionTranscript {
            val structure: CborArray<CborItem<*>> = encodedItem.data()
            return fromCborStructure(structure)
        }

        override fun fromCborStructure(structure: CborArray<CborItem<*>>): SessionTranscript {
            val handoverItem: CborItem<*>? = structure.optional<CborItem<*>>(HANDOVER)
            val handover: Handover<*, CborItem<*>> = (handoverItem?.let { Handover.fromCborStructure(it) } ?: QrHandover() as Handover<*, CborNil>) as Handover<*, CborItem<*>>
            val readerKeyBytes = structure.optional<CborEncodedItem<CborMap<NumberLabel, CborItem<*>>>>(ENGAGEMENT_READER_KEY)
            val readerKey = readerKeyBytes?.copy(CoseKey.fromCborStructure(readerKeyBytes.data()))

            return SessionTranscript(
                deviceEngagement = structure.optional<CborEncodedItem<CborMap<NumberLabel, CborItem<*>>>>(DEVICE_ENGAGEMENT)?.let {
                    val engagement = DeviceEngagement.Decoder.fromCborStructure(it.data())
                    it.copy(engagement)
                },
                eReaderKey = readerKey,
                handover = handover,
                original = null,
            )
        }

        override fun fromCborStructureWithOriginal(structure: CborArray<CborItem<*>>, original: ByteArray?): SessionTranscript {
            return fromCborStructure(structure).copy(original = original)
        }

        override fun decodeCbor(bytes: ByteArray): SessionTranscript {
            val encodedItem = cborSerializer.decode<CborEncodedItem<CborArray<CborItem<*>>>>(bytes)
            return fromCborStructureWithOriginal(encodedItem.data(), bytes)
        }

        @JsStatic
        fun fromOid4vpHandover(handover: OID4VPHandover): SessionTranscript = SessionTranscript(handover = handover as Handover<*, CborItem<*>>, original = null)

        @JsStatic
        fun fromOid4vpClientIdAndResponseUri(
            clientId: String,
            responseUri: String,
            mdocNonce: String = Uuid.v4String(),
            authorizationRequestNonce: String,
        ) =
            fromOid4vpHandover(
                OID4VPHandover.fromClientIdAndResponseUri(
                    clientId = clientId,
                    responseUri = responseUri,
                    mdocNonce = mdocNonce,
                    authorizationRequestNonce = authorizationRequestNonce
                )
            )


    }

}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Handover", exact = true)
sealed class Handover<out CborViewType, CborType : CborItem<*>>(cddl: CDDL) : CborStructure<CborViewType, CborType>(cddl) {
    companion object : HasFromCbor<CborItem<*>, Handover<*, CborItem<*>>> {
        @Suppress("UNCHECKED_CAST")
        override fun fromCborStructure(structure: CborItem<*>): Handover<*, CborItem<*>> {

            return when (structure) {
                is CborNil -> QrHandover() as Handover<CborStructure<*, *>, CborItem<*>>
                is CborByteString -> RestApiHandover(structure.value) as Handover<CborStructure<*, *>, CborItem<*>>
                is CborArray<*> -> {
                    val values = structure.value
                    if (values.size >= 3) {
                        OID4VPHandover.fromCborStructure(structure as CborArray<CborItem<*>>) as Handover<CborStructure<*, *>, CborItem<*>>
                    } else {
                        NfcHandover.fromCborStructure(structure as CborArray<CborItem<*>>) as Handover<CborStructure<*, *>, CborItem<*>>
                    }
                }
                else -> throw IllegalArgumentException("Could not convert Cbor structure to a Handover Object")
            }
        }

        override fun decodeCbor(bytes: ByteArray) = fromCborStructure(cborSerializer.decode(bytes))
    }

}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("QrHandover", exact = true)
class QrHandover : Handover<QrHandover, CborNil>(CDDL.nil) {
    override fun cborBuilder(): CborBuilder<QrHandover> {
        return CborBuilder<QrHandover>(CDDL.nil.newCborItem(null), this)
    }

    companion object Decoder : HasFromCbor<CborNil, QrHandover> {
        override fun fromCborStructure(structure: CborNil): QrHandover {

//            require(structure.value.size == 1) { "QrHandover must have exactly one element which is null" }
//            require(structure.value[0] is CborNil) { "QrHandover must have exactly one element which is null" }
            return QrHandover()
        }

        override fun decodeCbor(bytes: ByteArray) = fromCborStructure(cborSerializer.decode(bytes))
    }

}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcHandover", exact = true)
data class NfcHandover(val handoverSelectMessage: ByteArray, val handoverRequestMessage: ByteArray?) : Handover<NfcHandover, CborArray<CborItem<*>>>(CDDL.list) {
    override fun cborBuilder(): CborBuilder<NfcHandover> = cborArrayBuilder(this) {
        addItem(handoverSelectMessage.toCborByteString())
        addItem(handoverRequestMessage?.toCborByteString() ?: CDDL.nil.newCborItem(null))
    }


    companion object Decoder : HasFromCbor<CborArray<CborItem<*>>, NfcHandover> {
        const val HANDOVER_SELECT_MESSAGE = 0
        const val HANDOVER_REQUEST_MESSAGE = 1

        override fun fromCborStructure(structure: CborArray<CborItem<*>>) =
            NfcHandover(
                handoverSelectMessage = (structure.required(HANDOVER_SELECT_MESSAGE) as CborByteString).value,
                handoverRequestMessage = (structure.optional(HANDOVER_REQUEST_MESSAGE) as? CborByteString)?.value
            )

        override fun decodeCbor(bytes: ByteArray) = fromCborStructure(cborSerializer.decode(bytes))
    }
}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("OID4VPHandover", exact = true)
data class OID4VPHandover(
    val clientIdHash: ByteArray,
    val responseUriHash: ByteArray,
    /** Authorization request nonce*/
    val nonce: String,
) :
    Handover<OID4VPHandover, CborArray<CborItem<*>>>(CDDL.list) {
    override fun cborBuilder(): CborBuilder<OID4VPHandover> = cborArrayBuilder(this) {
        addItem(clientIdHash.toCborByteString())
        addItem(responseUriHash.toCborByteString())
        addItem(nonce.toCborString())
    }


    companion object Decoder : HasFromCbor<CborArray<CborItem<*>>, OID4VPHandover> {
        const val CLIENT_ID_HASH = 0
        const val RESPONSE_URI_HASH = 1
        const val AUTHORIZATION_REQUEST_NONCE = 2


        override fun fromCborStructure(structure: CborArray<CborItem<*>>) =
            OID4VPHandover(
                clientIdHash = (structure.required(CLIENT_ID_HASH) as CborByteString).value,
                responseUriHash = (structure.required(RESPONSE_URI_HASH) as CborByteString).value,
                nonce = (structure.required(AUTHORIZATION_REQUEST_NONCE) as CborString).value
            )

        @JsStatic
        fun fromClientIdAndResponseUri(
            clientId: String,
            responseUri: String,
            mdocNonce: String = Uuid.v4String(),
            authorizationRequestNonce: String,
        ) = oid4vpHandoverFromClientIdAndResponseUri(
            clientId = clientId,
            responseUri = responseUri,
            mdocGeneratedNonce = mdocNonce,
            authorizationRequestNonce = authorizationRequestNonce
        )


        override fun decodeCbor(data: ByteArray) = fromCborStructure(cborSerializer.decode(data))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as OID4VPHandover

        if (!clientIdHash.contentEquals(other.clientIdHash)) return false
        if (!responseUriHash.contentEquals(other.responseUriHash)) return false
        if (nonce != other.nonce) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientIdHash.contentHashCode()
        result = 31 * result + responseUriHash.contentHashCode()
        result = 31 * result + nonce.hashCode()
        return result
    }
}

/**
 * RestApiHandover for ISO 18013-7 "Device Retrieval to a website".
 *
 * Per ISO 18013-7 Annex A.8:
 * "The handover element is defined as:
 *  Handover = EngagementToApp / any
 *  EngagementToApp = ReaderEngagementBytesHash"
 *
 * The ReaderEngagementBytesHash is a SHA-256 hash of the CBOR-encoded
 * ReaderEngagement structure (wrapped in tag 24).
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("RestApiHandover", exact = true)
data class RestApiHandover(
    val readerEngagementHash: ByteArray
) : Handover<RestApiHandover, CborByteString>(CDDL.bstr) {
    override fun cborBuilder(): CborBuilder<RestApiHandover> =
        CborBuilder(readerEngagementHash.toCborByteString(), this)

    companion object Decoder : HasFromCbor<CborByteString, RestApiHandover> {
        override fun fromCborStructure(structure: CborByteString): RestApiHandover {
            return RestApiHandover(structure.value)
        }

        override fun decodeCbor(bytes: ByteArray) = fromCborStructure(cborSerializer.decode(bytes))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RestApiHandover

        return readerEngagementHash.contentEquals(other.readerEngagementHash)
    }

    override fun hashCode(): Int {
        return readerEngagementHash.contentHashCode()
    }
}

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

package com.sphereon.mdoc.transfer.reader

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborNil
import com.sphereon.cbor.CborString
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.oid4vp.oid4vpHandoverFromClientIdAndResponseUri
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderAuthentication", exact = true)
data class ReaderAuthentication(
    val sessionTranscript: SessionTranscript,
    val itemsRequestBytes: CborEncodedItem<DeviceItemsRequest>,
) {
    companion object {
        @JsStatic
        @JvmStatic
        val READER_AUTHENTICATION = CborString("ReaderAuthentication")
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
    val original: ByteArray?,
) {
    @Suppress("UNCHECKED_CAST")
    companion object {
        const val DEVICE_ENGAGEMENT = 0
        const val ENGAGEMENT_READER_KEY = 1
        const val HANDOVER = 2

        @JsStatic
        @JvmStatic
        fun fromOid4vpHandover(handover: OID4VPHandover): SessionTranscript = SessionTranscript(handover = handover as Handover<*, CborItem<*>>, original = null)

        @JsStatic
        @JvmStatic
        fun fromOid4vpClientIdAndResponseUri(
            clientId: String,
            responseUri: String,
            mdocNonce: String = Uuid.v4String(),
            authorizationRequestNonce: String,
        ) = fromOid4vpHandover(
            OID4VPHandover.fromClientIdAndResponseUri(
                clientId = clientId,
                responseUri = responseUri,
                mdocNonce = mdocNonce,
                authorizationRequestNonce = authorizationRequestNonce,
            ),
        )
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Handover", exact = true)
sealed class Handover<out CborViewType, CborType : CborItem<*>>

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("QrHandover", exact = true)
class QrHandover : Handover<QrHandover, CborNil>()

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcHandover", exact = true)
data class NfcHandover(
    val handoverSelectMessage: ByteArray,
    val handoverRequestMessage: ByteArray?,
) : Handover<NfcHandover, CborArray<CborItem<*>>>() {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as NfcHandover

        if (!handoverSelectMessage.contentEquals(other.handoverSelectMessage)) {
            return false
        }
        if (handoverRequestMessage != null) {
            if (other.handoverRequestMessage == null) {
                return false
            }
            if (!handoverRequestMessage.contentEquals(other.handoverRequestMessage)) {
                return false
            }
        } else if (other.handoverRequestMessage != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = handoverSelectMessage.contentHashCode()
        result = 31 * result + (handoverRequestMessage?.contentHashCode() ?: 0)
        return result
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
) : Handover<OID4VPHandover, CborArray<CborItem<*>>>() {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as OID4VPHandover

        if (!clientIdHash.contentEquals(other.clientIdHash)) {
            return false
        }
        if (!responseUriHash.contentEquals(other.responseUriHash)) {
            return false
        }
        if (nonce != other.nonce) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = clientIdHash.contentHashCode()
        result = 31 * result + responseUriHash.contentHashCode()
        result = 31 * result + nonce.hashCode()
        return result
    }

    companion object {
        @JsStatic
        @JvmStatic
        fun fromClientIdAndResponseUri(
            clientId: String,
            responseUri: String,
            mdocNonce: String = Uuid.v4String(),
            authorizationRequestNonce: String,
        ) = oid4vpHandoverFromClientIdAndResponseUri(
            clientId = clientId,
            responseUri = responseUri,
            mdocGeneratedNonce = mdocNonce,
            authorizationRequestNonce = authorizationRequestNonce,
        )
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
    val readerEngagementHash: ByteArray,
) : Handover<RestApiHandover, CborByteString>() {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as RestApiHandover

        return readerEngagementHash.contentEquals(other.readerEngagementHash)
    }

    override fun hashCode(): Int = readerEngagementHash.contentHashCode()
}

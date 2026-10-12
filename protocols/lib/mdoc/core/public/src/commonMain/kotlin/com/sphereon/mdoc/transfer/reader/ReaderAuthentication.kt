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
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.oid4vp.oid4vpHandoverFromInputs
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

        /**
         * Build a SessionTranscript with the OID4VP 1.0 final §B.2.6 OpenID4VPHandover.
         *
         * @param clientId OID4VP `client_id` (with §5.9.3 prefix when applicable).
         * @param nonce OID4VP `nonce` from the authorization request.
         * @param jwkThumbprint Raw 32-byte SHA-256 thumbprint (RFC 7638) of the verifier's
         *   encryption-key JWK. REQUIRED for encrypted responses (`direct_post.jwt`,
         *   `dc_api.jwt`); MUST be null for plain responses (`direct_post`, `dc_api`).
         * @param responseUri OID4VP `response_uri` (or `redirect_uri` for non-`direct_post`
         *   modes; pass whichever the request actually used).
         */
        @JsStatic
        @JvmStatic
        fun fromOid4vpClientIdAndResponseUri(
            clientId: String,
            nonce: String,
            jwkThumbprint: ByteArray?,
            responseUri: String,
        ) = fromOid4vpHandover(
            OID4VPHandover.fromOid4vpInputs(
                clientId = clientId,
                nonce = nonce,
                jwkThumbprint = jwkThumbprint,
                responseUri = responseUri,
            ),
        )

        /**
         * Build a SessionTranscript for OpenID4VP 1.0 §B.2.6.2 (Digital Credentials API).
         * [origin] is the raw, validated browser origin. It must not be the `origin:`-prefixed
         * `client_id`; the caller obtains it from trusted Digital Credentials API context.
         */
        @JsStatic
        @JvmStatic
        fun fromOid4vpDigitalCredentialsApi(
            origin: String,
            nonce: String,
            jwkThumbprint: ByteArray?,
        ): SessionTranscript {
            val handover =
                OID4VPDCAPIHandover.fromInputs(
                    origin = origin,
                    nonce = nonce,
                    jwkThumbprint = jwkThumbprint,
                )
            return SessionTranscript(
                handover = handover as Handover<*, CborItem<*>>,
                original = null,
            )
        }

        /**
         * Build the ISO/IEC TS 18013-7 Annex B OID4VP handover.
         *
         * This is intentionally a separate factory from [fromOid4vpClientIdAndResponseUri].
         * Regular OID4VP/DCQL hashes a four-element handover-info structure, whereas Annex B
         * hashes the client and response URI together with the mdoc-generated nonce and carries
         * the authorization-request nonce as the third handover element.
         */
        @JsStatic
        @JvmStatic
        fun fromIso18013Oid4vp(
            clientId: String,
            responseUri: String,
            mdocGeneratedNonce: String,
            nonce: String,
        ): SessionTranscript =
            SessionTranscript(
                handover =
                    Iso18013Oid4vpHandover.fromInputs(
                        clientId = clientId,
                        responseUri = responseUri,
                        mdocGeneratedNonce = mdocGeneratedNonce,
                        nonce = nonce,
                    ) as Handover<*, CborItem<*>>,
                original = null,
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

/**
 * OpenID4VP SessionTranscript handover per OID4VP 1.0 final §B.2.6 (verbatim):
 *
 * ```
 * OpenID4VPHandover         = ["OpenID4VPHandover", OpenID4VPHandoverInfoHash]
 * OpenID4VPHandoverInfoHash = bstr  ; sha-256 of OpenID4VPHandoverInfoBytes
 * OpenID4VPHandoverInfoBytes = bstr .cbor OpenID4VPHandoverInfo
 * OpenID4VPHandoverInfo = [
 *     client_id,
 *     nonce,
 *     JwkThumbprint OR null,
 *     redirect_uri / response_uri
 * ]
 * ```
 *
 * Spec §8.3 / appendix B.2.6.2 verbatim:
 * > "If the response is encrypted, e.g., using `direct_post.jwt`, the third element MUST be
 * >  the JWK SHA-256 Thumbprint as defined in [@!RFC7638], encoded as a Byte String, of the
 * >  Verifier's public key used to encrypt the response. Otherwise, the third element MUST
 * >  be `null`."
 *
 * The pre-final `clientIdHash` / `responseUriHash` / `nonce` shape this replaced is no
 * longer in any draft or the final spec — it has been removed entirely, not deprecated.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("OID4VPHandover", exact = true)
data class OID4VPHandover(
    val clientId: String,
    /** Authorization request `nonce`. */
    val nonce: String,
    /**
     * Verifier's encryption-key JWK thumbprint per RFC 7638, raw 32-byte SHA-256.
     * Non-null for `direct_post.jwt` (encrypted response), null for `direct_post`.
     */
    val jwkThumbprint: ByteArray?,
    val responseUri: String,
) : Handover<OID4VPHandover, CborArray<CborItem<*>>>() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as OID4VPHandover
        if (clientId != other.clientId) return false
        if (nonce != other.nonce) return false
        if (jwkThumbprint == null) {
            if (other.jwkThumbprint != null) return false
        } else {
            if (other.jwkThumbprint == null) return false
            if (!jwkThumbprint.contentEquals(other.jwkThumbprint)) return false
        }
        if (responseUri != other.responseUri) return false
        return true
    }

    override fun hashCode(): Int {
        var result = clientId.hashCode()
        result = 31 * result + nonce.hashCode()
        result = 31 * result + (jwkThumbprint?.contentHashCode() ?: 0)
        result = 31 * result + responseUri.hashCode()
        return result
    }

    companion object {
        @JsStatic
        @JvmStatic
        fun fromOid4vpInputs(
            clientId: String,
            nonce: String,
            jwkThumbprint: ByteArray?,
            responseUri: String,
        ) = oid4vpHandoverFromInputs(
            clientId = clientId,
            nonce = nonce,
            jwkThumbprint = jwkThumbprint,
            responseUri = responseUri,
        )
    }
}

/** OpenID4VP 1.0 final §B.2.6.2 handover for Digital Credentials API invocation. */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("OID4VPDCAPIHandover", exact = true)
data class OID4VPDCAPIHandover(
    /** Raw browser origin, without the `origin:` client-id prefix. */
    val origin: String,
    val nonce: String,
    /** Raw 32-byte RFC 7638 thumbprint for dc_api.jwt; null for dc_api. */
    val jwkThumbprint: ByteArray?,
) : Handover<OID4VPDCAPIHandover, CborArray<CborItem<*>>>() {
    override fun equals(other: Any?): Boolean =
        other is OID4VPDCAPIHandover && origin == other.origin && nonce == other.nonce &&
            (jwkThumbprint?.contentEquals(other.jwkThumbprint ?: return false) ?: (other.jwkThumbprint == null))

    override fun hashCode(): Int = 31 * (31 * origin.hashCode() + nonce.hashCode()) + (jwkThumbprint?.contentHashCode() ?: 0)

    companion object {
        @JsStatic
        @JvmStatic
        fun fromInputs(origin: String, nonce: String, jwkThumbprint: ByteArray?): OID4VPDCAPIHandover {
            require(origin.isNotBlank() && !origin.startsWith("origin:")) { "OID4VP DC API handover requires raw caller origin" }
            require(nonce.isNotBlank()) { "OID4VP DC API handover requires nonce" }
            require(jwkThumbprint == null || jwkThumbprint.size == 32) { "OID4VP DC API JWK thumbprint must be 32 bytes" }
            return OID4VPDCAPIHandover(origin, nonce, jwkThumbprint)
        }
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

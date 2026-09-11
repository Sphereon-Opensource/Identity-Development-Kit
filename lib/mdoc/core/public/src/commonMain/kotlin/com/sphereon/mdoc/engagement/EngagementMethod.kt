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

package com.sphereon.mdoc.engagement

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodec

@JsExportCompat
interface MdocEngagementMethod {
    val type: EngagementType
}

/**
 * QR code engagement method for holder-initiated engagement.
 *
 * Per ISO/IEC 18013-5, QR code engagement uses the opaque URI scheme `mdoc:` (no slashes).
 * This is used when the holder displays a QR code containing their DeviceEngagement.
 *
 * Format: `mdoc:<base64url-of-DeviceEngagement>`
 *
 * @param scheme The URI scheme (default: "mdoc:" per ISO 18013-5)
 */
@JsExportCompat
data class QREngagementMethod(
    val scheme: String = "mdoc:",
) : MdocEngagementMethod {
    override val type: EngagementType = EngagementType.QR
}

@JsExportCompat
class NfcEngagementMethod : MdocEngagementMethod {
    override val type: EngagementType = EngagementType.NFC
}

/**
 * OID4VP engagement method for OAuth 2.0 based engagement (ISO 18013-7 Annex B).
 *
 * Per ISO 18013-7 B.3.1.3.2, OID4VP uses the `mdoc-openid4vp://` URI scheme for wallet invocation.
 * The URI format is:
 * ```
 * mdoc-openid4vp://?client_id=example.com&request_uri=https://example.com/request
 * ```
 *
 * This differs from other engagement methods in that:
 * - It doesn't use CBOR-encoded device/reader engagement
 * - The invocation carries only `client_id` and `request_uri`; the signed request object
 *   carries the remaining authorization parameters
 * - Protocol uses OAuth 2.0 / OpenID4VP flows with JWT instead of CBOR
 *
 * @param authorizationRequestUri The full `mdoc-openid4vp://` URI from the verifier's QR code or deep link
 */
@JsExportCompat
data class Oid4vpEngagementMethod(
    val authorizationRequestUri: String,
) : MdocEngagementMethod {
    override val type: EngagementType = EngagementType.TO_APP

    init {
        require(authorizationRequestUri.startsWith("mdoc-openid4vp://")) {
            "OID4VP Authorization Request URI must start with 'mdoc-openid4vp://' per ISO 18013-7 B.3.1.3.2"
        }
    }
}

/**
 * Reader engagement method for reader-initiated (reverse) engagement.
 *
 * Reader engagement uses the URI scheme based on retrieval type:
 * - `mdoc:` for classic reverse engagement (ISO 18013-5 BLE/NFC)
 * - `mdoc://` for website retrieval (ISO 18013-7 Annex A)
 *
 * This is used when the reader displays a QR code or deep link containing their ReaderEngagement.
 *
 * Format: `mdoc:<base64url-of-ReaderEngagement>` or `mdoc://<base64url-of-ReaderEngagement>`
 *
 * @param readerEngagement The reader engagement containing reader's ephemeral key and retrieval methods
 */
@JsExportCompat
data class ReaderEngagementMethod(
    val readerEngagement: ReaderEngagement,
    private val readerEngagementCborCodec: ReaderEngagementCborCodec? = null,
) : MdocEngagementMethod {
    override val type: EngagementType = EngagementType.TO_APP

    init {
        require(!readerEngagement.deviceRetrievalMethods.isNullOrEmpty()) { "Reader engagement does not have retrieval methods" }
    }

    /**
     * Get the engagement URI for QR code display or deep linking.
     *
     * @return URI starting with `mdoc:` (classic reverse engagement) or `mdoc://` (website retrieval)
     */
    fun getEngagementDataUri(): String {
        val scheme =
            if (readerEngagement.hasWebsiteRetrievalMethod) {
                "mdoc://"
            } else {
                "mdoc:"
            }
        return requireNotNull(readerEngagementCborCodec) {
            "ReaderEngagementCborCodec must be provided to generate a ReaderEngagement URI"
        }.encodeUri(readerEngagement, scheme).getOrThrow()
    }
}

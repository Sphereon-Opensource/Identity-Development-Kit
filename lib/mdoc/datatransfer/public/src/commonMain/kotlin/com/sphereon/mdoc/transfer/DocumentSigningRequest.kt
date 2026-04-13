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

package com.sphereon.mdoc.transfer

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.Document
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Represents a single document signing request with its associated data.
 * This ensures request, document, and device key are always correctly associated,
 * preventing errors from parallel list management.
 *
 * The session transcript is automatically bound by the TransferManager context,
 * eliminating the need for manual transcript management.
 *
 * ## Usage
 *
 * ### Basic usage:
 * ```kotlin
 * val signingRequest = DocumentSigningRequest.of(
 *     request = docRequest,
 *     document = document,
 *     deviceKeyInfo = keyInfo
 * )
 * ```
 *
 * ### With custom options:
 * ```kotlin
 * val signingRequest = DocumentSigningRequest(
 *     request = docRequest,
 *     document = document,
 *     deviceKeyInfo = keyInfo,
 *     deviceNamespaces = DeviceNameSpaces(customNamespaces),
 *     requireDeviceX5Chain = true
 * )
 * ```
 *
 * @param request The DocRequest specifying what data elements to disclose
 * @param document The Document to sign (contains IssuerSigned data + MSO)
 * @param deviceKeyInfo The device key for signing (null = derive from MSO's deviceKeyInfo)
 * @param deviceNamespaces Device-signed namespaces (usually empty for mDL, used for additional holder-signed data)
 * @param unprotectedHeader Optional COSE unprotected header for device signature
 * @param protectedHeader Optional COSE protected header for device signature
 * @param requireDeviceX5Chain Whether to require x5chain in device signature
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocumentSigningRequest", exact = true)
data class DocumentSigningRequest(
    val request: DocRequest,
    val document: Document,
    val deviceKeyInfo: KeyInfoType<*>? = null,
    val deviceNamespaces: DeviceNameSpaces = DeviceNameSpaces(mapOf()),
    val unprotectedHeader: CoseHeaderCbor? = null,
    val protectedHeader: CoseHeaderCbor? = null,
    val requireDeviceX5Chain: Boolean = false,
) {
    init {
        // Validate at construction time to fail fast
        require(request.itemsRequest.docType == document.docType) {
            "Request docType '${request.itemsRequest.docType}' must match document docType '${document.docType}'"
        }
    }

    companion object {
        /**
         * Convenience factory for the common case where only request, document, and key are needed.
         * All other parameters use sensible defaults.
         *
         * @param request The DocRequest specifying what to disclose
         * @param document The Document to sign
         * @param deviceKeyInfo The device key (null = derive from MSO)
         * @return DocumentSigningRequest with default options
         */
        fun of(
            request: DocRequest,
            document: Document,
            deviceKeyInfo: KeyInfoType<*>? = null,
        ) = DocumentSigningRequest(
            request = request,
            document = document,
            deviceKeyInfo = deviceKeyInfo,
        )
    }
}

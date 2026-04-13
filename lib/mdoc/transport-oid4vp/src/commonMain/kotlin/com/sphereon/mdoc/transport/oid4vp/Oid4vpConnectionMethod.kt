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

package com.sphereon.mdoc.transport.oid4vp

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodVersion
import com.sphereon.mdoc.transfer.device.Oid4vpOptions
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.ConnectionMethodBase
import com.sphereon.mdoc.transport.TransportType
import com.sphereon.core.compat.JsExportCompat

/**
 * Connection method for OID4VP transport per ISO 18013-7 Annex B.
 *
 * This represents the OpenID4VP credential presentation protocol for mdocs,
 * which uses:
 * - Custom URL scheme (`mdoc-openid4vp://`) for wallet invocation
 * - Authorization Request with DCQL (default) or Presentation Definition (legacy)
 * - Direct Post mode for encrypted response
 * - JARM (JWT-secured Authorization Response Mode)
 *
 * The OID4VP flow in ISO 18013-7:
 * 1. Verifier invokes wallet via `mdoc-openid4vp://` URI
 * 2. Wallet fetches Authorization Request Object from `request_uri` (HTTPS)
 * 3. Wallet validates JWT signature and resolves DCQL (default) or Presentation Definition (legacy)
 * 4. Wallet presents credentials using mdoc format (DeviceResponse)
 * 5. Wallet POSTs encrypted Authorization Response to `response_uri` (HTTPS)
 * 6. Verifier decrypts and validates response
 *
 * Per ISO 18013-7 B.3.1.3.2, the wallet invocation URI format is:
 * ```
 * mdoc-openid4vp://?client_id=example.com&request_uri=https://example.com/request
 * ```
 *
 * @property options OID4VP retrieval options with client_id, request_uri, response_uri, and nonce
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpConnectionMethod", exact = true)
data class Oid4vpConnectionMethod(
    override val options: Oid4vpOptions
) : ConnectionMethodBase<Oid4vpOptions>(options) {

    init {
        // Validate required fields
        // Per ISO 18013-7 B.3.1.3.2, client_id and request_uri are required in mdoc-openid4vp:// URI
        require(options.clientId.isNotBlank()) {
            "OID4VP client_id must not be blank"
        }
        require(options.requestUri?.isNotBlank() == true) {
            "OID4VP request_uri must not be blank - it's required to fetch Authorization Request Object"
        }

        // Note: responseUri and nonce are nullable initially
        // They will be populated after fetching the Authorization Request Object from request_uri
    }

    val connectionId: String
        get() = "oid4vp:${options.clientId}:${options.requestUri.hashCode()}"

    override fun toString(): String {
        return "Oid4vpConnectionMethod(clientId='${options.clientId}', requestUri='${options.requestUri}', responseUri='${options.responseUri}', nonce=${if (options.nonce != null) "'***'" else "null"})"
    }

    override val transportType: TransportType = TransportType.OID4VP

    override fun toDeviceRetrievalMethod(): DeviceRetrievalMethod {
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.OID4VP,
            version = DeviceRetrievalMethodVersion(1u), // OID4VP version 1
            retrievalOptions = options
        )
    }

    companion object : Factory {
        /**
         * Checks if this factory supports the given device retrieval method.
         *
         * @param deviceRetrievalMethod The device retrieval method to check
         * @return true if this is an OID4VP retrieval method with Oid4vpOptions
         */
        override fun supports(deviceRetrievalMethod: DeviceRetrievalMethod): Boolean {
            return deviceRetrievalMethod.type == DeviceRetrievalMethodType.OID4VP &&
                    deviceRetrievalMethod.retrievalOptions is Oid4vpOptions
        }

        /**
         * Creates an OID4VP connection method from a device retrieval method.
         *
         * @param deviceRetrievalMethod The device retrieval method to parse
         * @return Oid4vpConnectionMethod if the method is supported, null otherwise
         */
        override fun create(deviceRetrievalMethod: DeviceRetrievalMethod): ConnectionMethod? {
            if (!supports(deviceRetrievalMethod)) {
                return null
            }
            val oid4vpOptions = deviceRetrievalMethod.retrievalOptions as Oid4vpOptions
            return Oid4vpConnectionMethod(options = oid4vpOptions)
        }
    }
}

/**
 * Create OID4VP connection method from Authorization Request parameters.
 *
 * This is typically called after parsing the `mdoc-openid4vp://` URI and
 * fetching the Authorization Request Object from `request_uri`.
 *
 * @param clientId The verifier's client identifier (e.g., DNS name)
 * @param requestUri Optional HTTPS URL to fetch Authorization Request Object
 * @param responseUri The HTTPS URL where wallet POSTs encrypted response
 * @param nonce The cryptographic nonce from Authorization Request (min 16 bytes)
 * @param presentationDefinitionUri Optional URL to fetch Presentation Definition (legacy)
 * @return OID4VP connection method ready for transport
 */
fun oid4vpConnectionMethod(
    clientId: String,
    requestUri: String? = null,
    responseUri: String,
    nonce: String,
    presentationDefinitionUri: String? = null
): Oid4vpConnectionMethod {
    val options = Oid4vpOptions(
        clientId = clientId,
        requestUri = requestUri,
        responseUri = responseUri,
        nonce = nonce,
        presentationDefinitionUri = presentationDefinitionUri
    )
    return Oid4vpConnectionMethod(options)
}

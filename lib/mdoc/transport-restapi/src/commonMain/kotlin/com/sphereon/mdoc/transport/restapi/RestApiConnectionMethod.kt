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

package com.sphereon.mdoc.transport.restapi

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.ConnectionMethodBase
import com.sphereon.mdoc.transport.TransportType

/**
 * Connection method for REST API transport (Device Retrieval to Website).
 *
 * This implements the REST API-based data transfer as specified in ISO 18013-7 Annex A.
 * The mdoc holder sends the DeviceResponse to a reader's web endpoint via HTTPS POST.
 *
 * ## Usage
 * ```kotlin
 * val restApiMethod = RestApiConnectionMethod(
 *     options = RestApiOptions(
 *         uri = "https://reader.example.com/mdoc/response"
 *     )
 * )
 * ```
 *
 * ## ISO 18013-7 Compliance
 *
 * This implements "Device Retrieval to Website" as specified in ISO 18013-7 Annex A.
 * The reader provides a URI in the device engagement QR code, and the holder POSTs
 * the DeviceResponse to that URI.
 *
 * ## Security
 *
 * - **MUST** use HTTPS (not HTTP)
 * - **MUST** validate server certificate
 * - **SHOULD** use certificate pinning for known readers
 * - DeviceResponse is already encrypted at session level
 *
 * @param options REST API-specific configuration (contains URI)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RestApiConnectionMethod", exact = true)
data class RestApiConnectionMethod(
    override val options: RestApiOptions
) : ConnectionMethodBase<RestApiOptions>(options) {

    override val transportType: TransportType = TransportType.REST_API

    /**
     * Validate that the URI uses HTTPS
     */
    init {
        require(options.uri.startsWith("https://", ignoreCase = true)) {
            "REST API URI must use HTTPS, got: ${options.uri}"
        }
    }

    override fun toDeviceRetrievalMethod(): DeviceRetrievalMethod {
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.WEBSITE,
            retrievalOptions = options
        )
    }

    override fun toString(): String {
        return "RestApiConnectionMethod(uri=${options.uri})"
    }

    companion object : Factory {

        /**
         * Checks if this factory supports the given device retrieval method.
         *
         * @param deviceRetrievalMethod The device retrieval method to check
         * @return true if this is a REST API retrieval method with RestApiOptions
         */
        override fun supports(deviceRetrievalMethod: DeviceRetrievalMethod): Boolean {
            return deviceRetrievalMethod.type == DeviceRetrievalMethodType.WEBSITE &&
                    deviceRetrievalMethod.retrievalOptions is RestApiOptions
        }

        /**
         * Creates a REST API connection method from a device retrieval method.
         *
         * @param deviceRetrievalMethod The device retrieval method to parse
         * @return RestApiConnectionMethod if the method is supported, null otherwise
         */
        override fun create(deviceRetrievalMethod: DeviceRetrievalMethod): ConnectionMethod? {
            if (!supports(deviceRetrievalMethod)) {
                return null
            }
            val restApiOptions = deviceRetrievalMethod.retrievalOptions as RestApiOptions
            return RestApiConnectionMethod(options = restApiOptions)
        }
    }
}

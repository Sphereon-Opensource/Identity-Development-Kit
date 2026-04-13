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

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.cddl_uint
import kotlin.experimental.ExperimentalObjCName

import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalStatusCode", exact = true)
enum class DeviceRetrievalStatusCode(
    val statusCode: cddl_uint,
    val statusMessage: String,
    val explanation: String,
    val actionsRequired: String
) {
    OK(
        0L, "OK", "Normal processing. This status message shall be " +
                "returned if no other status is returned", "No specific action required"
    ),
    GENERAL_ERROR(
        10L,
        "General Error",
        "The mdoc returns an error without any given reason.",
        "The mdoc reader may inspect the\n" +
                "problem. The mdoc reader may\n" +
                "continue the transaction"
    ),
    CBOR_DECODING_ERROR(
        11L,
        "CBOR decoding error",
        "The mdoc indicates an error during CBOR decoding that the data received is not valid CBOR. Returning this status code is optional.",
        "The mdoc reader may inspect the\n" +
                "problem. The mdoc reader may\n" +
                "continue the transaction."
    ),
    CBOR_VALIDATION_ERROR(
        12L,
        "CBOR validation error",
        "The mdoc indicates an error during CBOR validation,\n" +
                "e.g. wrong CBOR structures. Returning this status code is optional",
        "The mdoc reader may inspect the\n" +
                "problem. The mdoc reader may\n" +
                "continue the transaction."
    );

    companion object {
        fun fromStatusCode(statusCode: cddl_uint) = entries.first {
            it.statusCode == statusCode
        }
    }
}

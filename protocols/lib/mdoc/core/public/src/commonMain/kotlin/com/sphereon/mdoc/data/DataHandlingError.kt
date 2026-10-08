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

package com.sphereon.mdoc.data

import com.sphereon.cbor.cddl_int
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DataHandlingError", exact = true)
enum class DataHandlingError(
    val errorCode: DataHandlingErrorCode,
    val errorCodeMessage: String,
    val errorDescription: String,
) {
    OK(
        DataHandlingErrorCode.OK,
        "Data not returned",
        "NThe mdoc does not provide the requested document or data element without\n" +
            "any given reason. This element may be used in all cases.",
    ),
    RFU(
        DataHandlingErrorCode.RFU,
        "RFU",
        "RFU",
    ),
    APPLICATION_SPECIFIC(
        DataHandlingErrorCode.APPLICATION_SPECIFIC,
        "These error codes may be used for application-specific purposes.",
        "These error codes may be used for application-specific purposes.",
    ),
    ;

    companion object Factory {
        @JsStatic
        @JvmStatic
        fun fromErrorCode(errorCode: cddl_int): DataHandlingError = entries.find { it.errorCode == DataHandlingErrorCode.Factory.fromErrorCodeValue(errorCode) }!!
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DataHandlingErrorCode", exact = true)
enum class DataHandlingErrorCode(
    val value: cddl_int,
) {
    OK(0L),
    RFU(1L),
    APPLICATION_SPECIFIC(-1L),
    ;

    companion object Factory {
        @JsStatic
        @JvmStatic
        fun fromErrorCodeValue(errorCode: cddl_int): DataHandlingErrorCode =
            if (errorCode == OK.value) {
                OK
            } else if (errorCode >= RFU.value) {
                RFU
            } else {
                // erroCode <= ErrorCodeAlias.APPLICATION_SPECIFIC
                APPLICATION_SPECIFIC
            }
    }
}

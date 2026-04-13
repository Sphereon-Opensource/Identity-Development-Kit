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

package com.sphereon.cbor

import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

/**
 * Enumeration of options that can be passed to [Cbor.toDiagnosticsEncoded], shamelessly copied from Google
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DiagnosticOption", exact = true)
@JsExportCompat
enum class DiagnosticOption {
    /**
     * Prints out embedded CBOR, that is, byte strings tagged with [CborTagged.ENCODED_CBOR].
     */
    EMBEDDED_CBOR,

    /**
     * Inserts newlines and indentation to make the output more readable.
     */
    PRETTY_PRINT,

    /**
     * Prints "<length> bytes" or "indefinite-size byte-string" instead of the bytes in the byte
     * string.
     */
    BSTR_PRINT_LENGTH
}

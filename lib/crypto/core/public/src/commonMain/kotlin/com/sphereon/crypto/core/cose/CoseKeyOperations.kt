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

package com.sphereon.crypto.core.cose

import com.sphereon.cbor.CborUInt
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoseKeyOperations", exact = true)
enum class CoseKeyOperations(val paramName: String, val value: Int, val explanation: String) {

    SIGN("sign", 1, "The key is used to create signatures.  Requires private key fields"),
    VERIFY("verify", 2, "The key is used for verification of signatures"),
    ENCRYPT("encrypt", 3, "The key is used for key transport encryption."),
    DECRYPT("decrypt", 4, "The key is used for key transport decryption"),
    WRAP_KEY("wrap key", 5, "The key is used for key wrap encryption."),
    UNWRAP_KEY("unwrap key", 6, "The key is used for key wrap decryption. Requires private key fields"),
    DERIVE_KEY("derive key", 7, "The key is used for deriving keys.  Requires private key fields"),
    DERIVE_BITS(
        "derive bits",
        8,
        "The key is used for deriving bits not to be used as a key.  Requires private key fields."
    ),
    MAC_CREATE("MAC create", 9, "The key is used for creating MACs. "),
    MAC_VERIFY("MAC verify", 10, "The key is used for validating MACs.");

    fun toCbor(): CborUInt {
        return CborUInt(this.value)
    }

    override fun toString() = value.toString()

    companion object {
        @JsStatic
        @JsName("fromValue")
        fun fromValue(value: Int): CoseKeyOperations {
            return entries.find { entry -> entry.value == value }
                ?: throw IllegalArgumentException("Unknown value $value")
        }
    }
}

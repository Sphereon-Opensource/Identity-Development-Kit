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

package com.sphereon.crypto.core.cose

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoseKeyOperations", exact = true)
enum class CoseKeyOperations(
    val paramName: String,
    val value: Int,
    val explanation: String,
) {
    SIGN("sign", value = 1, explanation = "The key is used to create signatures.  Requires private key fields"),
    VERIFY("verify", value = 2, explanation = "The key is used for verification of signatures"),
    ENCRYPT("encrypt", value = 3, explanation = "The key is used for key transport encryption."),
    DECRYPT("decrypt", value = 4, explanation = "The key is used for key transport decryption"),
    WRAP_KEY("wrap key", value = 5, explanation = "The key is used for key wrap encryption."),
    UNWRAP_KEY("unwrap key", value = 6, explanation = "The key is used for key wrap decryption. Requires private key fields"),
    DERIVE_KEY("derive key", value = 7, explanation = "The key is used for deriving keys.  Requires private key fields"),
    DERIVE_BITS(
        "derive bits",
        value = 8,
        explanation = "The key is used for deriving bits not to be used as a key.  Requires private key fields.",
    ),
    MAC_CREATE("MAC create", value = 9, explanation = "The key is used for creating MACs. "),
    MAC_VERIFY("MAC verify", value = 10, explanation = "The key is used for validating MACs."),
    ;

    override fun toString() = value.toString()

    companion object {
        @JsStatic
        @JsName("fromValue")
        @JvmStatic
        fun fromValue(value: Int): CoseKeyOperations =
            entries.find { entry -> entry.value == value }
                ?: throw IllegalArgumentException("Unknown value $value")
    }
}

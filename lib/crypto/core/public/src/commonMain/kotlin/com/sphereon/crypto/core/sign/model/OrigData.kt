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

package com.sphereon.crypto.core.sign.model

import com.sphereon.core.api.Base64UrlSerializer
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName

/**
 * Represent the original data at the start of a signature/digest process or data which gets included in a signature.
 * This data eventually will be merged with a signature
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("OrigData", exact = true)
@JsExportCompat
@Serializable
data class
OrigData
    @JvmOverloads
    constructor(
        @Serializable(with = Base64UrlSerializer::class) val value: ByteArray,
        val mimeType: String? = null,
        val name: String? = "document",
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as OrigData

            if (!value.contentEquals(other.value)) {
                return false
            }
            if (mimeType != other.mimeType) {
                return false
            }
            if (name != other.name) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = value.contentHashCode()
            result = 31 * result + (mimeType?.hashCode() ?: 0)
            result = 31 * result + (name?.hashCode() ?: 0)
            return result
        }
    }

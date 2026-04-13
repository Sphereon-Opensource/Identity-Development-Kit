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

package com.sphereon.crypto.core.kms.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("PasswordInputCallback", exact = true)
@Serializable
@JsExportCompat
data class PasswordInputCallback(
    val password: CharArray,
    val protectionAlgorithm: String? = null,
    @Contextual
    val protectionParameters: Any? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as PasswordInputCallback

        if (!password.contentEquals(other.password)) {
            return false
        }
        if (protectionAlgorithm != other.protectionAlgorithm) {
            return false
        }
        if (protectionParameters != other.protectionParameters) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = password.contentHashCode()
        result = 31 * result + (protectionAlgorithm?.hashCode() ?: 0)
        result = 31 * result + (protectionParameters?.hashCode() ?: 0)
        return result
    }
}

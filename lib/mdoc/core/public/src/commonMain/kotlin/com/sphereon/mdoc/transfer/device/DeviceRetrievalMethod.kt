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
 */

package com.sphereon.mdoc.transfer.device

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalMethod", exact = true)
data class DeviceRetrievalMethod(
    val type: DeviceRetrievalMethodType,
    val version: DeviceRetrievalMethodVersion = DeviceRetrievalMethodVersion(1u),
    val retrievalOptions: DeviceRetrievalOptions,
    val original: ByteArray? = null,
) {
    override fun toString(): String = "DeviceRetrievalMethod(type=${type.name}, version=${version.version}, retrievalOptions=$retrievalOptions)"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as DeviceRetrievalMethod

        if (type != other.type) {
            return false
        }
        if (version != other.version) {
            return false
        }
        if (retrievalOptions != other.retrievalOptions) {
            return false
        }
        if (!original.contentEquals(other.original)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + retrievalOptions.hashCode()
        result = 31 * result + (original?.contentHashCode() ?: 0)
        return result
    }
}

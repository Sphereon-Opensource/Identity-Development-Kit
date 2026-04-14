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

package com.sphereon.data.link.ble.filter

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ManufacturerData", exact = true)
data class ManufacturerData(
    /**
     * Two-octet [Company IdentifierAlias Code][https://www.bluetooth.com/specifications/assigned-numbers/company-identifiers/]
     */
    val code: Int,
    /**
     * the Manufacturer Data (not including the leading two identifier octets)
     */
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as ManufacturerData

        if (code != other.code) {
            return false
        }
        if (!data.contentEquals(other.data)) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = code
        result = 31 * result + data.contentHashCode()
        return result
    }
}

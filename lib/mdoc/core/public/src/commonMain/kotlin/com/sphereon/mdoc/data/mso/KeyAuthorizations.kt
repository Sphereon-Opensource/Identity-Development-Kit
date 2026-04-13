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

package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.StringLabel
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyAuthorizations", exact = true)
data class KeyAuthorizations(
    val nameSpaces: AuthorizedNameSpacesAlias? = null,
    val dataElements: AuthorizedDataElementsAlias? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is KeyAuthorizations) {
            return false
        }

        if (nameSpaces != other.nameSpaces) {
            return false
        }
        if (dataElements != other.dataElements) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = nameSpaces?.hashCode() ?: 0
        result = 31 * result + (dataElements?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "KeyAuthorizations(nameSpaces=$nameSpaces, dataElements=$dataElements)"

    companion object {
        val NAME_SPACES = StringLabel("nameSpaces")
        val DATA_ELEMENTS = StringLabel("dataElements")
    }
}

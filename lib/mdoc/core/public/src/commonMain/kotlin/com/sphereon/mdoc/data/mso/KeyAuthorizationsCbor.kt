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

package com.sphereon.mdoc.data.mso

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.core.compat.JsExportCompat


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyAuthorizationsCbor", exact = true)
data class KeyAuthorizationsCbor(
    val nameSpaces: AuthorizedNameSpacesAlias? = null,
    val dataElements: AuthorizedDataElementsAlias? = null
) : CborStructure<KeyAuthorizationsCbor, CborMap<StringLabel, CborItem<*>>>(CDDL.map) {
    override fun cborBuilder(): CborBuilder<KeyAuthorizationsCbor> = cborMapBuilder(this) {
        optional(NAME_SPACES, nameSpaces)
        optional(DATA_ELEMENTS, dataElements)
    }



    companion object: HasFromCbor<CborMap<StringLabel, CborItem<*>>, KeyAuthorizationsCbor> {
        val NAME_SPACES = StringLabel("nameSpaces")
        val DATA_ELEMENTS = StringLabel("dataElements")


        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>) =
            KeyAuthorizationsCbor(NAME_SPACES.optional(structure), DATA_ELEMENTS.optional(structure))

        override fun decodeCbor(bytes: ByteArray): KeyAuthorizationsCbor = fromCborStructure(cborSerializer.decode(bytes))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyAuthorizationsCbor) return false

        if (nameSpaces != other.nameSpaces) return false
        if (dataElements != other.dataElements) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nameSpaces?.hashCode() ?: 0
        result = 31 * result + (dataElements?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "KeyAuthorizationsCbor(nameSpaces=$nameSpaces, dataElements=$dataElements)"
    }
}

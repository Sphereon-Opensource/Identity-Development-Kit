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

package com.sphereon.mdoc.data

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CDDL
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.IntentToRetain
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.oid4vp.Oid4VPConstraintField
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DataElement", exact = true)
data class DataElement(
    val identifier: DataElementIdentifier, val intentToRetain: IntentToRetain, val definition: DataElementDef? = null
) {
    fun toPair(): Pair<DataElementIdentifier, IntentToRetain> {
        return Pair(identifier, intentToRetain)
    }

    override fun toString(): String {
        return "DataElement(identifier=$identifier, intentToRetain=$intentToRetain, definition=$definition)"
    }

    companion object {
        fun fromDefinition(def: DataElementDef): DataElement {
            return DataElement(def.identifier, IntentToRetain(def.presence.mandatory), def)
        }
    }


}

@OptIn(ExperimentalObjCName::class)
@ObjCName("DataElementDef", exact = true)
interface DataElementDef {
    val nameSpace: NameSpace
    val identifier: DataElementIdentifier
    val presence: Presence

    val details: String
    val cddls: Array<CDDL>
    val cddl: CDDL
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("AbstractDataElementDef", exact = true)
abstract class AbstractDataElementDef: DataElementDef {
    abstract override val nameSpace: NameSpace
    abstract override val identifier: DataElementIdentifier
    abstract override val presence: Presence
    abstract override val cddls: Array<CDDL>

    override val cddl: CDDL
        get() = this.cddls.first()

    @JsName("toElement")
    fun toElement(intentToRetain: IntentToRetain = IntentToRetain(false)): DataElement {
        return DataElement(identifier, intentToRetain)
    }

    fun toOid4VPConstraintField(intentToRetain: Boolean = false) = Oid4VPConstraintField.fromDataElementDef(this, intentToRetain)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AbstractDataElementDef) return false

        if (nameSpace != other.nameSpace) return false
        if (identifier != other.identifier) return false
        if (presence != other.presence) return false
        if (cddl != other.cddl) return false
        if (!cddls.contentEquals(other.cddls)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nameSpace.hashCode()
        result = 31 * result + identifier.hashCode()
        result = 31 * result + presence.hashCode()
        result = 31 * result + cddl.hashCode()
        result = 31 * result + cddls.contentHashCode()
        return result
    }


    override fun toString(): String {
        return "DataElementDef(nameSpace='$nameSpace', identifier='$identifier', presence=$presence, cddl=$cddl, cddls=${cddls.contentToString()})"
    }


}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Presence", exact = true)
enum class Presence(val value: String, val mandatory: Boolean) {
    MANDATORY("MANDATORY", true),
    OPTIONAL("OPTIONAL", false);
}

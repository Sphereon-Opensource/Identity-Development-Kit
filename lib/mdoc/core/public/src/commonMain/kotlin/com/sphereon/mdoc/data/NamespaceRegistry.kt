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

package com.sphereon.mdoc.data

import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.NameSpace
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("NamespaceRegistry", exact = true)
class NamespaceRegistry {
    val entries = mutableMapOf<String, List<DataElementDef>>()

    fun register(defs: List<DataElementDef>) = register(* defs.toTypedArray())

    fun register(vararg defs: DataElementDef) {
        for (def in defs) {
            entries.getOrPut(def.nameSpace.toString()) { listOf() }.toMutableList().add(def)
        }
    }

    fun get(nameSpace: NameSpace): List<DataElementIdentifier> = entries[nameSpace.toString()]?.map { it.identifier } ?: listOf()

    fun get(nameSpace: String): List<DataElementIdentifier> = entries[nameSpace]?.map { it.identifier } ?: listOf()

    fun has(nameSpace: NameSpace): Boolean = entries.containsKey(nameSpace.toString())

    fun hasDataElement(dataElement: DataElementDef): Boolean = entries[dataElement.nameSpace.toString()]?.first { dataElement.identifier == it.identifier } != null
}

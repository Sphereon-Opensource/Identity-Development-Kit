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

package com.sphereon.cbor

import kotlin.experimental.ExperimentalObjCName
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("HasToCborWithOriginal", exact = true)
@JsExportCompat
interface HasToCborWithOriginal<CborStructureType : CborItem<*>> : HasToCbor<CborStructureType> {
    val original: ByteArray?

    override fun encodeCbor(): ByteArray {
        if (original != null) return original!!
        return super.encodeCbor()
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("HasToCbor", exact = true)
@JsExportCompat
interface HasToCbor<CborStructureType : CborItem<*>> {
    @JsName("toCborStructure")
    fun toCborStructure(): CborStructureType

    @JsName("encodeCbor")
    fun encodeCbor(): ByteArray = Cbor.encode(toCborStructure())
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("HasFromCborWithOriginal", exact = true)
@JsExportCompat
interface HasFromCborWithOriginal<CborStructureType : CborItem<*>, T : HasToCbor<CborStructureType>> : HasFromCbor<CborStructureType, T> {
    @JsName("fromCborStructureWithOriginal")
    fun fromCborStructureWithOriginal(structure: CborStructureType, original: ByteArray?): T

    override fun decodeCbor(bytes: ByteArray): T = fromCborStructureWithOriginal(Cbor.decode(bytes), bytes)

}

@OptIn(ExperimentalObjCName::class)
@ObjCName("HasFromCbor", exact = true)
@JsExportCompat
interface HasFromCbor<CborStructureType : CborItem<*>, T : HasToCbor<CborStructureType>> {

    @JsName("fromCborItem")
    fun fromCborStructure(structure: CborStructureType): T
    @JsName("decodeCbor")
    fun decodeCbor(bytes: ByteArray): T = fromCborStructure(Cbor.decode(bytes))
}

//interface HasFromAndToCbor<CborType : CborItem<*>, T> : HasFromCbor<CborType, T>, HasToCbor<CborType>


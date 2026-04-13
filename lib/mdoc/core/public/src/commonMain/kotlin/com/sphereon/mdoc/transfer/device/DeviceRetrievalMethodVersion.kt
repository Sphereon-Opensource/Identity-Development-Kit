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

package com.sphereon.mdoc.transfer.device

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import kotlinx.serialization.Serializable
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.toCborUIntFromUint
import kotlin.js.JsStatic
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalMethodVersion", exact = true)
value class DeviceRetrievalMethodVersion(val version: UInt) {
    fun toCborItem(): CborUInt {
        return version.toCborUIntFromUint()
    }

    companion object {
        @JsStatic
        fun fromCborStructure(item: CborUInt): DeviceRetrievalMethodVersion {
            return DeviceRetrievalMethodVersion(item.value.toUInt())
        }
    }

    override fun toString(): String {
        return "$version"
    }
}
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

package com.sphereon.cbor.json

import kotlin.experimental.ExperimentalObjCName
import kotlinx.serialization.Serializable
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("HasToJsonString", exact = true)
@JsExportCompat
interface HasToJsonString {
    fun toJsonString(): String
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("HasToJsonDTO", exact = true)
@JsExportCompat
interface HasToJsonDTO {
    fun <T> toJsonDTO(): T
}

@Serializable
@JsExportCompat
abstract class JsonView: HasToJsonString, HasToJsonDTO {
    override fun <T> toJsonDTO() = toJsonDTO<T>(this)
    abstract fun toCbor(): Any
}

expect fun <T> toJsonDTO(subject: HasToJsonString): T



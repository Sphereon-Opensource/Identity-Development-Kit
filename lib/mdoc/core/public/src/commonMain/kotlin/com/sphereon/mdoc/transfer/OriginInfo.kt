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

package com.sphereon.mdoc.transfer

import com.sphereon.cbor.StringLabel
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("OriginInfoCategory", exact = true)
value class OriginInfoCategory(
    val category: UInt,
) {
    override fun toString(): String = category.toString()
}

@Serializable
@JvmInline
@OptIn(ExperimentalObjCName::class)
@ObjCName("OriginInfoType", exact = true)
value class OriginInfoType(
    val infoType: UInt,
) {
    override fun toString(): String = infoType.toString()
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("OriginInfoDetails", exact = true)
data class OriginInfoDetails(
    val map: Map<String, Any?>,
) : Map<String, Any?> by map {
    companion object {
        const val DOMAIN = "domain"
    }
}

typealias OriginInfos = Array<OriginInfo>

@JsExportCompat
data class OriginInfo(
    val cat: OriginInfoCategory,
    val type: OriginInfoType,
    val details: OriginInfoDetails? = null,
    val original: ByteArray?,
) {
    companion object {
        @JvmStatic
        val CAT = StringLabel("cat")

        @JvmStatic
        val TYPE = StringLabel("type")

        @JvmStatic
        val DETAILS = StringLabel("details")
    }
}

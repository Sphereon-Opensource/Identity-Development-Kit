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

package com.sphereon.mdoc.transfer.device

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.StringLabel
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ServerRetrievalMethods", exact = true)
data class ServerRetrievalMethods(
    val Oidc: ServerRetrievalInfo?,
    val WebApi: ServerRetrievalInfo?,
) {
    companion object {
        @JsStatic
        @JvmStatic
        val OIDC = StringLabel("Oidc")

        @JsStatic
        @JvmStatic
        val WEB_API = StringLabel("WebApi")
    }
}

internal fun ServerRetrievalMethods.toCborItem(): CborMap<StringLabel, CborItem<*>> {
    val entries = mutableMapOf<StringLabel, CborItem<*>>()
    Oidc?.let { entries[ServerRetrievalMethods.OIDC] = it.toCborItem() }
    WebApi?.let { entries[ServerRetrievalMethods.WEB_API] = it.toCborItem() }
    return CborMap(entries)
}

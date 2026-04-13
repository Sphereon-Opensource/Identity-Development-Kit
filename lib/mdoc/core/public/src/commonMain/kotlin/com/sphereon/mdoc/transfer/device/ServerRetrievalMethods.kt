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

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ServerRetrievalMethods", exact = true)
data class ServerRetrievalMethods(val Oidc: ServerRetrievalInfo?, val WebApi: ServerRetrievalInfo?) :
    CborStructure<ServerRetrievalMethods, CborMap<StringLabel, CborItem<*>>>(CDDL.map) {
    override fun cborBuilder(): CborBuilder<ServerRetrievalMethods> = cborMapBuilder(this) {
        optional(OIDC, Oidc?.cborBuilder()?.build())
        optional(WEB_API, WebApi?.cborBuilder()?.build())
    }


    companion object Decoder : HasFromCbor<CborMap<StringLabel, CborItem<*>>, ServerRetrievalMethods> {
        @JsStatic
        val OIDC = StringLabel("Oidc")

        @JsStatic
        val WEB_API = StringLabel("WebApi")

        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>): ServerRetrievalMethods {
            return ServerRetrievalMethods(
                OIDC.optional(structure), WEB_API.optional(structure)
            )
        }
    }
}
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
import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.dsl.cborArrayBuilder
import com.sphereon.cbor.toCborString
import com.sphereon.cbor.toCborUIntFromUint
import com.sphereon.cbor.toUInt
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ServerRetrievalInfo", exact = true)
data class ServerRetrievalInfo(val version: UInt, val issuerUrl: String, val serverRetrievalToken: String) {
    fun cborBuilder(): CborBuilder<ServerRetrievalInfo> = cborArrayBuilder(this) {
        add(version.toCborUIntFromUint())
        add(issuerUrl.toCborString())
        add(serverRetrievalToken.toCborString())
    }

    fun toCbor(): ByteArray {
        return cborBuilder().encodedBuild()
    }

    companion object {
        @JsStatic
        fun fromCbor(serverRetrievalInfo: ByteArray): ServerRetrievalInfo {
            val items: CborArray<CborItem<*>> = Cbor.decode(serverRetrievalInfo)
            return ServerRetrievalInfo((items.required(0) as CborUInt).toUInt(), (items.required(1) as CborString).value, (items.required(2) as CborString).value)
        }
    }
}
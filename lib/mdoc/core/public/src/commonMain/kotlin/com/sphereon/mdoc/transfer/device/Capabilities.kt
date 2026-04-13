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
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborInt
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.cbor.toCborBool
import com.sphereon.crypto.core.cose.CoseCurve

@OptIn(ExperimentalObjCName::class)
@ObjCName("Capabilities", exact = true)
data class Capabilities(
    val macKeysSupport: Boolean? = null,
    val macKeyCurves: Array<CoseCurve>? = null,
    val handoverSessionEstablishmentSupport: Boolean? = null,
    val readerAuthAllSupport: Boolean? = null,
    val extendedRequestSupport: Boolean? = null,
    val additionalItems: CborMap<NumberLabel, CborItem<*>>? = null
) :
    CborStructure<Capabilities, CborMap<NumberLabel, CborItem<*>>>(CDDL.map) {
    override fun cborBuilder(): CborBuilder<Capabilities> = cborMapBuilder(this) {
        optional(MAC_KEYS_SUPPORT, macKeysSupport?.toCborBool())
        optional(MAC_KEY_CURVES, if (macKeyCurves.isNullOrEmpty()) null else macKeyCurves.map { it.value }.toTypedArray())
        optional(HANDOVER_SESSION_ESTABLISHMENT_SUPPORT, handoverSessionEstablishmentSupport?.toCborBool())
        optional(READER_AUTH_ALL_SUPPORT, readerAuthAllSupport?.toCborBool())
        optional(EXTENDED_REQUEST_SUPPORT, extendedRequestSupport?.toCborBool())
        // TODO: Handle additionalItems if needed
    }

    companion object Decoder : HasFromCbor<CborMap<NumberLabel, CborItem<*>>, Capabilities> {
        val MAC_KEYS_SUPPORT = NumberLabel(0)
        val MAC_KEY_CURVES = NumberLabel(1)

        val HANDOVER_SESSION_ESTABLISHMENT_SUPPORT = NumberLabel(2)
        val READER_AUTH_ALL_SUPPORT = NumberLabel(3)
        val EXTENDED_REQUEST_SUPPORT = NumberLabel(4)

        override fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>): Capabilities {
            return Capabilities(
                macKeysSupport = MAC_KEYS_SUPPORT.required(structure), macKeyCurves = MAC_KEY_CURVES.required<CborArray<CborInt>>(structure).value.map {
                    CoseCurve.fromValue(it.value.toInt())
                }.toTypedArray(),
                handoverSessionEstablishmentSupport = HANDOVER_SESSION_ESTABLISHMENT_SUPPORT.optional(structure),
                readerAuthAllSupport = READER_AUTH_ALL_SUPPORT.optional(structure),
                extendedRequestSupport = EXTENDED_REQUEST_SUPPORT.optional(structure),
                // TODO: Filter out the above
                additionalItems = structure
            )
        }
    }

}
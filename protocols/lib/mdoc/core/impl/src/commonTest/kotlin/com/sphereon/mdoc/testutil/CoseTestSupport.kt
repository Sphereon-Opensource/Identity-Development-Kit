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

package com.sphereon.mdoc.testutil

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.NumberLabel
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.crypto.core.cose.CoseSign1CborCodecImpl

private val coseKeyCodec = CoseKeyCborCodecImpl()
private val coseSign1Codec = CoseSign1CborCodecImpl()

internal fun encodeCoseKey(value: CoseKeyType): ByteArray =
    when (value) {
        is CoseKey -> coseKeyCodec.encode(value).getOrThrow()
    }

internal fun coseKeyAsCborMap(value: CoseKeyType): CborMap<NumberLabel, CborItem<*>> = Cbor.decode(encodeCoseKey(value))

internal fun coseSign1AsCborArray(value: CoseSign1<*>): CborArray<CborItem<*>> = Cbor.decode(coseSign1Codec.encode(value).getOrThrow())

internal fun coseKeyTypeAsCborUInt(value: CoseKeyTypeEnum): CborUInt = CborUInt(value.value.toLong())

internal fun coseCurveAsCborUInt(value: CoseCurve): CborUInt = CborUInt(value.value.toLong())

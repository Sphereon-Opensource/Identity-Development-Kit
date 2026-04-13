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

package com.sphereon.crypto.core

import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.toCborByteString
import com.sphereon.cbor.toCborString
import com.sphereon.core.api.Encoding
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("UNCHECKED_CAST")
class EncodedCborItemTest {
    @Test
    fun shouldEncodeAndDecodeWrapper() {
        val input = "input".toCborString()
        val cborInput =
            CborEncodedItem(
                com.sphereon.cbor.Cbor
                    .encode(input),
                input,
            )
        val bytes =
            com.sphereon.cbor.Cbor
                .encode(cborInput)

        // Not using type on purpose
        val test: CborItem<*> =
            com.sphereon.cbor.Cbor
                .decode(bytes)
        assertEquals(CDDL.bstr, test.cddl)
        val cborEncoded: CborEncodedItem<CborString> = test as CborEncodedItem<CborString>
        assertEquals(
            input.value,
            cborEncoded
                .data {
                    com.sphereon.cbor.Cbor
                        .decode(it)
                }.value,
        )
        println(test.toString())
    }

    @Test
    fun shouldEncodeAndDecodeKid() {
        val cborKey = CoseKey(kid = "11".toCborByteString(Encoding.UTF8), kty = CborUInt(CoseKeyTypeEnum.EC2.value.toLong()))
        val jsonKey = cborKey.toJson()
        println(jsonKey)
    }
}

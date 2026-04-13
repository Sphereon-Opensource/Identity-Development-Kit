/*
 * Â© 2026 Sphereon International B.V.
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

package com.sphereon.crypto.core.cose

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

data class DecodedCbor<T>(
    val value: T,
    val originalBytes: ByteArray,
)

interface CoseKeyCborCodec {
    fun encode(value: CoseKey): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<DecodedCbor<CoseKey>, IdkError>
}

interface CoseHeaderCborCodec {
    fun encode(value: CoseHeaderCbor): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<DecodedCbor<CoseHeaderCbor>, IdkError>
}

interface CoseSign1CborCodec {
    fun encode(value: CoseSign1<*>): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<DecodedCbor<CoseSign1<Any>>, IdkError>
}

interface CoseMac0CborCodec {
    fun encode(value: CoseMac0Cbor): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<DecodedCbor<CoseMac0Cbor>, IdkError>
}

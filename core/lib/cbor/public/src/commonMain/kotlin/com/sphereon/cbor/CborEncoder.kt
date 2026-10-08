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

package com.sphereon.cbor

import com.sphereon.util.appendUInt16
import com.sphereon.util.appendUInt32
import com.sphereon.util.appendUInt64
import com.sphereon.util.appendUInt8
import kotlinx.io.bytestring.ByteStringBuilder

internal fun cborEncodeLength(
    builder: ByteStringBuilder,
    majorType: MajorType,
    length: Int,
) = cborEncodeLength(builder, majorType, length.toULong())

internal fun cborEncodeLength(
    builder: ByteStringBuilder,
    majorType: MajorType,
    length: ULong,
) {
    val majorTypeShifted = (majorType.type shl 5).toUByte()
    builder.apply {
        when {
            length < 24U -> appendUInt8(majorTypeShifted.or(length.toUByte()))
            length < (1UL shl 8) -> appendUInt8(majorTypeShifted.or(24u)).appendUInt8(length.toUByte())
            length < (1UL shl 16) -> appendUInt8(majorTypeShifted.or(25u)).appendUInt16(length.toUInt())
            length < (1UL shl 32) -> appendUInt8(majorTypeShifted.or(26u)).appendUInt32(length.toUInt())
            else -> appendUInt8(majorTypeShifted.or(27u)).appendUInt64(length)
        }
    }
}

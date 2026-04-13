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

package com.sphereon.mdoc.engagement

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.mdoc.DecodedMdoc

interface DeviceEngagementCborCodec {
    fun encode(value: DeviceEngagement): IdkResult<ByteArray, IdkError>

    fun encodeItem(value: DeviceEngagement): IdkResult<CborEncodedItem<DeviceEngagement>, IdkError>

    fun encodeTag24(value: DeviceEngagement): IdkResult<ByteArray, IdkError>

    fun encodeMessage(value: DeviceEngagement): IdkResult<ByteArray, IdkError>

    fun encodeMessageItem(value: CborEncodedItem<DeviceEngagement>): IdkResult<ByteArray, IdkError>

    fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<DeviceEngagement>, IdkError>

    fun decodeMessage(bytes: ByteArray): IdkResult<DecodedMdoc<DeviceEngagement>, IdkError>
}

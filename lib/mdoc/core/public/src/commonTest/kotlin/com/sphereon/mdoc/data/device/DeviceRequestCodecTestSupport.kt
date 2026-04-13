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

@file:Suppress("DEPRECATION")

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.StringLabel

private val deviceRequestCborCodec = DeviceRequestCborCodecImpl()

internal fun encodeDeviceRequestWithCodec(value: DeviceRequest): ByteArray = deviceRequestCborCodec.encode(value).getOrThrow()

internal fun decodeDeviceRequestWithCodec(bytes: ByteArray): DeviceRequest = deviceRequestCborCodec.decode(bytes).getOrThrow().value

internal fun encodeDocRequestWithCodec(value: DocRequest): ByteArray {
    val encodedRequest = encodeDeviceRequestWithCodec(DeviceRequest(docRequests = arrayOf(value), original = null))
    val structure = decodeCborMap(encodedRequest)
    val docRequests = requireEntry(structure, DeviceRequest.DOC_REQUESTS) as CborArray<CborMap<CborItem<*>, CborItem<*>>>
    return com.sphereon.cbor.Cbor
        .encode(docRequests.value.single())
}

internal fun decodeDocRequestWithCodec(bytes: ByteArray): DocRequest {
    val docRequest = decodeCborMap(bytes)
    val encodedRequest =
        Cbor.encode(
            CborMap(
                mutableMapOf(
                    DeviceRequest.VERSION to CborString("1.0"),
                    DeviceRequest.DOC_REQUESTS to CborArray(mutableListOf(docRequest)),
                ),
            ),
        )
    return requireNotNull(decodeDeviceRequestWithCodec(encodedRequest).docRequests).single()
}

internal fun encodeDeviceItemsRequestWithCodec(value: DeviceItemsRequest): ByteArray {
    val encodedDocRequest = encodeDocRequestWithCodec(DocRequest(itemsRequest = value))
    val docRequest = decodeCborMap(encodedDocRequest)
    val taggedItemsRequest = requireEntry(docRequest, DocRequest.ITEMS_REQUEST) as CborEncodedItem<CborMap<CborItem<*>, CborItem<*>>>
    return taggedItemsRequest.value.taggedItem.value
}

internal fun decodeDeviceItemsRequestWithCodec(bytes: ByteArray): DeviceItemsRequest {
    val encodedDocRequest =
        Cbor.encode(
            CborMap(
                mutableMapOf(
                    DocRequest.ITEMS_REQUEST to CborEncodedItem<CborMap<StringLabel, CborItem<*>>>(bytes),
                ),
            ),
        )
    return decodeDocRequestWithCodec(encodedDocRequest).itemsRequest
}

@Suppress("UNCHECKED_CAST")
private fun decodeCborMap(bytes: ByteArray): CborMap<CborItem<*>, CborItem<*>> =
    com.sphereon.cbor.Cbor
        .decode(bytes)

private fun requireEntry(
    structure: CborMap<CborItem<*>, CborItem<*>>,
    label: StringLabel,
): CborItem<*> =
    structure.value.entries
        .firstOrNull { (key, _) -> StringLabel.fromCborItem(key) == label }
        ?.value
        ?: throw IllegalArgumentException("Key (${label.value}) not found in cbor map")

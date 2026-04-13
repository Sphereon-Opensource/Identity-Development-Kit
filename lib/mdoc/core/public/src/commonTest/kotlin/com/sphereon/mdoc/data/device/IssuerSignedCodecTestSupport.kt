/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborEncodedItem

private val issuerSignedCborCodec = IssuerSignedCborCodecImpl()
private val issuerSignedItemCborCodec = IssuerSignedItemCborCodecImpl()

internal fun encodeIssuerSignedWithCodec(value: IssuerSigned): ByteArray = issuerSignedCborCodec.encode(value).getOrThrow()

internal fun decodeIssuerSignedWithCodec(bytes: ByteArray): IssuerSigned = issuerSignedCborCodec.decode(bytes).getOrThrow().value

@Suppress("UNCHECKED_CAST")
internal fun <Type : Any> encodeIssuerSignedItemWithCodec(value: IssuerSignedItem<Type>): ByteArray = issuerSignedItemCborCodec.encode(value as IssuerSignedItem<Any>).getOrThrow()

internal fun decodeIssuerSignedItemWithCodec(bytes: ByteArray): IssuerSignedItem<Any> = issuerSignedItemCborCodec.decode(bytes).getOrThrow().value

internal fun decodeIssuerSignedItemWithCodec(item: com.sphereon.cbor.CborItem<*>): IssuerSignedItem<Any> = issuerSignedItemCborCodec.decode(item).getOrThrow()

@Suppress("UNCHECKED_CAST")
internal fun <Type : Any> encodeIssuerSignedItemAsEncoded(value: IssuerSignedItem<Type>): CborEncodedItem<IssuerSignedItem<Any>> =
    issuerSignedItemCborCodec.encodeItem(value as IssuerSignedItem<Any>).getOrThrow()

internal fun issuerSignedItemCodec(): IssuerSignedItemCborCodec = issuerSignedItemCborCodec

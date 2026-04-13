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

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborParser
import com.sphereon.cbor.CborParserImpl
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.toCborItem
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.mdoc.DecodedMdoc
import com.sphereon.mdoc.data.mso.DigestID
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<IssuerSignedItemCborCodec>())
class IssuerSignedItemCborCodecImpl(
    private val cborParser: CborParser = CborParserImpl(),
) : IssuerSignedItemCborCodec {
    override fun encode(value: IssuerSignedItem<Any>): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "IssuerSignedItem",
            operation = { encodeIssuerSignedItem(value) },
        )

    override fun encodeItem(value: IssuerSignedItem<Any>): IdkResult<com.sphereon.cbor.CborEncodedItem<IssuerSignedItem<Any>>, IdkError> =
        encode(value).map { encoded ->
            com.sphereon.cbor.CborEncodedItem(encoded, value)
        }

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<IssuerSignedItem<Any>>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val map = requireStringLabelMap(item, "IssuerSignedItem")
            decodeValue(
                typeName = "IssuerSignedItem",
                bytes = bytes,
                operation = { decodeIssuerSignedItem(map) },
            )
        }

    override fun decode(item: CborItem<*>): IdkResult<IssuerSignedItem<Any>, IdkError> =
        try {
            Ok(decodeIssuerSignedItem(requireStringLabelMap(item, "IssuerSignedItem")))
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode IssuerSignedItem: ${e.message}", throwable = e))
        } catch (expected: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode IssuerSignedItem: ${expected.message}", exception = expected))
        }
}

private inline fun <T> encodeValue(
    typeName: String,
    operation: () -> T,
): IdkResult<T, IdkError> =
    try {
        Ok(operation())
    } catch (e: IllegalArgumentException) {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to encode $typeName: ${e.message}", throwable = e))
    } catch (expected: Throwable) {
        Err(IdkError.UNKNOWN_ERROR(message = "Failed to encode $typeName: ${expected.message}", exception = expected))
    }

private inline fun <T> decodeValue(
    typeName: String,
    bytes: ByteArray,
    operation: () -> T,
): IdkResult<DecodedMdoc<T>, IdkError> =
    try {
        Ok(DecodedMdoc(operation(), bytes))
    } catch (e: IllegalArgumentException) {
        Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode $typeName: ${e.message}", throwable = e))
    } catch (expected: Throwable) {
        Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode $typeName: ${expected.message}", exception = expected))
    }

private fun encodeIssuerSignedItem(value: IssuerSignedItem<Any>): ByteArray =
    com.sphereon.cbor.Cbor.encode(
        CborMap(
            mutableMapOf(
                IssuerSignedItem.DIGEST_ID to CborUInt(value.digestID.toString().toLong()),
                IssuerSignedItem.RANDOM to value.random.toCborItem(),
                IssuerSignedItem.ELEMENT_IDENTIFIER to CborString(value.elementIdentifier.toString()),
                IssuerSignedItem.ELEMENT_VALUE to value.elementValue.toCborItem(),
            ),
        ),
    )

@Suppress("UNCHECKED_CAST")
private fun decodeIssuerSignedItem(structure: CborMap<StringLabel, CborItem<*>>): IssuerSignedItem<Any> =
    IssuerSignedItem(
        digestID =
            DigestID(
                IssuerSignedItem.DIGEST_ID
                    .required<CborUInt>(structure)
                    .value
                    .toUInt(),
            ),
        random = RandomValue.Decoder.fromCborItem(IssuerSignedItem.RANDOM.required(structure)),
        elementIdentifier = DataElementIdentifier(IssuerSignedItem.ELEMENT_IDENTIFIER.required<CborString>(structure).value),
        elementValue = (IssuerSignedItem.ELEMENT_VALUE.required(structure) as CborItem<Any>).value,
    )

@Suppress("UNCHECKED_CAST")
private fun requireStringLabelMap(
    item: CborItem<*>,
    typeName: String,
): CborMap<StringLabel, CborItem<*>> {
    require(item is CborMap<*, *>) { "$typeName must be encoded as a CBOR map" }

    val normalizedEntries =
        item.value.entries
            .associate { (key, value) ->
                val keyItem =
                    key as? CborItem<*>
                        ?: throw IllegalArgumentException("$typeName map keys must be CBOR items")
                val valueItem =
                    value as? CborItem<*>
                        ?: throw IllegalArgumentException("$typeName map values must be CBOR items")
                StringLabel.fromCborItem(keyItem) to valueItem
            }.toMutableMap()

    return CborMap(normalizedEntries, item.indefiniteLength)
}

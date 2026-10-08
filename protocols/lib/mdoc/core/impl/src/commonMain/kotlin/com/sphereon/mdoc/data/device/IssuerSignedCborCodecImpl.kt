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

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborNInt
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
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseSign1CborCodec
import com.sphereon.crypto.core.cose.CoseSign1CborCodecImpl
import com.sphereon.mdoc.DecodedMdoc
import com.sphereon.mdoc.data.mso.DigestID
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<IssuerSignedCborCodec>())
class IssuerSignedCborCodecImpl(
    private val cborParser: CborParser,
    private val coseSign1Codec: CoseSign1CborCodec,
    private val issuerSignedItemCodec: IssuerSignedItemCborCodec,
) : IssuerSignedCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        coseSign1Codec = CoseSign1CborCodecImpl(cborParser),
        issuerSignedItemCodec = IssuerSignedItemCborCodecImpl(cborParser),
    )

    override fun encode(value: IssuerSigned): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "IssuerSigned",
            operation = { encodeIssuerSigned(value, cborParser, coseSign1Codec) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<IssuerSigned>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val map = requireStringLabelMap(item, "IssuerSigned")
            decodeValue(
                typeName = "IssuerSigned",
                bytes = bytes,
                operation = { decodeIssuerSigned(map, bytes, coseSign1Codec, issuerSignedItemCodec) },
            )
        }

    override fun decode(item: CborItem<*>): IdkResult<IssuerSigned, IdkError> =
        try {
            Ok(decodeIssuerSigned(requireStringLabelMap(item, "IssuerSigned"), null, coseSign1Codec, issuerSignedItemCodec))
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode IssuerSigned: ${e.message}", throwable = e))
        } catch (expected: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode IssuerSigned: ${expected.message}", exception = expected))
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

private fun encodeIssuerSigned(
    value: IssuerSigned,
    cborParser: CborParser,
    coseSign1Codec: CoseSign1CborCodec,
): ByteArray {
    value.original?.let { return it }

    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            IssuerSigned.ISSUER_AUTH to encodeCborItem(coseSign1Codec.encode(value.issuerAuth).getOrThrow(), cborParser),
        )
    value.nameSpaces?.let { nameSpaces ->
        entries[IssuerSigned.NAME_SPACES] =
            CborMap(
                nameSpaces.entries
                    .associate { (nameSpace, encodedItems) ->
                        CborString(nameSpace.toString()) to CborArray(encodedItems.toMutableList())
                    }.toMutableMap(),
            )
    }
    return com.sphereon.cbor.Cbor
        .encode(CborMap(entries))
}

private fun decodeIssuerSigned(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray?,
    coseSign1Codec: CoseSign1CborCodec,
    issuerSignedItemCodec: IssuerSignedItemCborCodec,
): IssuerSigned {
    val nameSpaces =
        IssuerSigned.NAME_SPACES
            .optional<CborMap<CborItem<*>, CborArray<CborEncodedItem<CborMap<CborItem<*>, CborItem<*>>>>>>(structure)
            ?.value
            ?.map { (nameSpaceKey, encodedItems) ->
                decodeNameSpace(nameSpaceKey) to
                    encodedItems.value
                        .map { encodedItem ->
                            encodedItem.copy(
                                data = issuerSignedItemCodec.decode(encodedItem.value.value).getOrThrow().value,
                            )
                        }.toTypedArray()
            }?.toMap()

    val issuerAuth =
        IssuerSigned.ISSUER_AUTH
            .required<CborArray<CborItem<*>>>(structure)
            .let { decodeIssuerAuth(it, coseSign1Codec) }

    return IssuerSigned(
        nameSpaces = nameSpaces,
        issuerAuth = issuerAuth,
        original = original,
    )
}

@Suppress("UNCHECKED_CAST")
private fun decodeIssuerAuth(
    structure: CborArray<CborItem<*>>,
    coseSign1Codec: CoseSign1CborCodec,
): COSE_Sign1<MobileSecurityObject> =
    coseSign1Codec
        .decode(
            com.sphereon.cbor.Cbor
                .encode(structure),
        ).getOrThrow()
        .value as COSE_Sign1<MobileSecurityObject>

private fun decodeNameSpace(key: CborItem<*>): NameSpace =
    when (key) {
        is StringLabel -> NameSpace(key.value)
        is CborString -> NameSpace(key.value)
        else -> throw IllegalArgumentException("IssuerSigned nameSpaces keys must be CBOR strings")
    }

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

private fun encodeCborItem(
    bytes: ByteArray,
    cborParser: CborParser,
): CborItem<*> = cborParser.parse(bytes).getOrThrow()

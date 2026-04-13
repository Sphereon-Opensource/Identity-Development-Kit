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

import com.sphereon.cbor.CborInt
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
import com.sphereon.mdoc.DecodedMdoc
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DocumentCborCodec>())
class DocumentCborCodecImpl(
    private val cborParser: CborParser,
    private val issuerSignedCodec: IssuerSignedCborCodec,
    private val deviceSignedCodec: DeviceSignedCborCodec,
) : DocumentCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        issuerSignedCodec = IssuerSignedCborCodecImpl(cborParser),
        deviceSignedCodec = DeviceSignedCborCodecImpl(cborParser),
    )

    override fun encode(value: Document): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "Document",
            operation = { encodeDocument(value, cborParser, issuerSignedCodec, deviceSignedCodec) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<Document>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val map = requireStringLabelMap(item, "Document")
            decodeValue(
                typeName = "Document",
                bytes = bytes,
                operation = { decodeDocument(map, bytes, issuerSignedCodec, deviceSignedCodec) },
            )
        }

    override fun decode(item: CborItem<*>): IdkResult<Document, IdkError> =
        try {
            Ok(decodeDocument(requireStringLabelMap(item, "Document"), null, issuerSignedCodec, deviceSignedCodec))
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode Document: ${e.message}", throwable = e))
        } catch (expected: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode Document: ${expected.message}", exception = expected))
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

private fun encodeDocument(
    value: Document,
    cborParser: CborParser,
    issuerSignedCodec: IssuerSignedCborCodec,
    deviceSignedCodec: DeviceSignedCborCodec,
): ByteArray {
    value.original?.let { return it }

    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            Document.DOC_TYPE to CborString(value.docType.toString()),
            Document.ISSUER_SIGNED to encodeCborItem(issuerSignedCodec.encode(value.issuerSigned).getOrThrow(), cborParser),
        )
    value.deviceSigned?.let { entries[Document.DEVICE_SIGNED] = encodeCborItem(deviceSignedCodec.encode(it).getOrThrow(), cborParser) }
    val errors = value.errors
    if (!errors.isNullOrEmpty()) {
        entries[Document.ERRORS] = encodeDocumentErrors(errors)
    }
    return com.sphereon.cbor.Cbor
        .encode(CborMap(entries))
}

private fun decodeDocument(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray?,
    issuerSignedCodec: IssuerSignedCborCodec,
    deviceSignedCodec: DeviceSignedCborCodec,
): Document =
    Document(
        docType = DocType(Document.DOC_TYPE.required<CborString>(structure).value),
        issuerSigned = issuerSignedCodec.decode(Document.ISSUER_SIGNED.required(structure)).getOrThrow(),
        deviceSigned =
            Document.DEVICE_SIGNED
                .optional<CborMap<CborItem<*>, CborItem<*>>>(structure)
                ?.let { deviceSignedCodec.decode(it).getOrThrow() },
        errors =
            Document.ERRORS
                .optional<CborMap<CborItem<*>, CborItem<*>>>(structure)
                ?.let { decodeDocumentErrors(it) },
        original = original,
    )

private fun encodeDocumentErrors(value: Map<NameSpace, Map<DataElementIdentifier, Long>>): CborMap<CborString, CborMap<CborString, CborItem<*>>> =
    CborMap(
        value.entries
            .associate { (nameSpace, errors) ->
                CborString(nameSpace.toString()) to
                    CborMap(
                        errors.entries
                            .associate { (identifier, errorCode) ->
                                CborString(identifier.toString()) to errorCode.toCborItem()
                            }.toMutableMap(),
                    )
            }.toMutableMap(),
    )

private fun decodeDocumentErrors(structure: CborMap<CborItem<*>, CborItem<*>>): Map<NameSpace, Map<DataElementIdentifier, Long>> =
    structure.value
        .map { (nameSpaceKey, items) ->
            decodeNameSpace(nameSpaceKey) to
                requireNestedMap(items, "Document.errors")
                    .value
                    .map { (identifierKey, value) ->
                        decodeDataElementIdentifier(identifierKey) to decodeErrorCodeAlias(value)
                    }.toMap()
        }.toMap()

private fun decodeNameSpace(key: CborItem<*>): NameSpace =
    when (key) {
        is StringLabel -> NameSpace(key.value)
        is CborString -> NameSpace(key.value)
        else -> throw IllegalArgumentException("Document.errors keys must be CBOR strings")
    }

private fun decodeDataElementIdentifier(key: CborItem<*>): DataElementIdentifier =
    when (key) {
        is StringLabel -> DataElementIdentifier(key.value)
        is CborString -> DataElementIdentifier(key.value)
        else -> throw IllegalArgumentException("Document.errors item keys must be CBOR strings")
    }

private fun decodeErrorCodeAlias(value: CborItem<*>): Long =
    when (value) {
        is CborInt -> value.value
        is CborUInt -> value.value
        is CborNInt -> -value.value
        else -> throw IllegalArgumentException("Document.errors values must be CBOR ints")
    }

@Suppress("UNCHECKED_CAST")
private fun requireNestedMap(
    item: CborItem<*>,
    typeName: String,
): CborMap<CborItem<*>, CborItem<*>> =
    item as? CborMap<CborItem<*>, CborItem<*>>
        ?: throw IllegalArgumentException("$typeName must be encoded as a CBOR map")

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

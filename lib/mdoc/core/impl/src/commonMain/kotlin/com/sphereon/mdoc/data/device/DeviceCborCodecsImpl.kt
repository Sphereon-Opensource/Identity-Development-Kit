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
import com.sphereon.cbor.CborBool
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborInt
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborNInt
import com.sphereon.cbor.CborParser
import com.sphereon.cbor.CborParserImpl
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.toCborBool
import com.sphereon.cbor.toCborItem
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.cose.CoseSign1CborCodec
import com.sphereon.crypto.core.cose.CoseSign1CborCodecImpl
import com.sphereon.mdoc.DecodedMdoc
import com.sphereon.mdoc.data.mso.DigestID
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.json.oid4vpJsonSerializer
import com.sphereon.mdoc.transfer.reader.ReaderAuthenticationBytes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DeviceRequestCborCodec>())
class DeviceRequestCborCodecImpl(
    private val cborParser: CborParser,
    private val coseKeyCodec: CoseKeyCborCodec,
    private val coseSign1Codec: CoseSign1CborCodec,
) : DeviceRequestCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        coseKeyCodec = CoseKeyCborCodecImpl(cborParser),
        coseSign1Codec = CoseSign1CborCodecImpl(cborParser),
    )

    override fun encode(value: DeviceRequest): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "DeviceRequest",
            operation = { encodeDeviceRequest(value, cborParser, coseKeyCodec, coseSign1Codec) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<DeviceRequest>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val map = requireStringLabelMap(item, "DeviceRequest")
            decodeValue(
                typeName = "DeviceRequest",
                bytes = bytes,
                operation = { decodeDeviceRequest(map, bytes, coseKeyCodec, coseSign1Codec) },
            )
        }
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DeviceResponseCborCodec>())
class DeviceResponseCborCodecImpl(
    private val cborParser: CborParser,
    private val documentCodec: DocumentCborCodec,
) : DeviceResponseCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        documentCodec = DocumentCborCodecImpl(cborParser),
    )

    override fun encode(value: DeviceResponse): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "DeviceResponse",
            operation = { encodeDeviceResponse(value, cborParser, documentCodec) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<DeviceResponse>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val map = requireStringLabelMap(item, "DeviceResponse")
            decodeValue(
                typeName = "DeviceResponse",
                bytes = bytes,
                operation = { decodeDeviceResponse(map, bytes, documentCodec) },
            )
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

private fun encodeDeviceRequest(
    value: DeviceRequest,
    cborParser: CborParser,
    coseKeyCodec: CoseKeyCborCodec,
    coseSign1Codec: CoseSign1CborCodec,
): ByteArray {
    value.original?.let { return it }

    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            DeviceRequest.VERSION to CborString(value.version.toString()),
        )
    value.docRequests?.let { docRequests ->
        entries[DeviceRequest.DOC_REQUESTS] =
            CborArray(
                docRequests.map { encodeDocRequest(it, cborParser, coseSign1Codec) }.toMutableList(),
            )
    }
    value.macKeys?.let { macKeys ->
        entries[DeviceRequest.MAC_KEYS] =
            CborArray(
                macKeys.map { encodeCborItem(coseKeyCodec.encode(it).getOrThrow(), cborParser, "CoseKey") }.toMutableList(),
            )
    }
    value.oid4vpRequest?.let {
        entries[DeviceRequest.OID4VP_REQUEST] =
            CborByteString(
                oid4vpJsonSerializer.encodeToString(it).decodeFrom(Encoding.UTF8),
            )
    }
    return com.sphereon.cbor.Cbor
        .encode(CborMap(entries))
}

private fun encodeDeviceResponse(
    value: DeviceResponse,
    cborParser: CborParser,
    documentCodec: DocumentCborCodec,
): ByteArray {
    value.original?.let { return it }

    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            DeviceResponse.VERSION to CborString(value.version.toString()),
            DeviceResponse.STATUS to CborUInt(value.status.value.toLong()),
        )
    value.documents?.let { documents ->
        entries[DeviceResponse.DOCUMENTS] =
            CborArray(
                documents.map { encodeCborItem(documentCodec.encode(it).getOrThrow(), cborParser, "Document") }.toMutableList(),
            )
    }
    val documentErrors = value.documentErrors
    if (!documentErrors.isNullOrEmpty()) {
        entries[DeviceResponse.DOCUMENT_ERRORS] = encodeDocumentErrors(documentErrors)
    }
    return com.sphereon.cbor.Cbor
        .encode(CborMap(entries))
}

private fun encodeDocRequest(
    value: DocRequest,
    cborParser: CborParser,
    coseSign1Codec: CoseSign1CborCodec,
): CborMap<StringLabel, CborItem<*>> {
    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            DocRequest.ITEMS_REQUEST to CborEncodedItem<CborMap<StringLabel, CborItem<*>>>(encodeDeviceItemsRequest(value.itemsRequest)),
        )
    value.readerAuth?.let {
        entries[DocRequest.READER_AUTH] = encodeCborItem(coseSign1Codec.encode(it).getOrThrow(), cborParser, "ReaderAuth")
    }
    return CborMap(entries)
}

private fun encodeDeviceItemsRequest(value: DeviceItemsRequest): ByteArray {
    value.original?.let { return it }

    val nameSpaces =
        CborMap(
            value.nameSpaces.entries
                .associate { (nameSpace, identifiers) ->
                    StringLabel(nameSpace.toString()) to
                        CborMap(
                            identifiers.entries
                                .associate { (identifier, intentToRetain) ->
                                    StringLabel(identifier.toString()) to intentToRetain.toString().toBooleanStrict().toCborBool()
                                }.toMutableMap(),
                        )
                }.toMutableMap(),
        )

    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            DeviceItemsRequest.DOC_TYPE to CborString(value.docType.toString()),
            DeviceItemsRequest.NAME_SPACES to nameSpaces,
        )
    value.requestInfo?.let { entries[DeviceItemsRequest.REQUEST_INFO] = it.toCborItem() }
    return com.sphereon.cbor.Cbor
        .encode(CborMap(entries))
}

private fun encodeDocumentErrors(value: Array<Map<DocType, DocumentError>>): CborArray<CborMap<CborString, CborItem<*>>> =
    CborArray(
        value
            .map { documentErrors ->
                CborMap(
                    documentErrors.entries
                        .associate { (docType, error) ->
                            CborString(docType.toString()) to encodeDocumentError(error)
                        }.toMutableMap(),
                )
            }.toMutableList(),
    )

private fun encodeDocumentError(value: DocumentError): CborItem<*> =
    if (value.errorCode < 0) {
        CborNInt(-value.errorCode.toLong())
    } else {
        CborUInt(value.errorCode.toLong())
    }

private fun decodeDeviceRequest(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray,
    coseKeyCodec: CoseKeyCborCodec,
    coseSign1Codec: CoseSign1CborCodec,
): DeviceRequest {
    val macKeys =
        DeviceRequest.MAC_KEYS
            .optional<CborArray<CborMap<NumberLabel, CborItem<*>>>>(structure)
            ?.value
            ?.map { decodeCoseKey(it, coseKeyCodec) }
            ?.toTypedArray()

    val docRequests =
        DeviceRequest.DOC_REQUESTS
            .optional<CborArray<CborMap<CborItem<*>, CborItem<*>>>>(structure)
            ?.value
            ?.map { decodeDocRequest(normalizeStringLabelMap(it, "DocRequest"), coseSign1Codec) }
            ?.toTypedArray()

    return DeviceRequest(
        version = DeviceRequestVersion(DeviceRequest.VERSION.required<CborString>(structure).value),
        docRequests = docRequests,
        macKeys = macKeys,
        oid4vpRequest =
            DeviceRequest.OID4VP_REQUEST
                .optional<CborByteString>(structure)
                ?.value
                ?.let { oid4vpJsonSerializer.decodeFromString(it.encodeTo(Encoding.UTF8)) },
        original = original,
    )
}

private fun decodeDeviceResponse(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray,
    documentCodec: DocumentCborCodec,
): DeviceResponse =
    DeviceResponse(
        version = DeviceResponseVersion(DeviceResponse.VERSION.required<CborString>(structure).value),
        documents =
            DeviceResponse.DOCUMENTS
                .optional<CborArray<CborMap<CborItem<*>, CborItem<*>>>>(structure)
                ?.value
                ?.map { documentCodec.decode(it).getOrThrow() }
                ?.toTypedArray(),
        documentErrors =
            DeviceResponse.DOCUMENT_ERRORS
                .optional<CborArray<CborMap<CborItem<*>, CborItem<*>>>>(structure)
                ?.value
                ?.map { it.toDocTypeErrorMap() }
                ?.toTypedArray(),
        status =
            DeviceResponseStatus(
                DeviceResponse.STATUS
                    .required<CborUInt>(structure)
                    .value
                    .toUInt(),
            ),
        original = original,
    )

private fun decodeDocRequest(
    structure: CborMap<StringLabel, CborItem<*>>,
    coseSign1Codec: CoseSign1CborCodec,
): DocRequest {
    val taggedItemsRequest: com.sphereon.cbor.CborEncodedItem<CborMap<StringLabel, CborItem<*>>> =
        DocRequest.ITEMS_REQUEST.required(structure)
    val itemsRequestMap: CborMap<StringLabel, CborItem<*>> =
        normalizeStringLabelMap(
            com.sphereon.cbor.Cbor
                .decode(taggedItemsRequest.value.taggedItem.value),
            "DeviceItemsRequest",
        )
    val readerAuth =
        DocRequest.READER_AUTH
            .optional<CborArray<CborItem<*>>>(structure)
            ?.let { decodeReaderAuth(it, coseSign1Codec) }

    return DocRequest(
        itemsRequest =
            decodeDeviceItemsRequest(
                structure = itemsRequestMap,
                original = taggedItemsRequest.value.taggedItem.value,
            ),
        readerAuth = readerAuth,
        original = null,
    )
}

internal fun decodeDeviceItemsRequest(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray? = null,
): DeviceItemsRequest {
    val rawNameSpaces: CborMap<CborItem<*>, CborItem<*>> =
        DeviceItemsRequest.NAME_SPACES.required(structure)
    val nameSpaces =
        rawNameSpaces.value
            .map { (nameSpaceLabel, identifiers) ->
                val normalizedNameSpace =
                    when (nameSpaceLabel) {
                        is StringLabel -> nameSpaceLabel
                        is CborString -> StringLabel(nameSpaceLabel.value)
                        else -> throw IllegalArgumentException("Expected namespace string label but got ${nameSpaceLabel::class.simpleName}")
                    }
                val identifierMap =
                    identifiers as? CborMap<*, *>
                        ?: throw IllegalArgumentException("Expected namespace identifiers map but got ${identifiers::class.simpleName}")
                NameSpace(normalizedNameSpace.value) to
                    identifierMap.value
                        .map { (identifierLabel, intentToRetain) ->
                            val normalizedIdentifier =
                                when (identifierLabel) {
                                    is StringLabel -> identifierLabel
                                    is CborString -> StringLabel(identifierLabel.value)
                                    else -> throw IllegalArgumentException("Expected identifier string label but got ${identifierLabel::class.simpleName}")
                                }
                            val retain =
                                intentToRetain as? CborBool
                                    ?: throw IllegalArgumentException("Expected boolean intentToRetain but got ${intentToRetain?.let { it::class.simpleName }}")
                            DataElementIdentifier(normalizedIdentifier.value) to IntentToRetain(retain.value)
                        }.toMap()
            }.toMap()

    val requestInfo =
        DeviceItemsRequest.REQUEST_INFO
            .optional<CborMap<CborItem<*>, CborItem<*>>>(structure)
            ?.value
            ?.map { (key, value) ->
                val normalizedKey =
                    when (key) {
                        is StringLabel -> key.value
                        is CborString -> key.value
                        else -> throw IllegalArgumentException("Expected requestInfo string label but got ${key::class.simpleName}")
                    }
                normalizedKey to value.value!!
            }?.toMap()

    return DeviceItemsRequest(
        docType = DocType(DeviceItemsRequest.DOC_TYPE.required<CborString>(structure).value),
        nameSpaces = nameSpaces,
        requestInfo = requestInfo,
        original = original,
    )
}

private fun decodeCoseKey(
    structure: CborMap<NumberLabel, CborItem<*>>,
    coseKeyCodec: CoseKeyCborCodec,
): CoseKey =
    coseKeyCodec
        .decode(
            com.sphereon.cbor.Cbor
                .encode(structure),
        ).getOrThrow()
        .value

@Suppress("UNCHECKED_CAST")
private fun decodeReaderAuth(
    structure: CborArray<CborItem<*>>,
    coseSign1Codec: CoseSign1CborCodec,
): COSE_Sign1<ReaderAuthenticationBytes> =
    coseSign1Codec
        .decode(
            com.sphereon.cbor.Cbor
                .encode(structure),
        ).getOrThrow()
        .value as COSE_Sign1<ReaderAuthenticationBytes>

private fun CborMap<CborItem<*>, CborItem<*>>.toDocTypeErrorMap(): Map<DocType, DocumentError> =
    value
        .map { (key, value) ->
            val docType =
                when (key) {
                    is CborString -> DocType(key.value)
                    is StringLabel -> DocType(key.value)
                    else -> throw IllegalArgumentException("DeviceResponse documentErrors keys must be CBOR strings")
                }
            val documentError =
                when (value) {
                    is CborInt -> DocumentError(value.value.toInt())
                    is CborUInt -> DocumentError(value.value.toInt())
                    is CborNInt -> DocumentError(-value.value.toInt())
                    else -> throw IllegalArgumentException("DeviceResponse documentErrors values must be CBOR ints")
                }

            docType to documentError
        }.toMap()

private fun normalizeStringLabelMap(
    item: CborMap<CborItem<*>, CborItem<*>>,
    typeName: String,
): CborMap<StringLabel, CborItem<*>> {
    val normalizedEntries =
        item.value.entries
            .associate { (key, value) ->
                StringLabel.fromCborItem(key) to value
            }.toMutableMap()

    return CborMap(normalizedEntries, item.indefiniteLength)
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
    typeName: String,
): CborItem<*> =
    try {
        cborParser.parse(bytes).getOrThrow()
    } catch (expected: Throwable) {
        throw IllegalArgumentException("Failed to encode nested $typeName: ${expected.message}", expected)
    }

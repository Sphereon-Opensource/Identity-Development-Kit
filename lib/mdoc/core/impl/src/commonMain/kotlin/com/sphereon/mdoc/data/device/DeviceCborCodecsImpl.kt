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
import com.sphereon.cbor.CborFullDate
import com.sphereon.cbor.CborInt
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborNInt
import com.sphereon.cbor.CborParser
import com.sphereon.cbor.CborParserImpl
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborTagged
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
                operation = { decodeDeviceRequest(map, bytes, cborParser, coseKeyCodec, coseSign1Codec) },
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
            operation = { decodeDeviceResponse(map, bytes, documentCodec, cborParser) },
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
            DeviceRequest.VERSION to
                CborString(
                    if (value.hasSecondEditionFields()) {
                        "1.1"
                    } else {
                        value.version.toString()
                    },
                ),
        )
    value.docRequests?.let { docRequests ->
        entries[DeviceRequest.DOC_REQUESTS] =
            CborArray(
                docRequests.map { encodeDocRequest(it, cborParser, coseSign1Codec, coseKeyCodec) }.toMutableList(),
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
    value.deviceRequestInfo?.let {
        entries[DeviceRequest.DEVICE_REQUEST_INFO] = CborEncodedItem<Any>(com.sphereon.cbor.Cbor.encode(encodeDeviceRequestInfo(it)))
    }
    value.readerAuthAll?.let { readerAuthAll ->
        entries[DeviceRequest.READER_AUTH_ALL] =
            CborArray(
                readerAuthAll
                    .map { encodeCborItem(coseSign1Codec.encode(it).getOrThrow(), cborParser, "ReaderAuthAll") }
                    .toMutableList(),
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
    value.documents?.takeIf { it.isNotEmpty() }?.let { documents ->
        entries[DeviceResponse.DOCUMENTS] =
            CborArray(
                documents.map { encodeCborItem(documentCodec.encode(it).getOrThrow(), cborParser, "Document") }.toMutableList(),
            )
    }
    val documentErrors = value.documentErrors
    if (!documentErrors.isNullOrEmpty()) {
        entries[DeviceResponse.DOCUMENT_ERRORS] = encodeDocumentErrors(documentErrors)
    }
    require(
        value.status.value == 0u ||
            (value.documents.isNullOrEmpty() && value.zkDocuments.isNullOrEmpty() && value.encryptedDocuments.isNullOrEmpty()),
    ) { "Non-zero DeviceResponse status cannot carry response documents" }
    value.zkDocuments?.takeIf { it.isNotEmpty() }?.let { entries[DeviceResponse.ZK_DOCUMENTS] = CborArray(it.map { encodeZkDocument(it, cborParser) }.toMutableList()) }
    value.encryptedDocuments?.takeIf { it.isNotEmpty() }?.let { entries[DeviceResponse.ENCRYPTED_DOCUMENTS] = CborArray(it.map(::encodeEncryptedDocuments).toMutableList()) }
    return com.sphereon.cbor.Cbor
        .encode(CborMap(entries))
}

private fun encodeDocRequest(
    value: DocRequest,
    cborParser: CborParser,
    coseSign1Codec: CoseSign1CborCodec,
    coseKeyCodec: CoseKeyCborCodec,
): CborMap<StringLabel, CborItem<*>> {
    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            DocRequest.ITEMS_REQUEST to CborEncodedItem<CborMap<StringLabel, CborItem<*>>>(encodeDeviceItemsRequest(value.itemsRequest, coseKeyCodec)),
        )
    value.readerAuth?.let {
        entries[DocRequest.READER_AUTH] = encodeCborItem(coseSign1Codec.encode(it).getOrThrow(), cborParser, "ReaderAuth")
    }
    return CborMap(entries)
}

private fun encodeDeviceItemsRequest(value: DeviceItemsRequest, coseKeyCodec: CoseKeyCborCodec): ByteArray {
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
    value.docRequestInfo?.let { entries[DeviceItemsRequest.REQUEST_INFO] = encodeDocRequestInfo(it, coseKeyCodec) }
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
    cborParser: CborParser,
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
            ?.map { decodeDocRequest(normalizeStringLabelMap(it, "DocRequest"), coseSign1Codec, coseKeyCodec) }
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
        deviceRequestInfo = structure.value[DeviceRequest.DEVICE_REQUEST_INFO]?.let { encoded ->
            val bytes = (encoded as? CborEncodedItem<*>)?.value?.taggedItem?.value
                ?: error("DeviceRequestInfo must be tag 24")
            decodeDeviceRequestInfo(cborParser.parse(bytes).getOrThrow())
        },
        readerAuthAll = structure.value[DeviceRequest.READER_AUTH_ALL]?.let { value ->
            val array = value as? CborArray<*> ?: error("readerAuthAll must be an array")
            array.value.map { item -> decodeReaderAuthAll(item as CborItem<*>, coseSign1Codec) }.toTypedArray()
        },
    ).also { request ->
        // macKeys appeared in deployed 1.0 REST requests before the second-edition
        // version marker was adopted. Accept that legacy wire form, while requiring
        // 1.1 for the other second-edition structures.
        val requiresSecondEditionVersion =
            request.deviceRequestInfo != null ||
                request.readerAuthAll != null
        require(!requiresSecondEditionVersion || request.version.toString() == "1.1") {
            "DeviceRequest second-edition fields require version 1.1"
        }
    }
}

private fun DeviceRequest.hasSecondEditionFields(): Boolean =
    deviceRequestInfo != null ||
    readerAuthAll != null ||
        // macKeys is the ISO 18013-7 second-edition reader capability field.
        // Keep decoding legacy 1.0 requests containing it for compatibility, but
        // never emit a newly encoded request with a misleading 1.0 version.
        macKeys != null

private fun decodeDeviceResponse(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray,
    documentCodec: DocumentCborCodec,
    cborParser: CborParser,
): DeviceResponse {
    val status = DeviceResponseStatus(toUIntExact(DeviceResponse.STATUS.required<CborUInt>(structure).value, "DeviceResponse.status"))
    val documents = DeviceResponse.DOCUMENTS.optional<CborArray<CborMap<CborItem<*>, CborItem<*>>>>(structure)?.value?.map { documentCodec.decode(it).getOrThrow() }?.toTypedArray()
    val zkDocumentOriginals = original.rawMapArrayValues("zkDocuments", cborParser)
    val zkDocuments = structure.value[DeviceResponse.ZK_DOCUMENTS]?.let { item ->
        (item as? CborArray<*>)?.value?.mapIndexed { index, value ->
            decodeZkDocument(value as CborItem<*>, cborParser, zkDocumentOriginals?.getOrNull(index))
        }?.toTypedArray() ?: error("zkDocuments must be an array")
    }
    val encryptedDocumentOriginals = original.rawMapArrayValues("encryptedDocuments", cborParser)
    val encryptedDocuments = structure.value[DeviceResponse.ENCRYPTED_DOCUMENTS]?.let { item ->
        (item as? CborArray<*>)?.value?.mapIndexed { index, value ->
            decodeEncryptedDocuments(value as CborItem<*>, encryptedDocumentOriginals?.getOrNull(index))
        }?.toTypedArray() ?: error("encryptedDocuments must be an array")
    }
    require(status.value == 0u || (documents.isNullOrEmpty() && zkDocuments.isNullOrEmpty() && encryptedDocuments.isNullOrEmpty())) { "Non-zero DeviceResponse status cannot carry response documents" }
    return DeviceResponse(
        version = DeviceResponseVersion(DeviceResponse.VERSION.required<CborString>(structure).value),
        documents = documents,
        documentErrors =
            DeviceResponse.DOCUMENT_ERRORS
                .optional<CborArray<CborMap<CborItem<*>, CborItem<*>>>>(structure)
                ?.value
                ?.map { it.toDocTypeErrorMap() }
                ?.toTypedArray(),
        status = status,
        original = original,
        zkDocuments = zkDocuments,
        encryptedDocuments = encryptedDocuments,
    )
}

internal fun encodeZkDocument(value: ZkDocument, parser: CborParser): CborMap<StringLabel, CborItem<*>> {
    value.original?.let { return com.sphereon.cbor.Cbor.decode(it) }
    val data = encodeZkDocumentData(value.documentData)
    return CborMap(
        mutableMapOf(
            StringLabel("documentData") to CborEncodedItem(com.sphereon.cbor.Cbor.encode(data), value.documentData),
            StringLabel("proof") to CborByteString(value.proof),
        ),
    )
}

private fun encodeZkDocumentData(value: ZkDocumentData): CborMap<StringLabel, CborItem<*>> {
    val entries = mutableMapOf<StringLabel, CborItem<*>>()
    entries[StringLabel("docType")] = CborString(value.docType.toString())
    entries[StringLabel("zkSystemId")] = CborString(value.zkSystemId)
    entries[StringLabel("timestamp")] = CborFullDate(value.timestamp)
    value.issuerSigned?.let { entries[StringLabel("issuerSigned")] = encodeZkNameSpaces(it) }
    value.deviceSigned?.let { entries[StringLabel("deviceSigned")] = encodeZkNameSpaces(it) }
    value.msoX5chain?.let { entries[StringLabel("msoX5chain")] = CborArray(it.map(::CborByteString).toMutableList()) }
    value.unknown?.let { entries.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
    return CborMap(entries)
}

internal fun decodeZkDocument(item: CborItem<*>, parser: CborParser, original: ByteArray? = null): ZkDocument {
    val structure = requireStringLabelMap(item, "ZkDocument")
    val encodedData = structure.value[StringLabel("documentData")] as? CborEncodedItem<*> ?: error("ZkDocument.documentData must be tag 24")
    val dataItem = com.sphereon.cbor.Cbor.tryDecode(encodedData.value.taggedItem.value).getOrThrow()
    val data = decodeZkDocumentData(requireStringLabelMap(dataItem, "ZkDocumentData"))
    val proof = (structure.value[StringLabel("proof")] as? CborByteString)?.value ?: error("ZkDocument.proof must be a byte string")
    // Preserve the decoded envelope so a caller can forward an opaque proof without
    // accidentally normalising fields that this phase does not interpret.
    return ZkDocument(data, proof, original = original ?: com.sphereon.cbor.Cbor.encode(item))
}

private fun decodeZkDocumentData(structure: CborMap<StringLabel, CborItem<*>>): ZkDocumentData {
    val chain = structure.value[StringLabel("msoX5chain")]?.let { item ->
        (item as? CborArray<*>)?.value?.map { (it as? CborByteString)?.value ?: error("msoX5chain entries must be byte strings") }
            ?: error("msoX5chain must be an array")
    }
    val timestamp =
        structure.value[StringLabel("timestamp")].let { item ->
            when (item) {
                // Values constructed in-process retain the specialised wrapper. Values
                // decoded from CBOR are represented by the generic tagged-item class.
                is CborFullDate -> item.value
                is CborTagged<*> ->
                    require(item.tagNumber == CborTagged.FULL_DATE_STRING) {
                        "ZkDocumentData.timestamp must use full-date tag ${CborTagged.FULL_DATE_STRING}"
                    }.let {
                        (item.taggedItem as? CborString)?.value
                            ?: error("ZkDocumentData.timestamp full-date tag must contain a text string")
                    }
                else -> error("ZkDocumentData.timestamp must be a full-date")
            }
        }
    return ZkDocumentData(
        docType = DocType((structure.value[StringLabel("docType")] as? CborString)?.value ?: error("ZkDocumentData.docType is required")),
        zkSystemId = (structure.value[StringLabel("zkSystemId")] as? CborString)?.value ?: error("ZkDocumentData.zkSystemId is required"),
        timestamp = timestamp,
        issuerSigned = structure.value[StringLabel("issuerSigned")]?.let(::decodeZkNameSpaces),
        deviceSigned = structure.value[StringLabel("deviceSigned")]?.let(::decodeZkNameSpaces),
        msoX5chain = chain,
        unknown = unknownFields(structure, setOf("docType", "zkSystemId", "timestamp", "issuerSigned", "deviceSigned", "msoX5chain")),
    )
}

private fun encodeZkNameSpaces(value: Map<NameSpace, List<ZkSignedItem>>): CborMap<StringLabel, CborItem<*>> =
    CborMap(
        value.mapKeys { (nameSpace, _) -> StringLabel(nameSpace.toString()) }
            .mapValues { (_, items) -> CborArray(items.map(::encodeZkSignedItem).toMutableList()) }
            .toMutableMap(),
    )

private fun encodeZkSignedItem(value: ZkSignedItem): CborMap<StringLabel, CborItem<*>> {
    val fields =
        mutableMapOf<StringLabel, CborItem<*>>(
            StringLabel("elementIdentifier") to CborString(value.elementIdentifier.toString()),
            StringLabel("elementValue") to value.elementValue,
        )
    value.unknown?.let { fields.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
    return CborMap(fields)
}

private fun decodeZkNameSpaces(item: CborItem<*>): Map<NameSpace, List<ZkSignedItem>> {
    val map = requireStringLabelMap(item, "ZkNameSpaces")
    return map.value.map { (nameSpace, items) ->
        val array = items as? CborArray<*> ?: error("ZkNameSpaces values must be arrays")
        NameSpace(nameSpace.value) to array.value.map { itemValue ->
            val fields = requireStringLabelMap(itemValue, "ZkSignedItem")
            ZkSignedItem(
                elementIdentifier = DataElementIdentifier((fields.value[StringLabel("elementIdentifier")] as? CborString)?.value ?: error("elementIdentifier is required")),
                elementValue = fields.value[StringLabel("elementValue")] ?: error("elementValue is required"),
                unknown = unknownFields(fields, setOf("elementIdentifier", "elementValue")),
            )
        }
    }.toMap()
}

private fun encodeEncryptedDocuments(value: EncryptedDocuments): CborMap<StringLabel, CborItem<*>> {
    value.original?.let { return com.sphereon.cbor.Cbor.decode(it) }
    return CborMap(
        mutableMapOf(
            StringLabel("enc") to CborByteString(value.enc),
            StringLabel("cipherText") to CborByteString(value.cipherText),
            StringLabel("docRequestID") to CborUInt(value.docRequestID.toLong()),
        ),
    )
}

private fun decodeEncryptedDocuments(item: CborItem<*>, original: ByteArray? = null): EncryptedDocuments {
    val structure = requireStringLabelMap(item, "EncryptedDocuments")
    val id = (structure.value[StringLabel("docRequestID")] as? CborUInt)?.value ?: error("docRequestID must be unsigned")
    return EncryptedDocuments(
        enc = (structure.value[StringLabel("enc")] as? CborByteString)?.value ?: error("enc must be a byte string"),
        cipherText = (structure.value[StringLabel("cipherText")] as? CborByteString)?.value ?: error("cipherText must be a byte string"),
        docRequestID = toUIntExact(id, "EncryptedDocuments.docRequestID"),
        original = original ?: com.sphereon.cbor.Cbor.encode(item),
    )
}

/**
 * Returns the raw values of a string-keyed map entry whose value is an array.
 *
 * The normal CBOR model intentionally does not retain source offsets.  Response envelopes are
 * opaque to this module, however, so forwarding must not canonicalise an unknown envelope.  Walk
 * the already validated top-level map with the offset-aware parser to retain exact bytes for each
 * nested item.  If an unusual encoding cannot be walked, callers still have the canonical model
 * fallback above and the top-level response retains its complete original bytes.
 */
private fun ByteArray.rawMapArrayValues(
    name: String,
    parser: CborParser,
): List<ByteArray>? {
    val mapHeader = cborContainerHeader(this, 0, expectedMajorType = 5) ?: return null
    var offset = mapHeader.contentOffset
    var remaining = mapHeader.itemCount
    while (remaining == null || remaining > 0) {
        if (remaining == null && offset < size && this[offset].toInt() and 0xff == 0xff) break
        val key = parser.parseWithOffset(this, offset).getOrNull() ?: return null
        offset = key.offset
        val valueStart = offset
        val value = parser.parseWithOffset(this, offset).getOrNull() ?: return null
        offset = value.offset
        val keyName = (key.item as? CborString)?.value
        if (keyName == name) {
            return rawArrayItems(this, valueStart, parser)
        }
        if (remaining != null) remaining--
    }
    return null
}

private fun rawArrayItems(
    bytes: ByteArray,
    start: Int,
    parser: CborParser,
): List<ByteArray>? {
    val header = cborContainerHeader(bytes, start, expectedMajorType = 4) ?: return null
    var offset = header.contentOffset
    var remaining = header.itemCount
    val result = mutableListOf<ByteArray>()
    while (remaining == null || remaining > 0) {
        if (remaining == null && offset < bytes.size && bytes[offset].toInt() and 0xff == 0xff) break
        val item = parser.parseWithOffset(bytes, offset).getOrNull() ?: return null
        result += bytes.copyOfRange(offset, item.offset)
        offset = item.offset
        if (remaining != null) remaining--
    }
    return result
}

private data class CborContainerHeader(
    val contentOffset: Int,
    val itemCount: Int?,
)

private fun cborContainerHeader(
    bytes: ByteArray,
    start: Int,
    expectedMajorType: Int,
): CborContainerHeader? {
    if (start !in bytes.indices) return null
    val first = bytes[start].toInt() and 0xff
    if (first ushr 5 != expectedMajorType) return null
    val additionalInformation = first and 0x1f
    if (additionalInformation == 31) return CborContainerHeader(start + 1, null)
    val lengthBytes = when (additionalInformation) {
        in 0..23 -> 0
        24 -> 1
        25 -> 2
        26 -> 4
        27 -> 8
        else -> return null
    }
    val contentOffset = start + 1 + lengthBytes
    if (contentOffset > bytes.size) return null
    val count = when (lengthBytes) {
        0 -> additionalInformation
        1 -> bytes[start + 1].toInt() and 0xff
        2 -> ((bytes[start + 1].toInt() and 0xff) shl 8) or (bytes[start + 2].toInt() and 0xff)
        4 -> {
            var value = 0L
            for (index in 1..4) value = (value shl 8) or (bytes[start + index].toLong() and 0xff)
            if (value > Int.MAX_VALUE) return null
            value.toInt()
        }
        8 -> {
            var value = 0UL
            for (index in 1..8) value = (value shl 8) or (bytes[start + index].toULong() and 0xffUL)
            if (value > Int.MAX_VALUE.toULong()) return null
            value.toInt()
        }
        else -> return null
    }
    return CborContainerHeader(contentOffset, count)
}

private fun decodeDocRequest(
    structure: CborMap<StringLabel, CborItem<*>>,
    coseSign1Codec: CoseSign1CborCodec,
    coseKeyCodec: CoseKeyCborCodec,
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
                coseKeyCodec = coseKeyCodec,
            ),
        readerAuth = readerAuth,
        original = null,
    )
}

internal fun decodeDeviceItemsRequest(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray? = null,
    coseKeyCodec: CoseKeyCborCodec = CoseKeyCborCodecImpl(),
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

    val rawRequestInfo = structure.itemOrNull("requestInfo")
    val isSecondEditionRequestInfo = rawRequestInfo?.isSecondEditionDocRequestInfo() == true
    val requestInfo =
        rawRequestInfo
            ?.takeIf { !isSecondEditionRequestInfo && it is CborMap<*, *> }
            ?.let { it as CborMap<CborItem<*>, CborItem<*>> }
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
        docRequestInfo = rawRequestInfo?.takeIf { isSecondEditionRequestInfo }?.let { decodeDocRequestInfo(it, coseKeyCodec) },
    )
}

private fun CborItem<*>.isSecondEditionDocRequestInfo(): Boolean {
    val map = this as? CborMap<*, *> ?: return false
    val secondEditionKeys =
        setOf(
            "alternativeDataElements",
            "issuerIdentifiers",
            "uniqueDocSetRequired",
            "maximumResponseSize",
            "zkRequest",
            "docResponseEncryption",
        )
    return map.value.keys.any { key ->
        when (key) {
            is StringLabel -> key.value in secondEditionKeys
            is CborString -> key.value in secondEditionKeys
            else -> false
        }
    }
}

private fun encodeElementReference(value: ElementReference): CborArray<CborString> = CborArray(mutableListOf(CborString(value.first.toString()), CborString(value.second.toString())))

private fun decodeElementReference(item: CborItem<*>): ElementReference {
    val a = item as? CborArray<*> ?: throw IllegalArgumentException("Element reference must be an array")
    require(a.value.size == 2) { "Element reference must have two values" }
    return NameSpace((a.value[0] as? CborString)?.value ?: error("Element reference namespace must be text")) to
        DataElementIdentifier((a.value[1] as? CborString)?.value ?: error("Element reference identifier must be text"))
}

private fun encodeDocRequestInfo(value: DocRequestInfo, coseKeyCodec: CoseKeyCborCodec): CborMap<StringLabel, CborItem<*>> {
    val m = mutableMapOf<StringLabel, CborItem<*>>()
    value.alternativeDataElements?.let { sets ->
        m[StringLabel("alternativeDataElements")] =
            CborArray(
                sets.map { set ->
                    CborMap(
                        mutableMapOf(
                            StringLabel("requestedElement") to encodeElementReference(set.requestedElement),
                            StringLabel("alternativeElementSets") to
                                CborArray(
                                    set.alternativeElementSets
                                        .map { alternatives -> CborArray(alternatives.map(::encodeElementReference).toMutableList()) }
                                        .toMutableList(),
                                ),
                        ),
                    )
                }.toMutableList(),
            )
    }
    value.issuerIdentifiers?.let { m[StringLabel("issuerIdentifiers")] = CborArray(it.map(::CborByteString).toMutableList()) }
    value.uniqueDocSetRequired?.let { m[StringLabel("uniqueDocSetRequired")] = it.toCborBool() }
    value.maximumResponseSize?.let { m[StringLabel("maximumResponseSize")] = CborUInt(it.toLong()) }
    value.zkRequest?.let { m[StringLabel("zkRequest")] = encodeZkRequest(it) }
    value.docResponseEncryption?.let { m[StringLabel("docResponseEncryption")] = CborEncodedItem(com.sphereon.cbor.Cbor.encode(encodeEncryptionParameters(it, coseKeyCodec)), it) }
    value.unknown?.let { m.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
    return CborMap(m)
}

private fun decodeDocRequestInfo(item: CborItem<*>, coseKeyCodec: CoseKeyCborCodec): DocRequestInfo {
    val m = requireStringLabelMap(item, "DocRequestInfo")
    val alternatives = StringLabel("alternativeDataElements").optional<CborArray<*>>(m)?.value?.map { x ->
        val set = requireStringLabelMap(x, "AlternativeDataElementsSet")
        val alternativeSets =
            (set.value[StringLabel("alternativeElementSets")] as? CborArray<*>)?.value?.map { alternative ->
                (alternative as? CborArray<*>)?.value?.map { decodeElementReference(it as CborItem<*>) }
                    ?: error("AlternativeElementSet must be an array")
            } ?: error("alternativeElementSets is required")
        AlternativeDataElementsSet(
            requestedElement = decodeElementReference(set.value[StringLabel("requestedElement")] ?: error("requestedElement is required")),
            alternativeElementSets = alternativeSets,
        )
    }
    val ids = StringLabel("issuerIdentifiers").optional<CborArray<*>>(m)?.value?.map { (it as CborByteString).value }
    val max = StringLabel("maximumResponseSize").optional<CborUInt>(m)?.value?.let { toUIntExact(it, "DocRequestInfo.maximumResponseSize") }
    val encryption = StringLabel("docResponseEncryption").optional<CborEncodedItem<*>>(m)?.let { encoded ->
        val item = com.sphereon.cbor.Cbor.tryDecode(encoded.value.taggedItem.value).getOrThrow()
        decodeEncryptionParameters(item, coseKeyCodec)
    }
    return DocRequestInfo(
        alternativeDataElements = alternatives,
        issuerIdentifiers = ids,
        uniqueDocSetRequired = StringLabel("uniqueDocSetRequired").optional<CborBool>(m)?.value,
        maximumResponseSize = max,
        zkRequest = StringLabel("zkRequest").optional<CborItem<*>>(m)?.let(::decodeZkRequest),
        docResponseEncryption = encryption,
        unknown = unknownFields(m, setOf("alternativeDataElements", "issuerIdentifiers", "uniqueDocSetRequired", "maximumResponseSize", "zkRequest", "docResponseEncryption")),
    )
}

private fun encodeZkRequest(v: ZkRequest): CborMap<StringLabel, CborItem<*>> {
    val specs = v.systemSpecs.map { spec ->
        val fields = mutableMapOf<StringLabel, CborItem<*>>()
        fields[StringLabel("zkSystemId")] = CborString(spec.zkSystemId)
        fields[StringLabel("system")] = CborString(spec.system)
        fields[StringLabel("params")] = CborMap<StringLabel, CborItem<*>>(spec.params.mapKeys { StringLabel(it.key) }.toMutableMap())
        spec.unknown?.let { fields.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
        CborMap(fields)
    }
    val fields = mutableMapOf<StringLabel, CborItem<*>>(
            StringLabel("zkRequired") to v.zkRequired.toCborBool(),
            StringLabel("systemSpecs") to CborArray(specs.toMutableList()),
    )
    v.unknown?.let { fields.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
    return CborMap(fields)
}

private fun decodeZkRequest(item: CborItem<*>): ZkRequest {
    val m = requireStringLabelMap(item, "ZkRequest")
    val specs = StringLabel("systemSpecs").required<CborArray<*>>(m).value.map { s ->
        val sm = requireStringLabelMap(s, "ZkSystemSpec")
        val params = sm.value[StringLabel("params")]?.let(::decodeStringItemMap) ?: error("ZkSystemSpec.params is required")
        val unknown = unknownFields(sm, setOf("zkSystemId", "system", "params"))
        ZkSystemSpec(
            zkSystemId = (sm.value[StringLabel("zkSystemId")] as? CborString)?.value ?: error("zkSystemId is required"),
            system = (sm.value[StringLabel("system")] as? CborString)?.value ?: error("system is required"),
            params = params,
            unknown = unknown,
        )
    }
    val required = StringLabel("zkRequired").required<CborBool>(m).value
    return ZkRequest(systemSpecs = specs, zkRequired = required, unknown = unknownFields(m, setOf("systemSpecs", "zkRequired")))
}

internal fun encodeEncryptionParameters(v: EncryptionParameters, coseKeyCodec: CoseKeyCborCodec): CborMap<StringLabel, CborItem<*>> {
    val fields = mutableMapOf<StringLabel, CborItem<*>>()
    fields[StringLabel("recipientPublicKey")] = com.sphereon.cbor.Cbor.tryDecode(coseKeyCodec.encode(v.recipientPublicKey).getOrThrow()).getOrThrow()
    v.nonce?.let { fields[StringLabel("nonce")] = CborByteString(it) }
    v.recipientCertificate?.let { fields[StringLabel("recipientCertificate")] = CborArray(it.map(::CborByteString).toMutableList()) }
    v.unknown?.let { fields.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
    return CborMap(fields)
}

private fun decodeEncryptionParameters(item: CborItem<*>, coseKeyCodec: CoseKeyCborCodec): EncryptionParameters {
    val fields = requireStringLabelMap(item, "EncryptionParameters")
    val keyItem = fields.value[StringLabel("recipientPublicKey")] ?: error("recipientPublicKey is required")
    @Suppress("UNCHECKED_CAST")
    val keyMap = keyItem as? CborMap<NumberLabel, CborItem<*>> ?: error("recipientPublicKey must be a COSE_Key map")
    val key = coseKeyCodec.decode(com.sphereon.cbor.Cbor.encode(keyMap)).getOrThrow().value
    val certificate = fields.value[StringLabel("recipientCertificate")]?.let { itemValue ->
        (itemValue as? CborArray<*>)?.value?.map { (it as? CborByteString)?.value ?: error("recipientCertificate entries must be byte strings") }
            ?: error("recipientCertificate must be an array")
    }
    return EncryptionParameters(
        recipientPublicKey = key,
        nonce = (fields.value[StringLabel("nonce")] as? CborByteString)?.value,
        recipientCertificate = certificate,
        unknown = unknownFields(fields, setOf("recipientPublicKey", "nonce", "recipientCertificate")),
    )
}

private fun encodeDeviceRequestInfo(value: DeviceRequestInfo): CborMap<StringLabel, CborItem<*>> {
    val fields = mutableMapOf<StringLabel, CborItem<*>>()
    value.useCases?.let { useCases ->
        fields[StringLabel("useCases")] = CborArray(useCases.map(::encodeUseCase).toMutableList())
    }
    value.unknown?.let { fields.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
    return CborMap(fields)
}

private fun encodeUseCase(value: UseCase): CborMap<StringLabel, CborItem<*>> {
    val fields =
        mutableMapOf<StringLabel, CborItem<*>>(
            StringLabel("mandatory") to value.mandatory.toCborBool(),
            StringLabel("documentSets") to
                CborArray(
                    value.documentSets
                        .map { documentSet -> CborArray(documentSet.map { CborUInt(it.toLong()) }.toMutableList()) }
                        .toMutableList(),
                ),
        )
    value.purposeHints?.let { purposeHints ->
        fields[StringLabel("purposeHints")] =
            CborMap(
                purposeHints.mapKeys { (key, _) -> StringLabel(key) }
                    .mapValues { (_, code) -> CborInt(code.toLong()) }
                    .toMutableMap(),
            )
    }
    value.unknown?.let { fields.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
    return CborMap(fields)
}
private fun decodeDeviceRequestInfo(item: CborItem<*>): DeviceRequestInfo {
    val m = requireStringLabelMap(item, "DeviceRequestInfo")
    val useCases = StringLabel("useCases").optional<CborArray<*>>(m)?.value?.map { x ->
        val useCase = requireStringLabelMap(x, "UseCase")
        val hintMap = useCase.value[StringLabel("purposeHints")] as? CborMap<*, *>
        val hints = hintMap?.value?.entries?.associate { entry ->
            val key = entry.key as? CborItem<*> ?: error("purposeHints keys must be CBOR items")
            val value = entry.value as? CborItem<*> ?: error("purposeHints values must be CBOR items")
            val name = (key as? StringLabel)?.value ?: (key as? CborString)?.value ?: error("purposeHints keys must be text")
            val code = when (value) {
                is CborInt, is CborUInt, is CborNInt -> value.asInt
                else -> error("purposeHints values must be integers")
            }
            name to code
        }
        UseCase(
            mandatory = StringLabel("mandatory").required<CborBool>(useCase).value,
            documentSets =
                StringLabel("documentSets")
                    .required<CborArray<*>>(useCase)
                    .value
                    .map { documentSet ->
                        (documentSet as? CborArray<*>)?.value?.map {
                            (it as? CborUInt)?.value?.let { toUIntExact(it, "UseCase.documentSets") }
                                ?: error("documentSets values must be unsigned")
                        } ?: error("documentSets entries must be arrays")
                    },
            purposeHints = hints,
            unknown = unknownFields(useCase, setOf("mandatory", "documentSets", "purposeHints")),
        )
    }
    return DeviceRequestInfo(useCases = useCases, unknown = unknownFields(m, setOf("useCases")))
}

@Suppress("UNCHECKED_CAST")
private fun decodeReaderAuthAll(
    value: CborItem<*>,
    coseSign1Codec: CoseSign1CborCodec,
): ReaderAuthAll =
    coseSign1Codec
        .decode(com.sphereon.cbor.Cbor.encode(value))
        .getOrThrow()
        .value as ReaderAuthAll

private fun CborMap<StringLabel, CborItem<*>>.itemOrNull(name: String): CborItem<*>? = value[StringLabel(name)]

private fun decodeStringItemMap(item: CborItem<*>): Map<String, CborItem<*>> =
    requireStringLabelMap(item, "CBOR map")
        .value
        .map { (key, value) -> key.value to value }
        .toMap()

private fun unknownFields(
    structure: CborMap<StringLabel, CborItem<*>>,
    known: Set<String>,
): Map<String, CborItem<*>>? =
    structure.value
        .filterKeys { it.value !in known }
        .mapKeys { (key, _) -> key.value }
        .takeIf { it.isNotEmpty() }

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
                    is CborInt -> DocumentError(toIntExact(value.value, "DeviceResponse.documentErrors"))
                    is CborUInt -> DocumentError(toIntExact(value.value, "DeviceResponse.documentErrors"))
                    is CborNInt -> {
                        require(value.value <= Int.MAX_VALUE.toLong() + 1L) {
                            "DeviceResponse.documentErrors value is outside the Int range"
                        }
                        DocumentError((-value.value).toInt())
                    }
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

private fun toUIntExact(
    value: Long,
    field: String,
): UInt {
    require(value in 0..UInt.MAX_VALUE.toLong()) { "$field is outside the UInt range" }
    return value.toUInt()
}

private fun toIntExact(
    value: Long,
    field: String,
): Int {
    require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$field is outside the Int range" }
    return value.toInt()
}

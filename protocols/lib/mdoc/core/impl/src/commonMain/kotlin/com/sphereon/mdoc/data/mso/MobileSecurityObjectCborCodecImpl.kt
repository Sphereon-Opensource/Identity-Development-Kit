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

package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborParser
import com.sphereon.cbor.CborParserImpl
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborTDate
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.CborTagged
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.TDate
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.mdoc.DecodedMdoc
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.NameSpace
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<MobileSecurityObjectCborCodec>())
class MobileSecurityObjectCborCodecImpl(
    private val cborParser: CborParser = CborParserImpl(),
    private val coseKeyCodec: CoseKeyCborCodec = CoseKeyCborCodecImpl(cborParser),
) : MobileSecurityObjectCborCodec {
    override fun encode(value: MobileSecurityObject): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "MobileSecurityObject",
            operation = { encodeMobileSecurityObject(value, coseKeyCodec) },
        )

    override fun encodeItem(value: MobileSecurityObject): IdkResult<CborEncodedItem<MobileSecurityObject>, IdkError> =
        encode(value).map { encoded ->
            CborEncodedItem(encoded, value.copy(original = encoded))
        }

    override fun encodeTag24(value: MobileSecurityObject): IdkResult<ByteArray, IdkError> =
        encodeItem(value).flatMap { encodedItem ->
            encodeValue(
                typeName = "MobileSecurityObject tag 24 wrapper",
                operation = {
                    com.sphereon.cbor.Cbor
                        .encode(encodedItem)
                },
            )
        }

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<MobileSecurityObject>, IdkError> =
        parseMobileSecurityObject(cborParser, bytes).flatMap { (structure, originalBytes) ->
            decodeValue(
                typeName = "MobileSecurityObject",
                bytes = originalBytes,
                operation = { decodeMobileSecurityObject(structure, originalBytes, coseKeyCodec) },
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

private fun encodeMobileSecurityObject(
    value: MobileSecurityObject,
    coseKeyCodec: CoseKeyCborCodec,
): ByteArray {
    value.original?.let { return it }

    return com.sphereon.cbor.Cbor.encode(
        CborMap(
            mutableMapOf(
                MobileSecurityObject.VERSION to CborString(value.version.toString()),
                MobileSecurityObject.DIGEST_ALGORITHM to CborString(value.digestAlgorithm.toString()),
                MobileSecurityObject.VALUE_DIGESTS to encodeValueDigests(value.valueDigests),
                MobileSecurityObject.DEVICE_KEY_INFO to encodeDeviceKeyInfo(value.deviceKeyInfo, coseKeyCodec),
                MobileSecurityObject.DOC_TYPE to CborString(value.docType.toString()),
                MobileSecurityObject.VALIDITY_INFO to encodeValidityInfo(value.validityInfo),
            ).also { entries -> value.status?.let { entries[MobileSecurityObject.STATUS] = encodeStatus(it) } },
        ),
    )
}

private fun encodeStatus(value: Status): CborMap<StringLabel, CborItem<*>> {
    val fields = mutableMapOf<StringLabel, CborItem<*>>()
    value.identifierList?.let { fields[StringLabel("identifier_list")] = encodeIdentifierListInfo(it) }
    value.statusList?.let { fields[StringLabel("status_list")] = encodeStatusListInfo(it) }
    value.unknown?.let { fields.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
    return CborMap(fields)
}

private fun encodeIdentifierListInfo(value: IdentifierListInfo): CborMap<StringLabel, CborItem<*>> {
    val fields =
        mutableMapOf<StringLabel, CborItem<*>>(
            StringLabel("id") to CborByteString(value.id),
            StringLabel("uri") to CborString(value.uri),
        )
    value.certificate?.let { fields[StringLabel("certificate")] = CborByteString(it) }
    value.unknown?.let { fields.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
    return CborMap(fields)
}

private fun encodeStatusListInfo(value: StatusListInfo): CborMap<StringLabel, CborItem<*>> {
    val fields =
        mutableMapOf<StringLabel, CborItem<*>>(
            StringLabel("idx") to CborUInt(value.idx.toLong()),
            StringLabel("uri") to CborString(value.uri),
        )
    value.certificate?.let { fields[StringLabel("certificate")] = CborByteString(it) }
    value.aggregationUri?.let { fields[StringLabel("aggregation_uri")] = CborString(it) }
    value.unknown?.let { fields.putAll(it.mapKeys { (key, _) -> StringLabel(key) }) }
    return CborMap(fields)
}

private fun encodeValueDigests(valueDigests: Map<NameSpace, Map<DigestID, ByteArray>>): ValueDigestsAlias =
    CborMap(
        valueDigests.entries
            .associate { (nameSpace, digests) ->
                StringLabel(nameSpace.toString()) to
                    CborMap(
                        digests.entries
                            .associate { (digestId, digestBytes) ->
                                NumberLabel(digestId.toString().toLong()) to CborByteString(digestBytes)
                            }.toMutableMap(),
                    )
            }.toMutableMap(),
    )

private fun encodeDeviceKeyInfo(
    value: DeviceKeyInfo,
    coseKeyCodec: CoseKeyCborCodec,
): CborMap<StringLabel, CborItem<*>> {
    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            DeviceKeyInfo.DEVICE_KEY to
                com.sphereon.cbor.Cbor
                    .tryDecode(coseKeyCodec.encode(value.deviceKey).getOrThrow())
                    .getOrThrow(),
        )
    value.keyAuthorizations?.let { entries[DeviceKeyInfo.KEY_AUTHORIZATIONS] = encodeKeyAuthorizations(it) }
    value.keyInfo?.let { entries[DeviceKeyInfo.KEY_INFO] = it }
    return CborMap(entries)
}

private fun encodeKeyAuthorizations(value: KeyAuthorizations): CborMap<StringLabel, CborItem<*>> {
    val entries = mutableMapOf<StringLabel, CborItem<*>>()
    value.nameSpaces?.let { entries[KeyAuthorizations.NAME_SPACES] = it }
    value.dataElements?.let { entries[KeyAuthorizations.DATA_ELEMENTS] = it }
    return CborMap(entries)
}

private fun encodeValidityInfo(value: ValidityInfo): CborMap<StringLabel, CborItem<*>> {
    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            ValidityInfo.SIGNED to value.signed.toCborItem(),
            ValidityInfo.VALID_FROM to value.validFrom.toCborItem(),
            ValidityInfo.VALID_UNTIL to value.validUntil.toCborItem(),
        )
    value.expectedUpdate?.let { entries[ValidityInfo.EXPECTED_UPDATE] = it.toCborItem() }
    return CborMap(entries)
}

private fun decodeMobileSecurityObject(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray,
    coseKeyCodec: CoseKeyCborCodec,
): MobileSecurityObject {
    val valueDigests =
        MobileSecurityObject.VALUE_DIGESTS
            .required<CborMap<CborItem<*>, CborItem<*>>>(structure)
            .value
            .map { (nameSpaceKey, digestsItem) ->
                val nameSpace = NameSpace(StringLabel.fromCborItem(nameSpaceKey).value)
                nameSpace to
                    requireDigestMap(digestsItem, "MobileSecurityObject.valueDigests[$nameSpace]")
                        .value
                        .map { (digestId, digestValue) ->
                            DigestID(toUIntExact(digestId.value, "MobileSecurityObject.valueDigests[$nameSpace] digestID")) to digestValue.value.copyOf()
                        }.toMap()
            }.toMap()

    return MobileSecurityObject(
        version = MsoVersion(MobileSecurityObject.VERSION.required<CborString>(structure).value),
        digestAlgorithm = DigestAlgorithm(MobileSecurityObject.DIGEST_ALGORITHM.required<CborString>(structure).value),
        valueDigests = valueDigests,
        deviceKeyInfo = decodeDeviceKeyInfo(MobileSecurityObject.DEVICE_KEY_INFO.required(structure), coseKeyCodec),
        docType = DocType(MobileSecurityObject.DOC_TYPE.required<CborString>(structure).value),
        validityInfo =
            decodeValidityInfo(
                requireStringLabelMap(MobileSecurityObject.VALIDITY_INFO.required(structure), "MobileSecurityObject.validityInfo"),
            ),
        original = original,
        status = MobileSecurityObject.STATUS.optional<CborItem<*>>(structure)?.let(::decodeStatus),
    )
}

private fun decodeStatus(item: CborItem<*>): Status {
    val fields = requireStringLabelMap(item, "MobileSecurityObject.status")
    val identifierList = fields.value[StringLabel("identifier_list")]?.let(::decodeIdentifierListInfo)
    val statusList = fields.value[StringLabel("status_list")]?.let(::decodeStatusListInfo)
    return Status(
        identifierList = identifierList,
        statusList = statusList,
        unknown = unknownFields(fields, setOf("identifier_list", "status_list")),
    )
}

private fun decodeIdentifierListInfo(item: CborItem<*>): IdentifierListInfo {
    val fields = requireStringLabelMap(item, "IdentifierListInfo")
    val certificate = fields.value[StringLabel("certificate")]
        ?.let { value -> (value as? CborByteString)?.value ?: error("IdentifierListInfo.certificate must be a byte string") }
    return IdentifierListInfo(
        id = (fields.value[StringLabel("id")] as? CborByteString)?.value ?: error("IdentifierListInfo.id must be a byte string"),
        uri = (fields.value[StringLabel("uri")] as? CborString)?.value ?: error("IdentifierListInfo.uri must be text"),
        certificate = certificate,
        unknown = unknownFields(fields, setOf("id", "uri", "certificate")),
    )
}

private fun decodeStatusListInfo(item: CborItem<*>): StatusListInfo {
    val fields = requireStringLabelMap(item, "StatusListInfo")
    val certificate = fields.value[StringLabel("certificate")]
        ?.let { value -> (value as? CborByteString)?.value ?: error("StatusListInfo.certificate must be a byte string") }
    val aggregationUri = fields.value[StringLabel("aggregation_uri")]
        ?.let { value -> (value as? CborString)?.value ?: error("StatusListInfo.aggregation_uri must be text") }
    return StatusListInfo(
        idx = (fields.value[StringLabel("idx")] as? CborUInt)?.value?.let { toUIntExact(it, "StatusListInfo.idx") }
            ?: error("StatusListInfo.idx must be unsigned"),
        uri = (fields.value[StringLabel("uri")] as? CborString)?.value ?: error("StatusListInfo.uri must be text"),
        certificate = certificate,
        aggregationUri = aggregationUri,
        unknown = unknownFields(fields, setOf("idx", "uri", "certificate", "aggregation_uri")),
    )
}

private fun parseMobileSecurityObject(
    cborParser: CborParser,
    bytes: ByteArray,
): IdkResult<Pair<CborMap<StringLabel, CborItem<*>>, ByteArray>, IdkError> =
    cborParser.parse(bytes).flatMap { item ->
        val actualBytes =
            when (item) {
                is CborEncodedItem<*> -> item.value.taggedItem.value
                else -> bytes
            }
        val actualItemResult =
            if (actualBytes.contentEquals(bytes)) {
                Ok(item)
            } else {
                cborParser.parse(actualBytes)
            }

        actualItemResult.map { actualItem ->
            requireStringLabelMap(actualItem, "MobileSecurityObject") to actualBytes
        }
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

@Suppress("UNCHECKED_CAST")
private fun requireDigestMap(
    item: CborItem<*>,
    typeName: String,
): CborMap<NumberLabel, CborByteString> {
    require(item is CborMap<*, *>) { "$typeName must be encoded as a CBOR map" }

    val normalizedEntries =
        item.value.entries
            .associate { (key, value) ->
                val keyItem =
                    key as? CborItem<*>
                        ?: throw IllegalArgumentException("$typeName map keys must be CBOR items")
                val valueItem =
                    value as? CborByteString
                        ?: throw IllegalArgumentException("$typeName map values must be CBOR byte strings")
                NumberLabel.fromCborItem(keyItem) to valueItem
            }.toMutableMap()
    return CborMap(normalizedEntries, item.indefiniteLength)
}

private fun decodeDeviceKeyInfo(
    item: CborItem<*>,
    coseKeyCodec: CoseKeyCborCodec,
): DeviceKeyInfo {
    val structure = requireStringLabelMap(item, "DeviceKeyInfo")
    val encodedDeviceKey =
        com.sphereon.cbor.Cbor.encode(
            requireNumberLabelItemMap(DeviceKeyInfo.DEVICE_KEY.required(structure), "DeviceKeyInfo.deviceKey"),
        )

    return DeviceKeyInfo(
        deviceKey = coseKeyCodec.decode(encodedDeviceKey).getOrThrow().value,
        keyAuthorizations =
            DeviceKeyInfo.KEY_AUTHORIZATIONS
                .optional<CborItem<*>>(structure)
                ?.let { decodeKeyAuthorizations(it) },
        keyInfo =
            DeviceKeyInfo.KEY_INFO
                .optional<CborItem<*>>(structure)
                ?.let { requireNumberLabelItemMap(it, "DeviceKeyInfo.keyInfo") },
        original = null,
    )
}

@Suppress("UNCHECKED_CAST")
private fun unknownFields(
    structure: CborMap<StringLabel, CborItem<*>>,
    known: Set<String>,
): Map<String, CborItem<*>>? =
    structure.value
        .filterKeys { it.value !in known }
        .mapKeys { (key, _) -> key.value }
        .takeIf { it.isNotEmpty() }

private fun decodeKeyAuthorizations(item: CborItem<*>): KeyAuthorizations {
    val structure = requireStringLabelMap(item, "KeyAuthorizations")

    return KeyAuthorizations(
        nameSpaces =
            KeyAuthorizations.NAME_SPACES
                .optional<CborItem<*>>(structure)
                ?.let { requireStringLabelArray(it, "KeyAuthorizations.nameSpaces") },
        dataElements =
            KeyAuthorizations.DATA_ELEMENTS
                .optional<CborItem<*>>(structure)
                ?.let { requireAuthorizedDataElements(it, "KeyAuthorizations.dataElements") },
    )
}

private fun decodeValidityInfo(structure: CborMap<StringLabel, CborItem<*>>): ValidityInfo =
    ValidityInfo(
        signed = decodeTDate(ValidityInfo.SIGNED.required(structure), "ValidityInfo.signed"),
        validFrom = decodeTDate(ValidityInfo.VALID_FROM.required(structure), "ValidityInfo.validFrom"),
        validUntil = decodeTDate(ValidityInfo.VALID_UNTIL.required(structure), "ValidityInfo.validUntil"),
        expectedUpdate =
            ValidityInfo.EXPECTED_UPDATE
                .optional<CborItem<*>>(structure)
                ?.let { decodeTDate(it, "ValidityInfo.expectedUpdate") },
    )

private fun decodeTDate(
    item: CborItem<*>,
    typeName: String,
): TDate =
    when (item) {
        is CborTDate -> TDate(item.value)
        is CborString -> TDate(item.value)
        is CborTagged<*> -> TDate(item.value as? String ?: throw IllegalArgumentException("$typeName must contain a string value"))
        else -> throw IllegalArgumentException("$typeName must be encoded as a tagged or string date value")
    }

@Suppress("UNCHECKED_CAST")
private fun requireNumberLabelItemMap(
    item: CborItem<*>,
    typeName: String,
): CborMap<NumberLabel, CborItem<*>> {
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
                NumberLabel.fromCborItem(keyItem) to valueItem
            }.toMutableMap()
    return CborMap(normalizedEntries, item.indefiniteLength)
}

@Suppress("UNCHECKED_CAST")
private fun requireStringLabelArray(
    item: CborItem<*>,
    typeName: String,
): CborArray<StringLabel> {
    if (item !is CborArray<*>) {
        throw IllegalArgumentException("$typeName must be encoded as a CBOR array")
    }

    val normalizedItems =
        item.value
            .map { value ->
                val valueItem =
                    value as? CborItem<*>
                        ?: throw IllegalArgumentException("$typeName array items must be CBOR items")
                StringLabel.fromCborItem(valueItem)
            }.toMutableList()
    return CborArray(normalizedItems, item.indefiniteLength)
}

@Suppress("UNCHECKED_CAST")
private fun requireAuthorizedDataElements(
    item: CborItem<*>,
    typeName: String,
): AuthorizedDataElementsAlias {
    require(item is CborMap<*, *>) { "$typeName must be encoded as a CBOR map" }

    val normalizedEntries =
        item.value.entries
            .associate { (nameSpaceKey, dataElementsItem) ->
                val keyItem =
                    nameSpaceKey as? CborItem<*>
                        ?: throw IllegalArgumentException("$typeName map keys must be CBOR items")
                val valueArray =
                    dataElementsItem as? CborArray<*>
                        ?: throw IllegalArgumentException("$typeName map values must be CBOR arrays")
                StringLabel.fromCborItem(keyItem) to
                    CborArray(
                        valueArray.value
                            .map { dataElement ->
                                dataElement as? CborString
                                    ?: throw IllegalArgumentException("$typeName array items must be CBOR strings")
                            }.toMutableList(),
                        valueArray.indefiniteLength,
                    )
            }.toMutableMap()
    return CborMap(normalizedEntries, item.indefiniteLength)
}

private fun toUIntExact(
    value: Long,
    field: String,
): UInt {
    require(value in 0..UInt.MAX_VALUE.toLong()) { "$field is outside the UInt range" }
    return value.toUInt()
}

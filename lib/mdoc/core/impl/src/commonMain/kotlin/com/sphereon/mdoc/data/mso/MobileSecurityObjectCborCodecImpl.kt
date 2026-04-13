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
            ),
        ),
    )
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
                            DigestID(digestId.value.toUInt()) to digestValue.value.copyOf()
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

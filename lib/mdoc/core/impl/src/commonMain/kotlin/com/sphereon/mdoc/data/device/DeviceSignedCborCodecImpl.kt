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
import com.sphereon.cbor.CborParser
import com.sphereon.cbor.CborParserImpl
import com.sphereon.cbor.CborString
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.toCborItem
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseMac0CborCodec
import com.sphereon.crypto.core.cose.CoseMac0CborCodecImpl
import com.sphereon.crypto.core.cose.CoseSign1CborCodec
import com.sphereon.crypto.core.cose.CoseSign1CborCodecImpl
import com.sphereon.mdoc.DecodedMdoc
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DeviceSignedCborCodec>())
class DeviceSignedCborCodecImpl(
    private val cborParser: CborParser,
    private val coseSign1Codec: CoseSign1CborCodec,
    private val coseMac0Codec: CoseMac0CborCodec,
) : DeviceSignedCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        coseSign1Codec = CoseSign1CborCodecImpl(cborParser),
        coseMac0Codec = CoseMac0CborCodecImpl(cborParser),
    )

    override fun encode(value: DeviceSigned): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "DeviceSigned",
            operation = { encodeDeviceSigned(value, cborParser, coseSign1Codec, coseMac0Codec) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<DeviceSigned>, IdkError> =
        cborParser.parse(bytes).flatMap { item ->
            val map = requireStringLabelMap(item, "DeviceSigned")
            decodeValue(
                typeName = "DeviceSigned",
                bytes = bytes,
                operation = { decodeDeviceSigned(map, bytes, coseSign1Codec, coseMac0Codec) },
            )
        }

    override fun decode(item: CborItem<*>): IdkResult<DeviceSigned, IdkError> =
        try {
            Ok(decodeDeviceSigned(requireStringLabelMap(item, "DeviceSigned"), null, coseSign1Codec, coseMac0Codec))
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode DeviceSigned: ${e.message}", throwable = e))
        } catch (expected: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode DeviceSigned: ${expected.message}", exception = expected))
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

private fun encodeDeviceSigned(
    value: DeviceSigned,
    cborParser: CborParser,
    coseSign1Codec: CoseSign1CborCodec,
    coseMac0Codec: CoseMac0CborCodec,
): ByteArray {
    value.original?.let { return it }

    return com.sphereon.cbor.Cbor.encode(
        CborMap(
            mutableMapOf(
                DeviceSigned.NAME_SPACES to CborEncodedItem<CborMap<CborString, CborMap<CborString, CborItem<*>>>>(encodeDeviceNameSpaces(value.nameSpaces)),
                DeviceSigned.DEVICE_AUTH to encodeDeviceAuth(value.deviceAuth, cborParser, coseSign1Codec, coseMac0Codec),
            ),
        ),
    )
}

private fun encodeDeviceNameSpaces(value: DeviceNameSpaces): ByteArray =
    com.sphereon.cbor.Cbor.encode(
        CborMap(
            value.value.entries
                .associate { (nameSpace, items) ->
                    CborString(nameSpace.toString()) to
                        CborMap(
                            items.value.entries
                                .associate { (identifier, elementValue) ->
                                    CborString(identifier.toString()) to elementValue.toCborItem()
                                }.toMutableMap(),
                        )
                }.toMutableMap(),
        ),
    )

private fun encodeDeviceAuth(
    value: DeviceAuth,
    cborParser: CborParser,
    coseSign1Codec: CoseSign1CborCodec,
    coseMac0Codec: CoseMac0CborCodec,
): CborMap<StringLabel, CborItem<*>> {
    val entries = mutableMapOf<StringLabel, CborItem<*>>()
    value.deviceSignature?.let {
        entries[DeviceAuth.DEVICE_SIGNATURE] = encodeCborItem(coseSign1Codec.encode(it).getOrThrow(), cborParser)
    }
    value.deviceMac?.let {
        entries[DeviceAuth.DEVICE_MAC] =
            it.coseMac0?.let { mac -> encodeCborItem(coseMac0Codec.encode(mac).getOrThrow(), cborParser) }
                ?: it.toCborItem()
    }
    return CborMap(entries)
}

private fun decodeDeviceSigned(
    structure: CborMap<StringLabel, CborItem<*>>,
    original: ByteArray?,
    coseSign1Codec: CoseSign1CborCodec,
    coseMac0Codec: CoseMac0CborCodec,
): DeviceSigned {
    val encodedNameSpaces: CborEncodedItem<CborMap<CborItem<*>, CborItem<*>>> = DeviceSigned.NAME_SPACES.required(structure)

    return DeviceSigned(
        nameSpaces =
            decodeDeviceNameSpaces(
                com.sphereon.cbor.Cbor
                    .decode(encodedNameSpaces.value.taggedItem.value),
            ),
        deviceAuth =
            decodeDeviceAuth(
                normalizeStringLabelMap(DeviceSigned.DEVICE_AUTH.required(structure), "DeviceAuth"),
                coseSign1Codec,
                coseMac0Codec,
            ),
        original = original,
    )
}

private fun decodeDeviceAuth(
    structure: CborMap<StringLabel, CborItem<*>>,
    coseSign1Codec: CoseSign1CborCodec,
    coseMac0Codec: CoseMac0CborCodec,
): DeviceAuth {
    val deviceSignature =
        DeviceAuth.DEVICE_SIGNATURE
            .optional<CborArray<CborItem<*>>>(structure)
            ?.let { decodeDeviceSignature(it, coseSign1Codec) }
    val deviceMac =
        structure.value[DeviceAuth.DEVICE_MAC]?.let { item ->
            when (item) {
                is CborString -> DeviceMac.fromCborItem(item)
                else ->
                    DeviceMac.fromCoseMac0(
                        coseMac0Codec
                            .decode(com.sphereon.cbor.Cbor.encode(item))
                            .getOrThrow()
                            .value
                            .also { mac ->
                                require(mac.payload == null) {
                                    "DeviceMac COSE_Mac0 must use a detached payload"
                                }
                            },
                    )
            }
        }

    return DeviceAuth(
        deviceSignature = deviceSignature,
        deviceMac = deviceMac,
        original = null,
    )
}

private fun decodeDeviceNameSpaces(structure: CborMap<CborItem<*>, CborItem<*>>): DeviceNameSpaces =
    DeviceNameSpaces(
        structure.value
            .map { (nameSpaceKey, items) ->
                decodeNameSpace(nameSpaceKey) to decodeDeviceSignedItems(requireNestedMap(items, "DeviceSignedItems"))
            }.toMap(),
    )

private fun decodeDeviceSignedItems(structure: CborMap<CborItem<*>, CborItem<*>>): DeviceSignedItems =
    DeviceSignedItems(
        structure.value
            .map { (identifierKey, value) ->
                decodeDataElementIdentifier(identifierKey) to value.value as Any
            }.toMap(),
    )

@Suppress("UNCHECKED_CAST")
private fun decodeDeviceSignature(
    structure: CborArray<CborItem<*>>,
    coseSign1Codec: CoseSign1CborCodec,
): COSE_Sign1<DeviceAuthentication> =
    coseSign1Codec
        .decode(
            com.sphereon.cbor.Cbor
                .encode(structure),
        ).getOrThrow()
        .value as COSE_Sign1<DeviceAuthentication>

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

private fun encodeCborItem(
    bytes: ByteArray,
    cborParser: CborParser,
): CborItem<*> = cborParser.parse(bytes).getOrThrow()

private fun decodeNameSpace(key: CborItem<*>): NameSpace =
    when (key) {
        is StringLabel -> NameSpace(key.value)
        is com.sphereon.cbor.CborString -> NameSpace(key.value)
        else -> throw IllegalArgumentException("DeviceSigned nameSpaces keys must be CBOR strings")
    }

private fun decodeDataElementIdentifier(key: CborItem<*>): DataElementIdentifier =
    when (key) {
        is StringLabel -> DataElementIdentifier(key.value)
        is com.sphereon.cbor.CborString -> DataElementIdentifier(key.value)
        else -> throw IllegalArgumentException("DeviceSigned item keys must be CBOR strings")
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

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

package com.sphereon.mdoc.transfer.reader

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBool
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborInt
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborParser
import com.sphereon.cbor.CborParserImpl
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.toCborBool
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.DecodedMdoc
import com.sphereon.mdoc.transfer.OriginInfo
import com.sphereon.mdoc.transfer.OriginInfoCategory
import com.sphereon.mdoc.transfer.OriginInfoDetails
import com.sphereon.mdoc.transfer.OriginInfoType
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.Capabilities
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodVersion
import com.sphereon.mdoc.transfer.device.DeviceRetrievalOptions
import com.sphereon.mdoc.transfer.device.NfcOptions
import com.sphereon.mdoc.transfer.device.Oid4vpOptions
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.device.WifiAwareOptions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ReaderEngagementCborCodec>())
class ReaderEngagementCborCodecImpl(
    private val cborParser: CborParser,
    private val coseKeyCodec: CoseKeyCborCodec,
) : ReaderEngagementCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        coseKeyCodec = CoseKeyCborCodecImpl(cborParser),
    )

    override fun encode(value: ReaderEngagement): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "ReaderEngagement",
            operation = { encodeReaderEngagement(value) },
        )

    override fun encodeTag24(value: ReaderEngagement): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "ReaderEngagement tag 24 wrapper",
            operation = { encodeReaderEngagementTag24(value) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<ReaderEngagement>, IdkError> =
        try {
            parseReaderEngagement(cborParser, bytes).flatMap { (structure, originalBytes) ->
                decodeValue(
                    typeName = "ReaderEngagement",
                    bytes = originalBytes,
                    operation = { decodeReaderEngagement(structure, originalBytes, coseKeyCodec) },
                )
            }
        } catch (e: IllegalArgumentException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decode ReaderEngagement: ${e.message}", throwable = e))
        } catch (expected: Throwable) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to decode ReaderEngagement: ${expected.message}", exception = expected))
        }

    override fun encodeUri(
        value: ReaderEngagement,
        scheme: String,
    ): IdkResult<String, IdkError> = encode(value).map { encoded -> "$scheme${encoded.encodeToBase64Url()}" }

    override fun decodeUri(uri: String): IdkResult<DecodedMdoc<ReaderEngagement>, IdkError> =
        decodeValue(
            typeName = "ReaderEngagement URI",
            bytes = uri.encodeToByteArray(),
            operation = { extractPayload(uri).decodeFromBase64Url() },
        ).flatMap { decodedPayload ->
            decode(decodedPayload.value)
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

private fun extractPayload(uri: String): String =
    when {
        uri.startsWith("mdoc-openid4vp://") -> {
            val afterScheme = uri.substring(17)
            if (afterScheme.startsWith("?")) {
                throw IllegalArgumentException(
                    "mdoc-openid4vp:// URI contains query parameters, not a base64url-encoded ReaderEngagement. " +
                        "Cannot parse ReaderEngagement from query parameter format. " +
                        "If the Authorization Request contains a ReaderEngagement, fetch it from request_uri first.",
                )
            }
            afterScheme
        }

        uri.startsWith("mdoc://") -> {
            uri.substring(7)
        }

        uri.startsWith("mdoc:") && !uri.startsWith("mdoc://") -> {
            uri.substring(5)
        }

        else -> {
            throw IllegalArgumentException(
                "URI must start with 'mdoc:' (classic), 'mdoc://' (website), or 'mdoc-openid4vp://' (OID4VP). " +
                    "Got: ${uri.take(20)}...",
            )
        }
    }

private fun parseReaderEngagement(
    cborParser: CborParser,
    bytes: ByteArray,
): IdkResult<Pair<CborMap<NumberLabel, CborItem<*>>, ByteArray>, IdkError> =
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
            requireNumberLabelMap(actualItem, "ReaderEngagement") to bytes
        }
    }

private fun encodeReaderEngagement(value: ReaderEngagement): ByteArray {
    value.original?.let { return it }

    val entries =
        mutableMapOf<NumberLabel, CborItem<*>>(
            ReaderEngagement.VERSION to value.version.toCborItem(),
            ReaderEngagement.SECURITY to encodeReaderEngagementSecurity(value.security),
        )

    when (value) {
        is ReaderEngagement.V1_0 -> {
            value.deviceRetrievalMethods
                ?.takeIf { it.isNotEmpty() }
                ?.map(::encodeDeviceRetrievalMethod)
                ?.let { methods ->
                    entries[ReaderEngagement.DEVICE_RETRIEVAL_METHODS] =
                        CborArray(methods.map { method -> method as CborItem<*> }.toMutableList())
                }
            value.protocolInfo?.let { entries[ReaderEngagement.PROTOCOL_INFO] = it }
            value.additionalItems?.value?.forEach { (key, item) -> entries[key] = item }
        }

        is ReaderEngagement.V1_1 -> {
            value.deviceRetrievalMethods
                ?.takeIf { it.isNotEmpty() }
                ?.map(::encodeDeviceRetrievalMethod)
                ?.let { methods ->
                    entries[ReaderEngagement.DEVICE_RETRIEVAL_METHODS] =
                        CborArray(methods.map { method -> method as CborItem<*> }.toMutableList())
                }
            value.protocolInfo?.let { entries[ReaderEngagement.PROTOCOL_INFO] = it }
            value.originInfos
                ?.takeIf { it.isNotEmpty() }
                ?.let { originInfos ->
                    entries[ReaderEngagement.ORIGIN_INFOS] =
                        CborArray(originInfos.map { originInfo -> encodeOriginInfo(originInfo) as CborItem<*> }.toMutableList())
                }
            value.capabilities?.let { entries[ReaderEngagement.CAPABILITIES] = encodeCapabilities(it) }
            value.additionalItems?.value?.forEach { (key, item) -> entries[key] = item }
        }
    }

    return com.sphereon.cbor.Cbor
        .encode(CborMap(entries))
}

private fun encodeReaderEngagementTag24(value: ReaderEngagement): ByteArray {
    val original = value.original
    if (original != null && isTag24Wrapped(original)) {
        return original
    }

    val unwrappedBytes = original ?: encodeReaderEngagement(value)
    return com.sphereon.cbor.Cbor.encode(
        CborEncodedItem(
            value = unwrappedBytes,
            data = value.copyWithOriginal(unwrappedBytes),
        ),
    )
}

private fun decodeReaderEngagement(
    structure: CborMap<NumberLabel, CborItem<*>>,
    original: ByteArray,
    coseKeyCodec: CoseKeyCborCodec,
): ReaderEngagement =
    when (val version = ReaderEngagementVersion.Decoder.fromCborItem(ReaderEngagement.VERSION.required(structure)).toString()) {
        "1.0" -> decodeReaderEngagementV1_0(structure, original, coseKeyCodec)
        "1.1" -> decodeReaderEngagementV1_1(structure, original, coseKeyCodec)
        else -> throw IllegalArgumentException("Version must be 1.0 or 1.1, got: $version")
    }

private fun decodeReaderEngagementV1_0(
    structure: CborMap<NumberLabel, CborItem<*>>,
    original: ByteArray,
    coseKeyCodec: CoseKeyCborCodec,
): ReaderEngagement.V1_0 =
    ReaderEngagement.V1_0(
        security = decodeReaderEngagementSecurity(ReaderEngagement.SECURITY.required(structure), coseKeyCodec),
        deviceRetrievalMethods =
            ReaderEngagement.DEVICE_RETRIEVAL_METHODS
                .optional<CborArray<CborItem<*>>>(structure)
                ?.let(::decodeDeviceRetrievalMethods),
        protocolInfo = structure.value[ReaderEngagement.PROTOCOL_INFO],
        additionalItems =
            extractAdditionalItems(
                structure,
                setOf(
                    ReaderEngagement.VERSION,
                    ReaderEngagement.SECURITY,
                    ReaderEngagement.DEVICE_RETRIEVAL_METHODS,
                    ReaderEngagement.PROTOCOL_INFO,
                ),
            ),
        original = original,
    )

private fun decodeReaderEngagementV1_1(
    structure: CborMap<NumberLabel, CborItem<*>>,
    original: ByteArray,
    coseKeyCodec: CoseKeyCborCodec,
): ReaderEngagement.V1_1 =
    ReaderEngagement.V1_1(
        security = decodeReaderEngagementSecurity(ReaderEngagement.SECURITY.required(structure), coseKeyCodec),
        deviceRetrievalMethods =
            ReaderEngagement.DEVICE_RETRIEVAL_METHODS
                .optional<CborArray<CborItem<*>>>(structure)
                ?.let(::decodeDeviceRetrievalMethods),
        protocolInfo = structure.value[ReaderEngagement.PROTOCOL_INFO],
        originInfos =
            ReaderEngagement.ORIGIN_INFOS
                .optional<CborArray<CborMap<CborItem<*>, CborItem<*>>>>(structure)
                ?.value
                ?.map { decodeOriginInfo(normalizeStringLabelMap(it, "OriginInfo")) }
                ?.toTypedArray(),
        capabilities =
            ReaderEngagement.CAPABILITIES
                .optional<CborMap<CborItem<*>, CborItem<*>>>(structure)
                ?.let { decodeCapabilities(normalizeNumberLabelMap(it, "Capabilities")) },
        additionalItems =
            extractAdditionalItems(
                structure,
                setOf(
                    ReaderEngagement.VERSION,
                    ReaderEngagement.SECURITY,
                    ReaderEngagement.DEVICE_RETRIEVAL_METHODS,
                    ReaderEngagement.PROTOCOL_INFO,
                    ReaderEngagement.ORIGIN_INFOS,
                    ReaderEngagement.CAPABILITIES,
                ),
            ),
        original = original,
    )

private fun encodeReaderEngagementSecurity(value: ReaderEngagementSecurity): CborArray<CborItem<*>> =
    CborArray(
        mutableListOf(
            CborUInt(value.cipherSuite.toLong()),
            value.eReaderKeyBytes.value,
        ),
    )

private fun decodeReaderEngagementSecurity(
    structure: CborArray<CborItem<*>>,
    coseKeyCodec: CoseKeyCborCodec,
): ReaderEngagementSecurity {
    val encodedReaderKey: CborEncodedItem<CborMap<NumberLabel, CborItem<*>>> = structure.required(1)
    val readerKey = coseKeyCodec.decode(encodedReaderKey.value.taggedItem.value).getOrThrow().value as CoseKeyType

    return ReaderEngagementSecurity(
        cipherSuite = structure.required<CborUInt>(0).value.toUInt(),
        eReaderKeyBytes = encodedReaderKey.copy(readerKey),
    )
}

private fun encodeDeviceRetrievalMethod(value: DeviceRetrievalMethod): CborArray<CborItem<*>> =
    CborArray(
        mutableListOf(
            CborUInt(value.type.type.toLong()),
            CborUInt(value.version.version.toLong()),
            encodeDeviceRetrievalOptions(value.retrievalOptions),
        ),
    )

private fun decodeDeviceRetrievalMethods(items: CborArray<CborItem<*>>): Array<DeviceRetrievalMethod>? =
    if (items.value.isEmpty()) {
        null
    } else {
        items.value
            .mapIndexed { index, item ->
                decodeDeviceRetrievalMethod(requireArray(item, "DeviceRetrievalMethod[$index]"))
            }.toTypedArray()
    }

private fun decodeDeviceRetrievalMethod(structure: CborArray<CborItem<*>>): DeviceRetrievalMethod {
    val type = DeviceRetrievalMethodType.entries.first { it.type == structure.required<CborUInt>(0).value.toUInt() }
    val version = DeviceRetrievalMethodVersion(structure.required<CborUInt>(1).value.toUInt())
    val options = decodeDeviceRetrievalOptions(type, requireNumberLabelMap(structure.required(2), "DeviceRetrievalOptions"))

    return DeviceRetrievalMethod(
        type = type,
        version = version,
        retrievalOptions = options,
        original = null,
    )
}

private fun encodeDeviceRetrievalOptions(value: DeviceRetrievalOptions): CborMap<NumberLabel, CborItem<*>> =
    when (value) {
        is NfcOptions -> {
            CborMap(
                mutableMapOf(
                    NfcOptions.MAX_COMMAND_DATA_FIELD_LENGTH to CborUInt(value.maxCommandDataFieldLength.toLong()),
                    NfcOptions.MAX_RESPONSE_DATA_FIELD_LENGTH to CborUInt(value.maxResponseDataFieldLength.toLong()),
                ),
            )
        }

        is BleOptions -> {
            val entries =
                mutableMapOf<NumberLabel, CborItem<*>>(
                    BleOptions.PERIPHERAL_SERVER_MODE to value.peripheralServerMode.toCborBool(),
                    BleOptions.CENTRAL_CLIENT_MODE to value.centralClientMode.toCborBool(),
                )
            value.peripheralServerModeUuid?.let { entries[BleOptions.PERIPHERAL_SERVER_MODE_UUID] = CborByteString(it.toByteArray()) }
            value.centralClientModeUuid?.let { entries[BleOptions.CENTRAL_CLIENT_MODE_UUID] = CborByteString(it.toByteArray()) }
            value.peripheralServerModeDeviceAddress?.let { entries[BleOptions.PERIPHERAL_SERVER_MODE_DEVICE_ADDRESS] = CborByteString(it) }
            CborMap(entries)
        }

        is WifiAwareOptions -> {
            val entries = mutableMapOf<NumberLabel, CborItem<*>>()
            value.passPhrase?.let { entries[WifiAwareOptions.PASS_PHRASE] = CborString(it) }
            value.channelInfoOperatingClass?.let { entries[WifiAwareOptions.CHANNEL_INFO_OPERATING_CLASS] = CborUInt(it.toLong()) }
            value.channelInfoChannelNumber?.let { entries[WifiAwareOptions.CHANNEL_INFO_CHANNEL_NUMBER] = CborUInt(it.toLong()) }
            value.supportedBands?.let { entries[WifiAwareOptions.SUPPORTED_BANDS] = CborByteString(it) }
            CborMap(entries)
        }

        is RestApiOptions -> {
            CborMap(mutableMapOf(RestApiOptions.URI to CborString(value.uri)))
        }

        is Oid4vpOptions -> {
            val entries =
                mutableMapOf<NumberLabel, CborItem<*>>(
                    Oid4vpOptions.CLIENT_ID to CborString(value.clientId),
                )
            value.responseUri?.let { entries[Oid4vpOptions.RESPONSE_URI] = CborString(it) }
            value.nonce?.let { entries[Oid4vpOptions.NONCE] = CborString(it) }
            value.requestUri?.let { entries[Oid4vpOptions.REQUEST_URI] = CborString(it) }
            value.presentationDefinitionUri?.let { entries[Oid4vpOptions.PRESENTATION_DEFINITION_URI] = CborString(it) }
            CborMap(entries)
        }
    }

private fun decodeDeviceRetrievalOptions(
    type: DeviceRetrievalMethodType,
    structure: CborMap<NumberLabel, CborItem<*>>,
): DeviceRetrievalOptions =
    when (type) {
        DeviceRetrievalMethodType.NFC -> {
            NfcOptions(
                maxCommandDataFieldLength =
                    requireUInt(
                        structure.value[NfcOptions.MAX_COMMAND_DATA_FIELD_LENGTH],
                        "NfcOptions.maxCommandDataFieldLength",
                    ).value.toUInt(),
                maxResponseDataFieldLength =
                    requireUInt(
                        structure.value[NfcOptions.MAX_RESPONSE_DATA_FIELD_LENGTH],
                        "NfcOptions.maxResponseDataFieldLength",
                    ).value.toUInt(),
            )
        }

        DeviceRetrievalMethodType.BLE -> {
            BleOptions(
                peripheralServerMode = requireBool(structure.value[BleOptions.PERIPHERAL_SERVER_MODE], "BleOptions.peripheralServerMode").value,
                centralClientMode = requireBool(structure.value[BleOptions.CENTRAL_CLIENT_MODE], "BleOptions.centralClientMode").value,
                peripheralServerModeUuid =
                    optionalByteString(structure.value[BleOptions.PERIPHERAL_SERVER_MODE_UUID], "BleOptions.peripheralServerModeUuid")
                        ?.value
                        ?.let(Uuid::fromByteArray),
                centralClientModeUuid =
                    optionalByteString(structure.value[BleOptions.CENTRAL_CLIENT_MODE_UUID], "BleOptions.centralClientModeUuid")
                        ?.value
                        ?.let(Uuid::fromByteArray),
                peripheralServerModeDeviceAddress =
                    optionalByteString(
                        structure.value[BleOptions.PERIPHERAL_SERVER_MODE_DEVICE_ADDRESS],
                        "BleOptions.peripheralServerModeDeviceAddress",
                    )?.value,
            )
        }

        DeviceRetrievalMethodType.WIFI_WARE -> {
            WifiAwareOptions(
                passPhrase = optionalString(structure.value[WifiAwareOptions.PASS_PHRASE], "WifiAwareOptions.passPhrase")?.value,
                channelInfoOperatingClass =
                    optionalUInt(
                        structure.value[WifiAwareOptions.CHANNEL_INFO_OPERATING_CLASS],
                        "WifiAwareOptions.channelInfoOperatingClass",
                    )?.value?.toUInt(),
                channelInfoChannelNumber =
                    optionalUInt(
                        structure.value[WifiAwareOptions.CHANNEL_INFO_CHANNEL_NUMBER],
                        "WifiAwareOptions.channelInfoChannelNumber",
                    )?.value?.toUInt(),
                supportedBands = optionalByteString(structure.value[WifiAwareOptions.SUPPORTED_BANDS], "WifiAwareOptions.supportedBands")?.value,
            )
        }

        DeviceRetrievalMethodType.WEBSITE -> {
            RestApiOptions(
                uri = requireString(structure.value[RestApiOptions.URI], "RestApiOptions.uri").value,
            )
        }

        DeviceRetrievalMethodType.OID4VP -> {
            Oid4vpOptions(
                clientId = requireString(structure.value[Oid4vpOptions.CLIENT_ID], "Oid4vpOptions.clientId").value,
                responseUri = optionalString(structure.value[Oid4vpOptions.RESPONSE_URI], "Oid4vpOptions.responseUri")?.value,
                nonce = optionalString(structure.value[Oid4vpOptions.NONCE], "Oid4vpOptions.nonce")?.value,
                requestUri = optionalString(structure.value[Oid4vpOptions.REQUEST_URI], "Oid4vpOptions.requestUri")?.value,
                presentationDefinitionUri =
                    optionalString(
                        structure.value[Oid4vpOptions.PRESENTATION_DEFINITION_URI],
                        "Oid4vpOptions.presentationDefinitionUri",
                    )?.value,
            )
        }
    }

private fun encodeOriginInfo(value: OriginInfo): CborMap<StringLabel, CborItem<*>> {
    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            OriginInfo.CAT to CborUInt(value.cat.category.toLong()),
            OriginInfo.TYPE to CborUInt(value.type.infoType.toLong()),
        )
    value.details?.let { details ->
        entries[OriginInfo.DETAILS] =
            CborMap(
                details.entries
                    .associate { (key, detailValue) ->
                        StringLabel(key) to CborString(detailValue?.toString() ?: "")
                    }.toMutableMap(),
            )
    }
    return CborMap(entries)
}

private fun decodeOriginInfo(structure: CborMap<StringLabel, CborItem<*>>): OriginInfo {
    val details =
        structure.value[OriginInfo.DETAILS]?.let { detailsItem ->
            val detailsMap = requireStringLabelMap(detailsItem, "OriginInfo.details")
            OriginInfoDetails(
                detailsMap.value.entries.associate { (key, value) ->
                    key.value to (value.value?.toString() ?: "")
                },
            )
        }

    return OriginInfo(
        cat = OriginInfoCategory(requireUInt(structure.value[OriginInfo.CAT], "OriginInfo.cat").value.toUInt()),
        type = OriginInfoType(requireUInt(structure.value[OriginInfo.TYPE], "OriginInfo.type").value.toUInt()),
        details = details,
        original = null,
    )
}

private fun encodeCapabilities(value: Capabilities): CborMap<NumberLabel, CborItem<*>> {
    val entries = mutableMapOf<NumberLabel, CborItem<*>>()
    value.macKeysSupport?.let { entries[Capabilities.MAC_KEYS_SUPPORT] = it.toCborBool() }
    value.macKeyCurves
        ?.takeIf { it.isNotEmpty() }
        ?.let { curves ->
            entries[Capabilities.MAC_KEY_CURVES] = CborArray(curves.map { curve -> CborUInt(curve.value.toLong()) as CborItem<*> }.toMutableList())
        }
    value.handoverSessionEstablishmentSupport?.let { entries[Capabilities.HANDOVER_SESSION_ESTABLISHMENT_SUPPORT] = it.toCborBool() }
    value.readerAuthAllSupport?.let { entries[Capabilities.READER_AUTH_ALL_SUPPORT] = it.toCborBool() }
    value.extendedRequestSupport?.let { entries[Capabilities.EXTENDED_REQUEST_SUPPORT] = it.toCborBool() }
    value.additionalItems?.value?.forEach { (key, item) ->
        if (key !in entries) {
            entries[key] = item
        }
    }
    return CborMap(entries)
}

private fun decodeCapabilities(structure: CborMap<NumberLabel, CborItem<*>>): Capabilities =
    Capabilities(
        macKeysSupport = optionalBool(structure.value[Capabilities.MAC_KEYS_SUPPORT], "Capabilities.macKeysSupport")?.value,
        macKeyCurves = decodeCurves(structure.value[Capabilities.MAC_KEY_CURVES]),
        handoverSessionEstablishmentSupport =
            optionalBool(
                structure.value[Capabilities.HANDOVER_SESSION_ESTABLISHMENT_SUPPORT],
                "Capabilities.handoverSessionEstablishmentSupport",
            )?.value,
        readerAuthAllSupport = optionalBool(structure.value[Capabilities.READER_AUTH_ALL_SUPPORT], "Capabilities.readerAuthAllSupport")?.value,
        extendedRequestSupport = optionalBool(structure.value[Capabilities.EXTENDED_REQUEST_SUPPORT], "Capabilities.extendedRequestSupport")?.value,
        additionalItems = structure,
    )

private fun decodeCurves(item: CborItem<*>?): Array<CoseCurve>? =
    item?.let {
        requireArray(it, "Capabilities.macKeyCurves")
            .value
            .mapIndexed { index, curveItem ->
                CoseCurve.fromValue(requireIntValue(curveItem, "Capabilities.macKeyCurves[$index]"))
            }.toTypedArray()
    }

private fun extractAdditionalItems(
    structure: CborMap<NumberLabel, CborItem<*>>,
    reservedLabels: Set<NumberLabel>,
): CborMap<NumberLabel, CborItem<*>>? {
    val entries =
        structure.value
            .filterKeys { it !in reservedLabels }
            .toMutableMap()

    return if (entries.isEmpty()) {
        null
    } else {
        CborMap(entries, structure.indefiniteLength)
    }
}

private fun isTag24Wrapped(bytes: ByteArray): Boolean =
    bytes.size >= 2 &&
        bytes[0] == 0xD8.toByte() &&
        bytes[1] == 0x18.toByte()

@Suppress("UNCHECKED_CAST")
private fun requireNumberLabelMap(
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

private fun normalizeNumberLabelMap(
    item: CborMap<CborItem<*>, CborItem<*>>,
    typeName: String,
): CborMap<NumberLabel, CborItem<*>> = requireNumberLabelMap(item, typeName)

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

private fun normalizeStringLabelMap(
    item: CborMap<CborItem<*>, CborItem<*>>,
    typeName: String,
): CborMap<StringLabel, CborItem<*>> = requireStringLabelMap(item, typeName)

@Suppress("UNCHECKED_CAST")
private fun requireArray(
    item: CborItem<*>?,
    fieldName: String,
): CborArray<CborItem<*>> =
    item as? CborArray<CborItem<*>>
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR array")

private fun requireBool(
    item: CborItem<*>?,
    fieldName: String,
): CborBool =
    item as? CborBool
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR boolean")

private fun optionalBool(
    item: CborItem<*>?,
    fieldName: String,
): CborBool? = item?.let { requireBool(it, fieldName) }

private fun requireString(
    item: CborItem<*>?,
    fieldName: String,
): CborString =
    item as? CborString
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR string")

private fun optionalString(
    item: CborItem<*>?,
    fieldName: String,
): CborString? = item?.let { requireString(it, fieldName) }

private fun requireUInt(
    item: CborItem<*>?,
    fieldName: String,
): CborUInt =
    item as? CborUInt
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR uint")

private fun optionalUInt(
    item: CborItem<*>?,
    fieldName: String,
): CborUInt? = item?.let { requireUInt(it, fieldName) }

private fun requireByteString(
    item: CborItem<*>?,
    fieldName: String,
): CborByteString =
    item as? CborByteString
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR byte string")

private fun optionalByteString(
    item: CborItem<*>?,
    fieldName: String,
): CborByteString? = item?.let { requireByteString(it, fieldName) }

private fun requireIntValue(
    item: CborItem<*>?,
    fieldName: String,
): Int =
    when (item) {
        is CborInt -> item.value.toInt()
        is CborUInt -> item.value.toInt()
        else -> throw IllegalArgumentException("$fieldName must be encoded as a CBOR integer")
    }

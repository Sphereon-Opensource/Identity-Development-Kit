/*
 * Ã‚Â© 2026 Sphereon International B.V.
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

package com.sphereon.mdoc.engagement

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
import com.sphereon.mdoc.transfer.device.ServerRetrievalInfo
import com.sphereon.mdoc.transfer.device.ServerRetrievalMethods
import com.sphereon.mdoc.transfer.device.WifiAwareOptions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DeviceEngagementCborCodec>())
class DeviceEngagementCborCodecImpl(
    private val cborParser: CborParser,
    private val coseKeyCodec: CoseKeyCborCodec,
) : DeviceEngagementCborCodec {
    constructor(
        cborParser: CborParser = CborParserImpl(),
    ) : this(
        cborParser = cborParser,
        coseKeyCodec = CoseKeyCborCodecImpl(cborParser),
    )

    override fun encode(value: DeviceEngagement): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "DeviceEngagement",
            operation = { encodeDeviceEngagement(value) },
        )

    override fun encodeItem(value: DeviceEngagement): IdkResult<CborEncodedItem<DeviceEngagement>, IdkError> =
        encode(value).map { encoded ->
            CborEncodedItem(encoded, value.copyWithOriginal(encoded))
        }

    override fun encodeTag24(value: DeviceEngagement): IdkResult<ByteArray, IdkError> =
        encodeItem(value).flatMap { encodedItem ->
            encodeValue(
                typeName = "DeviceEngagement tag 24 wrapper",
                operation = {
                    com.sphereon.cbor.Cbor
                        .encode(encodedItem)
                },
            )
        }

    override fun encodeMessage(value: DeviceEngagement): IdkResult<ByteArray, IdkError> = encodeItem(value).flatMap { encodedItem -> encodeMessageItem(encodedItem) }

    override fun encodeMessageItem(value: CborEncodedItem<DeviceEngagement>): IdkResult<ByteArray, IdkError> =
        encodeValue(
            typeName = "DeviceEngagementMessage",
            operation = { encodeDeviceEngagementMessage(value) },
        )

    override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<DeviceEngagement>, IdkError> =
        parseDeviceEngagement(cborParser, bytes).flatMap { (structure, originalBytes) ->
            decodeValue(
                typeName = "DeviceEngagement",
                bytes = originalBytes,
                operation = { decodeDeviceEngagement(structure, originalBytes, coseKeyCodec) },
            )
        }

    override fun decodeMessage(bytes: ByteArray): IdkResult<DecodedMdoc<DeviceEngagement>, IdkError> =
        parseDeviceEngagementMessage(cborParser, bytes).flatMap { encodedItem ->
            decode(encodedItem.value.taggedItem.value)
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

private fun parseDeviceEngagement(
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
            requireNumberLabelMap(actualItem, "DeviceEngagement") to actualBytes
        }
    }

private fun parseDeviceEngagementMessage(
    cborParser: CborParser,
    bytes: ByteArray,
): IdkResult<CborEncodedItem<DeviceEngagement>, IdkError> =
    cborParser.parse(bytes).map { item ->
        val map = requireStringLabelMap(item, "DeviceEngagementMessage")
        DeviceEngagementMessage.DEVICE_ENGAGEMENT_BYTES.required(map)
    }

private fun encodeDeviceEngagement(value: DeviceEngagement): ByteArray {
    value.original?.let { return it }

    val entries =
        mutableMapOf<NumberLabel, CborItem<*>>(
            DeviceEngagement.VERSION to value.version.toCborItem(),
            DeviceEngagement.SECURITY to encodeDeviceEngagementSecurity(value.security),
        )

    when (value) {
        is DeviceEngagement.V1_0 -> {
            value.deviceRetrievalMethods?.let {
                entries[DeviceEngagement.DEVICE_RETRIEVAL_METHODS] =
                    it
                        .map(::encodeDeviceRetrievalMethod)
                        .let { methods -> CborArray(methods.map { method -> method as CborItem<*> }.toMutableList()) }
            }
            value.serverRetrievalMethod?.let {
                entries[DeviceEngagement.SERVER_RETRIEVAL_METHOD] = encodeServerRetrievalMethods(it)
            }
            value.protocolInfo?.let { entries[DeviceEngagement.PROTOCOL_INFO] = it }
            value.additionalItems?.value?.forEach { (key, item) -> entries[key] = item }
        }

        is DeviceEngagement.V1_1 -> {
            value.deviceRetrievalMethods?.let {
                entries[DeviceEngagement.DEVICE_RETRIEVAL_METHODS] =
                    it
                        .map(::encodeDeviceRetrievalMethod)
                        .let { methods -> CborArray(methods.map { method -> method as CborItem<*> }.toMutableList()) }
            }
            value.protocolInfo?.let { entries[DeviceEngagement.PROTOCOL_INFO] = it }
            value.originInfos?.let {
                entries[DeviceEngagement.ORIGIN_INFOS] = CborArray(it.map { originInfo -> encodeOriginInfo(originInfo) as CborItem<*> }.toMutableList())
            }
            value.capabilities?.let { entries[DeviceEngagement.CAPABILITIES] = encodeCapabilities(it) }
            value.additionalItems?.value?.forEach { (key, item) -> entries[key] = item }
        }
    }

    return com.sphereon.cbor.Cbor
        .encode(CborMap(entries))
}

private fun encodeDeviceEngagementMessage(value: CborEncodedItem<DeviceEngagement>): ByteArray =
    com.sphereon.cbor.Cbor.encode(
        CborMap(
            mutableMapOf(
                DeviceEngagementMessage.DEVICE_ENGAGEMENT_BYTES to value,
            ),
        ),
    )

private fun encodeDeviceEngagementSecurity(value: DeviceEngagementSecurity): CborArray<CborItem<*>> =
    CborArray(
        mutableListOf(
            CborUInt(value.cipherSuite.toLong()),
            value.eDeviceKeyBytes.value,
        ),
    )

private fun encodeDeviceRetrievalMethod(value: DeviceRetrievalMethod): CborArray<CborItem<*>> =
    CborArray(
        mutableListOf(
            CborUInt(value.type.type.toLong()),
            CborUInt(value.version.version.toLong()),
            encodeDeviceRetrievalOptions(value.retrievalOptions),
        ),
    )

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

private fun encodeServerRetrievalMethods(value: ServerRetrievalMethods): CborMap<StringLabel, CborItem<*>> {
    val entries = mutableMapOf<StringLabel, CborItem<*>>()
    value.Oidc?.let { entries[ServerRetrievalMethods.OIDC] = encodeServerRetrievalInfo(it) }
    value.WebApi?.let { entries[ServerRetrievalMethods.WEB_API] = encodeServerRetrievalInfo(it) }
    return CborMap(entries)
}

private fun encodeServerRetrievalInfo(value: ServerRetrievalInfo): CborArray<CborItem<*>> =
    CborArray(
        mutableListOf(
            CborUInt(value.version.toLong()),
            CborString(value.issuerUrl),
            CborString(value.serverRetrievalToken),
        ),
    )

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

private fun decodeDeviceEngagement(
    structure: CborMap<NumberLabel, CborItem<*>>,
    original: ByteArray,
    coseKeyCodec: CoseKeyCborCodec,
): DeviceEngagement =
    when (val version = DeviceEngagementVersion.Decoder.fromCborItem(DeviceEngagement.VERSION.required(structure)).toString()) {
        "1.0" -> decodeDeviceEngagementV1_0(structure, original, coseKeyCodec)
        "1.1" -> decodeDeviceEngagementV1_1(structure, original, coseKeyCodec)
        else -> throw IllegalArgumentException("Version must be 1.0 or 1.1, got: $version")
    }

private fun decodeDeviceEngagementV1_0(
    structure: CborMap<NumberLabel, CborItem<*>>,
    original: ByteArray,
    coseKeyCodec: CoseKeyCborCodec,
): DeviceEngagement.V1_0 =
    DeviceEngagement.V1_0(
        security = decodeDeviceEngagementSecurity(DeviceEngagement.SECURITY.required(structure), coseKeyCodec),
        deviceRetrievalMethods =
            DeviceEngagement.DEVICE_RETRIEVAL_METHODS
                .optional<CborArray<CborItem<*>>>(structure)
                ?.let(::decodeDeviceRetrievalMethods),
        serverRetrievalMethod =
            DeviceEngagement.SERVER_RETRIEVAL_METHOD
                .optional<CborMap<CborItem<*>, CborItem<*>>>(structure)
                ?.let { decodeServerRetrievalMethods(normalizeStringLabelMap(it, "ServerRetrievalMethods")) },
        protocolInfo = structure.value[DeviceEngagement.PROTOCOL_INFO],
        additionalItems =
            extractAdditionalItems(
                structure,
                setOf(
                    DeviceEngagement.VERSION,
                    DeviceEngagement.SECURITY,
                    DeviceEngagement.DEVICE_RETRIEVAL_METHODS,
                    DeviceEngagement.SERVER_RETRIEVAL_METHOD,
                    DeviceEngagement.PROTOCOL_INFO,
                ),
            ),
        original = original,
    )

private fun decodeDeviceEngagementV1_1(
    structure: CborMap<NumberLabel, CborItem<*>>,
    original: ByteArray,
    coseKeyCodec: CoseKeyCborCodec,
): DeviceEngagement.V1_1 =
    DeviceEngagement.V1_1(
        security = decodeDeviceEngagementSecurity(DeviceEngagement.SECURITY.required(structure), coseKeyCodec),
        deviceRetrievalMethods =
            DeviceEngagement.DEVICE_RETRIEVAL_METHODS
                .optional<CborArray<CborItem<*>>>(structure)
                ?.let(::decodeDeviceRetrievalMethods),
        originInfos =
            DeviceEngagement.ORIGIN_INFOS
                .optional<CborArray<CborMap<CborItem<*>, CborItem<*>>>>(structure)
                ?.value
                ?.map { decodeOriginInfo(normalizeStringLabelMap(it, "OriginInfo")) }
                ?.toTypedArray(),
        protocolInfo = structure.value[DeviceEngagement.PROTOCOL_INFO],
        capabilities =
            DeviceEngagement.CAPABILITIES
                .optional<CborMap<CborItem<*>, CborItem<*>>>(structure)
                ?.let { decodeCapabilities(normalizeNumberLabelMap(it, "Capabilities")) },
        additionalItems =
            extractAdditionalItems(
                structure,
                setOf(
                    DeviceEngagement.VERSION,
                    DeviceEngagement.SECURITY,
                    DeviceEngagement.DEVICE_RETRIEVAL_METHODS,
                    DeviceEngagement.PROTOCOL_INFO,
                    DeviceEngagement.ORIGIN_INFOS,
                    DeviceEngagement.CAPABILITIES,
                ),
            ),
        original = original,
    )

private fun decodeDeviceEngagementSecurity(
    structure: CborArray<CborItem<*>>,
    coseKeyCodec: CoseKeyCborCodec,
): DeviceEngagementSecurity {
    val encodedDeviceKey: CborEncodedItem<CborMap<NumberLabel, CborItem<*>>> = structure.required(1)
    val deviceKey = coseKeyCodec.decode(encodedDeviceKey.value.taggedItem.value).getOrThrow().value as CoseKeyType

    return DeviceEngagementSecurity(
        cipherSuite = structure.required<CborUInt>(0).value.toUInt(),
        eDeviceKeyBytes = encodedDeviceKey.copy(deviceKey),
    )
}

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
    val typeItem = structure.required<CborUInt>(0)
    val type = DeviceRetrievalMethodType.entries.first { it.type == typeItem.value.toUInt() }
    val version = DeviceRetrievalMethodVersion(structure.required<CborUInt>(1).value.toUInt())
    val optionsMap = requireNumberLabelMap(structure.required(2), "DeviceRetrievalOptions")

    return DeviceRetrievalMethod(
        type = type,
        version = version,
        retrievalOptions = decodeDeviceRetrievalOptions(type, optionsMap),
        original = null,
    )
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
                peripheralServerModeUuid = (structure.value[BleOptions.PERIPHERAL_SERVER_MODE_UUID] as? CborByteString)?.value?.let(Uuid::fromByteArray),
                centralClientModeUuid = (structure.value[BleOptions.CENTRAL_CLIENT_MODE_UUID] as? CborByteString)?.value?.let(Uuid::fromByteArray),
                peripheralServerModeDeviceAddress = (structure.value[BleOptions.PERIPHERAL_SERVER_MODE_DEVICE_ADDRESS] as? CborByteString)?.value,
            )
        }

        DeviceRetrievalMethodType.WIFI_WARE -> {
            WifiAwareOptions(
                passPhrase = (structure.value[WifiAwareOptions.PASS_PHRASE] as? CborString)?.value,
                channelInfoOperatingClass = (structure.value[WifiAwareOptions.CHANNEL_INFO_OPERATING_CLASS] as? CborUInt)?.value?.toUInt(),
                channelInfoChannelNumber = (structure.value[WifiAwareOptions.CHANNEL_INFO_CHANNEL_NUMBER] as? CborUInt)?.value?.toUInt(),
                supportedBands = (structure.value[WifiAwareOptions.SUPPORTED_BANDS] as? CborByteString)?.value,
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
                responseUri = (structure.value[Oid4vpOptions.RESPONSE_URI] as? CborString)?.value,
                nonce = (structure.value[Oid4vpOptions.NONCE] as? CborString)?.value,
                requestUri = (structure.value[Oid4vpOptions.REQUEST_URI] as? CborString)?.value,
                presentationDefinitionUri = (structure.value[Oid4vpOptions.PRESENTATION_DEFINITION_URI] as? CborString)?.value,
            )
        }
    }

private fun decodeServerRetrievalMethods(structure: CborMap<StringLabel, CborItem<*>>): ServerRetrievalMethods =
    ServerRetrievalMethods(
        Oidc = structure.value[ServerRetrievalMethods.OIDC]?.let { decodeServerRetrievalInfo(requireArray(it, "ServerRetrievalInfo")) },
        WebApi = structure.value[ServerRetrievalMethods.WEB_API]?.let { decodeServerRetrievalInfo(requireArray(it, "ServerRetrievalInfo")) },
    )

private fun decodeServerRetrievalInfo(structure: CborArray<CborItem<*>>): ServerRetrievalInfo =
    ServerRetrievalInfo(
        version = structure.required<CborUInt>(0).value.toUInt(),
        issuerUrl = requireString(structure.value.getOrNull(1), "ServerRetrievalInfo.issuerUrl").value,
        serverRetrievalToken = requireString(structure.value.getOrNull(2), "ServerRetrievalInfo.serverRetrievalToken").value,
    )

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

private fun decodeCapabilities(structure: CborMap<NumberLabel, CborItem<*>>): Capabilities =
    Capabilities(
        macKeysSupport = (structure.value[Capabilities.MAC_KEYS_SUPPORT] as? CborBool)?.value,
        macKeyCurves = decodeCurves(structure.value[Capabilities.MAC_KEY_CURVES]),
        handoverSessionEstablishmentSupport = (structure.value[Capabilities.HANDOVER_SESSION_ESTABLISHMENT_SUPPORT] as? CborBool)?.value,
        readerAuthAllSupport = (structure.value[Capabilities.READER_AUTH_ALL_SUPPORT] as? CborBool)?.value,
        extendedRequestSupport = (structure.value[Capabilities.EXTENDED_REQUEST_SUPPORT] as? CborBool)?.value,
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
): CborMap<StringLabel, CborItem<*>> {
    val normalizedEntries =
        item.value.entries
            .associate { (key, value) ->
                StringLabel.fromCborItem(key) to value
            }.toMutableMap()

    return CborMap(normalizedEntries, item.indefiniteLength)
}

private fun requireBool(
    item: CborItem<*>?,
    fieldName: String,
): CborBool =
    item as? CborBool
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR boolean")

@Suppress("UNCHECKED_CAST")
private fun requireArray(
    item: CborItem<*>?,
    fieldName: String,
): CborArray<CborItem<*>> =
    item as? CborArray<CborItem<*>>
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR array")

private fun requireString(
    item: CborItem<*>?,
    fieldName: String,
): CborString =
    item as? CborString
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR string")

private fun requireUInt(
    item: CborItem<*>?,
    fieldName: String,
): CborUInt =
    item as? CborUInt
        ?: throw IllegalArgumentException("$fieldName must be encoded as a CBOR uint")

private fun requireIntValue(
    item: CborItem<*>?,
    fieldName: String,
): Int =
    when (item) {
        is CborInt -> item.value.toInt()
        is CborUInt -> item.value.toInt()
        else -> throw IllegalArgumentException("$fieldName must be encoded as a CBOR integer")
    }

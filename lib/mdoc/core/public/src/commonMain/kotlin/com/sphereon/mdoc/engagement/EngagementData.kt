/*
 * © 2026 Sphereon International B.V.
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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.mdoc.engagement

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.logging.IMdocDebugLogger
import com.sphereon.mdoc.transfer.OriginInfo
import com.sphereon.mdoc.transfer.OriginInfoCategory
import com.sphereon.mdoc.transfer.OriginInfoDetails
import com.sphereon.mdoc.transfer.OriginInfoType
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.Capabilities
import com.sphereon.mdoc.transfer.device.DataRetrievalTransmissionType
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodec
import com.sphereon.util.stringify
import io.ktor.http.Url
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEngagementData", exact = true)
class EngagementData private constructor(
    private val ephemeralKey: ResolvedKeyInfoType<CoseKeyType>,
    private val retrievalMethods: Set<DeviceRetrievalMethod>,
    private val curve: Curve = Curve.P_256,
    private val role: MdocRole = MdocRole.MDOC,
    private var engagementMethods: Set<MdocEngagementMethod>,
    private var deviceEngagement: CborEncodedItem<DeviceEngagement>? = null,
    private var readerEngagement: ReaderEngagement? = null, // For reverse engagement
    private var blePeripheralServerModeUUID: Uuid? = null,
    private var bleCentralClientModeUUID: Uuid? = null,
    private var debugLogger: IMdocDebugLogger? = null,
    private val coseKeyCborCodec: CoseKeyCborCodec? = null,
    private val deviceEngagementCborCodec: DeviceEngagementCborCodec? = null,
    private val readerEngagementCborCodec: ReaderEngagementCborCodec? = null,
) {
    private var engagementUri: String? = null // Store engagement URI for reverse engagement (mdoc: or mdoc://)

    private fun requireCoseKeyCborCodec(): CoseKeyCborCodec = requireNotNull(coseKeyCborCodec) { "CoseKeyCborCodec must be provided to EngagementData for key encoding" }

    private fun requireDeviceEngagementCborCodec(): DeviceEngagementCborCodec =
        requireNotNull(deviceEngagementCborCodec) { "DeviceEngagementCborCodec must be provided to EngagementData for device engagement encoding" }

    private fun requireReaderEngagementCborCodec(): ReaderEngagementCborCodec =
        requireNotNull(readerEngagementCborCodec) { "ReaderEngagementCborCodec must be provided to EngagementData for reader engagement encoding/decoding" }

    fun isReaderEngagement() = readerEngagement != null

    fun isDeviceEngagement() = !isReaderEngagement()

    fun withDeviceEngagement(deviceEngagement: CborEncodedItem<DeviceEngagement>) =
        apply {
            this.deviceEngagement = deviceEngagement
        }

    fun getEphemeralKey() = ephemeralKey

    fun getRetrievalMethods() = retrievalMethods

    fun getCurve() = curve

    fun getRole() = role

    fun getUuid(required: Boolean = true): Uuid? {
        if (isRestApiRetrievalSupported() || isRestApiEngagementSupported()) {
            return null
        }
        val centralUuid = getBleCentralClientModeUuid()
        val peripheralUuid = getBlePeripheralServerModeUuid()

        // Return whichever exists, preferring central client mode UUID
        // Per ISO/IEC 18013-5, central and peripheral UUIDs can be different
        // (see Table 2, keys 10 and 11 in BleOptions structure)
        val uuid = centralUuid ?: peripheralUuid

        require(uuid != null || !required) { "No UUID found for retrieval method, but required was true" }

        return uuid
    }

    fun isBleRetrievalSupported() = retrievalMethods.any { it.retrievalOptions is BleOptions }

    fun isRestApiRetrievalSupported() = retrievalMethods.any { it.retrievalOptions is RestApiOptions }

    fun isNfcRetrievalSupported() = false // Not supported yet

    fun isWifiAwareRetrievalSupported() = false // Not supported yet

    fun retrievalTransmissionTypesSupported(): Set<DataRetrievalTransmissionType> =
        setOfNotNull(
            DataRetrievalTransmissionType.BLE.takeIf { isBleRetrievalSupported() },
            DataRetrievalTransmissionType.NFC.takeIf { isNfcRetrievalSupported() },
            DataRetrievalTransmissionType.WIFI_AWARE.takeIf { isWifiAwareRetrievalSupported() },
            DataRetrievalTransmissionType.WEBSITE.takeIf { isRestApiRetrievalSupported() },
        )

    fun getEngagementMethods() = engagementMethods

    fun isNfcEngagementSupported() = engagementMethods.any { it is NfcEngagementMethod }

    fun isQrEngagementSupported() = engagementMethods.any { it is QREngagementMethod }

    fun isRestApiEngagementSupported() = engagementMethods.any { it is ReaderEngagementMethod || it is Oid4vpEngagementMethod }

    fun getReaderEngagement(): ReaderEngagement? {
        if (readerEngagement === null) {
            val uri = engagementUri ?: error("Engagement URI is not set. Cannot generate ReaderEngagement")
            this.readerEngagement = requireReaderEngagementCborCodec().decodeUri(uri).getOrThrow().value
        }
        return this.readerEngagement
    }

    fun getDeviceEngagement(): CborEncodedItem<DeviceEngagement> {
        if (deviceEngagement === null) {
            this.deviceEngagement = requireDeviceEngagementCborCodec().encodeItem(createDeviceEngagement()).getOrThrow()
        }
        return this.deviceEngagement!!
    }

    fun getBlePeripheralServerModeUuid(): Uuid? {
        if (blePeripheralServerModeUUID == null &&
            retrievalMethods.any {
                (it.retrievalOptions as? BleOptions)?.peripheralServerMode == true
            }
        ) {
            // Find all BLE methods with peripheral server mode
            val peripheralMethods =
                retrievalMethods
                    .filter { (it.retrievalOptions as? BleOptions)?.peripheralServerMode == true }
                    .map { (it.retrievalOptions as BleOptions).peripheralServerModeUuid }
                    .filterNotNull()
                    .distinct()

            require(peripheralMethods.size == 1) {
                "Multiple BLE peripheral server methods with different UUIDs found. " +
                    "All peripheral server methods must use the same UUID."
            }

            this.blePeripheralServerModeUUID = peripheralMethods.first()
        }
        return blePeripheralServerModeUUID
    }

    fun getBleCentralClientModeUuid(): Uuid? {
        if (bleCentralClientModeUUID == null &&
            retrievalMethods.any {
                (it.retrievalOptions as? BleOptions)?.centralClientMode == true
            }
        ) {
            // Find all BLE methods with central client mode
            val centralMethods =
                retrievalMethods
                    .filter { (it.retrievalOptions as? BleOptions)?.centralClientMode == true }
                    .map { (it.retrievalOptions as BleOptions).centralClientModeUuid }
                    .filterNotNull()
                    .distinct()

            require(centralMethods.size == 1) {
                "Multiple BLE central client methods with different UUIDs found. " +
                    "All central client methods must use the same UUID."
            }

            this.bleCentralClientModeUUID = centralMethods.first()
        }
        return bleCentralClientModeUUID
    }

    fun getBleRetrievalMethod(): DeviceRetrievalMethod? = retrievalMethods.firstOrNull { method -> method.retrievalOptions is BleOptions }

    fun getBleRetrievalMethodOptions(): BleOptions? = getBleRetrievalMethod()?.retrievalOptions as? BleOptions

    private fun createDeviceEngagement(): DeviceEngagement {
        check(role == MdocRole.MDOC) { "Only holder role is supported to create a device engagement" }

        // Check that we have at least one retrieval method
        check(retrievalMethods.isNotEmpty()) { "At least one device retrieval method is required" }

        // Get all BLE methods (there may be multiple after splitting)
        val bleMethods = retrievalMethods.filter { it.retrievalOptions is BleOptions }

        // Validate each BLE method
        bleMethods.forEach { method ->
            val bleOptions = method.retrievalOptions as BleOptions

            // After splitting, each method should have exactly ONE mode enabled
            val bothEnabled = bleOptions.peripheralServerMode && bleOptions.centralClientMode
            val noneEnabled = !bleOptions.peripheralServerMode && !bleOptions.centralClientMode

            check(!bothEnabled) {
                "INTERNAL ERROR: BLE retrieval method with both modes should have been " +
                    "split by Builder.addRetrievalMethod(). This indicates a bug in the builder."
            }

            check(!noneEnabled) {
                "Each BLE retrieval method must have exactly one mode enabled " +
                    "(either peripheral server or central client, not neither)"
            }

            // Validate UUID is present for the enabled mode
            if (bleOptions.peripheralServerMode) {
                checkNotNull(bleOptions.peripheralServerModeUuid) {
                    "Peripheral server mode is enabled but UUID is not present"
                }
            }
            if (bleOptions.centralClientMode) {
                checkNotNull(bleOptions.centralClientModeUuid) {
                    "Central client mode is enabled but UUID is not present"
                }
            }
        }
        val website = retrievalMethods.firstOrNull { it.type == DeviceRetrievalMethodType.WEBSITE }
        if (website != null) {
            requireNotNull(readerEngagement) { "Reader engagement must be present when performing website device retrieval" }
            val restApiOptions =
                readerEngagement!!.deviceRetrievalMethods?.firstOrNull { it.type == DeviceRetrievalMethodType.WEBSITE }?.retrievalOptions as? RestApiOptions
                    ?: throw IllegalArgumentException("Device retrieval website")
            // FIXME. We should set the domain from a referer
            val domain = Url(restApiOptions.uri).host

            val coseKey: CoseKeyType = CoseKey.fromDTO(ephemeralKey.key).toPublicKey()
            val encodedCoseKeyBytes = requireCoseKeyCborCodec().encode(CoseKey.fromDTO(coseKey)).getOrThrow()

            return DeviceEngagement.V1_1(
                security =
                    DeviceEngagementSecurity(
                        cipherSuite = curve.cose.value.toUInt(),
                        eDeviceKeyBytes = CborEncodedItem(encodedCoseKeyBytes, coseKey),
                    ),
                //                deviceRetrievalMethods = retrievalMethods.toTypedArray(),
                originInfos = arrayOf(OriginInfo(cat = OriginInfoCategory(1u), type = OriginInfoType(1u), details = OriginInfoDetails(mapOf("domain" to domain)), null)),
                capabilities = Capabilities(macKeysSupport = false, macKeyCurves = null),
                original = null,
            )
        }

        // COMBINE BLE methods before creating engagement
        // Per ISO 18013-5 section 9.3, both BLE modes should be represented as a single
        // BLE retrieval method with both mode flags set in BleOptions (keys 0 and 1)
        // Use the existing combine logic from BleConnectionMethod
        val combinedRetrievalMethods = combineBleMethods(retrievalMethods)

        // Create engagement with combined retrieval methods
        val coseKey: CoseKeyType = CoseKey.fromDTO(ephemeralKey.key).toPublicKey()
        val encodedCoseKeyBytes = requireCoseKeyCborCodec().encode(CoseKey.fromDTO(coseKey)).getOrThrow()
        val encodedDeviceKey = CborEncodedItem(encodedCoseKeyBytes, coseKey)
        val eDeviceKeyBytes = encodedDeviceKey.value.taggedItem.value
        debugLogger?.logEDeviceKey("Creating EDeviceKey for DeviceEngagement", eDeviceKeyBytes)

        return DeviceEngagement
            .V1_0(
                security =
                    DeviceEngagementSecurity(
                        cipherSuite = curve.cose.value.toUInt(),
                        eDeviceKeyBytes = encodedDeviceKey,
                    ),
                deviceRetrievalMethods = combinedRetrievalMethods.toTypedArray(),
                original = null,
            ).also { engagement ->
                val engagementBytes =
                    requireDeviceEngagementCborCodec()
                        .encodeItem(engagement)
                        .getOrThrow()
                        .value.taggedItem.value
                val combinedBleMethods = combinedRetrievalMethods.filter { it.retrievalOptions is BleOptions }
                debugLogger?.logDeviceEngagement(
                    "Created DeviceEngagement with ${combinedBleMethods.size} BLE retrieval method(s)",
                    engagementBytes,
                )
            }
    }

    /**
     * Combines multiple BLE retrieval methods into a single one.
     * This mirrors the logic in BleConnectionMethod.combine() but works directly with DeviceRetrievalMethod.
     */
    private fun combineBleMethods(methods: Set<DeviceRetrievalMethod>): Set<DeviceRetrievalMethod> {
        if (methods.size <= 1) {
            return methods
        }

        val bleMethods = methods.filter { it.type == DeviceRetrievalMethodType.BLE }
        if (bleMethods.size <= 1) {
            return methods
        }

        val nonBleMethods = methods.filter { it.type != DeviceRetrievalMethodType.BLE }

        // Merge all BLE options (same logic as BleConnectionMethod.combine())
        var supportsCentralClientMode = false
        var supportsPeripheralServerMode = false
        var centralClientUuid: Uuid? = null
        var peripheralServerUuid: Uuid? = null
        var deviceAddress: ByteArray? = null

        bleMethods.forEach { method ->
            val bleOptions = method.retrievalOptions as BleOptions
            if (bleOptions.centralClientMode) {
                supportsCentralClientMode = true
                centralClientUuid = bleOptions.centralClientModeUuid
            }
            if (bleOptions.peripheralServerMode) {
                supportsPeripheralServerMode = true
                peripheralServerUuid = bleOptions.peripheralServerModeUuid
                if (deviceAddress == null) {
                    deviceAddress = bleOptions.peripheralServerModeDeviceAddress
                }
            }
        }

        val combinedBle =
            DeviceRetrievalMethod(
                type = DeviceRetrievalMethodType.BLE,
                retrievalOptions =
                    BleOptions(
                        peripheralServerMode = supportsPeripheralServerMode,
                        centralClientMode = supportsCentralClientMode,
                        peripheralServerModeUuid =
                            if (supportsPeripheralServerMode) {
                                peripheralServerUuid
                            } else {
                                null
                            },
                        centralClientModeUuid =
                            if (supportsCentralClientMode) {
                                centralClientUuid
                            } else {
                                null
                            },
                        peripheralServerModeDeviceAddress = deviceAddress,
                    ),
            )

        return (nonBleMethods + combinedBle).toSet()
    }

    /**
     * Generate engagement URI for QR code or app-to-app invocation.
     *
     * The URI format depends on the engagement type:
     * - **Device Engagement (ISO 18013-5)**: Uses `mdoc:` (no slashes) for holder QR codes
     * - **Reader Engagement**:
     *   - `mdoc:` for classic reverse engagement (ISO 18013-5 BLE/NFC)
     *   - `mdoc://` for website retrieval (ISO 18013-7 Annex A)
     *
     * @param scheme Optional custom scheme. If not provided, uses the correct scheme for the engagement type:
     *               - `mdoc:` for Device Engagement (holder QR codes)
     *               - `mdoc:` for classic reverse engagement (reader QR codes / app-to-app)
     *               - `mdoc://` for website retrieval (reader QR codes / app-to-app)
     * @return The engagement URI string
     */
    fun generateEngagementUri(scheme: String? = null): String {
        if (engagementUri == null) {
            // Check if this is a ReaderEngagement (reverse engagement)
            if (readerEngagement != null) {
                // For reader engagements, use a scheme based on the retrieval method
                // Default to mdoc:// for website retrieval and mdoc: for classic reverse engagement
                val actualScheme =
                    scheme ?: if (readerEngagement!!.hasWebsiteRetrievalMethod) {
                        "mdoc://"
                    } else {
                        "mdoc:"
                    }
                val readerEngagementCborCodec = requireReaderEngagementCborCodec()
                val generatedUri = readerEngagementCborCodec.encodeUri(readerEngagement!!, actualScheme).getOrThrow()
                engagementUri = generatedUri
                if (readerEngagement!!.original == null) {
                    val encodedReaderEngagement = readerEngagementCborCodec.encode(readerEngagement!!).getOrThrow()
                    readerEngagement = readerEngagement!!.copyWithOriginal(encodedReaderEngagement)
                }
                return engagementUri!!
            }

            // Device Engagement (holder QR code)
            val deviceEngagement = getDeviceEngagement()
            val decoded = deviceEngagement.data()

            require(isQrEngagementSupported()) { "QR Engagement is not enabled. Cannot generate QR code" }
            check(retrievalMethods.isNotEmpty()) { "Device retrieval methods are not present. Cannot generate QR code" }

            // Get all BLE methods
            val bleMethods =
                (decoded.deviceRetrievalMethods as Array<out DeviceRetrievalMethod>)
                    .filter { it.type == DeviceRetrievalMethodType.BLE }

            // Validate each BLE method
            bleMethods.forEach { ble ->
                check(ble.retrievalOptions is BleOptions) { "BLE retrieval options are not present. Cannot generate QR code" }
                val deviceRetrievalOptions = ble.retrievalOptions as BleOptions

                // Validate at least ONE mode is enabled (both modes can be enabled per ISO 18013-5)
                val noneEnabled = !deviceRetrievalOptions.peripheralServerMode && !deviceRetrievalOptions.centralClientMode

                check(!noneEnabled) {
                    "Each BLE method must have at least one mode enabled. Cannot generate QR code"
                }

                // Validate UUID for each enabled mode
                if (deviceRetrievalOptions.peripheralServerMode) {
                    checkNotNull(deviceRetrievalOptions.peripheralServerModeUuid) {
                        "Peripheral server mode is enabled but UUID is not present. Cannot generate QR code"
                    }
                }
                if (deviceRetrievalOptions.centralClientMode) {
                    checkNotNull(deviceRetrievalOptions.centralClientModeUuid) {
                        "Central client mode is enabled but UUID is not present. Cannot generate QR code"
                    }
                }
            }

            // Default to mdoc: (no slashes) for Device Engagement per ISO 18013-5
            val actualScheme = scheme ?: "mdoc:"
            val encodedEngagementBytes = deviceEngagement.value.taggedItem.value
            debugLogger?.logDeviceEngagement(
                "DeviceEngagement bytes for QR code (${bleMethods.size} BLE methods)",
                encodedEngagementBytes,
            )
            val qrData = "$actualScheme${encodedEngagementBytes.encodeToBase64Url()}"
            debugLogger?.logQrCodeData("Generated QR code URI", qrData)
            this@EngagementData.engagementUri = qrData
        }
        return engagementUri!!
    }

    override fun toString(): String =
        "MdocEngagementData(ephemeralKey=$ephemeralKey, retrievalMethods=${stringify(retrievalMethods)}, curve=$curve, role=$role, engagementMethods=${
            stringify(
                engagementMethods,
            )
        }, deviceEngagement=$deviceEngagement, blePeripheralServerModeUUID=$blePeripheralServerModeUUID, bleCentralClientModeUUID=$bleCentralClientModeUUID, qrCodeData=$engagementUri)"

    companion object {
        /**
         * Disambiguates BLE retrieval methods that have both modes enabled.
         * This mirrors the logic in BleConnectionMethod.disambiguate() but works with DeviceRetrievalMethod.
         */
        private fun disambiguateBleMethods(
            methods: List<DeviceRetrievalMethod>,
            role: MdocRole,
        ): List<DeviceRetrievalMethod> {
            val result = mutableListOf<DeviceRetrievalMethod>()

            methods.forEach { method ->
                if (method.type == DeviceRetrievalMethodType.BLE && method.retrievalOptions is BleOptions) {
                    val bleOptions = method.retrievalOptions as BleOptions

                    // If both modes are enabled, split into 2 separate methods
                    if (bleOptions.centralClientMode && bleOptions.peripheralServerMode) {
                        // Central client mode method
                        result.add(
                            DeviceRetrievalMethod(
                                type = DeviceRetrievalMethodType.BLE,
                                retrievalOptions =
                                    bleOptions.copy(
                                        peripheralServerMode = false,
                                        peripheralServerModeUuid = null,
                                    ),
                            ),
                        )

                        // Peripheral server mode method
                        result.add(
                            DeviceRetrievalMethod(
                                type = DeviceRetrievalMethodType.BLE,
                                retrievalOptions =
                                    bleOptions.copy(
                                        centralClientMode = false,
                                        centralClientModeUuid = null,
                                    ),
                            ),
                        )
                    } else {
                        // Single mode - keep as-is
                        result.add(method)
                    }
                } else {
                    // Non-BLE method - keep as-is
                    result.add(method)
                }
            }

            return result
        }

        fun holderBuilder(
            coseKeyCborCodec: CoseKeyCborCodec? = null,
            deviceEngagementCborCodec: DeviceEngagementCborCodec? = null,
            readerEngagementCborCodec: ReaderEngagementCborCodec? = null,
        ) = HolderBuilder(
            coseKeyCborCodec = coseKeyCborCodec,
            deviceEngagementCborCodec = deviceEngagementCborCodec,
            readerEngagementCborCodec = readerEngagementCborCodec,
        )

        /**
         * Create a builder for reader engagement (reverse engagement).
         * Sets the role to MDOC_READER instead of MDOC.
         */
        fun readerBuilder(
            coseKeyCborCodec: CoseKeyCborCodec? = null,
            deviceEngagementCborCodec: DeviceEngagementCborCodec? = null,
            readerEngagementCborCodec: ReaderEngagementCborCodec? = null,
        ) = HolderBuilder(
            role = MdocRole.MDOC_READER,
            coseKeyCborCodec = coseKeyCborCodec,
            deviceEngagementCborCodec = deviceEngagementCborCodec,
            readerEngagementCborCodec = readerEngagementCborCodec,
        )

        fun holderFromDeviceKey(
            ephemeralDeviceKey: ResolvedKeyInfoType<*>,
            retrievalMethods: Set<DeviceRetrievalMethod>,
        ) = holderBuilder()
            .withEphemeralKey(ephemeralDeviceKey = ephemeralDeviceKey)
            .withRetrievalMethods(retrievalMethods)
            .build()

        fun verifierFromDeviceEngagement(
            encodedEngagement: CborEncodedItem<DeviceEngagement>,
            engagementMethods: Set<MdocEngagementMethod> = setOf(QREngagementMethod()),
        ): EngagementData {
            val retrievalMethods = mutableSetOf<DeviceRetrievalMethod>()
            var blePeripheralServerModeUUID: Uuid? = null
            var bleCentralClientModeUUID: Uuid? = null
            val engagement = encodedEngagement.data()

            // DISAMBIGUATE BLE methods using the existing logic
            // Per ISO 18013-5, a single BLE retrieval method can have both modes enabled.
            // When the reader receives this, it needs to split it into 2 separate retrieval methods
            // for internal handling (one for central client mode, one for peripheral server mode).
            val allMethods = engagement.deviceRetrievalMethods?.toList() ?: emptyList()
            val disambiguatedMethods = disambiguateBleMethods(allMethods, MdocRole.MDOC_READER)

            disambiguatedMethods.forEach { method ->
                retrievalMethods.add(method)

                if (method.type == DeviceRetrievalMethodType.BLE && method.retrievalOptions is BleOptions) {
                    val bleOptions = method.retrievalOptions as BleOptions
                    // Track UUIDs (swapped for reader role - reader does opposite of holder)
                    if (bleOptions.peripheralServerMode) {
                        // Holder advertises as peripheral, so reader acts as central
                        bleCentralClientModeUUID = bleOptions.peripheralServerModeUuid
                    }
                    if (bleOptions.centralClientMode) {
                        // Holder scans as central, so reader advertises as peripheral
                        blePeripheralServerModeUUID = bleOptions.centralClientModeUuid
                    }
                }
            }

            require(retrievalMethods.isNotEmpty()) { "Device engagement does not contain a single retrieval method" }

            return EngagementData(
                deviceEngagement = encodedEngagement,
                ephemeralKey = ResolvedKeyInfo(key = engagement.security.eDeviceKeyBytes.data()),
                curve = Curve.fromCose(CoseCurve.fromValue(engagement.security.cipherSuite.toInt())),
                role = MdocRole.MDOC_READER,
                retrievalMethods = retrievalMethods,
                bleCentralClientModeUUID = bleCentralClientModeUUID,
                blePeripheralServerModeUUID = blePeripheralServerModeUUID,
                engagementMethods = engagementMethods,
                readerEngagement = null,
            )
        }
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("HolderBuilder", exact = true)
    class HolderBuilder(
        private val role: MdocRole = MdocRole.MDOC, // Allow role to be overridden for reader engagements
        private var coseKeyCborCodec: CoseKeyCborCodec? = null,
        private var deviceEngagementCborCodec: DeviceEngagementCborCodec? = null,
        private var readerEngagementCborCodec: ReaderEngagementCborCodec? = null,
    ) {
        private var ephemeralDeviceKey: ResolvedKeyInfoType<*>? = null
        private var retrievalMethods: Set<DeviceRetrievalMethod> = mutableSetOf()
        private var curve: Curve? = ephemeralDeviceKey?.signatureAlgorithm?.curve ?: ephemeralDeviceKey?.key?.getSignatureAlgorithm()?.curve

        private val engagementMethods = mutableSetOf<MdocEngagementMethod>()
        private var readerEngagement: ReaderEngagement? = null // For reverse engagement
        private var engagementUri: String? = null // Store the mdoc: or mdoc:// URI for reverse engagement
        private var debugLogger: IMdocDebugLogger? = null

        fun withEngagementMethods(vararg engagementMethods: MdocEngagementMethod) =
            apply {
                this.engagementMethods.addAll(engagementMethods)
                // Extract ReaderEngagement from ReaderEngagementMethod for TO_APP engagements
                engagementMethods.forEach { method ->
                    if (method is ReaderEngagementMethod) {
                        this.readerEngagement = method.readerEngagement
                    }
                }
            }

        fun withQrEngagement() = apply { this.engagementMethods.add(QREngagementMethod()) }

        fun withNfcEngagement() =
            apply {
                this.engagementMethods.add(NfcEngagementMethod())
                throw IllegalArgumentException("NFC not supported yet. QR only")
            }

        fun withReaderEngagement(
            readerEngagement: ReaderEngagement,
            engagementUri: String? = null,
        ) = apply {
            val engagementMethod =
                ReaderEngagementMethod(
                    readerEngagement = readerEngagement,
                    readerEngagementCborCodec = readerEngagementCborCodec,
                )
            this.engagementMethods.add(engagementMethod)
            this.readerEngagement = readerEngagement // Store the ReaderEngagement directly
            if (engagementUri != null) {
                this.engagementUri = engagementUri
            }
            engagementMethod.readerEngagement.deviceRetrievalMethods?.map { addRetrievalMethod(it) }
        }

        fun withReaderEngagementUri(dataUri: String) =
            apply {
                val readerEngagement =
                    requireNotNull(readerEngagementCborCodec) {
                        "ReaderEngagementCborCodec must be provided before using withReaderEngagementUri(...)"
                    }.decodeUri(dataUri).getOrThrow().value
                withReaderEngagement(readerEngagement, dataUri)
            }

        suspend fun withEphemeralDeviceKeyFromKeyManager(
            keyManager: KeyManagerService,
            signatureAlgorithm: SignatureAlgorithm,
            kmsProviderId: String? = null,
        ) = apply {
            this.ephemeralDeviceKey =
                keyManager
                    .generateKeyAsync(providerId = kmsProviderId, alg = signatureAlgorithm)
                    .toManagedKeyInfo<CoseKeyType>(visibility = KeyVisibility.PRIVATE, keyEncoding = KeyEncoding.COSE)
        }

        fun withEphemeralKey(
            ephemeralDeviceKey: ResolvedKeyInfoType<*>,
            curve: Curve? =
                ephemeralDeviceKey.signatureAlgorithm?.curve
                    ?: ephemeralDeviceKey.key.getSignatureAlgorithm()?.curve,
        ) = apply {
            this.ephemeralDeviceKey = ephemeralDeviceKey
            this.curve = curve
        }

        fun withRetrievalMethods(retrievalMethods: Set<DeviceRetrievalMethod>) = apply { retrievalMethods.forEach { addRetrievalMethod(it) } }

        fun withDebugLogger(debugLogger: IMdocDebugLogger?) = apply { this.debugLogger = debugLogger }

        fun withCborCodecs(
            coseKeyCborCodec: CoseKeyCborCodec,
            deviceEngagementCborCodec: DeviceEngagementCborCodec? = null,
            readerEngagementCborCodec: ReaderEngagementCborCodec? = null,
        ) = apply {
            this.coseKeyCborCodec = coseKeyCborCodec
            deviceEngagementCborCodec?.let { this.deviceEngagementCborCodec = it }
            readerEngagementCborCodec?.let { this.readerEngagementCborCodec = it }
        }

        fun addRetrievalMethod(retrievalMethod: DeviceRetrievalMethod) =
            apply {
                var options = retrievalMethod.retrievalOptions
                if (options is BleOptions) {
                    // Ensure UUIDs are set
                    if (options.centralClientMode && options.centralClientModeUuid == null) {
                        options = options.copy(centralClientModeUuid = options.peripheralServerModeUuid ?: Uuid.random())
                    }
                    if (options.peripheralServerMode && options.peripheralServerModeUuid == null) {
                        options = options.copy(peripheralServerModeUuid = options.centralClientModeUuid ?: Uuid.random())
                    }

                    // NEW: If both modes are enabled, split into TWO retrieval methods
                    if (options.centralClientMode && options.peripheralServerMode) {
                        // Add central client method
                        val centralMethod =
                            DeviceRetrievalMethod(
                                type = DeviceRetrievalMethodType.BLE,
                                retrievalOptions =
                                    options.copy(
                                        peripheralServerMode = false,
                                        peripheralServerModeUuid = null,
                                    ),
                            )
                        this.retrievalMethods += centralMethod

                        // Add peripheral server method
                        val peripheralMethod =
                            DeviceRetrievalMethod(
                                type = DeviceRetrievalMethodType.BLE,
                                retrievalOptions =
                                    options.copy(
                                        centralClientMode = false,
                                        centralClientModeUuid = null,
                                    ),
                            )
                        this.retrievalMethods += peripheralMethod

                        return@apply // Don't add the original combined method
                    }
                }
                this.retrievalMethods += retrievalMethod.copy(retrievalOptions = options)
            }

        fun build(): EngagementData {
            require(ephemeralDeviceKey !== null) { "An ephemeral device key needs to be provided" }
            require(retrievalMethods.isNotEmpty()) { "At least one device retrieval method needs to be provided" }
            require(engagementMethods.isNotEmpty()) { "At least one engagement technology needs to be provided (QR and/or NFC)" }
            val ephemeralCborKey = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(ephemeralDeviceKey!!)
            val data =
                EngagementData(
                    ephemeralKey = ephemeralCborKey,
                    retrievalMethods = retrievalMethods,
                    curve ?: Curve.P_256,
                    role = role,
                    engagementMethods = engagementMethods.toSet(),
                    debugLogger = debugLogger,
                    coseKeyCborCodec = coseKeyCborCodec,
                    deviceEngagementCborCodec = deviceEngagementCborCodec,
                    readerEngagementCborCodec = readerEngagementCborCodec,
                )
            // Set the readerEngagement and URI after construction if provided (for reverse engagement)
            if (readerEngagement != null) {
                data.readerEngagement = readerEngagement
            }
            if (engagementUri != null) {
                data.engagementUri = engagementUri
            }
            return data
        }
    }
}

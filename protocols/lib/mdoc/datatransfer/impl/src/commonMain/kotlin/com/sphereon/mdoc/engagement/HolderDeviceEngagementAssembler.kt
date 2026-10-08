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
 */

package com.sphereon.mdoc.engagement

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.logging.IMdocDebugLogger
import com.sphereon.mdoc.transfer.OriginInfo
import com.sphereon.mdoc.transfer.OriginInfoCategory
import com.sphereon.mdoc.transfer.OriginInfoDetails
import com.sphereon.mdoc.transfer.OriginInfoType
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.Capabilities
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.RestApiOptions
import kotlin.uuid.Uuid

internal data class PreparedHolderDeviceEngagement(
    val engagement: DeviceEngagement,
    val bleMethodCount: Int,
)

internal class HolderDeviceEngagementAssembler(
    private val coseKeyCborCodec: CoseKeyCborCodec,
) {
    fun create(
        data: EngagementData,
        debugLogger: IMdocDebugLogger? = null,
    ): PreparedHolderDeviceEngagement {
        check(data.getRole() == MdocRole.MDOC) { "Only holder role is supported to create a device engagement" }

        val retrievalMethods = data.getRetrievalMethods()
        check(retrievalMethods.isNotEmpty()) { "At least one device retrieval method is required" }

        validateBleMethods(retrievalMethods)

        val publicDeviceKey = CoseKey.fromDTO(data.getEphemeralKey().key).toPublicKey()
        val encodedDeviceKeyBytes = coseKeyCborCodec.encode(publicDeviceKey).getOrThrow()
        val encodedDeviceKey: CborEncodedItem<CoseKeyType> =
            CborEncodedItem(
                encodedDeviceKeyBytes,
                publicDeviceKey.copy(original = encodedDeviceKeyBytes),
            )
        debugLogger?.logEDeviceKey("Creating EDeviceKey for DeviceEngagement", encodedDeviceKeyBytes)

        val websiteRetrieval = retrievalMethods.firstOrNull { it.type == DeviceRetrievalMethodType.WEBSITE }
        if (websiteRetrieval != null) {
            val readerEngagement =
                requireNotNull(data.getReaderEngagement()) {
                    "Reader engagement must be present when performing website device retrieval"
                }
            val restApiOptions =
                readerEngagement.deviceRetrievalMethods
                    ?.firstOrNull { it.type == DeviceRetrievalMethodType.WEBSITE }
                    ?.retrievalOptions as? RestApiOptions
                    ?: throw IllegalArgumentException("Device retrieval website")
            return PreparedHolderDeviceEngagement(
                engagement =
                    DeviceEngagement.V1_1(
                        security =
                            DeviceEngagementSecurity(
                                cipherSuite =
                                    data
                                        .getCurve()
                                        .cose.value
                                        .toUInt(),
                                eDeviceKeyBytes = encodedDeviceKey,
                            ),
                        originInfos =
                            arrayOf(
                                OriginInfo(
                                    cat = OriginInfoCategory(1u),
                                    type = OriginInfoType(1u),
                                    details = OriginInfoDetails(
                                        mapOf(
                                            OriginInfoDetails.DOMAIN to
                                                (data.getTrustedOriginDomain() ?: ""),
                                        ),
                                    ),
                                    original = null,
                                ),
                            ),
                        capabilities = Capabilities(macKeysSupport = false, macKeyCurves = null),
                        original = null,
                    ),
                bleMethodCount = 0,
            )
        }

        val combinedRetrievalMethods = combineBleMethods(retrievalMethods)
        val bleMethodCount = combinedRetrievalMethods.count { it.retrievalOptions is BleOptions }

        return PreparedHolderDeviceEngagement(
            engagement =
                DeviceEngagement.V1_0(
                    security =
                        DeviceEngagementSecurity(
                            cipherSuite =
                                data
                                    .getCurve()
                                    .cose.value
                                    .toUInt(),
                            eDeviceKeyBytes = encodedDeviceKey,
                        ),
                    deviceRetrievalMethods = combinedRetrievalMethods.toTypedArray(),
                    original = null,
                ),
            bleMethodCount = bleMethodCount,
        )
    }

    private fun validateBleMethods(retrievalMethods: Set<DeviceRetrievalMethod>) {
        retrievalMethods
            .filter { it.retrievalOptions is BleOptions }
            .forEach { method ->
                val bleOptions = method.retrievalOptions as BleOptions
                val bothEnabled = bleOptions.peripheralServerMode && bleOptions.centralClientMode
                val noneEnabled = !bleOptions.peripheralServerMode && !bleOptions.centralClientMode

                check(!bothEnabled) {
                    "INTERNAL ERROR: BLE retrieval method with both modes should have been split by Builder.addRetrievalMethod(). This indicates a bug in the builder."
                }

                check(!noneEnabled) {
                    "Each BLE retrieval method must have exactly one mode enabled (either peripheral server or central client, not neither)"
                }

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
    }

    private fun combineBleMethods(methods: Set<DeviceRetrievalMethod>): Set<DeviceRetrievalMethod> {
        if (methods.size <= 1) {
            return methods
        }

        val bleMethods = methods.filter { it.type == DeviceRetrievalMethodType.BLE }
        if (bleMethods.size <= 1) {
            return methods
        }

        val nonBleMethods = methods.filter { it.type != DeviceRetrievalMethodType.BLE }

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
}

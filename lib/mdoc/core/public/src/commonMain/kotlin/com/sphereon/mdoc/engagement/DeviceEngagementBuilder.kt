/*
 * © 2025 Sphereon International B.V.
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

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.longToNumberLabel
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.transfer.OriginInfo
import com.sphereon.mdoc.transfer.OriginInfoCategory
import com.sphereon.mdoc.transfer.OriginInfoDetails
import com.sphereon.mdoc.transfer.OriginInfoType
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.Capabilities
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodVersion
import com.sphereon.mdoc.transfer.device.NfcOptions
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.device.ServerRetrievalMethods
import com.sphereon.mdoc.transfer.device.WifiAwareOptions
import kotlin.collections.plusAssign
import kotlin.properties.Delegates
import kotlin.uuid.Uuid

@DslMarker
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceEngagementDsl", exact = true)
annotation class DeviceEngagementDsl

@DeviceEngagementDsl
class DeviceEngagementBuilder(private val eDeviceKeyBytes: CborEncodedItem<CoseKeyType>) {
    /** defaults to autoselection for 1.0 or 1.1 */
    var version: String? = null

    private lateinit var securityDslObject: DeviceEngagementSecurity
    fun security(block: SecurityBuilder.() -> Unit) {
        securityDslObject = SecurityBuilder(eDeviceKeyBytes).apply(block).build()
    }

    private var originInfoDslObjects: MutableList<OriginInfo> = mutableListOf()
    fun originInfo(block: OriginInfoBuilder.() -> Unit) {
        originInfoDslObjects.add(OriginInfoBuilder().apply(block).build())
    }

    fun originInfos(blocks: Array<OriginInfoBuilder.() -> Unit>) {
        blocks.forEach { originInfo(it) }
    }

    private var capabilitiesDsl: Capabilities? = null
    fun capabilities(block: CapabilitiesBuilder.() -> Unit) {
        capabilitiesDsl = CapabilitiesBuilder().apply(block).build()
    }


    private var deviceRetrievalMethodsDsl: Array<DeviceRetrievalMethod>? = null
    fun retrievalMethods(block: RetrievalMethodsBuilder.() -> Unit) {
        deviceRetrievalMethodsDsl = RetrievalMethodsBuilder().apply(block).build()
    }

    /** if you need server‐side retrieval settings, assign here */
    var serverRetrievalMethod: ServerRetrievalMethods? = null

    /** if you need to add protocolInfo, assign here */
    var protocolInfo: ProtocolInfo? = null

    private val extra = mutableMapOf<NumberLabel, CborItem<*>>()
    fun additionalItem(label: Long, item: CborItem<*>) {
        extra[label.longToNumberLabel()] = item
    }

    fun build(): DeviceEngagement {
        if (version == null) {
            version = if (originInfoDslObjects.isEmpty() && capabilitiesDsl == null) "1.0" else "1.1"
        }

        require(version == "1.0" || version == "1.1") { "Version must be 1.0 or 1.1, got: $version" }
        require(::securityDslObject.isInitialized) { "Security must be specified" }
        if (version == "1.0") {
            require(originInfoDslObjects.isEmpty()) { "OriginInfo must not be specified for 1.0" }
            require(capabilitiesDsl == null) { "Capabilities must not be specified for 1.0" }
            return DeviceEngagement.V1_0(
                security = securityDslObject,
                deviceRetrievalMethods = deviceRetrievalMethodsDsl,
                serverRetrievalMethod = serverRetrievalMethod,
                protocolInfo = protocolInfo,
                additionalItems = if (extra.isNotEmpty()) CborMap(extra.toMutableMap()) else null,
                original = null
            )
        } else {
            require(!originInfoDslObjects.isEmpty() || capabilitiesDsl != null) { "Origin info and/or Capabilities must not be specified for 1.1" }
            return DeviceEngagement.V1_1(
                security = securityDslObject,
                deviceRetrievalMethods = deviceRetrievalMethodsDsl,
                originInfos = if (originInfoDslObjects.isNotEmpty()) originInfoDslObjects.toTypedArray() else null,
                capabilities = capabilitiesDsl,
                additionalItems = if (extra.isNotEmpty()) CborMap(extra.toMutableMap()) else null,
                original = null
            )
        }
    }

    fun encodeCbor(): ByteArray = build().encodeCbor()
}


@DeviceEngagementDsl
@OptIn(ExperimentalObjCName::class)
@ObjCName("OriginInfoBuilder", exact = true)
class OriginInfoBuilder {
    var cat: OriginInfoCategory? = null
    var type: OriginInfoType? = null
    val details: MutableMap<String, Any> = mutableMapOf<String, Any>()


    fun build(): OriginInfo {
        requireNotNull(cat) { "OriginInfo category must be specified" }
        requireNotNull(type) { "OriginInfo type must be specified" }
        return OriginInfo(cat = cat!!, type = type!!, details = if (details.isNotEmpty()) OriginInfoDetails(details) else null, original = null)
    }


}


@DeviceEngagementDsl
@OptIn(ExperimentalObjCName::class)
@ObjCName("CapabilitiesBuilder", exact = true)
class CapabilitiesBuilder {
    private var macKeysSupport: Boolean? = null


    fun build(): Capabilities = Capabilities(macKeysSupport = macKeysSupport)

}

@DeviceEngagementDsl
class SecurityBuilder(private val deviceKeyBytes: CborEncodedItem<CoseKeyType>? = null) {

    /**
     * From 9.1.5.2: only cipher suite 1 is supported for now
     */
    private val cipherSuite: UInt = 1u
    lateinit var eDeviceKeyBytes: CborEncodedItem<CoseKeyType>

    fun eDeviceKey(key: CoseKeyType) {
        eDeviceKeyBytes = CborEncodedItem.fromData(key)
    }

    fun build(): DeviceEngagementSecurity =
        DeviceEngagementSecurity(cipherSuite, deviceKeyBytes ?: eDeviceKeyBytes)
}


@DeviceEngagementDsl
@OptIn(ExperimentalObjCName::class)
@ObjCName("RetrievalMethodsBuilder", exact = true)
class RetrievalMethodsBuilder {
    private val list = mutableListOf<DeviceRetrievalMethod>()

    fun ble(block: BleOptionsBuilder.() -> Unit) {
        val opts = BleOptionsBuilder().apply(block).build()
        list.plusAssign(DeviceRetrievalMethod(DeviceRetrievalMethodType.BLE, DeviceRetrievalMethodVersion(1u), opts))
    }

    fun nfc(block: NfcOptionsBuilder.() -> Unit) {
        val opts = NfcOptionsBuilder().apply(block).build()
        list.plusAssign(DeviceRetrievalMethod(DeviceRetrievalMethodType.NFC, DeviceRetrievalMethodVersion(1u), opts))
    }

    fun wifiAware(block: WifiAwareOptionsBuilder.() -> Unit) {
        val opts = WifiAwareOptionsBuilder().apply(block).build()
        list.plusAssign(DeviceRetrievalMethod(DeviceRetrievalMethodType.WIFI_WARE, DeviceRetrievalMethodVersion(1u), opts))
    }

    fun website(block: WebsiteOptionsBuilder.() -> Unit) {
        val opts = WebsiteOptionsBuilder().apply(block).build()
        list.plusAssign(DeviceRetrievalMethod(DeviceRetrievalMethodType.WEBSITE, DeviceRetrievalMethodVersion(1u), opts))
    }

    fun build(): Array<DeviceRetrievalMethod>? =
        list.takeIf { it.isNotEmpty() }?.toTypedArray()
}


@DeviceEngagementDsl
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleOptionsBuilder", exact = true)
class BleOptionsBuilder {
    var peripheralServerMode: Boolean = false
    var peripheralServerModeUuid: Uuid? = null
    var centralClientMode: Boolean = false
    var centralClientModeUuid: Uuid? = null

    fun fromOptions(config: BleOptions) = apply {
        this.centralClientMode = config.centralClientMode
        this.centralClientModeUuid = config.centralClientModeUuid
        this.peripheralServerMode = config.peripheralServerMode
        this.peripheralServerModeUuid = config.peripheralServerModeUuid
    }

    fun build(): BleOptions = BleOptions(
        peripheralServerMode = peripheralServerMode,
        peripheralServerModeUuid = peripheralServerModeUuid,
        centralClientMode = centralClientMode,
        centralClientModeUuid = centralClientModeUuid
    )
}


@DeviceEngagementDsl
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcOptionsBuilder", exact = true)
class NfcOptionsBuilder {
    var maxCommandDataFieldLength: UInt by Delegates.notNull()
    var maxResponseDataFieldLength: UInt by Delegates.notNull()

    fun build(): NfcOptions = NfcOptions(
        maxCommandDataFieldLength = this.maxCommandDataFieldLength,
        maxResponseDataFieldLength = this.maxResponseDataFieldLength
    )
}


@DeviceEngagementDsl
@OptIn(ExperimentalObjCName::class)
@ObjCName("WifiAwareOptionsBuilder", exact = true)
class WifiAwareOptionsBuilder {
    // fill in your Wi-Fi‐Aware options here
    lateinit var passPhrase: String

    fun build(): WifiAwareOptions = WifiAwareOptions(
        passPhrase = passPhrase,
    )
}


@DeviceEngagementDsl
@OptIn(ExperimentalObjCName::class)
@ObjCName("WebsiteOptionsBuilder", exact = true)
class WebsiteOptionsBuilder {
    // fill in your Wi-Fi‐Aware options here
    lateinit var uri: String

    fun build(): RestApiOptions = RestApiOptions(
        uri = uri,
    )
}
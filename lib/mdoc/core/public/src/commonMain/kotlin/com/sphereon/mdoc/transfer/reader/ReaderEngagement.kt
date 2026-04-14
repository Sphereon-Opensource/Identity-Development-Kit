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

package com.sphereon.mdoc.transfer.reader

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborNull
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.NumberLabel
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.engagement.DeviceEngagementSecurity
import com.sphereon.mdoc.engagement.ProtocolInfo
import com.sphereon.mdoc.transfer.OriginInfo
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.Capabilities
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.NfcOptions
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.device.WifiAwareOptions
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderEngagement", exact = true)
sealed class ReaderEngagement {
    abstract val version: ReaderEngagementVersion
    abstract val security: ReaderEngagementSecurity
    abstract val deviceRetrievalMethods: Array<DeviceRetrievalMethod>?

    abstract val protocolInfo: ProtocolInfo?
    abstract val additionalItems: CborMap<NumberLabel, CborItem<*>>?
    abstract val original: ByteArray?

    val hasBleRetrievalMethod: Boolean
        get() = deviceRetrievalMethods?.any { it.type == DeviceRetrievalMethodType.BLE } ?: false
    val hasNfcRetrievalMethod: Boolean
        get() = deviceRetrievalMethods?.any { it.type == DeviceRetrievalMethodType.NFC } ?: false
    val hasWifiAwareRetrievalMethod: Boolean
        get() = deviceRetrievalMethods?.any { it.type == DeviceRetrievalMethodType.WIFI_WARE } ?: false
    val hasWebsiteRetrievalMethod: Boolean
        get() = deviceRetrievalMethods?.any { it.type == DeviceRetrievalMethodType.WEBSITE } ?: false

    fun getBleRetrievalOptions(): BleOptions? = deviceRetrievalMethods?.firstOrNull { it.type == DeviceRetrievalMethodType.BLE }?.retrievalOptions as? BleOptions?

    fun getNfcRetrievalOptions(): NfcOptions? = deviceRetrievalMethods?.firstOrNull { it.type == DeviceRetrievalMethodType.NFC }?.retrievalOptions as? NfcOptions?

    fun getWifiAwareRetrievalOptions(): WifiAwareOptions? = deviceRetrievalMethods?.firstOrNull { it.type == DeviceRetrievalMethodType.WIFI_WARE }?.retrievalOptions as? WifiAwareOptions?

    fun getWebsiteRetrievalOptions(): RestApiOptions? = deviceRetrievalMethods?.firstOrNull { it.type == DeviceRetrievalMethodType.WEBSITE }?.retrievalOptions as? RestApiOptions?

    abstract fun copyWithOriginal(original: ByteArray? = null): ReaderEngagement

    companion object Decoder {
        @JsStatic
        @JvmStatic
        val VERSION = NumberLabel(0)

        @JsStatic
        @JvmStatic
        val SECURITY = NumberLabel(1)

        @JsStatic
        @JvmStatic
        val DEVICE_RETRIEVAL_METHODS = NumberLabel(2)

        @JsStatic
        @JvmStatic
        val PROTOCOL_INFO = NumberLabel(4)

        @JsStatic
        @JvmStatic
        val ORIGIN_INFOS = NumberLabel(5) // v1.1

        @JsStatic
        @JvmStatic
        val CAPABILITIES = NumberLabel(6) // v1.1
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("V1_0", exact = true)
    data class V1_0(
        override val security: ReaderEngagementSecurity,
        override val deviceRetrievalMethods: Array<DeviceRetrievalMethod>? = null,
        override val protocolInfo: ProtocolInfo? = null,
        override val additionalItems: CborMap<NumberLabel, CborItem<*>>? = CborMap(mutableMapOf()),
        override val original: ByteArray?,
    ) : ReaderEngagement() {
        override val version: ReaderEngagementVersion = ReaderEngagementVersion("1.0")

        override fun copyWithOriginal(original: ByteArray?): ReaderEngagement = this.copy(original = original)

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is V1_0) {
                return false
            }

            if (version != other.version) {
                return false
            }
            if (security != other.security) {
                return false
            }
            if (deviceRetrievalMethods != null) {
                if (other.deviceRetrievalMethods == null) {
                    return false
                }
                if (!deviceRetrievalMethods.contentEquals(other.deviceRetrievalMethods)) {
                    return false
                }
            } else if (other.deviceRetrievalMethods != null) {
                return false
            }
            if (protocolInfo != other.protocolInfo) {
                return false
            }
            if (additionalItems != other.additionalItems) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = version.hashCode()
            result = 31 * result + security.hashCode()
            result = 31 * result + (deviceRetrievalMethods?.contentHashCode() ?: 0)
            result = 31 * result + (protocolInfo?.hashCode() ?: 0)
            result = 31 * result + (additionalItems?.hashCode() ?: 0)
            return result
        }
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("V1_1", exact = true)
    data class V1_1(
        override val security: ReaderEngagementSecurity,
        override val deviceRetrievalMethods: Array<DeviceRetrievalMethod>? = null,
        override val protocolInfo: ProtocolInfo? = null,
        val originInfos: Array<OriginInfo>? = null,
        val capabilities: Capabilities? = null,
        override val additionalItems: CborMap<NumberLabel, CborItem<*>>? = CborMap(mutableMapOf()),
        override val original: ByteArray?,
    ) : ReaderEngagement() {
        override val version: ReaderEngagementVersion = ReaderEngagementVersion("1.1")

        override fun copyWithOriginal(original: ByteArray?): ReaderEngagement = this.copy(original = original)

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || this::class != other::class) {
                return false
            }

            other as V1_1

            if (security != other.security) {
                return false
            }
            if (!deviceRetrievalMethods.contentEquals(other.deviceRetrievalMethods)) {
                return false
            }
            if (!originInfos.contentEquals(other.originInfos)) {
                return false
            }
            if (capabilities != other.capabilities) {
                return false
            }
            if (additionalItems != other.additionalItems) {
                return false
            }
            if (!original.contentEquals(other.original)) {
                return false
            }
            if (version != other.version) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = security.hashCode()
            result = 31 * result + (deviceRetrievalMethods?.contentHashCode() ?: 0)
            result = 31 * result + (originInfos?.contentHashCode() ?: 0)
            result = 31 * result + (capabilities?.hashCode() ?: 0)
            result = 31 * result + (additionalItems?.hashCode() ?: 0)
            result = 31 * result + (original?.contentHashCode() ?: 0)
            result = 31 * result + version.hashCode()
            return result
        }
    }
}

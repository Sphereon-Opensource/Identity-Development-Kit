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
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.cborViewArrayToCborItem
import com.sphereon.cbor.dsl.cborMap
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.experimental.oid4vp.OID4VP_PROTOCOL_INFO_LABEL
import com.sphereon.mdoc.experimental.oid4vp.Oid4vpRequestProtocolCbor
import com.sphereon.mdoc.transfer.OriginInfo
import com.sphereon.mdoc.transfer.device.Capabilities
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.ServerRetrievalMethods
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceEngagement", exact = true)
sealed class DeviceEngagement : CborStructure<DeviceEngagement, CborMap<NumberLabel, CborItem<*>>>(CDDL.map) {

    abstract val security: DeviceEngagementSecurity
    abstract val deviceRetrievalMethods: Array<DeviceRetrievalMethod>?
//    abstract val serverRetrievalMethod: ServerRetrievalMethods?

    abstract val additionalItems: CborMap<NumberLabel, CborItem<*>>?
    abstract val version: DeviceEngagementVersion
    abstract override val original: ByteArray?
    abstract fun copyWithOriginal(original: ByteArray? = null): DeviceEngagement

    /**
     * Generate engagement URI for QR code display.
     *
     * Per ISO/IEC 18013-5, Device Engagement URIs use the opaque scheme `mdoc:` (no slashes).
     * This is used when the mdoc (holder) displays a QR code for the reader to scan.
     *
     * Format: `mdoc:<base64url-of-DeviceEngagement>`
     *
     * @return The engagement URI string
     */
    fun toEngagementUri(): String = "mdoc:${encodeCbor().encodeToBase64Url()}"


    val hasBleDeviceRetrievalMethod: Boolean = deviceRetrievalMethods?.any { it.type == DeviceRetrievalMethodType.BLE } ?: false
    val hasNfcDeviceRetrievalMethod: Boolean = deviceRetrievalMethods?.any { it.type == DeviceRetrievalMethodType.NFC } ?: false
    val hasWifiAwareRetrievalMethod: Boolean = deviceRetrievalMethods?.any { it.type == DeviceRetrievalMethodType.WIFI_WARE } ?: false
    val hasWebsiteRetrievalMethod: Boolean = deviceRetrievalMethods?.any { it.type == DeviceRetrievalMethodType.WEBSITE } ?: false


    companion object Decoder : HasFromCborWithOriginal<CborMap<NumberLabel, CborItem<*>>, DeviceEngagement> {
        @JsStatic
        val VERSION = NumberLabel(0)

        @JsStatic
        val SECURITY = NumberLabel(1)

        @JsStatic
        val DEVICE_RETRIEVAL_METHODS = NumberLabel(2)

        @JsStatic
        val SERVER_RETRIEVAL_METHOD = NumberLabel(3) // We do not support it to begin with!

        @JsStatic
        val PROTOCOL_INFO = NumberLabel(4)

        @JsStatic
        val ORIGIN_INFOS = NumberLabel(5) // v1.1

        @JsStatic
        val CAPABILITIES = NumberLabel(6) // v1.1

        fun builderFromKey(eDeviceKey: CoseKeyType, block: DeviceEngagementBuilder.() -> Unit): DeviceEngagementBuilder =
            DeviceEngagementBuilder(CborEncodedItem.fromData(eDeviceKey)).apply(block)

        fun builder(eDeviceKeyBytes: CborEncodedItem<CoseKeyType>, block: DeviceEngagementBuilder.() -> Unit): DeviceEngagementBuilder =
            DeviceEngagementBuilder(eDeviceKeyBytes).apply(block)

        @Suppress("UNCHECKED_CAST")
        override fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>): DeviceEngagement {
            return when (val version = DeviceEngagementVersion.Decoder.fromCborStructure(VERSION.required(structure)).toString()) {
                "1.0" -> V1_0.fromCborStructure(structure)
                "1.1" -> V1_1.fromCborStructure(structure)
                else -> throw IllegalArgumentException("Version must be 1.0 or 1.1, got: $version")

            }
        }


        override fun fromCborStructureWithOriginal(structure: CborMap<NumberLabel, CborItem<*>>, original: ByteArray?): DeviceEngagement {
            return fromCborStructure(structure).copyWithOriginal(original = original)
        }

        override fun decodeCbor(bytes: ByteArray): DeviceEngagement {
            // RFC 7049 Section 2.4.4.1: CBOR Tag 24 wraps an encoded CBOR data item
            // ISO 18013-7 reverse engagement: DeviceEngagement may be wrapped in Tag 24
            // We need to unwrap it to get the actual DeviceEngagement bytes
            val rawCbor = cborSerializer.decode<CborItem<*>>(bytes)

            // Unwrap CBOR Tag 24 if present
            val actualBytes = if (rawCbor is CborEncodedItem<*>) {
                (rawCbor.value.taggedItem as CborByteString).value
            } else {
                bytes
            }

            return fromCborStructureWithOriginal(cborSerializer.decode(actualBytes), original = actualBytes)
        }

        /**
         * Parse Device Engagement from QR code URI.
         *
         * Per ISO/IEC 18013-5, Device Engagement URIs use the opaque scheme `mdoc:` (no slashes).
         * This is the format displayed in QR codes by the holder for the reader to scan.
         *
         * Format: `mdoc:<base64url-of-DeviceEngagement>`
         *
         * @param uri The engagement URI from QR code
         * @return Decoded DeviceEngagement
         * @throws IllegalStateException if URI doesn't start with `mdoc:`
         */
        fun fromEngagementUri(uri: String): DeviceEngagement {
            check(uri.startsWith("mdoc:")) { "Device Engagement URI must start with 'mdoc:' per ISO 18013-5. We got: $uri" }
            return decodeCbor(uri.substring(5).decodeFromBase64Url())
        }
    }


    @OptIn(ExperimentalObjCName::class)
    @ObjCName("V1_0", exact = true)
    data class V1_0(
        override val security: DeviceEngagementSecurity,
        override val deviceRetrievalMethods: Array<DeviceRetrievalMethod>? = null,
        val serverRetrievalMethod: ServerRetrievalMethods? = null,
        val protocolInfo: ProtocolInfo? = null,
        override val additionalItems: CborMap<NumberLabel, CborItem<*>>? = CborMap(mutableMapOf()),
        override val original: ByteArray?,
    ) : DeviceEngagement() {

        override val version: DeviceEngagementVersion = DeviceEngagementVersion("1.0")


        // SPHEREON Funke: Experimental credential format extension
        val hasOid4vpProtocolInfo: Boolean = protocolInfo is CborMap<*, *> && protocolInfo.value.containsKey(OID4VP_PROTOCOL_INFO_LABEL)

        // SPHEREON Funke: Experimental credential format extension
        fun getOid4vpProtocolInfo(): Oid4vpRequestProtocolCbor {
            check(hasOid4vpProtocolInfo) { "Oid4vp Protocol info is not present. Cannot use experimental extension" }
            return Oid4vpRequestProtocolCbor.fromProtocolInfo(protocolInfo!!)
        }


        override fun copyWithOriginal(original: ByteArray?): DeviceEngagement {
            return this.copy(original = original)
        }


        companion object {
            @Suppress("UNCHECKED_CAST")
            fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>): V1_0 {
                val version = DeviceEngagementVersion.Decoder.fromCborStructure(VERSION.required(structure))
                require(version.toString() == "1.0") { "Version must be 1.0, got: $version" }
                val deviceRet: CborArray<CborItem<*>>? = DEVICE_RETRIEVAL_METHODS.optional(structure)
                val serverRet: CborMap<StringLabel, CborItem<*>>? = SERVER_RETRIEVAL_METHOD.optional(structure)
                return V1_0(
                    security = DeviceEngagementSecurity.fromCborItem(SECURITY.required(structure)),
                    deviceRetrievalMethods = deviceRet?.let { DeviceRetrievalMethod.Decoder.fromDeviceEngagementCborStructure(deviceRet) },
                    serverRetrievalMethod = serverRet?.let { ServerRetrievalMethods.Decoder.fromCborStructure(it) },
                    protocolInfo = PROTOCOL_INFO.optional(structure),
                    original = null,
                )
            }

        }


        override fun cborBuilder(): CborBuilder<V1_0> = cborMapBuilder(this) {
            VERSION to version
            SECURITY to security
            optional(DEVICE_RETRIEVAL_METHODS, if (deviceRetrievalMethods.isNullOrEmpty()) null else deviceRetrievalMethods.cborViewArrayToCborItem())
            optional(SERVER_RETRIEVAL_METHOD, serverRetrievalMethod)
            optional(PROTOCOL_INFO, protocolInfo)
            if (additionalItems?.value?.isNotEmpty() == true) {
                additionalItems.value.forEach { put(it.key, it.value) }
            }
        }


        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is V1_0) return false

            if (version != other.version) return false
            if (security != other.security) return false
            if (deviceRetrievalMethods != null) {
                if (other.deviceRetrievalMethods == null) return false
                if (!deviceRetrievalMethods.contentEquals(other.deviceRetrievalMethods)) return false
            } else if (other.deviceRetrievalMethods != null) return false
            if (serverRetrievalMethod != other.serverRetrievalMethod) return false
            if (protocolInfo != other.protocolInfo) return false
            if (additionalItems != other.additionalItems) return false

            return true
        }

        override fun hashCode(): Int {
            var result = version.hashCode()
            result = 31 * result + security.hashCode()
            result = 31 * result + (deviceRetrievalMethods?.contentHashCode() ?: 0)
            result = 31 * result + (serverRetrievalMethod?.hashCode() ?: 0)
            result = 31 * result + (protocolInfo?.hashCode() ?: 0)
            result = 31 * result + (additionalItems?.hashCode() ?: 0)
            return result
        }

    }


    @OptIn(ExperimentalObjCName::class)
    @ObjCName("V1_1", exact = true)
    data class V1_1(
        override val security: DeviceEngagementSecurity,
        override val deviceRetrievalMethods: Array<DeviceRetrievalMethod>? = null,
        val originInfos: Array<OriginInfo>? = null,
        val protocolInfo: ProtocolInfo? = null,
        val capabilities: Capabilities? = null,
        override val additionalItems: CborMap<NumberLabel, CborItem<*>>? = CborMap(mutableMapOf()),
        override val original: ByteArray?,
    ) : DeviceEngagement() {

        override val version: DeviceEngagementVersion = DeviceEngagementVersion("1.1")


        override fun copyWithOriginal(original: ByteArray?): DeviceEngagement {
            return this.copy(original = original)
        }


        companion object {
            @Suppress("UNCHECKED_CAST")
            fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>): V1_1 {
                val version = DeviceEngagementVersion.Decoder.fromCborStructure(VERSION.required(structure))
                require(version.toString() == "1.1") { "Version must be 1.1, got: $version" }
                val deviceRet: CborArray<CborItem<*>>? = DEVICE_RETRIEVAL_METHODS.optional(structure)

                return V1_1(
                    security = DeviceEngagementSecurity.fromCborItem(SECURITY.required(structure)),
                    deviceRetrievalMethods = deviceRet?.let { DeviceRetrievalMethod.Decoder.fromDeviceEngagementCborStructure(deviceRet) },
                    protocolInfo = PROTOCOL_INFO.optional(structure),
                    originInfos = ORIGIN_INFOS.optional(structure),
                    capabilities = CAPABILITIES.optional(structure),
                    original = null,
                )
            }

        }


        override fun cborBuilder(): CborBuilder<V1_1> = cborMapBuilder(this) {
            VERSION to version
            SECURITY to security
            optional(DEVICE_RETRIEVAL_METHODS, if (deviceRetrievalMethods.isNullOrEmpty()) null else deviceRetrievalMethods.cborViewArrayToCborItem())
            optional(PROTOCOL_INFO, protocolInfo)
            optional(ORIGIN_INFOS, if (originInfos.isNullOrEmpty()) null else originInfos)
            optional(CAPABILITIES, capabilities)
            if (additionalItems?.value?.isNotEmpty() == true) {
                additionalItems.value.forEach { put(it.key, it.value) }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as V1_1

            if (security != other.security) return false
            if (!deviceRetrievalMethods.contentEquals(other.deviceRetrievalMethods)) return false
            if (!originInfos.contentEquals(other.originInfos)) return false
            if (capabilities != other.capabilities) return false
            if (additionalItems != other.additionalItems) return false
            if (!original.contentEquals(other.original)) return false
            if (version != other.version) return false

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


typealias ProtocolInfo = CborItem<*>

/**
 * Encodes the DeviceEngagement as DeviceEngagementBytes (CBOR Tag 24 wrapped).
 *
 * Per ISO 18013-7:
 * DeviceEngagementBytes = #6.24(bstr .cbor DeviceEngagement)
 *
 * This wraps the encoded DeviceEngagement CBOR in Tag 24 to indicate it contains
 * embedded CBOR data.
 *
 * @return The Tag 24 wrapped DeviceEngagement bytes
 */
fun DeviceEngagement.toDeviceEngagementBytes(): ByteArray {
    val deviceEngagementCbor = this.encodeCbor()
    val encodedItem = CborEncodedItem(deviceEngagementCbor, data = this)
    return cborSerializer.encode(encodedItem)
}

/**
 * Encodes the DeviceEngagement as a DeviceEngagementMessage for REST/WEBSITE endpoints.
 *
 * Per ISO 18013-7 Step 1:
 * DeviceEngagementMessage = {
 *   "deviceEngagementBytes": DeviceEngagementBytes
 * }
 * DeviceEngagementBytes = #6.24(bstr .cbor DeviceEngagement)
 *
 * This format is used when posting the device engagement to a REST or WEBSITE
 * retrieval method endpoint in the reverse engagement flow.
 *
 * @return CBOR-encoded DeviceEngagementMessage bytes ready for HTTP POST
 */
fun DeviceEngagement.toDeviceEngagementMessage(): ByteArray {
    val deviceEngagementBytes = this.toDeviceEngagementBytes()
    val message = cborMap {
        "deviceEngagementBytes" to CborByteString(deviceEngagementBytes)
    }
    return cborSerializer.encode(message)
}

fun CborEncodedItem<DeviceEngagement>.toDeviceEngagementMessage(): ByteArray {
    val message = cborMap {
        "deviceEngagementBytes" to this@toDeviceEngagementMessage
    }
    return cborSerializer.encode(message)
}

/**
 * Parses a DeviceEngagementMessage and extracts the DeviceEngagement.
 *
 * Per ISO 18013-7:
 * DeviceEngagementMessage = {
 *   "deviceEngagementBytes": DeviceEngagementBytes
 * }
 * DeviceEngagementBytes = #6.24(bstr .cbor DeviceEngagement)
 *
 * @param messageBytes The CBOR-encoded DeviceEngagementMessage bytes
 * @return The extracted DeviceEngagement
 * @throws IllegalArgumentException if the message format is invalid
 */
fun fromDeviceEngagementMessage(messageBytes: ByteArray): DeviceEngagement {
    val message = cborSerializer.decode<CborMap<StringLabel, CborItem<*>>>(messageBytes)
    val deviceEngagementBytesItem = message.value[StringLabel("deviceEngagementBytes")]
        ?: throw IllegalArgumentException("DeviceEngagementMessage must contain 'deviceEngagementBytes' key")

    val deviceEngagementBytes = when (deviceEngagementBytesItem) {
        is CborByteString -> deviceEngagementBytesItem.value
        else -> throw IllegalArgumentException("deviceEngagementBytes must be a byte string")
    }

    return DeviceEngagement.decodeCbor(deviceEngagementBytes)
}



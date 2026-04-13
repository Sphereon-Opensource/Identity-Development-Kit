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

package com.sphereon.mdoc.transfer.reader

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
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.cborViewArrayToCborItem
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.engagement.DeviceEngagementBuilder
import com.sphereon.mdoc.engagement.DeviceEngagementSecurity
import com.sphereon.mdoc.engagement.ProtocolInfo
import com.sphereon.mdoc.transfer.OriginInfo
import com.sphereon.mdoc.transfer.device.Capabilities
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.NfcOptions
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.device.WifiAwareOptions
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderEngagement", exact = true)
sealed class ReaderEngagement : CborStructure<ReaderEngagement, CborMap<NumberLabel, CborItem<*>>>(CDDL.map) {

    abstract val version: ReaderEngagementVersion
    abstract val security: ReaderEngagementSecurity
    abstract val deviceRetrievalMethods: Array<DeviceRetrievalMethod>?

    abstract val protocolInfo: ProtocolInfo?
    abstract val additionalItems: CborMap<NumberLabel, CborItem<*>>?
    abstract override val original: ByteArray?
    abstract fun copyWithOriginal(original: ByteArray? = null): ReaderEngagement

    override fun encodeCbor(): ByteArray {
        val additionalItemsLocal = additionalItems
        if (additionalItemsLocal != null && additionalItemsLocal.value.isEmpty()) {
            // Make sure we remove the empty map. Keep the != null check above to avoid infinite recursion.
            return when (this) {
                is V1_0 -> this.copy(additionalItems = null)
                is V1_1 -> this.copy(additionalItems = null)
            }.encodeCbor()
        }
        return super.encodeCbor()
    }

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
    fun getWifiAwareRetrievalOptions(): WifiAwareOptions? =
        deviceRetrievalMethods?.firstOrNull { it.type == DeviceRetrievalMethodType.WIFI_WARE }?.retrievalOptions as? WifiAwareOptions?

    fun getWebsiteRetrievalOptions(): RestApiOptions? = deviceRetrievalMethods?.firstOrNull { it.type == DeviceRetrievalMethodType.WEBSITE }?.retrievalOptions as? RestApiOptions?



    companion object Decoder : HasFromCborWithOriginal<CborMap<NumberLabel, CborItem<*>>, ReaderEngagement> {
        @JsStatic
        val VERSION = NumberLabel(0)

        @JsStatic
        val SECURITY = NumberLabel(1)

        @JsStatic
        val DEVICE_RETRIEVAL_METHODS = NumberLabel(2)

        @JsStatic
        val PROTOCOL_INFO = NumberLabel(4)

        @JsStatic
        val ORIGIN_INFOS = NumberLabel(5) // v1.1

        @JsStatic
        val CAPABILITIES = NumberLabel(6) // v1.1

        @JsStatic
        fun builderFromKey(eDeviceKey: CoseKeyType, block: DeviceEngagementBuilder.() -> Unit): DeviceEngagementBuilder =
            DeviceEngagementBuilder(CborEncodedItem.fromData(eDeviceKey)).apply(block)

        @JsStatic
        fun builder(eDeviceKeyBytes: CborEncodedItem<CoseKeyType>, block: DeviceEngagementBuilder.() -> Unit): DeviceEngagementBuilder =
            DeviceEngagementBuilder(eDeviceKeyBytes).apply(block)

        @Suppress("UNCHECKED_CAST")
        override fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>): ReaderEngagement {
            return when (val version = ReaderEngagementVersion.Decoder.fromCborStructure(VERSION.required(structure)).toString()) {
                "1.0" -> V1_0.fromCborStructure(structure)
                "1.1" -> V1_1.fromCborStructure(structure)
                else -> throw IllegalArgumentException("Version must be 1.0 or 1.1, got: $version")
            }
        }

        override fun fromCborStructureWithOriginal(structure: CborMap<NumberLabel, CborItem<*>>, original: ByteArray?): ReaderEngagement {
            return fromCborStructure(structure).copyWithOriginal(original = original)
        }

        override fun decodeCbor(bytes: ByteArray): ReaderEngagement {
            // RFC 7049 Section 2.4.4.1: CBOR Tag 24 wraps an encoded CBOR data item
            // ISO 18013-7 reverse engagement: ReaderEngagement may be wrapped in Tag 24
            // We need to unwrap it to get the actual ReaderEngagement bytes
            val rawCbor = cborSerializer.decode<CborItem<*>>(bytes)

            // Unwrap CBOR Tag 24 if present to parse the structure
            val actualBytes = if (rawCbor is CborEncodedItem<*>) {
                (rawCbor.value.taggedItem as CborByteString).value
            } else {
                bytes
            }

            // CRITICAL: Store the original input bytes (with Tag 24 if present) for REST API handover
            // Per ISO 18013-7 A.8, the hash in SessionTranscript must be computed on the exact bytes
            // from the QR code (which includes Tag 24 wrapping)
            return fromCborStructureWithOriginal(cborSerializer.decode(actualBytes), original = bytes)
        }

        /**
         * Parse Reader Engagement from reverse engagement URI.
         *
         * Supports three URI schemes for reverse engagement (reader-initiated):
         *
         * 1. **Classic Reverse Engagement (ISO 18013-5)**: `mdoc:` (opaque, no slashes)
         *    - Format: `mdoc:<base64url-of-ReaderEngagement>`
         *    - Transport: BLE or NFC (proximity-based)
         *    - Example: `mdoc:o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOB...`
         *
         * 2. **REST API / Website (ISO 18013-7 Annex A)**: `mdoc://` (hierarchical, with slashes)
         *    - Format: `mdoc://<base64url-of-ReaderEngagement>`
         *    - Transport: HTTPS POST
         *    - Example: `mdoc://o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOB...`
         *
         * 3. **OID4VP (ISO 18013-7 Annex B)**: `mdoc-openid4vp://` (custom OAuth scheme)
         *    - Format can be either:
         *      a) `mdoc-openid4vp://<base64url-of-ReaderEngagement>` (direct encoding)
         *      b) `mdoc-openid4vp://?client_id=...&request_uri=...` (query parameters, which may reference ReaderEngagement)
         *    - Transport: OAuth 2.0 / OpenID4VP over HTTPS
         *    - Example: `mdoc-openid4vp://o2d2ZXJzaW9uYzEuMGlkb2N1bWVudHOB...`
         *
         * @param mdocUri The engagement URI from QR code, deep link, or NFC tag
         * @return Decoded ReaderEngagement
         * @throws IllegalArgumentException if URI doesn't match any supported scheme or doesn't contain base64url payload
         */
        @JsStatic
        fun fromEngagementUri(mdocUri: String): ReaderEngagement {
            // Determine scheme and extract payload
            val (scheme, payload) = when {
                // OID4VP (ISO 18013-7 Annex B) - custom OAuth scheme
                mdocUri.startsWith("mdoc-openid4vp://") -> {
                    val afterScheme = mdocUri.substring(17)
                    // Check if it starts with '?' (query parameters) or is base64url payload
                    if (afterScheme.startsWith("?")) {
                        throw IllegalArgumentException(
                            "mdoc-openid4vp:// URI contains query parameters, not a base64url-encoded ReaderEngagement. " +
                                    "Cannot parse ReaderEngagement from query parameter format. " +
                                    "If the Authorization Request contains a ReaderEngagement, fetch it from request_uri first."
                        )
                    }
                    "mdoc-openid4vp://" to afterScheme
                }

                // REST API / Website (ISO 18013-7 Annex A) - hierarchical scheme with slashes
                mdocUri.startsWith("mdoc://") -> {
                    "mdoc://" to mdocUri.substring(7)
                }

                // Classic reverse engagement (ISO 18013-5) - opaque scheme without slashes
                mdocUri.startsWith("mdoc:") && !mdocUri.startsWith("mdoc://") -> {
                    "mdoc:" to mdocUri.substring(5)
                }

                else -> {
                    throw IllegalArgumentException(
                        "URI must start with 'mdoc:' (classic), 'mdoc://' (website), or 'mdoc-openid4vp://' (OID4VP). " +
                                "Got: ${mdocUri.take(20)}..."
                    )
                }
            }

            // Decode base64url payload
            return ReaderEngagement.decodeCbor(payload.decodeFromBase64Url())
        }
    }

    /**
     * Generate engagement URI for reverse engagement (reader-initiated).
     *
     * Supports generating URIs with different schemes:
     * - `mdoc:` - Classic reverse engagement (ISO 18013-5) for BLE/NFC
     * - `mdoc://` - REST API / Website (ISO 18013-7 Annex A) for HTTPS
     * - `mdoc-openid4vp://` - OID4VP (ISO 18013-7 Annex B) for OAuth 2.0/HTTPS
     *
     * Format: `<scheme><base64url-of-ReaderEngagement>`
     *
     * Note: OID4VP can also use query parameter format (`mdoc-openid4vp://?client_id=...`),
     * which is constructed differently and doesn't directly encode a ReaderEngagement.
     *
     * @param scheme The URI scheme (default: "mdoc://")
     * @return The engagement URI string
     */
    fun toEngagementUri(scheme: String = "mdoc://"): String {
        return "$scheme${encodeCbor().encodeToBase64Url()}"
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

        override fun copyWithOriginal(original: ByteArray?): ReaderEngagement {
            return this.copy(original = original)
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>): V1_0 {
                val version = ReaderEngagementVersion.Decoder.fromCborStructure(VERSION.required(structure))
                require(version.toString() == "1.0") { "Version must be 1.0, got: $version" }
                val deviceRet: CborArray<CborItem<*>>? = DEVICE_RETRIEVAL_METHODS.optional(structure)
                return V1_0(
                    security = ReaderEngagementSecurity.fromCborItem(SECURITY.required(structure)),
                    deviceRetrievalMethods = deviceRet?.let { DeviceRetrievalMethod.Decoder.fromDeviceEngagementCborStructure(deviceRet) },
                    protocolInfo = PROTOCOL_INFO.optional(structure),
                    original = null,
                )
            }
        }

        override fun cborBuilder(): CborBuilder<V1_0> = cborMapBuilder(this) {
            VERSION to version
            SECURITY to security
            optional(DEVICE_RETRIEVAL_METHODS, if (deviceRetrievalMethods.isNullOrEmpty()) null else deviceRetrievalMethods.cborViewArrayToCborItem())
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
            if (protocolInfo != other.protocolInfo) return false
            if (additionalItems != other.additionalItems) return false

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

        override fun copyWithOriginal(original: ByteArray?): ReaderEngagement {
            return this.copy(original = original)
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>): V1_1 {
                val version = ReaderEngagementVersion.Decoder.fromCborStructure(VERSION.required(structure))
                require(version.toString() == "1.1") { "Version must be 1.1, got: $version" }
                val deviceRet: CborArray<CborItem<*>>? = DEVICE_RETRIEVAL_METHODS.optional(structure)

                return V1_1(
                    security = ReaderEngagementSecurity.fromCborItem(SECURITY.required(structure)),
                    deviceRetrievalMethods = deviceRet?.let { DeviceRetrievalMethod.Decoder.fromDeviceEngagementCborStructure(deviceRet) },
                    protocolInfo = PROTOCOL_INFO.optional(structure),
                    originInfos = ORIGIN_INFOS.optional(structure),
                    capabilities = CAPABILITIES.optional(structure),
                    original = null, // Will be set by fromCborStructureWithOriginal if needed
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

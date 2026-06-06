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

package com.sphereon.mdoc.engagement

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.percentDecode
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodVersion
import com.sphereon.mdoc.transfer.device.NfcOptions
import com.sphereon.mdoc.transfer.device.Oid4vpOptions
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodec
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEngagementFactory", exact = true)
@JsExportCompat
interface MdocEngagementFactory {
    val holder: Holder
    val reader: Reader

    interface Holder {
        /**
         * Initializes an engagement instance from a pre-configured builder.
         *
         * @param builder The pre-configured MdocEngagementData.HolderBuilder
         * @return IdkResult with the initialized EngagementInstance or error
         */
        suspend fun createFromBuilder(builder: EngagementData.HolderBuilder): IdkResult<EngagementInstance, IdkError>

        /**
         * Initializes an engagement instance using an ephemeral key and configuration builder.
         *
         * @param ephemeralKey The ephemeral key to use for the engagement
         * @param configBuilder Configuration builder function for setting up the engagement
         * @return IdkResult with the initialized EngagementInstance or error
         */
        suspend fun createFromEphemeralKey(
            ephemeralKey: ResolvedKeyInfoType<*>,
            configBuilder: EngagementConfiguration.() -> Unit = {
                engagement {
                    qr {
                        scheme = "mdoc:"
                    }
                }
                retrieval {
                    ble {
                        centralClientMode = true
                        centralClientUuid = Uuid.random()
                        peripheralServerMode = false
                    }
                }
            },
        ): IdkResult<EngagementInstance, IdkError>

        /**
         * Initializes an engagement instance using the provided configuration builder.
         *
         * Automatically routes to correct typed property (nfcEngagement, qrEngagement, or toAppEngagement)
         * based on the engagement method specified in the configuration.
         *
         * For QR: BLE scanning starts immediately when getEngagementUri() called
         * For NFC: BLE scanning starts when start() called after tap
         *
         * @param configBuilder Configuration builder function for setting up the engagement
         * @return IdkResult with EngagementInstance or error if engagement method conflict (same type already active)
         */
        suspend fun createEngagement(configBuilder: EngagementConfiguration.() -> Unit = {}): IdkResult<EngagementInstance, IdkError>
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Reader", exact = true)
    interface Reader {
        suspend fun createEngagement(init: ReaderConfiguration.() -> Unit = {}): IdkResult<EngagementInstance, IdkError>
    }
}

@JsExportCompat
class EngagementConfiguration {
    private val _engagementMethods = mutableSetOf<MdocEngagementMethod>()
    private val _retrievalMethods = mutableSetOf<DeviceRetrievalMethod>()

    val engagementMethods: Set<MdocEngagementMethod>
        get() = _engagementMethods.ifEmpty { setOf(QREngagementMethod()) }

    val retrievalMethods: Set<DeviceRetrievalMethod>
        get() = _retrievalMethods.toSet()

    fun engagement(init: EngagementBuilder.() -> Unit) {
        val builder = EngagementBuilder()
        builder.init()
        _engagementMethods.addAll(builder.build())
    }

    fun retrieval(init: RetrievalBuilder.() -> Unit) {
        val builder = RetrievalBuilder(_engagementMethods)
        builder.init()
        _retrievalMethods.addAll(builder.build())
    }

    /**
     * Internal helper to apply shared BLE UUID to retrieval methods if not already set.
     * This enables automatic UUID sharing across engagements.
     *
     * Since BleOptions is immutable, this returns a new set of retrieval methods with
     * the shared UUID applied where appropriate.
     */
    @OptIn(ExperimentalUuidApi::class)
    internal fun getRetrievalMethodsWithSharedUuid(sharedUuid: Uuid): Set<DeviceRetrievalMethod> {
        // Backward compatibility: use same UUID for both modes
        return getRetrievalMethodsWithSharedUuids(
            sharedCentralClientUuid = sharedUuid,
            sharedPeripheralServerUuid = sharedUuid,
        )
    }

    /**
     * Apply shared BLE UUIDs to retrieval methods that don't have explicit UUIDs configured.
     *
     * This method supports separate UUIDs for central client mode and peripheral server mode,
     * which is the correct design as these are different BLE roles.
     *
     * @param sharedCentralClientUuid UUID to use for central client mode (holder initiates connection)
     * @param sharedPeripheralServerUuid UUID to use for peripheral server mode (holder waits for connection)
     * @return Set of retrieval methods with shared UUIDs applied where needed
     */
    @OptIn(ExperimentalUuidApi::class)
    fun getRetrievalMethodsWithSharedUuids(
        sharedCentralClientUuid: Uuid,
        sharedPeripheralServerUuid: Uuid,
    ): Set<DeviceRetrievalMethod> =
        _retrievalMethods
            .map { method ->
                if (method.retrievalOptions is BleOptions) {
                    val bleOptions = method.retrievalOptions as BleOptions

                    // Determine if we need to apply shared UUIDs
                    val needsCentralUuid = bleOptions.centralClientMode && bleOptions.centralClientModeUuid == null
                    val needsPeripheralUuid = bleOptions.peripheralServerMode && bleOptions.peripheralServerModeUuid == null

                    // If either UUID is missing, create new BleOptions with shared UUIDs
                    if (needsCentralUuid || needsPeripheralUuid) {
                        val updatedBleOptions =
                            bleOptions.copy(
                                centralClientModeUuid =
                                    bleOptions.centralClientModeUuid ?: if (bleOptions.centralClientMode) {
                                        sharedCentralClientUuid
                                    } else {
                                        null
                                    },
                                peripheralServerModeUuid =
                                    bleOptions.peripheralServerModeUuid ?: if (bleOptions.peripheralServerMode) {
                                        sharedPeripheralServerUuid
                                    } else {
                                        null
                                    },
                            )
                        // Recreate the DeviceRetrievalMethod with updated options
                        DeviceRetrievalMethod(
                            type = method.type,
                            version = method.version,
                            retrievalOptions = updatedBleOptions,
                        )
                    } else {
                        // UUID already set or not needed, keep original
                        method
                    }
                } else {
                    // Non-BLE method, keep as-is
                    method
                }
            }.toSet()
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderConfiguration", exact = true)
@JsExportCompat
class ReaderConfiguration {
    private val _engagementMethods = mutableSetOf<MdocEngagementMethod>()
    private val _retrievalMethods = mutableSetOf<DeviceRetrievalMethod>()

    val engagementMethods: Set<MdocEngagementMethod>
        get() = _engagementMethods.ifEmpty { setOf(QREngagementMethod()) }

    val retrievalMethods: Set<DeviceRetrievalMethod>
        get() = _retrievalMethods.toSet()

    fun engagement(init: EngagementBuilder.() -> Unit) {
        val builder = EngagementBuilder()
        builder.init()
        _engagementMethods.addAll(builder.build())
    }

    fun retrieval(init: RetrievalBuilder.() -> Unit) {
        val builder = RetrievalBuilder(_engagementMethods)
        builder.init()
        _retrievalMethods.addAll(builder.build())
    }
}

// Engagement builder for grouping engagement methods
@OptIn(ExperimentalObjCName::class)
@ObjCName("EngagementBuilder", exact = true)
@JsExportCompat
class EngagementBuilder {
    private val methods = mutableSetOf<MdocEngagementMethod>()

    fun qr(init: QrEngagementBuilder.() -> Unit = {}) {
        val builder = QrEngagementBuilder()
        builder.init()
        methods.add(builder.build())
    }

    fun nfc(init: NfcEngagementBuilder.() -> Unit = {}) {
        val builder = NfcEngagementBuilder()
        builder.init()
        methods.add(builder.build())
    }

    fun reader(init: ReaderEngagementBuilder.() -> Unit = {}) {
        val builder = ReaderEngagementBuilder()
        builder.init()
        methods.add(builder.build())
    }

    fun oid4vp(init: Oid4vpEngagementBuilder.() -> Unit = {}) {
        val builder = Oid4vpEngagementBuilder()
        builder.init()
        methods.add(builder.build())
    }

    fun build(): Set<MdocEngagementMethod> = methods.toSet()
}

// Retrieval builder for grouping retrieval methods
@OptIn(ExperimentalObjCName::class)
@ObjCName("RetrievalBuilder", exact = true)
@JsExportCompat
class RetrievalBuilder(
    private val engagementMethodSet: Set<MdocEngagementMethod>,
) {
    private val methods = mutableSetOf<DeviceRetrievalMethod>()

    fun ble(init: BleRetrievalBuilder.() -> Unit = {}) {
        val builder = BleRetrievalBuilder()
        builder.init()
        methods.add(builder.build())
    }

    fun nfc(init: NfcRetrievalBuilder.() -> Unit = {}) {
        val builder = NfcRetrievalBuilder()
        builder.init()
        methods.add(builder.build())
    }

    fun website(init: WebsiteRetrievalBuilder.() -> Unit = {}) {
        val builder = WebsiteRetrievalBuilder(engagementMethodSet)
        builder.init()
        methods.add(builder.build())
    }

    fun oid4vp(init: Oid4vpRetrievalBuilder.() -> Unit = {}) {
        val builder = Oid4vpRetrievalBuilder(engagementMethodSet)
        builder.init()
        methods.add(builder.build())
    }

    fun build(): Set<DeviceRetrievalMethod> = methods.toSet()
}

// Individual method builders
@OptIn(ExperimentalObjCName::class)
@ObjCName("QrEngagementBuilder", exact = true)
@JsExportCompat
class QrEngagementBuilder {
    var scheme: String = "mdoc:"

    fun build(): QREngagementMethod = QREngagementMethod(scheme)
}

@JsExportCompat
class NfcEngagementBuilder {
    fun build(): NfcEngagementMethod = NfcEngagementMethod()
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderEngagementBuilder", exact = true)
@JsExportCompat
class ReaderEngagementBuilder {
    private var readerEngagement: ReaderEngagement? = null
    private var readerEngagementCborCodec: ReaderEngagementCborCodec? = null

    fun withReaderEngagement(readerEngagement: ReaderEngagement) =
        apply {
            this.readerEngagement = readerEngagement
        }

    fun withReaderEngagementCborCodec(readerEngagementCborCodec: ReaderEngagementCborCodec) =
        apply {
            this.readerEngagementCborCodec = readerEngagementCborCodec
        }

    fun build(): ReaderEngagementMethod {
        val readerEngagement =
            requireNotNull(readerEngagement) {
                "Reader engagement must be specified. Decode engagement URIs before building engagement methods."
            }
        return ReaderEngagementMethod(readerEngagement, readerEngagementCborCodec)
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("BleRetrievalBuilder", exact = true)
@JsExportCompat
class BleRetrievalBuilder {
    var peripheralServerMode: Boolean = false

    var peripheralServerUuid: Uuid? = null
    var centralClientMode: Boolean = false

    var centralClientUuid: Uuid? = null

    var version: DeviceRetrievalMethodVersion = DeviceRetrievalMethodVersion(1u)

    fun enablePeripheralServer() {
        peripheralServerMode = true
    }

    fun enableCentralClient() {
        centralClientMode = true
    }

    fun disablePeripheralServer() {
        peripheralServerMode = false
    }

    fun disableCentralClient() {
        centralClientMode = false
    }

    fun withVersion(version: DeviceRetrievalMethodVersion) =
        apply {
            this.version = version
        }

    fun withVersion1() =
        apply {
            withVersion(DeviceRetrievalMethodVersion(1u))
        }

    fun build(): DeviceRetrievalMethod {
        if (!centralClientMode && !peripheralServerMode) {
            // we choose peripheral if none was supplied
            this.peripheralServerMode = true
        }
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.BLE,
            version = version,
            retrievalOptions =
                BleOptions(
                    peripheralServerMode = peripheralServerMode,
                    peripheralServerModeUuid = peripheralServerUuid,
                    centralClientMode = centralClientMode,
                    centralClientModeUuid = centralClientUuid,
                ),
        )
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcRetrievalBuilder", exact = true)
@JsExportCompat
class NfcRetrievalBuilder {
    var maxCommandDataFieldLength: UInt = 0xffff.toUInt()
    var maxResponseDataFieldLength: UInt = 0x10000.toUInt()

    var version: DeviceRetrievalMethodVersion = DeviceRetrievalMethodVersion(1u)

    fun withVersion(version: DeviceRetrievalMethodVersion) =
        apply {
            this.version = version
        }

    fun withVersion1() =
        apply {
            withVersion(DeviceRetrievalMethodVersion(1u))
        }

    fun build(): DeviceRetrievalMethod =
        DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.NFC,
            version = version,
            retrievalOptions = NfcOptions(maxCommandDataFieldLength = maxCommandDataFieldLength, maxResponseDataFieldLength = maxResponseDataFieldLength),
        )
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("WebsiteRetrievalBuilder", exact = true)
@JsExportCompat
class WebsiteRetrievalBuilder(
    private val engagementMethodSet: Set<MdocEngagementMethod>,
) {
    var uri: String? = null

    var version: DeviceRetrievalMethodVersion = DeviceRetrievalMethodVersion(1u)

    fun withVersion(version: DeviceRetrievalMethodVersion) =
        apply {
            this.version = version
        }

    fun withVersion1() =
        apply {
            withVersion(DeviceRetrievalMethodVersion(1u))
        }

    fun build(): DeviceRetrievalMethod {
        if (uri.isNullOrEmpty()) {
            val engagementMethod =
                engagementMethodSet.first {
                    it.type == EngagementType.TO_APP && (it as? ReaderEngagementMethod != null && it.readerEngagement.hasWebsiteRetrievalMethod)
                } as? ReaderEngagementMethod
            requireNotNull(engagementMethod) { "To app engagement method needed when website retrieval is used" }
            val restApiOptions = engagementMethod.readerEngagement.getWebsiteRetrievalOptions()!!
            uri = restApiOptions.uri
        }
        require(!uri.isNullOrEmpty()) { "URI must be specified for website retrieval method" }
        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.WEBSITE,
            version = version,
            retrievalOptions = RestApiOptions(uri = uri!!),
        )
    }
}

/**
 * Builder for OID4VP engagement method (ISO 18013-7 Annex B).
 *
 * This builder configures the holder-side OID4VP engagement, which uses
 * the `mdoc-openid4vp://` URI scheme for wallet invocation.
 *
 * ## Usage Example
 * ```kotlin
 * engagement {
 *     oid4vp {
 *         authorizationRequestUri = "mdoc-openid4vp://?client_id=verifier.com&request_uri=https://verifier.com/request/123"
 *         // Or parse from QR code
 *         fromUri("mdoc-openid4vp://...")
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpEngagementBuilder", exact = true)
@JsExportCompat
class Oid4vpEngagementBuilder {
    /**
     * The full `mdoc-openid4vp://` URI from the verifier.
     * This URI contains the client_id and request_uri parameters.
     */
    var authorizationRequestUri: String = ""

    /**
     * Parse OID4VP parameters from a `mdoc-openid4vp://` URI.
     *
     * Per ISO 18013-7 B.3.1.3.2, the URI format is:
     * ```
     * mdoc-openid4vp://?client_id=example.com&request_uri=https://example.com/request
     * ```
     *
     * @param uri The authorization request URI from QR code or deep link
     */
    fun fromUri(uri: String) =
        apply {
            require(uri.startsWith("mdoc-openid4vp://")) {
                "OID4VP URI must start with 'mdoc-openid4vp://'"
            }
            this.authorizationRequestUri = uri
        }

    fun build(): Oid4vpEngagementMethod {
        require(authorizationRequestUri.isNotBlank()) {
            "Authorization request URI must be specified for OID4VP engagement"
        }

        // Create OID4VP engagement method with the authorization request URI
        // The URI contains query parameters (client_id, request_uri, etc.)
        // which will be parsed by the Oid4vpRetrievalBuilder
        return Oid4vpEngagementMethod(authorizationRequestUri)
    }
}

/**
 * Builder for OID4VP retrieval method (ISO 18013-7 Annex B).
 *
 * This builder configures OID4VP retrieval options including:
 * - Client identifier (verifier)
 * - Request URI (HTTPS URL to fetch Authorization Request Object)
 * - Response URI (HTTPS URL to POST Authorization Response)
 * - Nonce (cryptographic nonce, min 16 bytes)
 * - Presentation Definition URI (optional, legacy; DCQL is the default)
 *
 * ## Usage Example
 * ```kotlin
 * retrieval {
 *     oid4vp {
 *         clientId = "verifier.example.com"
 *         requestUri = "https://verifier.example.com/request/abc123"
 *         responseUri = "https://verifier.example.com/response"
 *         nonce = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
 *     }
 * }
 * ```
 *
 * Or extract from Authorization Request URI:
 * ```kotlin
 * retrieval {
 *     oid4vp {
 *         fromAuthorizationRequestUri("mdoc-openid4vp://?client_id=...")
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpRetrievalBuilder", exact = true)
@JsExportCompat
class Oid4vpRetrievalBuilder(
    private val engagementMethodSet: Set<MdocEngagementMethod>,
) {
    /**
     * The verifier's client identifier (e.g., DNS name for x509_san_dns scheme).
     * Per ISO 18013-7 B.4.2.1, this is used to verify JWT signatures.
     */
    var clientId: String = ""

    /**
     * Optional HTTPS URL where the Authorization Request Object (JWT) can be fetched.
     * Per ISO 18013-7 B.4.2.2, this MUST use HTTPS if provided.
     */
    var requestUri: String? = null

    /**
     * HTTPS URL where the wallet POSTs the encrypted Authorization Response.
     * Per ISO 18013-7 B.4.2.3.2, this MUST use HTTPS.
     */
    var responseUri: String = ""

    /**
     * Cryptographic nonce from the Authorization Request.
     * Per ISO 18013-7 B.5.3, this MUST be at least 16 bytes (128 bits).
     */
    var nonce: String = ""

    /**
     * Optional HTTPS URL to fetch the Presentation Definition (legacy).
     * If not provided, DCQL should be included inline in the Authorization Request.
     */
    var presentationDefinitionUri: String? = null

    var version: DeviceRetrievalMethodVersion = DeviceRetrievalMethodVersion(1u)

    /**
     * Parse OID4VP parameters from a `mdoc-openid4vp://` Authorization Request URI.
     *
     * Per ISO 18013-7 Annex B, the URI typically contains only `client_id` and `request_uri`.
     * The holder must fetch the Authorization Request Object from `request_uri` to get
     * the actual parameters like `response_uri`, `nonce`, and `dcql_query`
     * (or legacy `presentation_definition`).
     *
     * @param uri The full `mdoc-openid4vp://` URI
     */
    fun fromAuthorizationRequestUri(uri: String) =
        apply {
            require(uri.startsWith("mdoc-openid4vp://")) {
                "OID4VP Authorization Request URI must start with 'mdoc-openid4vp://'"
            }

            // Parse query parameters
            val queryString = uri.substringAfter("mdoc-openid4vp://").substringAfter("?")
            val params =
                queryString.split("&").associate {
                    val (key, value) = it.split("=", limit = 2)
                    // Query-string semantics: `+` is a space, every `%HH` decodes.
                    key to value.percentDecode(plusAsSpace = true)
                }

            // Extract parameters from the URI
            // Per ISO 18013-7 B.3.1.3.2, the URI contains client_id and request_uri
            clientId = params["client_id"] ?: throw IllegalArgumentException("client_id parameter required")
            requestUri = params["request_uri"] ?: throw IllegalArgumentException("request_uri parameter required")

            // Note: response_uri, nonce, and dcql_query are NOT in the mdoc-openid4vp:// URI
            // They must be fetched from the Authorization Request Object at request_uri
            // These should be set separately after fetching the Authorization Request
        }

    /**
     * Extract OID4VP parameters from the OID4VP engagement method if available.
     * This is used when the holder receives an OID4VP engagement.
     */
    fun fromOid4vpEngagement() =
        apply {
            val engagementMethod =
                engagementMethodSet
                    .filterIsInstance<Oid4vpEngagementMethod>()
                    .firstOrNull()
                    ?: error("No OID4VP engagement method found")

            // Get the authorization request URI from the OID4VP engagement
            val uri = engagementMethod.authorizationRequestUri

            // Parse the URI to extract OID4VP parameters
            fromAuthorizationRequestUri(uri)
        }

    fun withVersion(version: DeviceRetrievalMethodVersion) =
        apply {
            this.version = version
        }

    fun withVersion1() =
        apply {
            withVersion(DeviceRetrievalMethodVersion(1u))
        }

    fun build(): DeviceRetrievalMethod {
        // If clientId not set, try to extract from engagement URI
        if (clientId.isBlank()) {
            fromOid4vpEngagement()
        }

        require(clientId.isNotBlank()) { "client_id must be specified for OID4VP" }
        require(requestUri?.isNotBlank() == true) { "request_uri must be specified for OID4VP" }

        // Note: response_uri and nonce are optional at this stage
        // Per ISO 18013-7 Annex B, these parameters are obtained by fetching
        // the Authorization Request Object from request_uri
        // The OID4VP transport layer will handle fetching these values

        return DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.OID4VP,
            version = version,
            retrievalOptions =
                Oid4vpOptions(
                    clientId = clientId,
                    requestUri = requestUri,
                    responseUri = responseUri.takeIf { it.isNotBlank() }, // Will be populated after fetching Authorization Request
                    nonce = nonce.takeIf { it.isNotBlank() }, // Will be populated after fetching Authorization Request
                    presentationDefinitionUri = presentationDefinitionUri,
                ),
        )
    }
}

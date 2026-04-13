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

package com.sphereon.mdoc.transfer.device

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborBool
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.cbor.toCborBool
import com.sphereon.cbor.toCborString
import com.sphereon.cbor.toCborUIntFromUint
import com.sphereon.cbor.toUInt
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic
import kotlin.uuid.Uuid

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalOptions", exact = true)
sealed class DeviceRetrievalOptions : CborStructure<DeviceRetrievalOptions, CborMap<NumberLabel, CborItem<*>>>(CDDL.map)


data class RestApiOptions(val uri: String) : DeviceRetrievalOptions() {
    @Suppress("UNCHECKED_CAST")
    override fun cborBuilder(): CborBuilder<RestApiOptions> = cborMapBuilder(this as DeviceRetrievalOptions) {
        URI to uri.toCborString()
    } as CborBuilder<RestApiOptions>

    companion object Decoder : HasFromCbor<CborMap<NumberLabel, CborItem<*>>, RestApiOptions> {
        @JsStatic
        val URI = NumberLabel(0)

        override fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>): RestApiOptions {
            return RestApiOptions(
                uri = (URI.required(structure) as CborString).value
            )
        }


        override fun decodeCbor(bytes: ByteArray): RestApiOptions = fromCborStructure(Cbor.decode(bytes))
    }

    override fun toString(): String {
        return "RestApiOptions(uri='$uri')"
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("WifiAwareOptions", exact = true)
data class WifiAwareOptions(
    val passPhrase: String?,
    val channelInfoOperatingClass: UInt? = null,
    val channelInfoChannelNumber: UInt? = null,
    val supportedBands: ByteArray? = null,
) : DeviceRetrievalOptions() {

    override fun cborBuilder(): CborBuilder<DeviceRetrievalOptions> = cborMapBuilder(this as DeviceRetrievalOptions) {
        optional(PASS_PHRASE, passPhrase?.toCborString())
        optional(CHANNEL_INFO_OPERATING_CLASS, channelInfoOperatingClass?.toCborUIntFromUint())
        optional(CHANNEL_INFO_CHANNEL_NUMBER, channelInfoChannelNumber?.toCborUIntFromUint())
        optional(SUPPORTED_BANDS, supportedBands?.let { CborByteString(it) })
    }

    companion object Decoder : HasFromCbor<CborMap<NumberLabel, CborItem<*>>, WifiAwareOptions> {
        @JsStatic
        val PASS_PHRASE = NumberLabel(0)

        @JsStatic
        val CHANNEL_INFO_OPERATING_CLASS = NumberLabel(1)

        @JsStatic
        val CHANNEL_INFO_CHANNEL_NUMBER = NumberLabel(2)

        @JsStatic
        val SUPPORTED_BANDS = NumberLabel(3)


        override fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>): WifiAwareOptions {
            return WifiAwareOptions(
                passPhrase = (PASS_PHRASE.optional(structure) as? CborString)?.value,
                channelInfoOperatingClass = (CHANNEL_INFO_OPERATING_CLASS.optional(structure) as? CborUInt)?.toUInt(),
                channelInfoChannelNumber = (CHANNEL_INFO_CHANNEL_NUMBER.optional(structure) as? CborUInt)?.toUInt(),
                supportedBands = (SUPPORTED_BANDS.optional(structure) as? CborByteString)?.value
            )
        }


        override fun decodeCbor(bytes: ByteArray): WifiAwareOptions = fromCborStructure(Cbor.decode(bytes))
    }

    override fun toString(): String {
        return "WifiAwareOptions(passPhrase=$passPhrase, channelInfoOperatingClass=$channelInfoOperatingClass, channelInfoChannelNumber=$channelInfoChannelNumber, supportedBands=${supportedBands?.contentToString()})"
    }

}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleOptions", exact = true)
data class BleOptions(
    val peripheralServerMode: Boolean,
    val centralClientMode: Boolean,
    // TODO: Prob number
    val peripheralServerModeUuid: Uuid? = null,
    // TODO: Prob number
    val centralClientModeUuid: Uuid? = null,
    val peripheralServerModeDeviceAddress: ByteArray? = null,
) : DeviceRetrievalOptions() {
    override fun cborBuilder(): CborBuilder<DeviceRetrievalOptions> = cborMapBuilder(this as DeviceRetrievalOptions) {
        PERIPHERAL_SERVER_MODE to peripheralServerMode.toCborBool()
        CENTRAL_CLIENT_MODE to centralClientMode.toCborBool()
        optional(PERIPHERAL_SERVER_MODE_UUID, peripheralServerModeUuid?.let { CborByteString(it.toByteArray()) })
        optional(CENTRAL_CLIENT_MODE_UUID, centralClientModeUuid?.let { CborByteString(it.toByteArray()) })
        optional(PERIPHERAL_SERVER_MODE_DEVICE_ADDRESS, peripheralServerModeDeviceAddress?.let { CborByteString(it) })
    }

    companion object Decoder : HasFromCbor<CborMap<NumberLabel, CborItem<*>>, BleOptions> {
        @JsStatic
        val PERIPHERAL_SERVER_MODE = NumberLabel(0)

        @JsStatic
        val CENTRAL_CLIENT_MODE = NumberLabel(1)

        @JsStatic
        val PERIPHERAL_SERVER_MODE_UUID = NumberLabel(10)

        @JsStatic
        val CENTRAL_CLIENT_MODE_UUID = NumberLabel(11)

        @JsStatic
        val PERIPHERAL_SERVER_MODE_DEVICE_ADDRESS = NumberLabel(20)

        override fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>) = BleOptions(
            peripheralServerMode = (PERIPHERAL_SERVER_MODE.required(structure) as CborBool).value,
            centralClientMode = (CENTRAL_CLIENT_MODE.required(structure) as CborBool).value,
            peripheralServerModeUuid = (PERIPHERAL_SERVER_MODE_UUID.optional(structure) as? CborByteString)?.value?.let { Uuid.Companion.fromByteArray(it) },
            centralClientModeUuid = (CENTRAL_CLIENT_MODE_UUID.optional(structure) as? CborByteString)?.value?.let { Uuid.Companion.fromByteArray(it) },
            peripheralServerModeDeviceAddress = (PERIPHERAL_SERVER_MODE_DEVICE_ADDRESS.optional(structure) as? CborByteString)?.value,
        )

        override fun decodeCbor(bytes: ByteArray) = fromCborStructure(Cbor.decode(bytes))


    }

    override fun toString(): String {
        return "BleOptions(peripheralServerMode=$peripheralServerMode, centralClientMode=$centralClientMode, peripheralServerModeUuid=$peripheralServerModeUuid, centralClientModeUuid=$centralClientModeUuid, peripheralServerModeDeviceAddress=${peripheralServerModeDeviceAddress})"
    }
}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcOptions", exact = true)
data class NfcOptions(
    val maxCommandDataFieldLength: UInt,
    val maxResponseDataFieldLength: UInt,
) : DeviceRetrievalOptions() {
    override fun cborBuilder(): CborBuilder<DeviceRetrievalOptions> = cborMapBuilder(this as DeviceRetrievalOptions) {
        MAX_COMMAND_DATA_FIELD_LENGTH to maxCommandDataFieldLength.toCborUIntFromUint()
        MAX_RESPONSE_DATA_FIELD_LENGTH to maxResponseDataFieldLength.toCborUIntFromUint()
    }


    companion object Decoder : HasFromCbor<CborMap<NumberLabel, CborItem<*>>, NfcOptions> {
        @JsStatic
        val MAX_COMMAND_DATA_FIELD_LENGTH = NumberLabel(0)

        @JsStatic
        val MAX_RESPONSE_DATA_FIELD_LENGTH = NumberLabel(1)

        override fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>) = NfcOptions(
            maxCommandDataFieldLength = (MAX_COMMAND_DATA_FIELD_LENGTH.required(structure) as CborUInt).value.toUInt(),
            maxResponseDataFieldLength = (MAX_RESPONSE_DATA_FIELD_LENGTH.required(structure) as CborUInt).value.toUInt()
        )

        override fun decodeCbor(bytes: ByteArray) = fromCborStructure(Cbor.decode(bytes))
    }

    override fun toString(): String {
        return "NfcOptions(maxCommandDataFieldLength=$maxCommandDataFieldLength, maxResponseDataFieldLength=$maxResponseDataFieldLength)"
    }
}

/**
 * OID4VP retrieval options for ISO 18013-7 Annex B.
 *
 * This implements the OpenID4VP retrieval method which uses:
 * - Custom URL scheme (`mdoc-openid4vp://`) for wallet invocation per ISO 18013-7 B.3.1.3.2
 * - Authorization Request with DCQL (ISO 18013-7 B.4.2); Presentation Definition is legacy/optional
 * - Direct Post JWT response mode (ISO 18013-7 B.4.3)
 * - JARM (JWT-secured Authorization Response Mode) for encrypted responses
 *
 * Per ISO 18013-7 B.3.1.3.1, wallets SHALL support static Wallet Metadata bound to
 * `mdoc-openid4vp://` scheme including:
 * - issuer: "https://self-issued.me/v2" (symbolic)
 * - authorization_endpoint: "mdoc-openid4vp://"
 * - response_types_supported: ["vp_token"]
 * - vp_formats_supported: {"mso_mdoc": {}}
 * - client_id_schemes_supported: ["x509_san_dns"]
 * - authorization_encryption_alg_values_supported: ["ECDH-ES"]
 * - authorization_encryption_enc_values_supported: ["A256GCM"]
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("does", exact = true)
 * Note: OID4VP uses JSON for metadata exchange (not CBOR), so this class does not
 * encode to CBOR for device retrieval methods. The OID4VP parameters are typically
 * exchanged via HTTP/HTTPS using JSON.
 *
 * ## Initialization
 * Per ISO 18013-7 B.3.1.3.2, the initial `mdoc-openid4vp://` URI contains only `client_id` and `request_uri`.
 * The wallet must fetch the Authorization Request Object from `request_uri` to obtain `response_uri`, `nonce`,
 * and `dcql_query` (or legacy `presentation_definition`). Therefore, `responseUri` and `nonce` are nullable and will be populated
 * after fetching the Authorization Request Object.
 *
 * @property clientId The verifier's client identifier (e.g., DNS name for x509_san_dns scheme)
 * @property requestUri HTTPS URL where Authorization Request Object (JWT) can be fetched
 * @property responseUri The HTTPS URL where the wallet POSTs the encrypted Authorization Response (null until fetched)
 * @property nonce Cryptographic nonce from the Authorization Request (null until fetched, min 16 bytes when set)
 * @property presentationDefinitionUri Optional HTTPS URL to fetch the Presentation Definition (legacy)
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpOptions", exact = true)
data class Oid4vpOptions(
    val clientId: String,
    val requestUri: String? = null,
    val responseUri: String? = null,
    val nonce: String? = null,
    val presentationDefinitionUri: String? = null,
) : DeviceRetrievalOptions() {

    init {
        // Per ISO 18013-7 B.4.2.2, request_uri MUST be HTTPS
        requestUri?.let {
            require(it.startsWith("https://")) {
                "OID4VP request_uri must use HTTPS per ISO 18013-7 B.4.2.2"
            }
        }

        // Per ISO 18013-7 B.4.2.3.2, response_uri MUST be HTTPS (when provided)
        responseUri?.let {
            if (it.isNotEmpty()) {
                require(it.startsWith("https://")) {
                    "OID4VP response_uri must use HTTPS per ISO 18013-7 B.4.2.3.2"
                }
            }
        }

        // Per ISO 18013-7 B.5.3, nonce must be at least 16 bytes (128 bits) when provided
        nonce?.let {
            if (it.isNotEmpty()) {
                require(it.length >= 16) {
                    "OID4VP nonce must be at least 16 bytes per ISO 18013-7 B.5.3"
                }
            }
        }

        presentationDefinitionUri?.let {
            require(it.startsWith("https://")) {
                "OID4VP presentation_definition_uri must use HTTPS"
            }
        }
    }

    override fun cborBuilder(): CborBuilder<DeviceRetrievalOptions> = cborMapBuilder(this as DeviceRetrievalOptions) {
        // OID4VP uses JSON for protocol parameters, not CBOR
        // This method should not be called for OID4VP
        CLIENT_ID to clientId.toCborString()
        optional(RESPONSE_URI, responseUri?.toCborString())
        optional(NONCE, nonce?.toCborString())
        optional(REQUEST_URI, requestUri?.toCborString())
        optional(PRESENTATION_DEFINITION_URI, presentationDefinitionUri?.toCborString())
    }

    companion object Decoder : HasFromCbor<CborMap<NumberLabel, CborItem<*>>, Oid4vpOptions> {
        @JsStatic
        val CLIENT_ID = NumberLabel(0)

        @JsStatic
        val RESPONSE_URI = NumberLabel(1)

        @JsStatic
        val NONCE = NumberLabel(2)

        @JsStatic
        val REQUEST_URI = NumberLabel(3)

        @JsStatic
        val PRESENTATION_DEFINITION_URI = NumberLabel(4)

        override fun fromCborStructure(structure: CborMap<NumberLabel, CborItem<*>>) = Oid4vpOptions(
            clientId = (CLIENT_ID.required(structure) as CborString).value,
            responseUri = (RESPONSE_URI.optional(structure) as? CborString)?.value,
            nonce = (NONCE.optional(structure) as? CborString)?.value,
            requestUri = (REQUEST_URI.optional(structure) as? CborString)?.value,
            presentationDefinitionUri = (PRESENTATION_DEFINITION_URI.optional(structure) as? CborString)?.value,
        )

        override fun decodeCbor(bytes: ByteArray) = fromCborStructure(Cbor.decode(bytes))
    }

    override fun toString(): String {
        return "Oid4vpOptions(clientId='$clientId', requestUri=$requestUri, responseUri='$responseUri', nonce='***', presentationDefinitionUri=$presentationDefinitionUri)"
    }
}

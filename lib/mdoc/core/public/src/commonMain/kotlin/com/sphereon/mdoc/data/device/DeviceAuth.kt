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

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborString
import com.sphereon.cbor.StringLabel
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import com.sphereon.util.stringify
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmInline
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceAuthType", exact = true)
enum class DeviceAuthType {
    SIGNATURE,
    MAC,
}

@Serializable
@JvmInline
value class DeviceMac(
    private val mac: String,
) {
    fun toCborItem(): CborString = CborString(mac)

    override fun toString(): String = mac

    companion object {
        fun fromCborItem(structure: CborString): DeviceMac = DeviceMac(structure.value)
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceAuth", exact = true)
data class DeviceAuth(
    val deviceSignature: COSE_Sign1<DeviceAuthentication>? = null,
    val deviceMac: DeviceMac? = null, // DeviceMac FIXME
    val original: ByteArray?,
) {
    init {
        assertValidState()
    }

    fun getAuthType(): DeviceAuthType {
        assertValidState()
        return if (deviceSignature != null) {
            DeviceAuthType.SIGNATURE
        } else {
            DeviceAuthType.MAC
        }
    }

    private fun assertValidState() {
        check(this.deviceSignature !== null || this.deviceMac !== null) {
            "Either a device signature or MAC should be present"
        }
        check(this.deviceMac == null || this.deviceSignature == null) {
            "Cannot have both a device signature and MAC at the same time"
        }
        if (this.deviceMac !== null) {
            throw NotImplementedError("Device MAC is not implemented yet. Only signatures supported for now")
        }
    }

    override fun toString(): String = "DeviceAuth(deviceSignature=$deviceSignature, deviceMac=$deviceMac, original=${stringify(original)})"

    companion object {
        @JsStatic
        val DEVICE_SIGNATURE = StringLabel("deviceSignature")

        @JsStatic
        val DEVICE_MAC = StringLabel("deviceMac")
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceAuthentication", exact = true)
data class DeviceAuthentication(
    val sessionTranscript: SessionTranscript,
    val docType: DocType,
    val deviceNamespaces: DeviceNameSpaces,
    val original: ByteArray?,
) {
    companion object {
        @JsStatic
        fun fromOid4vp(
            clientId: String,
            responseUri: String,
            mdocNonce: String = Uuid.v4String(),
            authorizationRequestNonce: String,
            docType: DocType,
            deviceNamespaces: DeviceNameSpaces,
        ): DeviceAuthentication {
            val sessionTranscript =
                SessionTranscript.fromOid4vpClientIdAndResponseUri(
                    clientId = clientId,
                    responseUri = responseUri,
                    mdocNonce = mdocNonce,
                    authorizationRequestNonce = authorizationRequestNonce,
                )
            return DeviceAuthentication(
                sessionTranscript = sessionTranscript,
                docType = docType,
                deviceNamespaces = deviceNamespaces,
                original = null,
            )
        }
    }
}

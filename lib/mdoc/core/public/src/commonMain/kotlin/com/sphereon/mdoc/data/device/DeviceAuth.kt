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

package com.sphereon.mdoc.data.device

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import kotlinx.serialization.Serializable
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCbor
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.HasToCbor
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.dsl.cborArrayBuilder
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.core.compat.Uuid
import com.sphereon.util.stringify
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic
import kotlin.jvm.JvmInline

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceAuthType", exact = true)
enum class DeviceAuthType {
    SIGNATURE, MAC
}


@Serializable
@JvmInline
value class DeviceMac(private val mac: String) : HasToCbor<CborString> {
    override fun toCborStructure(): CborString = CborString(mac)

    companion object: HasFromCbor<CborString, DeviceMac> {
        override fun fromCborStructure(structure: CborString): DeviceMac = DeviceMac(structure.value)
    }

    override fun toString(): String = mac
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceAuth", exact = true)
data class DeviceAuth(
    val deviceSignature: COSE_Sign1<DeviceAuthentication>? = null,
    val deviceMac: DeviceMac? = null, //DeviceMac FIXME
    override val original: ByteArray?,
) : CborStructure<DeviceAuth, CborMap<StringLabel, CborItem<*>>>(cddl = CDDL.map) {
    init {
        assertValidState()
    }

    override fun cborBuilder(): CborBuilder<DeviceAuth> {
        assertValidState()
        return cborMapBuilder(this) {
            if (deviceSignature !== null) {
                DEVICE_SIGNATURE to deviceSignature
            } else {
                DEVICE_MAC to (deviceMac ?: throw IllegalStateException("Device MAC should not be null if no signature is used"))
            }
        }
    }

    fun getAuthType(): DeviceAuthType {
        assertValidState()
        return if (deviceSignature != null) DeviceAuthType.SIGNATURE else DeviceAuthType.MAC
    }




    companion object: HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, DeviceAuth> {
        @JsStatic
        val DEVICE_SIGNATURE = StringLabel("deviceSignature")

        @JsStatic
        val DEVICE_MAC = StringLabel("deviceMac")


        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>): DeviceAuth {
            val mac: CborString? = DEVICE_MAC.optional(structure)
            val deviceAuth = DeviceAuth(
                deviceMac = mac?.let { DeviceMac.fromCborStructure(it)},
                deviceSignature = DEVICE_SIGNATURE.optional(structure),
                original = null,
            )
            return deviceAuth
        }

        override fun fromCborStructureWithOriginal(structure: CborMap<StringLabel, CborItem<*>>, original: ByteArray?): DeviceAuth {
            return fromCborStructure(structure).copy(original = original)
        }

        override fun decodeCbor(bytes: ByteArray): DeviceAuth = fromCborStructure(cborSerializer.decode(bytes))
    }

    private fun assertValidState() {
        if (this.deviceSignature === null && this.deviceMac === null) {
            throw IllegalStateException("Either a device signature or MAC should be present")
        } else if (this.deviceMac !== null && this.deviceSignature !== null) {
            throw IllegalStateException("Cannot have both a device signature and MAC at the same time")
        } else if (this.deviceMac !== null) {
            throw NotImplementedError("Device MAC is not implemented yet. Only signatures supported for now")
        }
    }

    override fun toString(): String {
        return "DeviceAuth(deviceSignature=$deviceSignature, deviceMac=$deviceMac, original=${stringify(original)})"
    }


}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceAuthentication", exact = true)
data class DeviceAuthentication(
    val sessionTranscript: SessionTranscript,
    val docType: DocType,
    val deviceNamespaces: DeviceNameSpaces,
    override val original: ByteArray?,
) : CborStructure<DeviceAuthentication, CborArray<CborItem<*>>>(cddl = CDDL.list, original = original) {
    override fun cborBuilder(): CborBuilder<DeviceAuthentication> = cborArrayBuilder(this) {
        add("DeviceAuthentication")
        add(sessionTranscript.toCborStructure())
        add(docType)
        add(CborEncodedItem.fromData(deviceNamespaces.toCborStructure()))
    }


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
            val sessionTranscript = SessionTranscript.fromOid4vpClientIdAndResponseUri(
                clientId = clientId,
                responseUri = responseUri,
                mdocNonce = mdocNonce,
                authorizationRequestNonce = authorizationRequestNonce
            )
            return DeviceAuthentication(
                sessionTranscript = sessionTranscript,
                docType = docType,
                deviceNamespaces = deviceNamespaces,
                original = null
            )
        }
    }
}

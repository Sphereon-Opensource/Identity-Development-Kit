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

package com.sphereon.mdoc.data.mso

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.util.stringify
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.core.compat.JsExportCompat

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceKeyInfoCbor", exact = true)
data class DeviceKeyInfoCbor(
    val deviceKey: CoseKey,
    val keyAuthorizations: KeyAuthorizationsCbor? = null,
    val keyInfo: KeyInfoCborAlias? = null,
    override val original: ByteArray?,
) : CborStructure<DeviceKeyInfoCbor, CborMap<StringLabel, CborItem<*>>>(CDDL.map) {
    override fun cborBuilder(): CborBuilder<DeviceKeyInfoCbor> = cborMapBuilder(this) {
        DEVICE_KEY to deviceKey.toCborStructure()
        optional(KEY_AUTHORIZATIONS, keyAuthorizations?.toCborStructure())
        optional(KEY_INFO, keyInfo)
    }


    fun toKeyInfo(): ResolvedKeyInfoType<CoseKeyType> {
        return ResolvedKeyInfo.fromKey(deviceKey)
    }

    override fun toString(): String {
        return "DeviceKeyInfoCbor(deviceKey=$deviceKey, keyAuthorizations=$keyAuthorizations, keyInfo=$keyInfo, original=${stringify(original)})"
    }

    companion object Decoder : HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, DeviceKeyInfoCbor> {
        val DEVICE_KEY = StringLabel("deviceKey")
        val KEY_AUTHORIZATIONS = StringLabel("keyAuthorizations")
        val KEY_INFO = StringLabel("keyInfo")

        fun fromKeyInfo(keyInfo: ResolvedKeyInfoType<*>): DeviceKeyInfoCbor {
            val cborInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(keyInfo)

            return DeviceKeyInfoCbor(deviceKey = cborInfo.key, original = null)
        }

        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>) = DeviceKeyInfoCbor(
            CoseKey.fromCborStructure(DEVICE_KEY.required(structure)),
            KEY_AUTHORIZATIONS.optional<CborMap<StringLabel, CborItem<*>>>(structure)?.let {
                KeyAuthorizationsCbor.fromCborStructure(it)
            },
            KEY_INFO.optional(structure),
            original = null,
        )

        override fun fromCborStructureWithOriginal(structure: CborMap<StringLabel, CborItem<*>>, original: ByteArray?): DeviceKeyInfoCbor {
            return fromCborStructure(structure).copy(original = original)
        }

        override fun decodeCbor(bytes: ByteArray) = fromCborStructureWithOriginal(cborSerializer.decode(bytes), bytes)
    }




}

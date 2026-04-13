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

package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.StringLabel
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.util.stringify
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceKeyInfo", exact = true)
data class DeviceKeyInfo(
    val deviceKey: CoseKey,
    val keyAuthorizations: KeyAuthorizations? = null,
    val keyInfo: KeyInfoAlias? = null,
    val original: ByteArray?,
) {
    fun toKeyInfo(): ResolvedKeyInfoType<CoseKeyType> = ResolvedKeyInfo.fromKey(deviceKey)

    override fun toString(): String = "DeviceKeyInfo(deviceKey=$deviceKey, keyAuthorizations=$keyAuthorizations, keyInfo=$keyInfo, original=${stringify(original)})"

    companion object {
        val DEVICE_KEY = StringLabel("deviceKey")
        val KEY_AUTHORIZATIONS = StringLabel("keyAuthorizations")
        val KEY_INFO = StringLabel("keyInfo")

        fun fromKeyInfo(keyInfo: ResolvedKeyInfoType<*>): DeviceKeyInfo {
            val cborInfo = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(keyInfo)

            return DeviceKeyInfo(deviceKey = cborInfo.key, original = null)
        }
    }
}

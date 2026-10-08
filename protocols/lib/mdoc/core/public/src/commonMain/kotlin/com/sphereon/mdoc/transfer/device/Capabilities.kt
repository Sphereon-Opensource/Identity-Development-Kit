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
 */

package com.sphereon.mdoc.transfer.device

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.NumberLabel
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.cose.CoseCurve
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Capabilities", exact = true)
data class Capabilities(
    val macKeysSupport: Boolean? = null,
    val macKeyCurves: Array<CoseCurve>? = null,
    val handoverSessionEstablishmentSupport: Boolean? = null,
    val readerAuthAllSupport: Boolean? = null,
    val extendedRequestSupport: Boolean? = null,
    val additionalItems: CborMap<NumberLabel, CborItem<*>>? = null,
) {
    companion object {
        @JvmStatic
        val MAC_KEYS_SUPPORT = NumberLabel(0)

        @JvmStatic
        val MAC_KEY_CURVES = NumberLabel(1)

        @JvmStatic
        val HANDOVER_SESSION_ESTABLISHMENT_SUPPORT = NumberLabel(2)

        @JvmStatic
        val READER_AUTH_ALL_SUPPORT = NumberLabel(3)

        @JvmStatic
        val EXTENDED_REQUEST_SUPPORT = NumberLabel(4)
    }
}

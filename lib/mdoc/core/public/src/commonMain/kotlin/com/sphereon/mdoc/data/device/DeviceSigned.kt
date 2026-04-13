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

import com.sphereon.cbor.StringLabel
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.util.stringify
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Device Signed are essentially self-asserted claims/dataElements, contrary to issuer signed items, which are externally asserted
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceSigned", exact = true)
data class DeviceSigned(
    val nameSpaces: DeviceNameSpaces = DeviceNameSpaces(),
    val deviceAuth: DeviceAuth,
    val original: ByteArray?,
) {
    override fun toString(): String = "DeviceSigned(nameSpaces=$nameSpaces, deviceAuth=$deviceAuth, original=${stringify(original)})"

    companion object {
        val NAME_SPACES = StringLabel("nameSpaces")
        val DEVICE_AUTH = StringLabel("deviceAuth")
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceNameSpaces", exact = true)
data class DeviceNameSpaces(
    val value: Map<NameSpace, DeviceSignedItems> = mutableMapOf(),
) {
    constructor(vararg items: DeviceNameSpace) : this(items.toMap())

    override fun toString(): String = "DeviceNameSpaces(value=${stringify(value)})"
}

typealias DeviceNameSpace = Pair<NameSpace, DeviceSignedItems>

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceSignedItems", exact = true)
data class DeviceSignedItems(
    val value: Map<DataElementIdentifier, Any> = mutableMapOf(),
) {
    constructor(vararg items: DeviceSignedItem) : this(items.toMap())

    override fun toString(): String = "DeviceSignedItems(value=${stringify(value)})"
}

typealias DeviceSignedItem = Pair<DataElementIdentifier, Any>

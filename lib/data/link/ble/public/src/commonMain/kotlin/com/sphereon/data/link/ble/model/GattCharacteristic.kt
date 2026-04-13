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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.link.ble.model

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalObjCName::class)
@ObjCName("CharacteristicWriteMode", exact = true)
enum class CharacteristicWriteMode(
    val value: Int,
) {
    /** Write characteristic without requiring a response by the remote device  */
    WRITE_TYPE_NO_RESPONSE(0x01),

    /**
     * Write characteristic, requesting acknowledgement by the remote device
     */
    WRITE_TYPE_DEFAULT(0x02),

    /** Write characteristic including authentication signature  */

    WRITE_TYPE_SIGNED(0x04),
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("GattCharacteristic", exact = true)
data class GattCharacteristic(
    override val id: Uuid,
    val properties: Set<GattProperty>,
    val service: GattService? = null,
) : HasUuidId {
    override fun toString(): String = "GattCharacteristic(id=$id, service=${service?.id ?: "<not-populated>"}, properties=$properties)"
}

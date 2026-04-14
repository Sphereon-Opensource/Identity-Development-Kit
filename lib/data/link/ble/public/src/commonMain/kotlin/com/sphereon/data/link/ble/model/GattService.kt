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

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("GattService", exact = true)
data class GattService(
    override val id: Uuid,
    val characteristics: List<GattCharacteristic>,
) : HasUuidId {
    override fun toString(): String = "GattService(id=$id, characteristics=$characteristics)"

    fun hasCharacteristic(characteristic: GattCharacteristic): Boolean = characteristics.any { it.id == characteristic.id }

    fun hasCharacteristicById(characteristicId: Uuid): Boolean = characteristics.any { it.id == characteristicId }

    fun getCharacteristic(characteristicId: Uuid): GattCharacteristic? = characteristics.firstOrNull { it.id == characteristicId }

    fun getCharacteristicById(characteristic: GattCharacteristic): GattCharacteristic? = characteristics.firstOrNull { it.id == characteristic.id }
}

fun Collection<GattService>.hasService(service: GattService): Boolean = this.any { it.id == service.id }

fun Collection<GattService>.getServiceById(serviceId: Uuid): GattService? = this.firstOrNull { it.id == serviceId }

fun Collection<GattService>.getServiceByCharacteristic(characteristic: GattCharacteristic): GattService? =
    this.firstOrNull { gattService -> gattService.characteristics.find { character -> character.id == characteristic.id } != null }

fun Collection<GattService>.getServiceByCharacteristicId(characteristicId: Uuid): GattService? =
    this.firstOrNull { gattService -> gattService.characteristics.find { characteristic -> characteristic.id == characteristicId } != null }

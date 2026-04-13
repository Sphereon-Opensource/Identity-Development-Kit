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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.link.ble.model

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.ExperimentalUuidApi


@OptIn(ExperimentalObjCName::class)


@ObjCName("IHasAddress", exact = true)


interface IHasAddress {
    val address: String
}

fun toAddress(value: String) = object : IHasAddress {
    override val address: String = value

    override fun toString(): String {
        return address
    }
}


@OptIn(ExperimentalObjCName::class)


@ObjCName("BleDevice", exact = true)


data class BleDevice(
    override val address: String,
    val name: String?,
    val rssi: Int? = null,
    val services: List<HasUuidId>? = null
): IHasAddress {
    override fun toString(): String {
        return "BleDevice(address='$address', name=$name, rssi=$rssi, services=$services)"
    }
}
/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.mdoc.engagement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.uuid.Uuid

/**
 * Implementation of SharedParameters for managing shared BLE UUIDs and ephemeral keys.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SharedParametersImpl", exact = true)
class SharedParametersImpl : SharedParameters {
    private val _bleCentralClientUuid = MutableStateFlow(Uuid.Companion.random())
    override val bleCentralClientUuid: StateFlow<Uuid> = _bleCentralClientUuid.asStateFlow()

    private val _blePeripheralServerUuid = MutableStateFlow(Uuid.Companion.random())
    override val blePeripheralServerUuid: StateFlow<Uuid> = _blePeripheralServerUuid.asStateFlow()

    private val _ephemeralKeyAlias = MutableStateFlow(generateEphemeralKeyAlias())
    override val ephemeralKeyAlias: StateFlow<String> = _ephemeralKeyAlias.asStateFlow()

    override suspend fun regenerate() {
        _bleCentralClientUuid.value = Uuid.Companion.random()
        _blePeripheralServerUuid.value = Uuid.Companion.random()
        _ephemeralKeyAlias.value = generateEphemeralKeyAlias()
    }

    override suspend fun useSameUuidForBothModes(uuid: Uuid?) {
        val sharedUuid = uuid ?: Uuid.Companion.random()
        _bleCentralClientUuid.value = sharedUuid
        _blePeripheralServerUuid.value = sharedUuid
    }

    private fun generateEphemeralKeyAlias(): String = "ephemeral-key-${Uuid.Companion.random()}"
}

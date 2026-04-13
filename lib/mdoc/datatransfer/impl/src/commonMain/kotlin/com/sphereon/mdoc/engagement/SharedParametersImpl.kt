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

    private fun generateEphemeralKeyAlias(): String {
        return "ephemeral-key-${Uuid.Companion.random()}"
    }
}
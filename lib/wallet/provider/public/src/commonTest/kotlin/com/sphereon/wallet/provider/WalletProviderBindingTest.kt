/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.provider

import com.sphereon.data.store.party.model.PartyRef
import com.sphereon.data.store.party.model.PartyType
import com.sphereon.wallet.wscd.WscdProfile
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletProviderBindingTest {
    @Test
    fun localBindingRoundTripsThroughJson() {
        val binding =
            WalletProviderBinding(
                providerId = "local-wallet-provider",
                kind = WalletProviderKind.LOCAL,
                descriptor =
                    WalletUnitDescriptor(
                        walletUnitId = "wu-personal",
                        walletInstanceId = "wi-personal",
                        organizationUnitRef = organizationUnitRef("ou-personal"),
                        wscdProfile = WscdProfile.Software,
                        status = WalletUnitStatus.ACTIVE,
                    ),
            )

        val json = Json.encodeToString(WalletProviderBinding.serializer(), binding)
        val decoded = Json.decodeFromString(WalletProviderBinding.serializer(), json)

        assertEquals(binding, decoded)
    }

    @Test
    fun managedBindingRoundTripsThroughJson() {
        val binding =
            WalletProviderBinding(
                providerId = "tenant-a",
                kind = WalletProviderKind.MANAGED,
                descriptor =
                    WalletUnitDescriptor(
                        walletUnitId = "wu-employee",
                        walletInstanceId = "wi-employee",
                        organizationUnitRef = organizationUnitRef("ou-employee"),
                        wscdProfile = WscdProfile.Remote,
                        status = WalletUnitStatus.PROVISIONING,
                    ),
            )

        val json = Json.encodeToString(WalletProviderBinding.serializer(), binding)
        val decoded = Json.decodeFromString(WalletProviderBinding.serializer(), json)

        assertEquals(binding, decoded)
        assertEquals(WalletProviderKind.MANAGED, decoded.kind)
    }

    private fun organizationUnitRef(partyId: String) =
        PartyRef(
            partyId = partyId,
            type = PartyType.ORGANIZATION_UNIT,
            displayName = "Test business unit",
        )
}

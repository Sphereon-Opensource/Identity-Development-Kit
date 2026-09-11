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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WalletProviderBindingTest {
    @Test
    fun unitProvisioningRequestRoundTripsItsRequiredProviderKind() {
        val request =
            UnitProvisioningRequest(
                profileId = "personal",
                wscdProfile = WscdProfile.Software,
                providerKind = WalletProviderKind.LOCAL,
            )

        val json = Json.encodeToString(UnitProvisioningRequest.serializer(), request)
        val decoded = Json.decodeFromString(UnitProvisioningRequest.serializer(), json)

        assertEquals(request, decoded)
        assertEquals(WalletProviderKind.LOCAL, decoded.providerKind)
    }

    @Test
    fun unitProvisioningRequestRejectsMissingProviderKind() {
        val json =
            Json.encodeToString(
                UnitProvisioningRequest.serializer(),
                UnitProvisioningRequest(
                    profileId = "personal",
                    wscdProfile = WscdProfile.Software,
                    providerKind = WalletProviderKind.LOCAL,
                ),
            ).replace(",\"providerKind\":\"LOCAL\"", "")

        assertFailsWith<SerializationException> {
            Json.decodeFromString(UnitProvisioningRequest.serializer(), json)
        }
    }

    @Test
    fun unitProvisioningRequestRejectsUnknownProviderKind() {
        val json =
            Json.encodeToString(
                UnitProvisioningRequest.serializer(),
                UnitProvisioningRequest(
                    profileId = "personal",
                    wscdProfile = WscdProfile.Software,
                    providerKind = WalletProviderKind.LOCAL,
                ),
            ).replace("\"providerKind\":\"LOCAL\"", "\"providerKind\":\"UNKNOWN\"")

        assertFailsWith<SerializationException> {
            Json.decodeFromString(UnitProvisioningRequest.serializer(), json)
        }
    }

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

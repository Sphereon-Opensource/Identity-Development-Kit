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
import kotlinx.serialization.Serializable

@Serializable
data class UnitProvisioningRequest(
    val profileId: String,
    val wscdProfile: WscdProfile,
    /** Display name used when the provider provisions or resolves the Wallet Unit's Business Unit. */
    val businessUnitDisplayName: String = profileId,
    /** Required assignment for managed providers; local providers create and return one. */
    val assignedOrganizationUnitRef: PartyRef? = null,
    /** Opaque provider-specific options (e.g. tenant, install-link token). */
    val options: Map<String, String> = emptyMap(),
)

@Serializable
data class WalletUnitDescriptor(
    val walletUnitId: String,
    val walletInstanceId: String,
    /** Required Business Unit Party assigned to this logical Wallet Unit. */
    val organizationUnitRef: PartyRef,
    val wscdProfile: WscdProfile,
    val status: WalletUnitStatus,
) {
    init {
        require(organizationUnitRef.type == PartyType.ORGANIZATION_UNIT) {
            "wallet_unit_business_unit_ref_wrong_party_type"
        }
        require(organizationUnitRef.partyId.isNotBlank()) { "wallet_unit_business_unit_ref_blank" }
    }
}

@Serializable
enum class WalletUnitStatus { PROVISIONING, ACTIVE, SUSPENDED, REVOKED }

@Serializable
enum class RevocationReason { USER_REQUEST, COMPROMISE, SUPERSEDED, PROVIDER_POLICY }

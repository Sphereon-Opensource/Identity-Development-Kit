/* Copyright 2026 Sphereon International B.V. */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.wallet.party

import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.Identity
import com.sphereon.data.store.party.model.IdentityIdentifier
import com.sphereon.data.store.party.model.IdentityPartyBinding
import com.sphereon.data.store.party.model.IdentityPrivacyMode
import com.sphereon.data.store.party.model.IdentityRole
import com.sphereon.data.store.party.model.Party
import com.sphereon.data.store.party.model.PartyOrigin
import com.sphereon.data.store.party.model.PartyType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WalletPartyModelsTest {
    @Test
    fun `known counterparty is an Organization with a role Identity and typed identifiers`() {
        knownOrganization()
    }

    @Test
    fun `a service party cannot masquerade as a wallet Organization contact`() {
        val valid = knownOrganization()
        assertFailsWith<IllegalArgumentException> {
            valid.copy(party = valid.party.copy(partyType = PartyType.SERVICE))
        }
    }

    private fun knownOrganization(): WalletKnownOrganization {
        val now = Instant.fromEpochSeconds(10)
        val organizationId = Uuid.random()
        val businessUnitId = Uuid.random()
        val identityId = Uuid.random()
        val party = Party(organizationId, "personal", PartyType.ORGANIZATION, PartyOrigin.CEREMONY_DISCOVERY, "Example issuer", "https://issuer.example", organizationUnitId = businessUnitId, createdAt = now, updatedAt = now)
        val identity = Identity(identityId, "personal", IdentityRole.ISSUER, privacyMode = IdentityPrivacyMode.PARTY_PROFILED, createdAt = now, updatedAt = now)
        val binding = IdentityPartyBinding(identityId, organizationId, PartyType.ORGANIZATION, "organization_identity", now)
        val identifier = IdentityIdentifier(Uuid.random(), identityId, "personal", IdentifierType.OID4VCI_ISSUER, "https://issuer.example", isPrimary = true, validFrom = now, createdAt = now, updatedAt = now)
        return WalletKnownOrganization(
            party,
            listOf(WalletKnownOrganizationIdentity(identity, binding, listOf(identifier), 10, 10, 1)),
        )
    }
}
